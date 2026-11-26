package com.m3e.clicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 常驻前台服务：让连点在后台继续运行，并在通知栏给出状态 + 一键停止。
 * 参考 KeyMapper 的做法（常驻通知 + 前台服务，避免被系统回收）。
 */
open class ClickerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "m3e_clicker_status"
        private const val NOTIFICATION_ID = 1001
        // 60Hz 足够流畅：再高只会增加跨进程 binder 调用，反而拖慢注入
        private const val POLL_INTERVAL_MS = 16L

        const val ACTION_STOP = "com.m3e.clicker.action.STOP"
        const val ACTION_OVERLAY_ON = "com.m3e.clicker.action.OVERLAY_ON"
        const val ACTION_OVERLAY_OFF = "com.m3e.clicker.action.OVERLAY_OFF"
        const val ACTION_OVERLAY_UPDATE = "com.m3e.clicker.action.OVERLAY_UPDATE"
        const val ACTION_TRAIL_ON = "com.m3e.clicker.action.TRAIL_ON"
        const val ACTION_TRAIL_OFF = "com.m3e.clicker.action.TRAIL_OFF"
        const val ACTION_TRAIL_UPDATE = "com.m3e.clicker.action.TRAIL_UPDATE"
        const val ACTION_RECENTER = "com.m3e.clicker.action.RECENTER"

        /** 开启 / 关闭 / 刷新悬浮窗；实际跑在主进程还是 :ghost 由幽灵模式决定 */
        fun overlay(context: Context, action: String) = OverlayDaemon.overlay(context, action)

        fun start(context: Context) = OverlayDaemon.start(context)

        fun stop(context: Context) = OverlayDaemon.stopAll(context)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var bridge: ClickerBridge

    /** 悬浮窗开关键上一次的触发计数，用于识别新的按下 */
    private var lastOverlaySeq = -1

    /** 校准键上一次的触发计数 */
    private var lastCalibSeq = -1

    override fun onCreate() {
        super.onCreate()
        bridge = ClickerBridge.get(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("连点服务运行中", "等待状态…"))
        // 被系统按 START_STICKY 拉起来时 intent 为空：按持久化配置把悬浮窗补回来
        if (OverlayHud.enabled(this)) OverlayHud.show(this)
        scope.launch {
            bridge.status.collect { status ->
                val title = when {
                    status.clicking -> "连点中"
                    status.trigger -> "监听鼠标左键"
                    else -> "连点服务运行中"
                }
                val text = "${status.minCps}–${status.maxCps} CPS · " +
                    if (status.pressed) "左键已按住" else "等待左键"
                refresh(title, text)
                if (lastOverlaySeq < 0) {
                    lastOverlaySeq = status.overlaySeq
                } else if (status.overlaySeq != lastOverlaySeq) {
                    lastOverlaySeq = status.overlaySeq
                    OverlayHud.toggle(this@ClickerForegroundService)
                }
                if (lastCalibSeq < 0) {
                    lastCalibSeq = status.calibSeq
                } else if (status.calibSeq != lastCalibSeq) {
                    lastCalibSeq = status.calibSeq
                    TrailOverlay.recenter()
                }
                OverlayHud.update(status)
                TrailOverlay.update(status)
            }
        }
        startTrailPolling()
    }

    /** 轨迹取点：固定 60Hz，只读内存标志，不碰 SharedPreferences；窗口/轨迹关闭时完全空转 */
    private fun startTrailPolling() {
        scope.launch(Dispatchers.Default) {
            while (true) {
                kotlinx.coroutines.delay(POLL_INTERVAL_MS)
                if (!OverlayHud.isShowing() || !OverlayHud.trailNow) continue
                val point = bridge.trailPoint() ?: continue
                withContext(Dispatchers.Main.immediate) {
                    OverlayHud.feedCursor(point.first, point.second)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                bridge.stopTrigger()
                ClickerBridge.get(this).stopManual()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_OVERLAY_ON -> if (OverlayHud.enabled(this)) OverlayHud.show(this)

            ACTION_OVERLAY_UPDATE -> if (OverlayHud.enabled(this)) {
                if (OverlayHud.isShowing()) OverlayHud.apply(this) else OverlayHud.show(this)
            } else {
                OverlayHud.hide()
            }

            ACTION_OVERLAY_OFF -> OverlayHud.hide()

            ACTION_RECENTER -> if (OverlayHud.isShowing()) OverlayHud.recenterTrail()

            // 轨迹画在悬浮窗内部：这里的动作只是让窗口按新配置重绘
            ACTION_TRAIL_ON, ACTION_TRAIL_UPDATE, ACTION_TRAIL_OFF ->
                if (OverlayHud.enabled(this)) {
                    if (OverlayHud.isShowing()) OverlayHud.apply(this) else OverlayHud.show(this)
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        OverlayHud.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var lastNotifTitle = ""
    private var lastNotifText = ""

    /** 文案没变就不重复 notify，避免每 250ms 一次系统 IPC 拖慢输入 */
    private fun refresh(title: String, text: String) {
        if (title == lastNotifTitle && text == lastNotifText) return
        lastNotifTitle = title
        lastNotifText = text
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(title, text))
        } catch (_: Throwable) {
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "连点器运行状态",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示连点器是否正在监听鼠标左键"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        } catch (_: Throwable) {
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, javaClass).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, "停止连点", stopIntent)
            .build()
    }
}
