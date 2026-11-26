package com.m3e.clicker

import android.content.Context

/**
 * 幽灵模式：悬浮窗与常驻服务跑在独立进程（:ghost）里。
 *
 * 从最近任务里划掉 App 只会结束主进程的 Activity/界面，带前台通知的 :ghost 进程
 * 仍会活着，悬浮窗继续显示、连点继续工作；再打开 App 时界面重新连上同一份配置。
 * 原理与 KeyMapper 把输入/悬浮层放在独立服务进程一致：界面只是控制端，随时可以死。
 */
object GhostMode {

    private const val KEY_GHOST = "ghostMode"

    fun enabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GHOST, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_GHOST, on).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(OverlayHud.PREFS_NAME, Context.MODE_PRIVATE)
}
