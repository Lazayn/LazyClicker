package com.m3e.clicker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class BridgeState {
    CHECKING,
    NO_SHIZUKU,
    SHIZUKU_NOT_RUNNING,
    NEED_PERMISSION,
    CONNECTING,
    CONNECTED,
    UNSUPPORTED,
    ERROR,
}

/** 输入节点详情（诊断用） */
data class NodeDetail(
    val path: String,
    val name: String,
    val mouse: Boolean,
    val ok: Boolean,
    val err: String,
    val events: Long,
)

/** 从 UserService 拉回来的运行状态 */
data class ServiceStatus(
    val ready: Boolean = false,
    val uid: Int = -1,
    val trigger: Boolean = false,
    val clicking: Boolean = false,
    val manual: Boolean = false,
    val pressed: Boolean = false,
    val inject: Boolean = false,
    val injectMode: String = "none",
    val enabled: Boolean = true,
    val switchSeq: Int = 0,
    val toggleKey: Int = -1,
    val capturing: Boolean = false,
    val capturedKey: Int = -1,
    val method: String = "default",
    val dcLocked: Boolean = false,
    val injectImpl: String = "",
    val injectError: String = "",
    val minCps: Int = 8,
    val maxCps: Int = 12,
    val cursorX: Int = 0,
    val cursorY: Int = 0,
    val width: Int = 1080,
    val height: Int = 2400,
    val followCursor: Boolean = true,
    val sensitivityValue: Float = 1.0f,
    val acceleration: Boolean = true,
    val err: String = "",
    val devices: List<String> = emptyList(),
    val nodes: List<NodeDetail> = emptyList(),
    val scanError: String = "",
    val clicks: Long = 0,
    val lastKey: String = "",
    val measuredCps: Int = 0,
    val pressedDevice: String = "",
    val dcCps: Int = 60,
    /** DC 持续时间区间（100..5000 ms） */
    val dcDurMinMs: Int = 800,
    val dcDurMaxMs: Int = 1500,
    val multiMin: Int = 20,
    val multiMax: Int = 40,
    val doublePct: Int = 60,
    val triplePct: Int = 20,
    val quadPct: Int = 10,
    val gapMin: Int = 2,
    val gapMax: Int = 4,
) {
    companion object {
        fun parse(raw: String?): ServiceStatus {
            if (raw.isNullOrBlank()) return ServiceStatus()
            return try {
                val json = JSONObject(raw)
                val devices = ArrayList<String>()
                val array = json.optJSONArray("devices") ?: JSONArray()
                for (i in 0 until array.length()) devices.add(array.optString(i))

                val nodes = ArrayList<NodeDetail>()
                val nodeArray = json.optString("nodeDetails", "").let {
                    if (it.isBlank()) null else runCatching { JSONArray(it) }.getOrNull()
                }
                if (nodeArray != null) {
                    for (i in 0 until nodeArray.length()) {
                        val o = nodeArray.optJSONObject(i) ?: continue
                        nodes.add(
                            NodeDetail(
                                path = o.optString("path", ""),
                                name = o.optString("name", ""),
                                mouse = o.optBoolean("mouse", false),
                                ok = o.optBoolean("ok", false),
                                err = o.optString("err", ""),
                                events = o.optLong("events", 0),
                            )
                        )
                    }
                }

                ServiceStatus(
                    ready = json.optBoolean("ready", false),
                    uid = json.optInt("uid", -1),
                    trigger = json.optBoolean("trigger", false),
                    clicking = json.optBoolean("clicking", false),
                    manual = json.optBoolean("manual", false),
                    pressed = json.optBoolean("pressed", false),
                    inject = json.optBoolean("inject", false),
                    injectMode = json.optString("injectMode", "none"),
                    enabled = json.optBoolean("enabled", true),
                    switchSeq = json.optInt("switchSeq", 0),
                    toggleKey = json.optInt("toggleKey", -1),
                    capturing = json.optBoolean("capturing", false),
                    capturedKey = json.optInt("capturedKey", -1),
                    method = json.optString("method", "default"),
                    dcLocked = json.optBoolean("dcLocked", false),
                    injectImpl = json.optString("injectImpl", ""),
                    injectError = json.optString("injectError", ""),
                    minCps = json.optInt("minCps", 8),
                    maxCps = json.optInt("maxCps", 12),
                    cursorX = json.optInt("cursorX", 0),
                    cursorY = json.optInt("cursorY", 0),
                    width = json.optInt("width", 1080),
                    height = json.optInt("height", 2400),
                    followCursor = json.optBoolean("followCursor", true),
                    acceleration = json.optBoolean("acceleration", true),
                    err = json.optString("err", ""),
                    devices = devices,
                    nodes = nodes,
                    scanError = json.optString("scanError", ""),
                    clicks = json.optLong("clicks", 0),
                    lastKey = json.optString("lastKey", ""),
                    measuredCps = json.optInt("measuredCps", 0),
                    pressedDevice = json.optString("pressedDevice", ""),
                    dcCps = json.optInt("dcCps", 60),
                    dcDurMinMs = json.optInt("dcDurMinMs", 800),
                    dcDurMaxMs = json.optInt("dcDurMaxMs", 1500),
                    multiMin = json.optInt("multiMin", 20),
                    multiMax = json.optInt("multiMax", 40),
                    doublePct = json.optInt("doublePct", 60),
                    triplePct = json.optInt("triplePct", 20),
                    quadPct = json.optInt("quadPct", 10),
                    gapMin = json.optInt("gapMin", 2),
                    gapMax = json.optInt("gapMax", 4),
                )
            } catch (_: Throwable) {
                ServiceStatus()
            }
        }
    }
}

