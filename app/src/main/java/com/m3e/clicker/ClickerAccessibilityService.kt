package com.m3e.clicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：作为**备用注入通道**（KeyMapper 同样提供无障碍回退）。
 *
 * 用途：当没有 Shizuku（或 Shizuku 注入被 ROM 拦截）时，仍然能用
 * GestureDescription 在指定坐标输出一次点击。注意这是触摸手势，
 * 不是鼠标左键事件，只作为兜底。
 */
class ClickerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClickerAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null

        fun tap(x: Float, y: Float): Boolean = instance?.dispatchTap(x, y) ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun dispatchTap(x: Float, y: Float): Boolean {
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (_: Throwable) {
            false
        }
    }
}
