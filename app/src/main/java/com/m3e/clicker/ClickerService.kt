package com.m3e.clicker

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Shizuku UserService：以 shell(uid 2000) 身份运行。
 *
 * 职责：
 *  - 侦测物理鼠标按键（evdev 直读 /dev/input/eventX，getevent 仅回退）；
 *  - 按住期间按所选模式重复输出鼠标按键（MotionEvent + buttonState）；
 *  - 三种点击模式：默认、DBC（一次基础点击扩展为 2/3/4 连点）、DC（限时高频 burst）。
 *
 * 参数只有一条入口：configureJson。
 */
class ClickerService : IClickerService.Stub {

    companion object {
        private const val TAG = "M3E-Service"
        private const val MANUAL_TIMEOUT_MS = 3_000L
        private const val MAX_HOLD_MS = 120_000L

        const val METHOD_DEFAULT = 0
        const val METHOD_DBC = 1
        const val METHOD_DC = 2
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
    private var manualActive = false

    @Volatile
    private var mousePressed = false

    @Volatile
    private var pressedDevice: String? = null

    /** 总开关：关闭后不输出，但监视线程保持运行（快速切换） */
    @Volatile
    private var enabled = true

    /** 绑定键位（evdev 键码），-1 表示未绑定 */
    @Volatile
    private var toggleKey = -1

    /** 是否处于键位捕获 */
    @Volatile
    private var capturing = false

    /** 捕获到的键码 */
    @Volatile
    private var capturedKey = -1

    /** 绑定键位切换计数：界面据此判断开关是硬件触发还是本地点击 */
    @Volatile
    private var switchSeq = 0

    /** 0=默认，1=DBC，2=DC */
    @Volatile
    private var method = METHOD_DEFAULT

    @Volatile
    private var minCps = 8

    @Volatile
    private var maxCps = 12

    @Volatile
    private var multiMin = 20

    @Volatile
    private var multiMax = 40

    @Volatile
    private var doublePct = 60

    @Volatile
    private var triplePct = 20

    @Volatile
    private var quadPct = 10

    /** 多倍点间隔区间（ms） */
    @Volatile
    private var gapMin = 2

    @Volatile
    private var gapMax = 4

    @Volatile
    private var dcCps = 60

    /** DC 持续时间区间（ms） */
    @Volatile
    private var dcDurMinMs = 800

    @Volatile
    private var dcDurMaxMs = 1_500

    /** DC 一轮结束后必须松手才能再次触发 */
    @Volatile
    private var dcLocked = false

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

    @Volatile
    private var measuredCps = 0

    private val outputTimes = ArrayDeque<Long>()

    private var screenWidth = 1080
    private var screenHeight = 2400

    private lateinit var injector: InputInjector
    private var monitor: EvdevExpert? = null
    private var monitorButton = -1
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

    override fun configureJson(json: String) {
        try {
            val o = JSONObject(json)
            enabled = o.optBoolean("enabled", true)
            toggleKey = o.optInt("toggleKey", -1).takeIf { it in 1..400 } ?: -1
            method = when (o.optInt("method", 0)) {
                METHOD_DBC -> METHOD_DBC
                METHOD_DC -> METHOD_DC
                else -> METHOD_DEFAULT
            }
            minCps = o.optInt("minCps", 8).coerceIn(1, 150)
            maxCps = o.optInt("maxCps", 12).coerceIn(1, 150)
            if (minCps > maxCps) {
                val t = minCps
                minCps = maxCps
                maxCps = t
            }
            multiMin = o.optInt("multiMin", 20).coerceIn(0, 100)
            multiMax = o.optInt("multiMax", 40).coerceIn(0, 100)
            if (multiMin > multiMax) {
                val t = multiMin
                multiMin = multiMax
                multiMax = t
            }
            doublePct = o.optInt("doublePct", 60).coerceIn(0, 100)
            triplePct = o.optInt("triplePct", 20).coerceIn(0, 100)
            quadPct = o.optInt("quadPct", 10).coerceIn(0, 100)
            gapMin = o.optInt("gapMin", 2).coerceIn(0, 10)
            gapMax = o.optInt("gapMax", 4).coerceIn(0, 10)
            if (gapMin > gapMax) {
                val t = gapMin
                gapMin = gapMax
                gapMax = t
            }
            dcCps = o.optInt("dcCps", 60).coerceIn(15, 150)
            dcDurMinMs = o.optInt("dcDurMinMs", 800).coerceIn(100, 5_000)
            dcDurMaxMs = o.optInt("dcDurMaxMs", 1_500).coerceIn(100, 5_000)
            if (dcDurMinMs > dcDurMaxMs) {
                val t = dcDurMinMs
                dcDurMinMs = dcDurMaxMs
                dcDurMaxMs = t
            }
            followCursor = o.optBoolean("followCursor", true)
            fixedX = o.optInt("fixedX", 0).coerceIn(0, max(screenWidth - 1, 0))
            fixedY = o.optInt("fixedY", 0).coerceIn(0, max(screenHeight - 1, 0))
            sensitivity = o.optDouble("sensitivity", 1.0).toFloat().coerceIn(0f, 3f)
            touchCompat = o.optBoolean("touchCompat", false)
            acceleration = o.optBoolean("acceleration", true)

            if (!enabled) {
                mousePressed = false
                stopLoop()
            } else {
                ensureMonitor()
            }
            Log.i(
                TAG,
                "configure enabled=$enabled method=$method cps=$minCps-$maxCps " +
                    "dc=$dcCps/${dcDurMinMs}-${dcDurMaxMs}ms gap=$gapMin-$gapMax toggleKey=$toggleKey"
            )
        } catch (t: Throwable) {
            lastError = "参数解析失败: ${t.message}"
        }
    }

    override fun startMouseTrigger() {
        ensureMonitor()
        // REL 设备没有绝对坐标：先把系统光标挪到屏幕中心，让推算坐标与真实光标对齐
        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f
        injector.moveCursor(cursorX, cursorY)
        manualActive = false
        dcLocked = false
        enabled = true
        lastError = ""
    }

    /** 关闭输出但不销毁监视线程：再次打开无需重新扫描设备 */
    override fun stopMouseTrigger() {
        enabled = false
        mousePressed = false
        manualActive = false
        dcLocked = false
        stopLoop()
    }

    override fun beginKeyCapture() {
        capturing = true
        capturedKey = -1
    }

    override fun endKeyCapture() {
        capturing = false
    }

    override fun startManualBurst() {
        if (!enabled) return
        manualActive = true
        manualDeadline = SystemClock.elapsedRealtime() + MANUAL_TIMEOUT_MS
        startLoop()
    }

    override fun stopManualBurst() {
        manualActive = false
        stopLoop()
    }

    override fun clickOnce() {
        if (!enabled) return
        if (!outputClicks(1)) lastError = injector.error ?: "注入失败"
    }

    override fun resetCursor() {
        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f
        injector.moveCursor(cursorX, cursorY)
    }

    override fun ping() {
        // 心跳不续期任何计时
    }

    override fun statusJson(): String {
        return try {
            val json = JSONObject()
            json.put("ready", ready)
            json.put("uid", Process.myUid())
            json.put("trigger", enabled)
            json.put("clicking", clicking.get())
            json.put("manual", manualActive)
            json.put("pressed", mousePressed)
            json.put("enabled", enabled)
            json.put("switchSeq", switchSeq)
            json.put("toggleKey", toggleKey)
            json.put("capturing", capturing)
            json.put("capturedKey", capturedKey)
            json.put("method", when (method) {
                METHOD_DBC -> "dbc"
                METHOD_DC -> "dc"
                else -> "default"
            })
            json.put("dcLocked", dcLocked)
            json.put("inject", injector.available)
            json.put("injectMode", injector.mouseMode)
            json.put("injectImpl", injector.managerName)
            json.put("injectError", injector.error ?: "")
            json.put("minCps", minCps)
            json.put("maxCps", maxCps)
            json.put("dcCps", dcCps)
            json.put("dcDurMinMs", dcDurMinMs)
            json.put("dcDurMaxMs", dcDurMaxMs)
            json.put("multiMin", multiMin)
            json.put("multiMax", multiMax)
            json.put("doublePct", doublePct)
            json.put("triplePct", triplePct)
            json.put("quadPct", quadPct)
            json.put("gapMin", gapMin)
            json.put("gapMax", gapMax)
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
            monitor?.stop()
            monitor = null
        } catch (_: Throwable) {
        }
        Log.i(TAG, "service destroy")
        System.exit(0)
    }

    // ------------------------------------------------------------ internals

    private fun ensureMonitor() {
        val wanted = 272 // BTN_LEFT
        if (monitor != null && monitorButton == wanted) return
        monitor?.stop()
        monitor = null
        monitorButton = wanted
        val m = EvdevExpert(
            watchButton = wanted,
            onPress = { device ->
                mousePressed = true
                pressedDevice = device
                if (enabled) {
                    // DC：一轮结束后必须松手再按
                    val blockedByDc = method == METHOD_DC && dcLocked
                    if (!blockedByDc) startLoop()
                }
            },
            onRelease = {
                mousePressed = false
                pressedDevice = null
                dcLocked = false
                if (!manualActive) stopLoop()
            },
            onRelativeMove = { dx, dy ->
                cursorX = (cursorX + scaledDelta(dx)).coerceIn(0f, (screenWidth - 1).toFloat())
                cursorY = (cursorY + scaledDelta(dy)).coerceIn(0f, (screenHeight - 1).toFloat())
            },
            onAbsoluteMove = { _, nx, ny ->
                if (nx >= 0f) cursorX = (nx * (screenWidth - 1)).coerceIn(0f, (screenWidth - 1).toFloat())
                if (ny >= 0f) cursorY = (ny * (screenHeight - 1)).coerceIn(0f, (screenHeight - 1).toFloat())
            },
            onDevicesChanged = { list ->
                Log.i(TAG, "devices: ${list.joinToString { it.label() }}")
            },
            onError = { message ->
                lastError = message
                Log.w(TAG, message)
            },
            onAnyKey = { label ->
                lastKeyEvent = label
            },
            onKeyEvent = { code, value ->
                if (capturing) {
                    // 键盘按键与鼠标侧键都可绑定（左/右键本身除外，避免与触发冲突）
                    if (code in 1..255 || code in 275..276 || code == 274) capturedKey = code
                } else if (toggleKey > 0 && code == toggleKey && value == 1) {
                    // 绑定键位：按下沿切换总开关，抬起与重复事件忽略
                    enabled = !enabled
                    switchSeq++
                    if (!enabled) {
                        mousePressed = false
                        stopLoop()
                    }
                }
            }
        )
        monitor = m
        m.start()
    }

    /**
     * 相对位移换算。sensitivity=0 时不移动光标。
     * acceleration=true 时按"位移越大倍率越高"近似系统指针加速，false 时 1:1 线性。
     */
    private fun scaledDelta(delta: Int): Float {
        if (delta == 0 || sensitivity <= 0f) return 0f
        val magnitude = abs(delta).toFloat()
        val gain = if (acceleration) 1f + min(magnitude, 20f) * 0.09f else 1f
        return delta * gain * sensitivity
    }

    private fun currentPoint(): Pair<Float, Float> =
        if (followCursor) cursorX to cursorY else fixedX.toFloat() to fixedY.toFloat()

    /** 输出 n 次按键，n>1 时按 gapMs 间隔（DBC 的多倍点） */
    private fun randomGap(): Int =
        if (gapMin >= gapMax) gapMin else Random.nextInt(gapMin, gapMax + 1)

    private fun outputClicks(n: Int, gap: Int = randomGap()): Boolean {
        val point = currentPoint()
        val secondary = false
        var ok = true
        for (i in 0 until n) {
            if (!injector.click(point.first, point.second, touchCompat, secondary)) {
                ok = false
            } else {
                clickCount++
                val now = SystemClock.elapsedRealtime()
                outputTimes.addLast(now)
                while (outputTimes.isNotEmpty() && now - outputTimes.first() > 1000L) {
                    outputTimes.removeFirst()
                }
                measuredCps = outputTimes.size
            }
            if (i < n - 1 && gap > 0) {
                try {
                    Thread.sleep(gap.toLong())
                } catch (_: InterruptedException) {
                    return ok
                }
            }
        }
        return ok
    }

    /**
     * DBC 的倍数选择：
     * 先由「多倍点概率」决定这次基础点击是否扩展；
     * 扩展后按 2/3/4 各自的概率独立随机，收集所有为 true 的倍数；
     * 多于一个 → 在其中随机取一个；恰好一个 → 用它；一个都没有 → 固定二倍点。
     */
    private fun pickMultiplier(): Int {
        val lo = min(multiMin, multiMax)
        val hi = max(multiMin, multiMax)
        val chance = if (lo == hi) lo else Random.nextInt(lo, hi + 1)
        if (chance <= 0 || Random.nextInt(100) >= chance) return 1

        val hits = ArrayList<Int>(3)
        if (doublePct > 0 && Random.nextInt(100) < doublePct) hits.add(2)
        if (triplePct > 0 && Random.nextInt(100) < triplePct) hits.add(3)
        if (quadPct > 0 && Random.nextInt(100) < quadPct) hits.add(4)
        return when {
            hits.size >= 2 -> hits[Random.nextInt(hits.size)]
            hits.size == 1 -> hits[0]
            else -> 2
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
        return enabled && mousePressed
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

    /** 随机基础间隔（毫秒） */
    private fun baseInterval(): Long {
        val lo = min(minCps, maxCps)
        val hi = max(minCps, maxCps)
        val cps = if (lo == hi) lo else Random.nextInt(lo, hi + 1)
        return (1000L / max(cps, 1)).coerceAtLeast(1L)
    }

    private fun clickLoop() {
        Log.i(TAG, "loop start method=$method")
        val loopStart = SystemClock.elapsedRealtime()
        val dcDuration: Long = if (method == METHOD_DC) {
            if (dcDurMinMs >= dcDurMaxMs) dcDurMinMs.toLong()
            else Random.nextInt(dcDurMinMs, dcDurMaxMs + 1).toLong()
        } else {
            0L
        }
        var nextAt = loopStart
        outputTimes.clear()

        while (clicking.get() && shouldKeepClicking()) {
            val elapsed = SystemClock.elapsedRealtime() - loopStart
            if (elapsed > MAX_HOLD_MS) {
                lastError = "超过单次连点上限，已自动停止"
                break
            }

            val interval: Long
            when {
                method == METHOD_DC -> {
                    // 达到持续时间即停止，需松手后重新按下
                    if (elapsed >= dcDuration) {
                        dcLocked = true
                        break
                    }
                    if (!outputClicks(1, gap = 0)) lastError = injector.error ?: "注入失败"
                    interval = (1000L / max(dcCps, 1)).coerceAtLeast(1L)
                }

                method == METHOD_DBC -> {
                    val multiplier = pickMultiplier()
                    if (!outputClicks(multiplier, randomGap())) lastError = injector.error ?: "注入失败"
                    interval = baseInterval()
                }

                else -> {
                    if (!outputClicks(1, gap = 0)) lastError = injector.error ?: "注入失败"
                    interval = baseInterval()
                }
            }

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