/** 界面参数 */
data class ClickerParams(
    /** 总开关（界面上的开关，也可由绑定键位切换） */
    val enabled: Boolean = true,
    /** 绑定键位（evdev 键码），-1 未绑定 */
    val toggleKey: Int = -1,
    /** 左键点击模式：default / dbc / dc */
    val method: String = "default",
    /** 左键默认与 DBC 的基础 CPS 区间（DBC 上限更低，由界面限制） */
    val minCps: Int = 8,
    val maxCps: Int = 12,
    /** 多倍点概率区间（0..100） */
    val multiMin: Int = 20,
    val multiMax: Int = 40,
    val doublePct: Int = 60,
    val triplePct: Int = 20,
    val quadPct: Int = 10,
    /** 多倍点间隔区间（0..10 ms） */
    val gapMin: Int = 2,
    val gapMax: Int = 4,
    /** DC 模式 */
    val dcCps: Int = 60,
    /** DC 持续时间区间（100..5000 ms） */
    val dcDurMinMs: Int = 800,
    val dcDurMaxMs: Int = 1500,
    val followCursor: Boolean = true,
    val fixedX: Int = 540,
    val fixedY: Int = 1200,
    val sensitivity: Float = 1.0f,
    val touchCompat: Boolean = false,
    val acceleration: Boolean = true,
)

/** 参数转成服务端唯一配置入口需要的 JSON */
fun ClickerParams.toJson(): String {
    return org.json.JSONObject().apply {
        put("enabled", enabled)
        put("toggleKey", toggleKey)
        put("method", when (method) {
            "dbc" -> 1
            "dc" -> 2
            else -> 0
        })
        put("minCps", minCps)
        put("maxCps", maxCps)
        put("multiMin", multiMin)
        put("multiMax", multiMax)
        put("doublePct", doublePct)
        put("triplePct", triplePct)
        put("quadPct", quadPct)
        put("gapMin", gapMin)
        put("gapMax", gapMax)
        put("dcCps", dcCps)
        put("dcDurMinMs", dcDurMinMs)
        put("dcDurMaxMs", dcDurMaxMs)
        put("followCursor", followCursor)
        put("fixedX", fixedX)
        put("fixedY", fixedY)
        put("sensitivity", sensitivity)
        put("touchCompat", touchCompat)
        put("acceleration", acceleration)
    }.toString()
}

/**
 * App 侧统一管理 Shizuku 授权、UserService 绑定、状态轮询、前台服务与无障碍回退。
 */
