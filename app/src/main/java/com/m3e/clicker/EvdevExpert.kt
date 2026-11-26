package com.m3e.clicker

import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/** 一个输入设备（可能带鼠标左键） */
data class MouseDevice(
    val path: String,
    val name: String,
    @Volatile var isMouse: Boolean,
    val absXMin: Int? = null,
    val absXMax: Int? = null,
    val absYMin: Int? = null,
    val absYMax: Int? = null,
) {
    /** 最近一次观测到的按键事件（"BTN_LEFT DOWN"），给扫描模式展示用 */
    @Volatile
    var lastKey: String = ""

    val hasAbsolute: Boolean
        get() = absXMin != null && absXMax != null && absYMin != null && absYMax != null

    fun label(): String = if (name.isBlank()) path.substringAfterLast('/') else "$name · $path"

    fun toJson(): String = buildString {
        append("{")
        append("\"path\":\"").append(path).append("\",")
        append("\"name\":\"").append(name.replace("\"", "'")).append("\",")
        append("\"mouse\":").append(isMouse).append(",")
        append("\"abs\":").append(hasAbsolute).append(",")
        append("\"ok\":").append(readOk).append(",")
        append("\"err\":\"").append((readError ?: "").replace("\"", "'")).append("\",")
        append("\"events\":").append(eventCount)
        append("}")
    }

    @Volatile
    var readOk: Boolean = false

    @Volatile
    var readError: String? = null

    @Volatile
    var eventCount: Long = 0
}

/**
 * KeyMapper 式 "expert mode" 的纯 Kotlin 实现：**直接读 /dev/input/eventX 原始事件**，
 * 而不是依赖 getevent 的文本输出（后者在不少 ROM 上不可用/被裁剪/SELinux 拦截）。
 *
 * 步骤：
 * 1) 枚举：/sys/class/input/eventN/device/capabilities 下的 key/abs/rel 位图 + /proc/bus/input/devices，
 *    找出带 BTN_LEFT/BTN_RIGHT 且不是触摸屏（无 ABS_MT）的设备；
 * 2) 读取：FileInputStream 按 `struct input_event` 逐字节解析
 *    （64 位进程 24 字节：timeval 16 + type 2 + code 2 + value 4；32 位 16 字节）；
 * 3) 回退：若某个节点不可读（EACCES），对该节点改用 `getevent -l <path>` 文本流。
 *
 * 解析结果只向上回报语义：左键按下/抬起、相对位移、绝对坐标。
 */
