package com.m3e.clicker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 悬浮窗/常驻服务的统一入口：
 * - 普通模式：ClickerForegroundService，跟随 App 主进程；
 * - 幽灵模式：ClickerGhostService（:ghost 进程），清后台后仍然存活。
 *
 * 界面只认识这里，不直接 Intents 到具体服务，切换幽灵模式时也不会残留另一条通知。
 */
object OverlayDaemon {

    fun serviceClass(context: Context): Class<out Service> =
        if (GhostMode.enabled(context)) ClickerGhostService::class.java
        else ClickerForegroundService::class.java

    fun overlay(context: Context, action: String) {
        start(context, action)
    }

    fun start(context: Context) {
        start(context, null)
    }

    /** 校准：轨迹层光标回中（悬浮窗可能在另一个进程） */
    fun recenter(context: Context) {
        start(context, ClickerForegroundService.ACTION_RECENTER)
    }

    /** 停止两个进程里的服务，避免模式切换后旧通知残留 */
    fun stopAll(context: Context) {
        try {
            context.stopService(Intent(context, ClickerForegroundService::class.java))
        } catch (_: Throwable) {
        }
        try {
            context.stopService(Intent(context, ClickerGhostService::class.java))
        } catch (_: Throwable) {
        }
    }

    private fun start(context: Context, action: String?) {
        val cls = serviceClass(context)
        val intent = Intent(context, cls)
        if (action != null) intent.action = action
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Throwable) {
        }
    }
}