class ClickerBridge private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "M3E-Bridge"
        private const val REQUEST_CODE = 4001
        private const val SERVICE_VERSION = 7

        @Volatile
        private var instance: ClickerBridge? = null

        fun get(context: Context): ClickerBridge {
            return instance ?: synchronized(this) {
                instance ?: ClickerBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(BridgeState.CHECKING)
    val state = _state.asStateFlow()

    private val _status = MutableStateFlow(ServiceStatus())
    val status = _status.asStateFlow()

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    /** 无障碍兜底连点是否在运行 */
    private val _a11yBurst = MutableStateFlow(false)
    val a11yBurst = _a11yBurst.asStateFlow()

    private val serviceArgs: Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(appContext.packageName, ClickerService::class.java.name)
    )
        .daemon(true)
        .processNameSuffix("clicker")
        .debuggable(false)
        .version(SERVICE_VERSION)

    @Volatile
    private var service: IClickerService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IClickerService.Stub.asInterface(binder)
            _state.value = BridgeState.CONNECTED
            Log.i(TAG, "user service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _state.value = BridgeState.NEED_PERMISSION
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refresh()
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        _state.value = BridgeState.SHIZUKU_NOT_RUNNING
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindService()
            } else {
                _state.value = BridgeState.NEED_PERMISSION
                _message.value = "Shizuku 授权被拒绝，请重新授权"
            }
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
        startPolling()
    }

    fun refresh() {
        _state.value = try {
            if (!Shizuku.pingBinder()) {
                if (isShizukuInstalled()) BridgeState.SHIZUKU_NOT_RUNNING else BridgeState.NO_SHIZUKU
            } else if (Shizuku.isPreV11()) {
                BridgeState.UNSUPPORTED
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                if (service != null) {
                    BridgeState.CONNECTED
                } else {
                    bindService()
                    BridgeState.CONNECTING
                }
            } else {
                BridgeState.NEED_PERMISSION
            }
        } catch (t: Throwable) {
            _message.value = t.message ?: t.toString()
            BridgeState.ERROR
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                refresh()
                return
            }
            if (Shizuku.isPreV11()) {
                _state.value = BridgeState.UNSUPPORTED
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindService()
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (t: Throwable) {
            _state.value = BridgeState.ERROR
            _message.value = t.message ?: t.toString()
        }
    }

    private fun bindService() {
        if (service != null) {
            _state.value = BridgeState.CONNECTED
            return
        }
        _state.value = BridgeState.CONNECTING
        scope.launch {
            try {
                Shizuku.bindUserService(serviceArgs, connection)
            } catch (t: Throwable) {
                _state.value = BridgeState.ERROR
                _message.value = "绑定连点服务失败: ${t.message}"
            }
        }
    }

    /** 在后台线程安全调用 UserService */
    fun send(block: (IClickerService) -> Unit) {
        val target = service
        if (target == null) {
            _message.value = "连点服务未就绪，请先授予 Shizuku 权限"
            return
        }
        scope.launch {
            try {
                block(target)
            } catch (t: Throwable) {
                _message.value = "服务调用失败: ${t.message}"
            }
        }
    }

    fun applyParams(params: ClickerParams) {
        send { svc -> svc.configureJson(params.toJson()) }
    }

    fun startTrigger(params: ClickerParams) {
        send { svc ->
            svc.configureJson(params.toJson())
            svc.startMouseTrigger()
        }
        ClickerForegroundService.start(appContext)
    }

    fun beginKeyCapture() = send { it.beginKeyCapture() }

    fun endKeyCapture() = send { it.endKeyCapture() }

    fun stopTrigger() {
        send { it.stopMouseTrigger() }
    }

    fun startManual() = send { it.startManualBurst() }

    fun stopManual() = send { it.stopManualBurst() }

    fun clickOnce() = send { it.clickOnce() }

    fun resetCursor() = send { it.resetCursor() }

    // ---------------------------------------------------------- 无障碍兜底

    fun accessibilityReady(): Boolean = SystemAccess.isAccessibilityEnabled(appContext)

    private var a11yJob: Job? = null

    /** 没有 Shizuku 时，用无障碍手势在固定坐标连点（兜底方案，不是鼠标左键） */
    fun startAccessibilityBurst(params: ClickerParams): Boolean {
        if (!ClickerAccessibilityService.isConnected()) {
            _message.value = "无障碍服务未连接，请先在系统设置里开启"
            return false
        }
        a11yJob?.cancel()
        _a11yBurst.value = true
        a11yJob = scope.launch {
            try {
                while (isActive && _a11yBurst.value) {
                    ClickerAccessibilityService.tap(
                        params.fixedX.toFloat(),
                        params.fixedY.toFloat()
                    )
                    val cps = if (params.minCps == params.maxCps) {
                        params.minCps
                    } else {
                        Random.nextInt(min(params.minCps, params.maxCps), max(params.minCps, params.maxCps) + 1)
                    }
                    delay((1000L / max(cps, 1)).coerceAtLeast(1L))
                }
            } finally {
                _a11yBurst.value = false
            }
        }
        return true
    }

    fun stopAccessibilityBurst() {
        _a11yBurst.value = false
        a11yJob?.cancel()
        a11yJob = null
    }

    fun accessibilityTap(params: ClickerParams) {
        ClickerAccessibilityService.tap(params.fixedX.toFloat(), params.fixedY.toFloat())
    }

    // ---------------------------------------------------------- 杂项

    fun isShizukuInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: Throwable) {
        false
    }

    fun openShizuku(): Boolean {
        val packageName = "moe.shizuku.privileged.api"
        return try {
            val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                true
            } else {
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private var anomalyCount = 0
    private var stateReset = false

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                val svc = service
                if (svc != null) {
                    try {
                        _status.value = ServiceStatus.parse(svc.statusJson())
                        svc.ping()
                        // 首次连上：清除上一轮残留的触发 / manual 状态
                        if (!stateReset) {
                            stateReset = true
                            try {
                                svc.stopMouseTrigger()
                                svc.stopManualBurst()
                            } catch (_: Throwable) {
                            }
                        }
                        // 恶性状态保护：没有 trigger / manual 却在连点 → 连点两次轮询后强制停止
                        val st = _status.value
                        if (st.clicking && !st.trigger && !st.manual) {
                            anomalyCount++
                            if (anomalyCount >= 2) {
                                anomalyCount = 0
                                try {
                                    svc.stopMouseTrigger()
                                    svc.stopManualBurst()
                                } catch (_: Throwable) {
                                }
                                ClickerForegroundService.stop(appContext)
                                _message.value = "检测到异常连点，已自动停止"
                            }
                        } else {
                            anomalyCount = 0
                        }
                    } catch (t: Throwable) {
                        _message.value = "与连点服务断开: ${t.message}"
                        service = null
                        _state.value = BridgeState.NEED_PERMISSION
                    }
                }
                delay(250)
            }
        }
    }

    @Suppress("unused")
    private fun now() = SystemClock.elapsedRealtime()
}
