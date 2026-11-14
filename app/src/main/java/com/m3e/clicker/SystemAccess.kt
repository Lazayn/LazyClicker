package com.m3e.clicker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** 后台运行相关的系统能力检查 / 跳转（通知、电池优化、无障碍） */
object SystemAccess {

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission(activity)) return
        try {
            activity.requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        } catch (_: Throwable) {
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Throwable) {
            false
        }
    }

    /** 直接弹系统「允许忽略电池优化」对话框；失败时退化为打开电池优化列表 */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            openBatterySettings(context)
            false
        }
    }

    fun openBatterySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
        }
    }

    /** 无障碍服务是否已启用（通过系统已启用列表判断，不依赖服务进程是否已连上） */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val component = ComponentName(context, ClickerAccessibilityService::class.java)
        return try {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            enabled.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(serviceInfo.packageName, serviceInfo.name) == component
            }
        } catch (_: Throwable) {
            ClickerAccessibilityService.isConnected()
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
        }
    }

    fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
        }
    }

    const val REQUEST_NOTIFICATIONS = 4201
}
