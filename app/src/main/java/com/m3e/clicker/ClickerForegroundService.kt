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

/**
 * 常驻前台服务：让连点在后台继续运行，并在通知栏给出状态 + 一键停止。
 * 参考 KeyMapper 的做法（常驻通知 + 前台服务，避免被系统回收）。
 */
class ClickerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "m3e_clicker_status"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.m3e.clicker.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ClickerForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ClickerForegroundService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var bridge: ClickerBridge

    override fun onCreate() {
        super.onCreate()
        bridge = ClickerBridge.get(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("连点服务运行中", "等待状态…"))
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
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            bridge.stopTrigger()
            ClickerBridge.get(this).stopManual()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refresh(title: String, text: String) {
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
            Intent(this, ClickerForegroundService::class.java).setAction(ACTION_STOP),
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
