package com.m3e.clicker

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import java.lang.reflect.Array
import java.lang.reflect.Method

/**
 * 注入能力：
 *  1) **输出键值**：构造 KeyEvent(DOWN/UP) 注入。Android 里鼠标按键没有对应 keyCode，
 *     所以"输出键值"必须走 KeyEvent —— 这也是 KeyMapper 的做法（它只检测左键，不做左侧注入）。
 *  2) 输出鼠标左键：MotionEvent + buttonState=BUTTON_PRIMARY（坐标必填）。
 *
 * 注入逻辑运行在 shell(uid 2000) 的 UserService 里，不受非 SDK 接口限制，
 * 优先使用静态的 `InputManagerGlobal#getInstance()`（Android 15 上 `InputManager#getInstance()`
 * 依赖 ActivityThread.currentApplication()，在 user service 进程里必定为 null）。
 */
class InputInjector {

    private var manager: Any? = null
    private var injectMethod: Method? = null
    private var obtainMethod: Method? = null
    private var setButtonState: Method? = null
    private var setActionButton: Method? = null
    private var mouseReady = false

    /** 最后一次失败原因（诊断展示） */
    @Volatile
    var error: String? = null
        private set

    val available: Boolean

    val managerName: String
        get() = manager?.javaClass?.name ?: "none"

    /** 左键注入是否具备鼠标按键语义：mouse / touch / none */
    val mouseMode: String
        get() = when {
            !available -> "none"
            mouseReady -> "mouse"
            else -> "touch"
        }

    init {
        var err: String? = null
        try {
            setButtonState = try {
                MotionEvent::class.java.getMethod("setButtonState", Integer.TYPE)
            } catch (_: Throwable) {
                null
            }
            setActionButton = try {
                MotionEvent::class.java.getMethod("setActionButton", Integer.TYPE)
            } catch (_: Throwable) {
                null
            }
            // 关键：这一步失败不能影响可用性，否则整条注入通道都会被判定为不可用
            obtainMethod = try {
                resolveObtainWithButtonState()
            } catch (_: Throwable) {
                null
            }

            manager = resolveManager()
            injectMethod = manager?.let { resolveInject(it) }
            if (manager == null || injectMethod == null) {
                err = "找不到 injectInputEvent（InputManagerGlobal / InputManager / IInputManager 均不可用）"
            }
            mouseReady = setButtonState != null || obtainMethod != null
        } catch (t: Throwable) {
            err = "${t.javaClass.simpleName}: ${t.message}"
        }
        error = err
        available = manager != null && injectMethod != null
    }