class EvdevExpert(
    /** 监听的鼠标按键 code：272=左键，273=右键 */
    private val watchButton: Int = BTN_LEFT,
    private val onPress: (String) -> Unit,
    private val onRelease: () -> Unit,
    private val onRelativeMove: (dx: Int, dy: Int) -> Unit,
    private val onAbsoluteMove: (device: MouseDevice, normalizedX: Float, normalizedY: Float) -> Unit,
    private val onDevicesChanged: (List<MouseDevice>) -> Unit,
    private val onError: (String) -> Unit,
    private val onAnyKey: (String) -> Unit = {},
    /** 原始按键回调：code 为 evdev 键码，value 1=按下 0=抬起 */
    private val onKeyEvent: (Int, Int) -> Unit = { _, _ -> },
) {
    companion object {
        private const val TAG = "M3E-Evdev"
        private const val GETEVENT = "/system/bin/getevent"

        private const val EV_SYN = 0x00
        private const val EV_KEY = 0x01
        private const val EV_REL = 0x02
        private const val EV_ABS = 0x03

        private const val REL_X = 0x00
        private const val REL_Y = 0x01

        private const val ABS_X = 0x00
        private const val ABS_Y = 0x01
        private const val ABS_MT_SLOT = 0x2f
        private const val ABS_MT_POSITION_X = 0x35
        private const val ABS_MT_POSITION_Y = 0x36

        private const val BTN_LEFT = 0x110 // 272
        private const val BTN_RIGHT = 0x111 // 273
    }

    private val running = AtomicBoolean(false)
    private var supervisor: Thread? = null
    private val readerThreads = HashMap<String, Thread>()
    private val streams = HashMap<String, FileInputStream>()
    private val processes = HashMap<String, java.lang.Process>()
    private val lock = Any()

    @Volatile
    var devices: List<MouseDevice> = emptyList()
        private set

    @Volatile
    var scanError: String? = null
        private set

    @Volatile
    private var absRangeCache: Map<String, IntArray>? = null

    @Volatile
    private var pressed = false

    @Volatile
    private var pressedDevice: String? = null

    /** 是否至少有一台设备正在被成功读取 */
    val isRunning: Boolean get() = running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        supervisor = Thread({ supervise() }, "m3e-evdev-supervisor").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        synchronized(lock) {
            processes.values.forEach {
                try {
                    it.destroy()
                } catch (_: Throwable) {
                }
            }
            processes.clear()
            streams.values.forEach {
                try {
                    it.close()
                } catch (_: Throwable) {
                }
            }
            streams.clear()
            readerThreads.values.forEach { it.interrupt() }
            readerThreads.clear()
        }
        supervisor?.interrupt()
        supervisor = null
        releaseIfPressed()
    }

    private fun releaseIfPressed() {
        if (pressed) {
            pressed = false
            pressedDevice = null
            onRelease()
        }
    }

    private fun supervise() {
        while (running.get()) {
            try {
                val (found, err) = scan()
                scanError = err
                devices = found
                onDevicesChanged(found)

                // 按住期间设备消失（拔线/断连）→ 立即释放，避免一直连点
                val stillThere = pressedDevice?.let { path -> found.any { it.path == path } } ?: true
                if (pressed && !stillThere) {
                    Log.w(TAG, "pressed device disappeared: $pressedDevice")
                    releaseIfPressed()
                }
                if (found.isEmpty()) {
                    if (err == null) {
                        onError("未找到带鼠标按键的输入设备，请确认鼠标已连接（OTG/蓝牙）")
                    }
                    Thread.sleep(2500)
                    continue
                }
                synchronized(lock) {
                    found.forEach { device ->
                        if (readerThreads[device.path]?.isAlive != true) {
                            val t = Thread({ readDevice(device) }, "m3e-evdev-${device.path}")
                            t.isDaemon = true
                            readerThreads[device.path] = t
                            t.start()
                        }
                    }
                }
                Thread.sleep(1500)
            } catch (_: InterruptedException) {
                return
            } catch (t: Throwable) {
                onError("设备扫描失败: ${t.message}")
                try {
                    Thread.sleep(2500)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun keyCodeOf(name: String): Int {
        val table = mapOf(
            "KEY_A" to 30, "KEY_B" to 48, "KEY_C" to 46, "KEY_D" to 32, "KEY_E" to 18,
            "KEY_F" to 33, "KEY_G" to 34, "KEY_H" to 35, "KEY_I" to 23, "KEY_J" to 36,
            "KEY_K" to 37, "KEY_L" to 38, "KEY_M" to 50, "KEY_N" to 49, "KEY_O" to 24,
            "KEY_P" to 25, "KEY_Q" to 16, "KEY_R" to 19, "KEY_S" to 31, "KEY_T" to 20,
            "KEY_U" to 22, "KEY_V" to 47, "KEY_W" to 17, "KEY_X" to 45, "KEY_Y" to 21,
            "KEY_Z" to 44,
            "KEY_1" to 2, "KEY_2" to 3, "KEY_3" to 4, "KEY_4" to 5, "KEY_5" to 6,
            "KEY_6" to 7, "KEY_7" to 8, "KEY_8" to 9, "KEY_9" to 10, "KEY_0" to 11,
            "KEY_ESC" to 1, "KEY_TAB" to 15, "KEY_SPACE" to 57, "KEY_ENTER" to 28,
            "KEY_LEFTSHIFT" to 42, "KEY_RIGHTSHIFT" to 54, "KEY_LEFTCTRL" to 29,
            "KEY_RIGHTCTRL" to 97, "KEY_LEFTALT" to 56, "KEY_RIGHTALT" to 100,
            "KEY_CAPSLOCK" to 58, "KEY_GRAVE" to 41, "KEY_MINUS" to 12, "KEY_EQUAL" to 13,
            "KEY_BACKSLASH" to 43, "KEY_SEMICOLON" to 39, "KEY_APOSTROPHE" to 40,
            "KEY_COMMA" to 51, "KEY_DOT" to 52, "KEY_SLASH" to 53,
            "KEY_F1" to 59, "KEY_F2" to 60, "KEY_F3" to 61, "KEY_F4" to 62,
            "KEY_F5" to 63, "KEY_F6" to 64, "KEY_F7" to 65, "KEY_F8" to 66,
            "KEY_F9" to 67, "KEY_F10" to 68, "KEY_F11" to 87, "KEY_F12" to 88,
            "BTN_MIDDLE" to 274, "BTN_SIDE" to 275, "BTN_EXTRA" to 276,
        )
        return table[name] ?: -1
    }

    private fun watchButtonName(): String =
        if (watchButton == BTN_RIGHT) "BTN_RIGHT" else "BTN_LEFT"

    // ---------------------------------------------------------------- 设备枚举

    private data class ProcInfo(
        val name: String,
        val handlers: String,
        val keyMask: String,
        val absMask: String,
        val relMask: String,
    )

    private fun scan(): Pair<List<MouseDevice>, String?> {
        val infos = readProcBusInput(File("/proc/bus/input/devices"))
        val nodes = File("/dev/input").listFiles()
            ?.filter { it.name.startsWith("event") }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (nodes.isEmpty()) {
            return emptyList<MouseDevice>() to "读取不到 /dev/input/event*（可能被 SELinux 限制）"
        }

        val result = ArrayList<MouseDevice>()
        for (node in nodes) {
            val eventName = node.name
            val info = infos[eventName]
            val sysDevice = File("/sys/class/input/$eventName/device")

            val name = info?.name
                ?: runCatching { File(sysDevice, "name").readText().trim() }.getOrNull()
                ?: eventName

            val keyMask = info?.keyMask
                ?: runCatching { File(sysDevice, "capabilities/key").readText() }.getOrNull()
                ?: ""
            val absMask = info?.absMask
                ?: runCatching { File(sysDevice, "capabilities/abs").readText() }.getOrNull()
                ?: ""
            val relMask = info?.relMask
                ?: runCatching { File(sysDevice, "capabilities/rel").readText() }.getOrNull()
                ?: ""
            val handlers = info?.handlers ?: ""

            val hasLeft = bitSet(keyMask, BTN_LEFT) || bitSet(keyMask, BTN_RIGHT)
            // 键盘节点的 key 位图里有字母键，说明是真键盘；不带上就永远收不到绑定的键盘键
            val hasKeyboard = bitSet(keyMask, 30) || bitSet(keyMask, 32) || bitSet(keyMask, 46)
            val isTouch = bitSet(absMask, ABS_MT_SLOT) || bitSet(absMask, ABS_MT_POSITION_X) ||
                bitSet(absMask, ABS_MT_POSITION_Y)
            val mouseHandler = handlers.contains("mouse")
            val hasRel = bitSet(relMask, REL_X) || bitSet(relMask, REL_Y)

            // 触摸屏 / 虚拟触摸设备排除；其余只要像鼠标或确实有 BTN_LEFT 就监听
            if (isTouch) continue
            val candidate = hasLeft || mouseHandler || hasRel || hasKeyboard
            if (!candidate) continue

            // ABS 范围（只有 getevent -p 能给出精确 absinfo，这里先占位，读到后用 getevent 覆盖）
            result.add(
                MouseDevice(
                    path = node.absolutePath,
                    name = name,
                    isMouse = mouseHandler || hasLeft,
                )
            )
        }
        // 鼠标设备优先
        result.sortBy { if (it.isMouse) 0 else 1 }

        // 若 getevent 可用，用它补上 ABS 的 min/max 范围（ABS 类设备需要）。结果缓存在进程内，避免反复起进程
        val absRanges = absRangeCache ?: run {
            if (File(GETEVENT).canExecute()) readGeteventAbs() else emptyMap()
        }.also { absRangeCache = it }
        val merged = result.map { d ->
            val r = absRanges[d.path] ?: return@map d
            d.copy(absXMin = r[0], absXMax = r[1], absYMin = r[2], absYMax = r[3])
        }
        if (merged.isEmpty()) {
            // 能力位图读不到（SELinux / sysfs 缺失 / 虚拟设备）时不要直接放弃：
            // 监听全部可读节点，靠真实事件里的 BTN_LEFT 来确认鼠标。
            val all = nodes.map { node ->
                MouseDevice(path = node.absolutePath, name = node.name, isMouse = false)
            }
            return all to "能力位图未识别到鼠标，已监听全部 ${all.size} 个输入节点"
        }
        return merged to null
    }

    /** getevent -p 里每个设备会打印 "ABS_X : value 0, min 0, max 32767, ..." */
    private fun readGeteventAbs(): Map<String, IntArray> {
        val output = exec(listOf(GETEVENT, "-p"), 2500)
        val map = HashMap<String, IntArray>()
        var path: String? = null
        var xMin: Int? = null
        var xMax: Int? = null
        var yMin: Int? = null
        var yMax: Int? = null

        fun flush() {
            val p = path ?: return
            if (xMin != null && xMax != null && yMin != null && yMax != null) {
                map[p] = intArrayOf(xMin!!, xMax!!, yMin!!, yMax!!)
            }
            path = null
            xMin = null
            xMax = null
            yMin = null
            yMax = null
        }

        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("add device")) {
                flush()
                val idx = line.indexOf("/dev/input/")
                if (idx >= 0) path = line.substring(idx).trim()
                return@forEach
            }
            if (path == null) return@forEach
            if (line.startsWith("ABS (0003): ABS_X")) {
                xMin = Regex("min\\s+(-?\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                xMax = Regex("max\\s+(-?\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
            } else if (line.startsWith("ABS (0003): ABS_Y")) {
                yMin = Regex("min\\s+(-?\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                yMax = Regex("max\\s+(-?\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
            }
        }
        flush()
        return map
    }

    private fun exec(args: List<String>, timeoutMs: Long): String {
        val builder = StringBuilder()
        var process: java.lang.Process? = null
        try {
            process = ProcessBuilder(args).redirectErrorStream(true).start()
            val worker = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { builder.appendLine(it) }
                } catch (_: Throwable) {
                }
            }
            worker.isDaemon = true
            worker.start()
            worker.join(timeoutMs)
        } catch (t: Throwable) {
            Log.w(TAG, "exec failed: ${t.message}")
        } finally {
            try {
                process?.destroy()
            } catch (_: Throwable) {
            }
        }
        return builder.toString()
    }

    /** 解析 /proc/bus/input/devices，拿到 name / handlers / 能力位图 */
    private fun readProcBusInput(file: File): Map<String, ProcInfo> {
        val map = HashMap<String, ProcInfo>()
        val text = runCatching { file.readText() }.getOrNull() ?: return map
        var name: String? = null
        var handlers = ""
        var keyMask = ""
        var absMask = ""
        var relMask = ""

        fun flush() {
            val h = handlers
            val ev = Regex("event([0-9]+)").find(h)?.value ?: return
            map[ev] = ProcInfo(name ?: "", h, keyMask, absMask, relMask)
            name = null
            handlers = ""
            keyMask = ""
            absMask = ""
            relMask = ""
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) {
                flush()
                return@forEach
            }
            when {
                line.startsWith("N: Name=") ->
                    name = line.removePrefix("N: Name=").trim().trim('"')
                line.startsWith("H: Handlers=") ->
                    handlers = line.removePrefix("H: Handlers=")
                line.startsWith("B: KEY=") ->
                    keyMask = line.removePrefix("B: KEY=")
                line.startsWith("B: ABS=") ->
                    absMask = line.removePrefix("B: ABS=")
                line.startsWith("B: REL=") ->
                    relMask = line.removePrefix("B: REL=")
            }
        }
        flush()
        return map
    }

    /**
     * 能力位图里第 bit 位是否为 1。
     * 内核打印的位图有两种字序（高字在前 / 低字在前），这里两种都尝试，宁可多识别不可漏识别。
     */
    private fun bitSet(mask: String, bit: Int): Boolean {
        if (mask.isBlank()) return false
        val words = mask.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val shift = bit % 64
        fun check(idx: Int): Boolean {
            val word = words.getOrNull(idx) ?: return false
            val v = word.toLongOrNull(16) ?: return false
            return ((v shr shift) and 1L) == 1L
        }
        return check(bit / 64) || check(words.size - 1 - bit / 64)
    }

    // ---------------------------------------------------------------- 原始事件读取

    private fun readDevice(device: MouseDevice) {
        var stream: FileInputStream? = null
        try {
            stream = FileInputStream(device.path)
            synchronized(lock) { streams[device.path] = stream }
            device.readError = null
            device.readOk = true

            val structSize = if (Process.is64Bit()) 24 else 16
            val buf = ByteArray(structSize)
            val bodyOffset = structSize - 8

            while (running.get()) {
                var read = 0
                while (read < structSize) {
                    val n = stream.read(buf, read, structSize - read)
                    if (n <= 0) throw java.io.EOFException("device closed")
                    read += n
                }
                device.eventCount++
                parseRawEvent(device, buf, bodyOffset)
            }
        } catch (t: Throwable) {
            device.readOk = false
            device.readError = t.javaClass.simpleName + ": " + (t.message ?: "")
            if (running.get()) {
                Log.w(TAG, "read ${device.path} failed: ${device.readError}")
                // 直接读失败（常见是 EACCES）→ 该设备改用 getevent 文本流
                fallbackGetevent(device)
            }
        } finally {
            try {
                stream?.close()
            } catch (_: Throwable) {
            }
            synchronized(lock) {
                readerThreads.remove(device.path)
                streams.remove(device.path)
            }
            if (pressed && pressedDevice == device.path) releaseIfPressed()
        }
    }

    private fun parseRawEvent(device: MouseDevice, buf: ByteArray, off: Int) {
        val type = (buf[off].toInt() and 0xff) or ((buf[off + 1].toInt() and 0xff) shl 8)
        val code = (buf[off + 2].toInt() and 0xff) or ((buf[off + 3].toInt() and 0xff) shl 8)
        val value = (buf[off + 4].toInt() and 0xff) or
            ((buf[off + 5].toInt() and 0xff) shl 8) or
            ((buf[off + 6].toInt() and 0xff) shl 16) or
            ((buf[off + 7].toInt() and 0xff) shl 24)

        when (type) {
            EV_SYN -> Unit
            EV_KEY -> {
                val label = "${keyName(code)} ${if (value == 1) "DOWN" else if (value == 0) "UP" else "REPEAT"}"
                device.lastKey = label
                onAnyKey("${device.name} · $label")
                onKeyEvent(code, value)
                if (code == BTN_LEFT || code == BTN_RIGHT) {
                    device.isMouse = true
                }
                if (code == watchButton) {
                    if (value == 1 && !pressed) {
                        pressed = true
                        pressedDevice = device.path
                        onPress(device.path)
                    } else if (value == 0 && pressed) {
                        releaseIfPressed()
                    }
                }
            }
            EV_REL -> when (code) {
                REL_X -> onRelativeMove(value, 0)
                REL_Y -> onRelativeMove(0, value)
            }
            EV_ABS -> when (code) {
                ABS_X -> emitAbsolute(device, value, isX = true)
                ABS_Y -> emitAbsolute(device, value, isX = false)
                // ABS_MT_* 一律忽略，避免触摸屏污染光标
            }
        }
    }

    private fun emitAbsolute(device: MouseDevice, value: Int, isX: Boolean) {
        if (!device.hasAbsolute) return
        if (isX) {
            val range = device.absXMax!! - device.absXMin!!
            if (range > 0) onAbsoluteMove(device, (value - device.absXMin!!).toFloat() / range, -1f)
        } else {
            val range = device.absYMax!! - device.absYMin!!
            if (range > 0) onAbsoluteMove(device, -1f, (value - device.absYMin!!).toFloat() / range)
        }
    }

    // ---------------------------------------------------------------- getevent 回退

    private fun fallbackGetevent(device: MouseDevice) {
        var process: java.lang.Process? = null
        try {
            process = java.lang.ProcessBuilder(GETEVENT, "-l", device.path).redirectErrorStream(true).start()
            synchronized(lock) { processes[device.path] = process }
            device.readOk = true
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    handleTextLine(device, line)
                }
            }
        } catch (t: Throwable) {
            device.readOk = false
            device.readError = (device.readError ?: "") + " | getevent: " + (t.message ?: "")
            if (running.get()) onError("${device.label()} 读取失败: ${t.message}")
        } finally {
            try {
                process?.destroy()
            } catch (_: Throwable) {
            }
            synchronized(lock) {
                processes.remove(device.path)
                readerThreads.remove(device.path)
            }
        }
    }

    private fun handleTextLine(device: MouseDevice, raw: String) {
        var line = raw.trim()
        if (line.isEmpty()) return
        if (line.startsWith("/dev/input/")) {
            val idx = line.indexOf(':')
            if (idx > 0) line = line.substring(idx + 1).trim()
        }
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 3) return
        val type = parts[0]
        val code = parts[1]
        val value = parts[2]

        when {
            type == "EV_KEY" -> {
                device.lastKey = "$code $value"
                onAnyKey("${device.name} · $code $value")
                val numeric = when (code) {
                    "BTN_LEFT" -> 272
                    "BTN_RIGHT" -> 273
                    else -> keyCodeOf(code)
                }
                if (numeric > 0) {
                    val v = when (value) {
                        "DOWN", "1", "00000001" -> 1
                        "UP", "0", "00000000" -> 0
                        else -> 2
                    }
                    onKeyEvent(numeric, v)
                }
                if (code == "BTN_LEFT" || code == "BTN_RIGHT") {
                    device.isMouse = true
                }
                if (code == watchButtonName()) {
                    val isDown = value == "DOWN" || value == "1" || value == "00000001"
                    val isUp = value == "UP" || value == "0" || value == "00000000"
                    if (isDown && !pressed) {
                        pressed = true
                        pressedDevice = device.path
                        onPress(device.path)
                    } else if (isUp && pressed) {
                        releaseIfPressed()
                    }
                }
            }
            type == "EV_REL" -> {
                val delta = value.toLongOrNull(16)?.toInt() ?: return
                when (code) {
                    "REL_X" -> onRelativeMove(delta, 0)
                    "REL_Y" -> onRelativeMove(0, delta)
                }
            }
            type == "EV_ABS" && device.hasAbsolute -> {
                val v = value.toLongOrNull(16)?.toInt() ?: return
                when (code) {
                    "ABS_X" -> emitAbsolute(device, v, isX = true)
                    "ABS_Y" -> emitAbsolute(device, v, isX = false)
                }
            }
        }
    }

    private fun keyName(code: Int): String = when (code) {
        BTN_LEFT -> "BTN_LEFT"
        0x111 -> "BTN_RIGHT"
        0x112 -> "BTN_MIDDLE"
        0x2f -> "BTN_TOOL_FINGER"
        0x145 -> "BTN_TOUCH"
        else -> "CODE_0x" + code.toString(16)
    }

    /** 给 UI / 诊断用的设备清单 JSON 数组 */
    fun devicesJson(): String = devices.joinToString(prefix = "[", postfix = "]") { it.toJson() }
}
