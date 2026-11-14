package com.m3e.clicker

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Shizuku UserService：以 shell(uid 2000) 身份运行。
 *
 * 职责：
 *  - 侦测物理鼠标左键（evdev：直接读 /dev/input/eventX，getevent 仅回退）；
 *  - 按住左键期间，按 minCps..maxCps 随机频率重复输出**键值**（KeyEvent）或鼠标左键（MotionEvent）；
 *    间隔公式参考 Leader-Lite 的 AutoClicker：1000 / random(min, max)。
 *
 * 说明：Android 里鼠标按键不是 keyCode，KeyMapper 也只做了"检测左键"。
 * 所以"输出键值"只能走 KeyEvent 注入，这也是本服务默认的输出方式。
 */
class ClickerService : IClickerService.Stub {

    companion object {
        private const val TAG = "M3E-Service"
        private const val MANUAL_TIMEOUT_MS = 3_000L
        private const val MIN_CPS = 1
        private const val MAX_CPS = 30

        const val OUTPUT_KEY = 0
        const val OUTPUT_MOUSE = 1

        /** 单次按住的最长连点时长（防止设备消失/事件丢失导致无限连点） */
        private const val MAX_HOLD_MS = 120_000L
    }

    constructor() : super() {
        initService("default")
    }

    @Keep
    constructor(context: Context) : super() {
        initService("context:${context.packageName}")
    }

    private val lock = Any()
    private val clicking = AtomicBoolean(false)

    @Volatile
    private var clickThread: Thread? = null

    @Volatile
    private var triggerEnabled = false

    @Volatile
    private var manualActive = false

    @Volatile
    private var mousePressed = false

    /** 当前按下的设备（路径），用于显示与"设备消失即释放"判断 */
    @Volatile
    private var pressedDevice: String? = null

    @Volatile
    private var minCps = 8

    @Volatile
    private var maxCps = 12

    @Volatile
    private var outputMode = OUTPUT_KEY

    @Volatile
    private var keyCode = 66 // KEYCODE_ENTER

    @Volatile
    private var followCursor = true

    @Volatile
    private var fixedX = 0

    @Volatile
    private var fixedY = 0

    @Volatile
    private var sensitivity = 1.0f

    @Volatile
    private var touchCompat = false

    /** true=按系统指针加速模型放大快速移动；false=原始输入 1:1 */
    @Volatile
    private var acceleration = true

    @Volatile
    private var cursorX = 0f

    @Volatile
    private var cursorY = 0f

    @Volatile
    private var manualDeadline = 0L

    @Volatile
    private var lastError = ""

    @Volatile
    private var clickCount = 0L

    @Volatile
    private var lastKeyEvent = ""

    /** 最近 1 秒的真实输出次数（用于界面/测试页显示） */
    @Volatile
    private var measuredCps = 0

    private val outputTimes = ArrayDeque<Long>()

    private var screenWidth = 1080
    private var screenHeight = 2400

    private lateinit var injector: InputInjector
    private var monitor: EvdevExpert? = null
    private var ready = false

    private fun initService(source: String) {
        try {
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            if (metrics.widthPixels > 0) {
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }
            applyShellScreenSize()
            cursorX = screenWidth / 2f
            cursorY = screenHeight / 2f
            injector = InputInjector()
            if (!injector.available) lastError = injector.error ?: "输入注入不可用"
            ready = true
            Log.i(TAG, "ready ($source) uid=${Process.myUid()} mouseMode=${injector.mouseMode} impl=${injector.managerName}")
        } catch (t: Throwable) {
            lastError = t.toString()
            Log.e(TAG, "init failed", t)
        }
    }