    private fun resolveManager(): Any? {
        // 1) InputManagerGlobal：静态、不依赖 Application（Android 14+ 首选）
        try {
            val cls = Class.forName("android.hardware.input.InputManagerGlobal")
            val getInstance = cls.getDeclaredMethod("getInstance").apply { isAccessible = true }
            getInstance.invoke(null)?.let { return it }
        } catch (_: Throwable) {
        }
        // 2) InputManager（旧版本）
        try {
            val cls = Class.forName("android.hardware.input.InputManager")
            val getInstance = cls.getDeclaredMethod("getInstance").apply { isAccessible = true }
            getInstance.invoke(null)?.let { return it }
        } catch (_: Throwable) {
        }
        // 3) 系统 input 服务
        return try {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "input") as? android.os.IBinder
                ?: return null
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            stub.getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveInject(target: Any): Method? {
        try {
            target.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Integer.TYPE)
                .apply { isAccessible = true }.let { return it }
        } catch (_: Throwable) {
        }
        return try {
            Class.forName("android.hardware.input.InputManagerGlobal")
                .getMethod("injectInputEvent", InputEvent::class.java, Integer.TYPE)
                .apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 带 buttonState 的隐藏 obtain 重载。
     *
     * 注意：`Class.forName("android.view.MotionEvent$PointerProperties[]")` 是非法类名
     * （JVM 的数组类名是 `[Landroid.view.MotionEvent$PointerProperties;`），上一版正是因此报
     * `ClassNotFoundException: Invalid name`。这里改用 `Array.newInstance(component, 0).javaClass`。
     */
    private fun resolveObtainWithButtonState(): Method? {
        val ppCls = Class.forName("android.view.MotionEvent\$PointerProperties")
        val pcCls = Class.forName("android.view.MotionEvent\$PointerCoords")
        val ppArrayCls = Array.newInstance(ppCls, 0).javaClass
        val pcArrayCls = Array.newInstance(pcCls, 0).javaClass

        val signatures = listOf(
            arrayOf(
                java.lang.Long.TYPE, java.lang.Long.TYPE, Integer.TYPE, Integer.TYPE,
                ppArrayCls, pcArrayCls,
                Integer.TYPE, Integer.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE,
                Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE,
            ),
            arrayOf(
                java.lang.Long.TYPE, java.lang.Long.TYPE, Integer.TYPE, Integer.TYPE,
                ppArrayCls, pcArrayCls,
                Integer.TYPE, Integer.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE,
                Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE,
            ),
        )
        for (sig in signatures) {
            try {
                return MotionEvent::class.java.getMethod("obtain", *sig).apply { isAccessible = true }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    // ------------------------------------------------------------ 输出键值

    /** 输出一次键值（DOWN + UP） */
    fun pressKey(keyCode: Int): Boolean {
        if (!available) return false
        if (keyCode <= 0) return false
        val downTime = SystemClock.uptimeMillis()
        val okDown = sendKey(keyCode, KeyEvent.ACTION_DOWN, downTime)
        val okUp = sendKey(keyCode, KeyEvent.ACTION_UP, SystemClock.uptimeMillis())
        return okDown && okUp
    }

    fun keyDown(keyCode: Int): Boolean =
        sendKey(keyCode, KeyEvent.ACTION_DOWN, SystemClock.uptimeMillis())

    fun keyUp(keyCode: Int): Boolean =
        sendKey(keyCode, KeyEvent.ACTION_UP, SystemClock.uptimeMillis())

    private fun sendKey(keyCode: Int, action: Int, eventTime: Long): Boolean {
        return try {
            val event = KeyEvent(eventTime, eventTime, action, keyCode, 0)
            try {
                event.source = InputDevice.SOURCE_KEYBOARD
            } catch (_: Throwable) {
            }
            send(event)
        } catch (t: Throwable) {
            error = "KeyEvent 构造失败: ${t.message}"
            false
        }
    }

    // ------------------------------------------------------------ 输出鼠标左键

    fun click(x: Float, y: Float, touchCompat: Boolean): Boolean {
        if (!available) return false
        return if (touchCompat || !mouseReady) clickAsTouch(x, y) else clickAsMouse(x, y)
    }

    fun moveCursor(x: Float, y: Float): Boolean {
        if (!available || !mouseReady) return false
        val ev = buildMouse(SystemClock.uptimeMillis(), MotionEvent.ACTION_HOVER_MOVE, x, y, 0, 0)
            ?: return false
        return send(ev)
    }

    private fun clickAsMouse(x: Float, y: Float): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = buildMouse(
            downTime, MotionEvent.ACTION_DOWN, x, y,
            MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_PRIMARY
        )
        val up = buildMouse(
            downTime, MotionEvent.ACTION_UP, x, y, 0, MotionEvent.BUTTON_PRIMARY
        )
        if (down == null || up == null) {
            error = "鼠标事件构造失败（带 buttonState 的 obtain 不可用）"
            return false
        }
        val okDown = send(down)
        val okUp = send(up)
        return okDown && okUp
    }

    private fun clickAsTouch(x: Float, y: Float): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = try {
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).also {
                it.source = InputDevice.SOURCE_TOUCHSCREEN
            }
        } catch (_: Throwable) {
            null
        }
        val up = try {
            MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0).also {
                it.source = InputDevice.SOURCE_TOUCHSCREEN
            }
        } catch (_: Throwable) {
            null
        }
        if (down == null || up == null) return false
        val okDown = send(down)
        val okUp = send(up)
        return okDown && okUp
    }

    private fun buildMouse(
        downTime: Long,
        action: Int,
        x: Float,
        y: Float,
        buttonState: Int,
        actionButton: Int,
    ): MotionEvent? {
        // 首选：带 buttonState 的隐藏 obtain 重载
        try {
            val m = obtainMethod
            if (m != null) {
                val ppCls = Class.forName("android.view.MotionEvent\$PointerProperties")
                val pcCls = Class.forName("android.view.MotionEvent\$PointerCoords")
                val pp = ppCls.getDeclaredConstructor().newInstance().also {
                    ppCls.getField("id").setInt(it, 0)
                    ppCls.getField("toolType").setInt(it, MotionEvent.TOOL_TYPE_MOUSE)
                }
                val pc = pcCls.getDeclaredConstructor().newInstance().also {
                    pcCls.getField("x").setFloat(it, x)
                    pcCls.getField("y").setFloat(it, y)
                    pcCls.getField("pressure").setFloat(it, 0f)
                    pcCls.getField("size").setFloat(it, 1f)
                }
                val ppArray = Array.newInstance(ppCls, 1).also { Array.set(it, 0, pp) }
                val pcArray = Array.newInstance(pcCls, 1).also { Array.set(it, 0, pc) }
                val args = if (m.parameterCount == 15) {
                    arrayOf(
                        downTime, SystemClock.uptimeMillis(), action, 1, ppArray, pcArray,
                        0, buttonState, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0, 0
                    )
                } else {
                    arrayOf(
                        downTime, SystemClock.uptimeMillis(), action, 1, ppArray, pcArray,
                        0, buttonState, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
                    )
                }
                val ev = m.invoke(null, *args) as? MotionEvent
                if (ev != null) return ev
            }
        } catch (t: Throwable) {
            error = "obtain 反射失败: ${t.message}"
        }

        // 兜底：普通 obtain + setButtonState / setActionButton
        return try {
            val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            ev.source = InputDevice.SOURCE_MOUSE
            try {
                setButtonState?.invoke(ev, buttonState)
            } catch (_: Throwable) {
            }
            try {
                if (actionButton != 0) setActionButton?.invoke(ev, actionButton)
            } catch (_: Throwable) {
            }
            ev
        } catch (_: Throwable) {
            null
        }
    }

    private fun send(event: InputEvent): Boolean {
        val m = injectMethod ?: return false
        val target = manager ?: return false
        return try {
            // 2 = INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH，保证 DOWN/UP 有序
            (m.invoke(target, event, 2) as? Boolean) ?: false
        } catch (t: Throwable) {
            error = "注入失败: ${t.javaClass.simpleName}: ${t.message}"
            false
        } finally {
            try {
                (event as? MotionEvent)?.recycle()
            } catch (_: Throwable) {
            }
        }
    }
}
