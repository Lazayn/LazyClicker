package com.m3e.clicker

import android.app.Application
import android.content.Context
import android.os.Build
import rikka.shizuku.ShizukuProvider

/**
 * 幽灵模式让 App 有了第二个进程（:ghost），Shizuku 的 binder 需要显式打开多进程转发，
 * 否则 :ghost 里的 ClickerBridge 拿不到 Shizuku，悬浮窗读不到状态。
 */
class ClickerApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val main = base.packageName == processName(base)
        ShizukuProvider.enableMultiProcessSupport(main)
    }

    override fun onCreate() {
        super.onCreate()
        if (packageName != processName(this)) {
            ShizukuProvider.requestBinderForNonProviderProcess(this)
        }
    }

    private fun processName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        return try {
            java.io.File("/proc/self/cmdline").readText().trim().substringBefore('\u0000')
        } catch (_: Throwable) {
            null
        }
    }
}