    /** Resources 里的尺寸在某些机型上不含状态栏/导航栏，优先用 `wm size` 的真实分辨率 */
    private fun applyShellScreenSize() {
        try {
            val out = Shell.run(listOf("/system/bin/sh", "-c", "wm size"), 2000)
            val match = Regex("(\\d+)x(\\d+)").findAll(out).lastOrNull() ?: return
            val w = match.groupValues[1].toIntOrNull() ?: return
            val h = match.groupValues[2].toIntOrNull() ?: return
            if (w in 200..5000 && h in 200..5000) {
                screenWidth = w
                screenHeight = h
            }
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------- AIDL

    override fun configure(
        minCps: Int,
        maxCps: Int,
        outputMode: Int,
        keyCode: Int,
        followCursor: Boolean,
        fixedX: Int,
        fixedY: Int,
        sensitivity: Float,
        touchCompat: Boolean,
        acceleration: Boolean
    ) {
        val lo = min(max(minCps, MIN_CPS), MAX_CPS)
        val hi = min(max(maxCps, MIN_CPS), MAX_CPS)
        this.minCps = min(lo, hi)
        this.maxCps = max(lo, hi)
        this.outputMode = if (outputMode == OUTPUT_MOUSE) OUTPUT_MOUSE else OUTPUT_KEY
        this.keyCode = if (keyCode in 1..400) keyCode else 66
        this.followCursor = followCursor
        this.fixedX = min(max(fixedX, 0), max(screenWidth - 1, 0))
        this.fixedY = min(max(fixedY, 0), max(screenHeight - 1, 0))
        this.sensitivity = min(max(sensitivity, 0.0f), 4.0f)
        this.touchCompat = touchCompat
        this.acceleration = acceleration
        Log.i(
            TAG,
            "configure cps=${this.minCps}-${this.maxCps} " +
                "output=${if (this.outputMode == OUTPUT_KEY) "key:$keyCode" else "mouse"} follow=$followCursor"
        )
    }

    override fun startMouseTrigger() {
        ensureMonitor()
        // REL 相对位移鼠标没有绝对坐标，先把系统光标挪到屏幕中心，让推算坐标与真实光标对齐
        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f
        if (outputMode == OUTPUT_MOUSE) injector.moveCursor(cursorX, cursorY)
        manualActive = false
        triggerEnabled = true
        lastError = ""
    }

    override fun stopMouseTrigger() {
        triggerEnabled = false
        mousePressed = false
        manualActive = false
        stopLoop()
        monitor?.stop()
        monitor = null
    }

    override fun startManualBurst() {
        manualActive = true
        manualDeadline = SystemClock.elapsedRealtime() + MANUAL_TIMEOUT_MS
        startLoop()
    }

    override fun stopManualBurst() {
        manualActive = false
        stopLoop()
    }

    override fun clickOnce() {
        if (!outputOnce()) {
            lastError = injector.error ?: "注入失败"
        }
    }

    override fun resetCursor() {
        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f
        injector.moveCursor(cursorX, cursorY)
    }

    override fun ping() {
        // 心跳只用于保活，不允许续期 manual 计时：
        // 否则界面每次轮询都会把 manual 生命周期无限延长 → 没有任何左键输入也一直连点。
    }

    override fun statusJson(): String {
        return try {
            val json = JSONObject()
            json.put("ready", ready)
            json.put("uid", Process.myUid())
            json.put("trigger", triggerEnabled)
            json.put("clicking", clicking.get())
            json.put("manual", manualActive)
            json.put("pressed", mousePressed)
            json.put("inject", injector.available)
            json.put("injectMode", injector.mouseMode)
            json.put("injectImpl", injector.managerName)
            json.put("injectError", injector.error ?: "")
            json.put("outputMode", if (outputMode == OUTPUT_KEY) "key" else "mouse")
            json.put("keyCode", keyCode)
            json.put("minCps", minCps)
            json.put("maxCps", maxCps)
            json.put("cursorX", cursorX.toInt())
            json.put("cursorY", cursorY.toInt())
            json.put("width", screenWidth)
            json.put("height", screenHeight)
            json.put("followCursor", followCursor)
            json.put("sensitivity", sensitivity)
            json.put("acceleration", acceleration)
            json.put("err", lastError)
            json.put("clicks", clickCount)
            json.put("measuredCps", measuredCps)
            json.put("pressedDevice", pressedDevice ?: "")
            json.put("lastKey", lastKeyEvent)
            val nodes = JSONArray()
            monitor?.devices?.forEach { nodes.put(it.label()) }
            json.put("devices", nodes)
            json.put("nodeDetails", monitor?.devicesJson() ?: "[]")
            json.put("scanError", monitor?.scanError ?: "")
            json.toString()
        } catch (t: Throwable) {
            try {
                JSONObject().put("ready", false).put("err", t.message ?: t.toString()).toString()
            } catch (_: Throwable) {
                "{}"
            }
        }
    }

    override fun destroy() {
        try {
            stopMouseTrigger()
            manualActive = false
            stopLoop()
        } catch (_: Throwable) {
        }
        Log.i(TAG, "service destroy")
        System.exit(0)
    }

    // ------------------------------------------------------------ internals

    private fun ensureMonitor() {
        if (monitor != null) return
        val m = EvdevExpert(
            onPress = { device ->
                mousePressed = true
                pressedDevice = device
                if (triggerEnabled) startLoop()
            },
            onRelease = {
                mousePressed = false
                pressedDevice = null
                if (!manualActive) stopLoop()
            },
            onRelativeMove = { dx, dy ->
                cursorX = (cursorX + mapDelta(dx)).coerceIn(0f, (screenWidth - 1).toFloat())
                cursorY = (cursorY + mapDelta(dy)).coerceIn(0f, (screenHeight - 1).toFloat())
            },
            onAbsoluteMove = { _, nx, ny ->
                if (nx >= 0f) cursorX = (nx * (screenWidth - 1)).coerceIn(0f, (screenWidth - 1).toFloat())
                if (ny >= 0f) cursorY = (ny * (screenHeight - 1)).coerceIn(0f, (screenHeight - 1).toFloat())
            },
            onDevicesChanged = { list ->
                Log.i(TAG, "devices: ${list.joinToString { it.label() }}")
                if (list.isEmpty()) lastError = "未识别到输入设备"
            },
            onError = { message ->
                lastError = message
                Log.w(TAG, message)
            },
            onAnyKey = { label ->
                lastKeyEvent = label
            }
        )
        monitor = m
        m.start()
    }

    /**
     * 把 evdev 的相对位移换算成屏幕位移。
     *
     * Android 的指针并不是原始输入直通：系统会按速度做加速（位移越大增益越高）。
     * acceleration=true 时用近似曲线模拟；false 时只按灵敏度线性换算（原始输入）。
     * sensitivity=0 时不移动光标（只输出事件）。
     */
    private fun mapDelta(raw: Int): Float {
        if (raw == 0 || sensitivity <= 0f) return 0f
        if (!acceleration) return raw * sensitivity
        val speed = kotlin.math.abs(raw)
        // 加速曲线：1x → 约 2.2x（速度越大增益越高）
        val gain = 1f + (min(speed, 60) / 60f) * 1.2f
        return raw * sensitivity * gain
    }

    /**
     * 相对位移换算：灵敏度为 0 时完全不移动（0x）。
     * 加速开启时按"位移越大→倍率越高"近似系统指针加速（Android 光标本来就是加速过的，不是原始 1:1）。
     */
    private fun scaledDelta(delta: Int): Float {
        if (delta == 0 || sensitivity <= 0f) return 0f
        val magnitude = kotlin.math.abs(delta).toFloat()
        val gain = if (acceleration) 1f + min(magnitude, 20f) * 0.09f else 1f
        return delta * gain * sensitivity
    }

    private fun currentPoint(): Pair<Float, Float> {
        return if (followCursor) {
            cursorX to cursorY
        } else {
            fixedX.toFloat() to fixedY.toFloat()
        }
    }

    /** 输出一次：键值 或 鼠标左键 */
    private fun outputOnce(): Boolean {
        return if (outputMode == OUTPUT_KEY) {
            injector.pressKey(keyCode)
        } else {
            val point = currentPoint()
            injector.click(point.first, point.second, touchCompat)
        }
    }

    private fun shouldKeepClicking(): Boolean {
        if (manualActive) {
            if (SystemClock.elapsedRealtime() > manualDeadline) {
                manualActive = false
                return false
            }
            return true
        }
        return triggerEnabled && mousePressed
    }

    private fun startLoop() {
        synchronized(lock) {
            if (clicking.get()) return
            clicking.set(true)
            val thread = Thread({ clickLoop() }, "m3e-clicker")
            thread.isDaemon = true
            clickThread = thread
            thread.start()
        }
    }

    private fun stopLoop() {
        clicking.set(false)
        clickThread?.interrupt()
        clickThread = null
    }

    private fun clickLoop() {
        Log.i(TAG, "loop start")
        val loopStart = SystemClock.elapsedRealtime()
        // 绝对时间排程：下一次触发时刻 = 上一次目标时刻 + 间隔，
        // 这样注入本身的耗时（每次 2 个事件 + WAIT_FOR_FINISH）不会累积成频率损失。
        var nextAt = loopStart
        outputTimes.clear()

        while (clicking.get() && shouldKeepClicking()) {
            if (SystemClock.elapsedRealtime() - loopStart > MAX_HOLD_MS) {
                // 兜底：设备消失 / 事件丢失时不允许无限连点
                lastError = "超过单次连点上限，已自动停止"
                break
            }

            if (outputOnce()) {
                clickCount++
                val now = SystemClock.elapsedRealtime()
                outputTimes.addLast(now)
                while (outputTimes.isNotEmpty() && now - outputTimes.first() > 1000L) {
                    outputTimes.removeFirst()
                }
                measuredCps = outputTimes.size
            } else {
                lastError = injector.error ?: "注入失败"
            }

            val lo = min(minCps, maxCps)
            val hi = max(maxCps, minCps)
            val cps = if (lo == hi) lo else Random.nextInt(lo, hi + 1)
            val interval = (1000L / max(cps, 1)).coerceAtLeast(1L)

            nextAt += interval
            val now = SystemClock.elapsedRealtime()
            if (nextAt <= now) nextAt = now + 1
            val wait = nextAt - now
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                break
            }
        }
        measuredCps = 0
        outputTimes.clear()
        clicking.set(false)
        Log.i(TAG, "loop stop")
    }
}
