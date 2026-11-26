package com.m3e.clicker

import android.content.Context
import androidx.compose.runtime.Immutable
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import org.json.JSONObject
import java.io.File
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 悬浮窗测试 HUD：纯 View 实现（不依赖 ComposeView），窗口挂在前台服务上，
 * 退出 App 后仍然可见。窗口内画背景（纯色 / 黑白棋盘）、CPS 数字、以及可选的
 * 鼠标轨迹测试（光标点 + 淡出尾迹）。形状、尺寸、圆角、不透明度、文字样式与
 * 位置都在设置里可调，长方形与圆形分别保存尺寸。
 */
object OverlayHud {

    const val PREFS_NAME = "lazy_clicker_prefs"

    const val SHAPE_RECT = "rect"
    const val SHAPE_CIRCLE = "circle"

    const val MODE_PREFIX = "prefix"
    const val MODE_SUFFIX = "suffix"
    const val MODE_NUMBER = "number"

    private const val KEY_ON = "overlayOn"
    private const val KEY_SHAPE = "overlayShape"
    private const val KEY_X = "overlayX"
    private const val KEY_Y = "overlayY"

    private const val KEY_RECT_W = "overlayRectW"
    private const val KEY_RECT_H = "overlayRectH"
    private const val KEY_RECT_RADIUS = "overlayRectRadius"
    private const val KEY_CIRCLE_D = "overlayCircleD"

    private const val KEY_CPS_ON = "overlayCpsOn"
    private const val KEY_CPS_MODE = "overlayCpsMode"
    private const val KEY_CPS_PREFIX = "overlayCpsPrefix"
    private const val KEY_CPS_SUFFIX = "overlayCpsSuffix"
    private const val KEY_CPS_X = "overlayCpsX"
    private const val KEY_CPS_Y = "overlayCpsY"

    private const val KEY_BG = "overlayBg"
    private const val KEY_CHECK_SIZE = "overlayCheckSize"
    private const val KEY_OPACITY = "overlayOpacity"
    private const val KEY_CHECKER_ON = "overlayCheckerOn"
    private const val KEY_CPS_COLOR = "overlayCpsColor"
    private const val KEY_BORDER_ON = "overlayBorderOn"
    private const val KEY_BORDER_W = "overlayBorderW"
    private const val KEY_BORDER_COLOR = "overlayBorderColor"
    private const val KEY_BORDER_OPACITY = "overlayBorderOpacity"
    private const val KEY_CHECK_A = "overlayCheckColorA"
    private const val KEY_CHECK_B = "overlayCheckColorB"

    /** 旧版本键，用于把已有尺寸迁移到长方形配置 */
    private const val OLD_KEY_W = "overlayW"
    private const val OLD_KEY_H = "overlayH"

    private const val DEFAULT_SOLID_COLOR = 0xB3262626.toInt()

    @Immutable
    data class Config(
        val on: Boolean = false,
        val shape: String = SHAPE_RECT,
        val rectW: Int = 200,
        val rectH: Int = 96,
        val rectRadius: Int = 20,
        val circleD: Int = 140,
        val cpsOn: Boolean = false,
        val cpsMode: String = MODE_NUMBER,
        val prefix: String = "CPS ",
        val suffix: String = " CPS",
        val cpsX: Float = 0.5f,
        val cpsY: Float = 0.5f,
        val checkSize: Int = 16,
        val checkColorA: Int = 0xFF1A1A1A.toInt(),
        val checkColorB: Int = 0xFF383838.toInt(),
        val opacity: Float = 1f,
        val checkerOn: Boolean = false,
        val cpsColor: Int = 0xFFFFFFFF.toInt(),
        val borderOn: Boolean = false,
        val borderW: Int = 2,
        val borderColor: Int = 0xFF4E9F63.toInt(),
        val borderOpacity: Float = 1f,
    ) {
        val widthDp: Int get() = if (shape == SHAPE_CIRCLE) circleD else rectW
        val heightDp: Int get() = if (shape == SHAPE_CIRCLE) circleD else rectH
    }

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var manager: WindowManager? = null
    private var cpsText: CpsView? = null
    private var canvas: HudCanvas? = null

    /** 服务上报的屏幕尺寸，固定模式按它把整屏映射进窗口 */
    internal var lastScreenW = 1080
    internal var lastScreenH = 2400

    /** 轨迹当前是否开启（轮询线程只读这个标志，避免每帧读 SharedPreferences） */
    @Volatile
    var trailNow = false
        private set

    fun isShowing(): Boolean = root != null

    fun enabled(context: Context): Boolean = config(context).on

    /**
     * 配置以文件快照为准：幽灵模式的 :ghost 进程有自己的一份 SharedPreferences 缓存，
     * 主进程改了参数它看不到；每次保存都写一份 JSON 快照，两边都能读到最新值。
     */
    fun config(context: Context): Config = readSnapshot(context) ?: readPrefs(context)

    private fun readPrefs(context: Context): Config {
        val sp = prefs(context)
        val shape = sp.getString(KEY_SHAPE, SHAPE_RECT) ?: SHAPE_RECT
        return Config(
            on = sp.getBoolean(KEY_ON, false),
            shape = if (shape == SHAPE_CIRCLE) SHAPE_CIRCLE else SHAPE_RECT,
            rectW = sp.getInt(KEY_RECT_W, sp.getInt(OLD_KEY_W, 200)).coerceIn(40, 480),
            rectH = sp.getInt(KEY_RECT_H, sp.getInt(OLD_KEY_H, 96)).coerceIn(40, 480),
            rectRadius = sp.getInt(KEY_RECT_RADIUS, 20).coerceIn(0, 240),
            circleD = sp.getInt(KEY_CIRCLE_D, 140).coerceIn(40, 480),
            cpsOn = sp.getBoolean(KEY_CPS_ON, false),
            cpsMode = when (sp.getString(KEY_CPS_MODE, MODE_NUMBER)) {
                MODE_PREFIX -> MODE_PREFIX
                MODE_SUFFIX -> MODE_SUFFIX
                else -> MODE_NUMBER
            },
            prefix = sp.getString(KEY_CPS_PREFIX, "CPS ") ?: "CPS ",
            suffix = sp.getString(KEY_CPS_SUFFIX, " CPS") ?: " CPS",
            cpsX = sp.getFloat(KEY_CPS_X, 0.5f).coerceIn(0f, 1f),
            cpsY = sp.getFloat(KEY_CPS_Y, 0.5f).coerceIn(0f, 1f),
            // 兼容旧版本：老配置里 bg=checker 时迁移成棋盘开关
            checkerOn = sp.getBoolean(
                KEY_CHECKER_ON,
                sp.getString(KEY_BG, "solid") == "checker",
            ),
            checkSize = sp.getInt(KEY_CHECK_SIZE, 16).coerceIn(4, 96),
            checkColorA = sp.getInt(KEY_CHECK_A, 0xFF1A1A1A.toInt()),
            checkColorB = sp.getInt(KEY_CHECK_B, 0xFF383838.toInt()),
            opacity = sp.getFloat(KEY_OPACITY, 1f).coerceIn(0f, 1f),
            cpsColor = sp.getInt(KEY_CPS_COLOR, 0xFFFFFFFF.toInt()),
            borderOn = sp.getBoolean(KEY_BORDER_ON, false),
            borderW = sp.getInt(KEY_BORDER_W, 2).coerceIn(0, 16),
            borderColor = sp.getInt(KEY_BORDER_COLOR, 0xFF4E9F63.toInt()),
            borderOpacity = sp.getFloat(KEY_BORDER_OPACITY, 1f).coerceIn(0.05f, 1f),
        )
    }

    fun saveConfig(context: Context, config: Config) {
        prefs(context).edit()
            .putBoolean(KEY_ON, config.on)
            .putString(KEY_SHAPE, config.shape)
            .putInt(KEY_RECT_W, config.rectW.coerceIn(40, 480))
            .putInt(KEY_RECT_H, config.rectH.coerceIn(40, 480))
            .putInt(KEY_RECT_RADIUS, config.rectRadius.coerceIn(0, 240))
            .putInt(KEY_CIRCLE_D, config.circleD.coerceIn(40, 480))
            .putBoolean(KEY_CPS_ON, config.cpsOn)
            .putString(KEY_CPS_MODE, config.cpsMode)
            .putString(KEY_CPS_PREFIX, config.prefix)
            .putString(KEY_CPS_SUFFIX, config.suffix)
            .putFloat(KEY_CPS_X, config.cpsX)
            .putFloat(KEY_CPS_Y, config.cpsY)
            .putInt(KEY_CHECK_SIZE, config.checkSize.coerceIn(4, 96))
            .putInt(KEY_CHECK_A, config.checkColorA)
            .putInt(KEY_CHECK_B, config.checkColorB)
            .putFloat(KEY_OPACITY, config.opacity.coerceIn(0f, 1f))
            .putBoolean(KEY_CHECKER_ON, config.checkerOn)
            .putInt(KEY_CPS_COLOR, config.cpsColor)
            .putBoolean(KEY_BORDER_ON, config.borderOn)
            .putInt(KEY_BORDER_W, config.borderW.coerceIn(0, 16))
            .putInt(KEY_BORDER_COLOR, config.borderColor)
            .putFloat(KEY_BORDER_OPACITY, config.borderOpacity.coerceIn(0.05f, 1f))
            .apply()
        writeSnapshot(context, config)
    }

    private fun snapshotFile(context: Context) = File(context.filesDir, "overlay_cfg.json")

    private fun writeSnapshot(context: Context, c: Config) {
        try {
            val json = JSONObject()
                .put("on", c.on)
                .put("shape", c.shape)
                .put("rectW", c.rectW)
                .put("rectH", c.rectH)
                .put("rectRadius", c.rectRadius)
                .put("circleD", c.circleD)
                .put("cpsOn", c.cpsOn)
                .put("cpsMode", c.cpsMode)
                .put("prefix", c.prefix)
                .put("suffix", c.suffix)
                .put("cpsX", c.cpsX)
                .put("cpsY", c.cpsY)
                .put("checkerOn", c.checkerOn)
                .put("checkSize", c.checkSize)
                .put("checkColorA", c.checkColorA)
                .put("checkColorB", c.checkColorB)
                .put("opacity", c.opacity)
                .put("cpsColor", c.cpsColor)
                .put("borderOn", c.borderOn)
                .put("borderW", c.borderW)
                .put("borderColor", c.borderColor)
                .put("borderOpacity", c.borderOpacity)
                .toString()
            // 与磁盘内容比对而不是只比本进程上一次写入：
            // 另一个进程（:ghost）可能刚刚写过不同的一份，只看内存会漏写回去
            val file = snapshotFile(context)
            if (file.exists() && file.readText() == json) return
            file.writeText(json)
        } catch (_: Throwable) {
        }
    }

    private fun readSnapshot(context: Context): Config? {
        return try {
            val file = snapshotFile(context)
            if (!file.exists()) return null
            val o = JSONObject(file.readText())
            Config(
                on = o.optBoolean("on", false),
                shape = if (o.optString("shape", SHAPE_RECT) == SHAPE_CIRCLE) SHAPE_CIRCLE else SHAPE_RECT,
                rectW = o.optInt("rectW", 200).coerceIn(40, 480),
                rectH = o.optInt("rectH", 96).coerceIn(40, 480),
                rectRadius = o.optInt("rectRadius", 20).coerceIn(0, 240),
                circleD = o.optInt("circleD", 140).coerceIn(40, 480),
                cpsOn = o.optBoolean("cpsOn", false),
                cpsMode = when (o.optString("cpsMode", MODE_NUMBER)) {
                    MODE_PREFIX -> MODE_PREFIX
                    MODE_SUFFIX -> MODE_SUFFIX
                    else -> MODE_NUMBER
                },
                prefix = o.optString("prefix", "CPS "),
                suffix = o.optString("suffix", " CPS"),
                cpsX = o.optDouble("cpsX", 0.5).toFloat().coerceIn(0f, 1f),
                cpsY = o.optDouble("cpsY", 0.5).toFloat().coerceIn(0f, 1f),
                checkerOn = o.optBoolean("checkerOn", false),
                checkSize = o.optInt("checkSize", 16).coerceIn(4, 96),
                checkColorA = o.optInt("checkColorA", 0xFF1A1A1A.toInt()),
                checkColorB = o.optInt("checkColorB", 0xFF383838.toInt()),
                opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                cpsColor = o.optInt("cpsColor", 0xFFFFFFFF.toInt()),
                borderOn = o.optBoolean("borderOn", false),
                borderW = o.optInt("borderW", 2).coerceIn(0, 16),
                borderColor = o.optInt("borderColor", 0xFF4E9F63.toInt()),
                borderOpacity = o.optDouble("borderOpacity", 1.0).toFloat().coerceIn(0.05f, 1f),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** 绑定键位切换开关，返回切换后的状态（无权限时不打开） */
    fun toggle(context: Context): Boolean {
        val cfg = config(context)
        if (!cfg.on) {
            if (!SystemAccess.canDrawOverlays(context)) return false
            saveConfig(context, cfg.copy(on = true))
            show(context)
            return true
        }
        saveConfig(context, cfg.copy(on = false))
        hide()
        return false
    }

    fun show(context: Context) {
        if (root != null) {
            apply(context)
            return
        }
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val density = context.resources.displayMetrics.density
            val cfg = config(context)

            val container = FrameLayout(context)
            val hud = HudCanvas(context)
            container.addView(
                hud,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            canvas = hud

            cpsText = CpsView(context).apply { text = "0" }
            container.addView(
                cpsText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START,
                ),
            )

            val lp = WindowManager.LayoutParams(
                (cfg.widthDp * density).toInt(),
                (cfg.heightDp * density).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = prefs(context).getInt(KEY_X, 24)
                y = prefs(context).getInt(KEY_Y, 160)
            }

            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            container.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = lp.x
                        startY = lp.y
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        lp.x = (startX + (event.rawX - downX)).toInt()
                        lp.y = (startY + (event.rawY - downY)).toInt()
                        try {
                            wm.updateViewLayout(container, lp)
                        } catch (_: Throwable) {
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        prefs(context).edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
                        true
                    }

                    else -> false
                }
            }

            wm.addView(container, lp)
            root = container
            params = lp
            manager = wm
            paint(context, cfg)
        } catch (_: Throwable) {
            root = null
            params = null
            manager = null
            cpsText = null
            canvas = null
        }
    }

    /** 配置改动后重排窗口与背景 */
    fun apply(context: Context) {
        val container = root ?: return
        val lp = params ?: return
        val density = context.resources.displayMetrics.density
        val cfg = config(context)
        lp.width = (cfg.widthDp * density).toInt()
        lp.height = (cfg.heightDp * density).toInt()
        paint(context, cfg)
        try {
            manager?.updateViewLayout(container, lp)
        } catch (_: Throwable) {
        }
    }

    fun hide() {
        val container = root ?: return
        try {
            manager?.removeView(container)
        } catch (_: Throwable) {
        }
        root = null
        params = null
        manager = null
        cpsText = null
        canvas = null
        trailNow = false
    }

    fun update(status: ServiceStatus) {
        val text = cpsText ?: return
        // 配置已经由 paint()/apply() 同步到画布上，这里不再读 SharedPreferences
        val cfg = canvas?.overlayCfg ?: config(text.context)
        if (!cfg.cpsOn) {
            if (text.visibility != View.GONE) text.visibility = View.GONE
        } else {
            if (text.visibility != View.VISIBLE) {
                text.visibility = View.VISIBLE
                positionCps(cfg)
            }
            val cps = if (status.clicking) status.measuredCps else 0
            text.text = when (cfg.cpsMode) {
                MODE_PREFIX -> cfg.prefix + cps
                MODE_SUFFIX -> "$cps${cfg.suffix}"
                else -> cps.toString()
            }
        }
        lastScreenW = status.width
        lastScreenH = status.height
        // 旧版服务（没有 trailX）或刚启动时回退到注入光标，避免跳到 (0,0)
        val tx = if (status.trailX == 0 && status.trailY == 0) status.cursorX else status.trailX
        val ty = if (status.trailX == 0 && status.trailY == 0) status.cursorY else status.trailY
        canvas?.onCursor(tx.toFloat(), ty.toFloat(), status.width, status.height)
    }

    @Volatile
    private var pendingX = Float.NaN

    @Volatile
    private var pendingY = Float.NaN

    @Volatile
    private var feedPending = false

    /** 服务上报的光标位置（屏幕坐标）。可从任意线程调用，合并到下一帧处理。 */
    fun feedCursor(cursorX: Float, cursorY: Float) {
        val c = canvas ?: return
        pendingX = cursorX
        pendingY = cursorY
        if (feedPending) return
        feedPending = true
        c.post {
            feedPending = false
            val x = pendingX
            val y = pendingY
            if (!x.isNaN() && !y.isNaN()) {
                c.onCursor(x, y, lastScreenW, lastScreenH)
            }
        }
    }

    fun recenterTrail() {
        canvas?.reset()
    }

    /** 背景 + 文字可见性与位置 */
    private fun paint(context: Context, cfg: Config) {
        val container = root ?: return
        canvas?.let {
            it.overlayCfg = cfg
            val trail = TrailOverlay.config(context)
            it.trailCfg = trail
            trailNow = trail.on
            it.invalidate()
        }
        cpsText?.let {
            it.setColor(cfg.cpsColor)
            it.visibility = if (cfg.cpsOn) View.VISIBLE else View.GONE
        }
        positionCps(cfg)
        container.invalidate()
    }

    /** 按百分比把 CPS 文字摆在窗口内 */
    private fun positionCps(cfg: Config) {
        val container = root ?: return
        val text = cpsText ?: return
        val density = container.resources.displayMetrics.density
        val wPx = (cfg.widthDp * density).toInt()
        val hPx = (cfg.heightDp * density).toInt()
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        text.measure(spec, spec)
        val tw = text.measuredWidth
        val th = text.measuredHeight
        val lp = text.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = ((wPx - tw) * cfg.cpsX).roundToInt().coerceAtLeast(0)
        lp.topMargin = ((hPx - th) * cfg.cpsY).roundToInt().coerceAtLeast(0)
        text.layoutParams = lp
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 窗口内容：背景（纯色 / 棋盘，按形状裁切）、鼠标轨迹与光标点。
     * 轨迹位置来自 UserService 的原始相对位移，灵敏度独立配置。
     */
    class HudCanvas(context: Context) : View(context) {

        var overlayCfg = Config()
        var trailCfg = TrailOverlay.Config()

        private val density = resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = 0x33FFFFFF
        }
        private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val clipPath = Path()
        private val borderPath = Path()
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC)
        }
        private val rectF = RectF()
        private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val checkerMatrix = Matrix()
        private var checkerBitmap: Bitmap? = null
        private var checkerShader: BitmapShader? = null
        private var checkerKey = ""

        /**
         * @param x/y 位移坐标（跟随模式下的世界坐标）
         * @param sx/sy 记录当时的窗口内坐标（固定模式用，回卷后旧尾迹留在原地）
         * @param brk 是否与上一采样之间发生了回卷跳变，绘制时断开
         */
        private companion object {
            /** 尾迹最多保留的采样点：60Hz 下足以覆盖 3 秒淡出，再多只是纯开销 */
            const val MAX_SAMPLES = 240

            /** 单帧最多绘制的残影小球数，避免轨迹很长时把主线程画满 */
            const val MAX_DOTS_PER_FRAME = 480
        }

        private data class Sample(
            val x: Float,
            val y: Float,
            val t: Long,
            val sx: Float,
            val sy: Float,
            val brk: Boolean,
        )

        /**
         * 光标位移（屏幕像素 × 轨迹灵敏度），以屏幕中心为原点。
         * 位置来自 ClickerService 自己维护的 cursorX/cursorY，所以和点击落点一致，
         * 相对鼠标和绝对设备（触摸板）都能跟着动。
         */
        private var offX = 0f
        private var offY = 0f
        private var targetX = 0f
        private var targetY = 0f
        private var fromX = 0f
        private var fromY = 0f
        private var animStart = 0L
        private var animDur = 1L
        private var initialized = false
        private var lastFeedAt = 0L
        private val samples = ArrayList<Sample>()

        fun reset() {
            offX = 0f
            offY = 0f
            targetX = 0f
            targetY = 0f
            fromX = 0f
            fromY = 0f
            animStart = 0L
            initialized = false
            samples.clear()
            invalidate()
        }

        /** 服务上报的光标估计值（屏幕坐标） */
        fun onCursor(cursorX: Float, cursorY: Float, screenW: Int, screenH: Int) {
            if (screenW <= 0 || screenH <= 0) return
            val sens = trailCfg.sensitivity
            val nx = (cursorX - screenW / 2f) * sens
            val ny = (cursorY - screenH / 2f) * sens
            if (!initialized) {
                offX = nx
                offY = ny
                targetX = nx
                targetY = ny
                initialized = true
                invalidate()
                return
            }
            if (abs(nx - targetX) < 0.05f && abs(ny - targetY) < 0.05f) return
            val now = android.os.SystemClock.uptimeMillis()
            val gap = now - lastFeedAt
            lastFeedAt = now
            // 250ms 状态路径用 240ms 缓动补顺；高频取点（<80ms）时几乎直接跟手
            animDur = if (gap in 1L..80L) 16L else 240L
            fromX = offX
            fromY = offY
            targetX = nx
            targetY = ny
            animStart = now
            invalidate()
        }

        /** 推进插值，返回是否还需要下一帧 */
        private fun advance(): Boolean {
            val elapsed = android.os.SystemClock.uptimeMillis() - animStart
            val t = (elapsed.toFloat() / animDur.toFloat()).coerceIn(0f, 1f)
            offX = fromX + (targetX - fromX) * t
            offY = fromY + (targetY - fromY) * t
            return t < 1f
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (oldw == 0 || oldh == 0) reset()
        }

        /** 固定模式：整块屏幕按比例映射进窗口，越界后从另一侧回卷 */
        private fun fixedPoint(offX: Float, offY: Float): Pair<Float, Float> {
            val w = width.toFloat()
            val h = height.toFloat()
            val screenW = OverlayHud.lastScreenW.toFloat().coerceAtLeast(1f)
            val screenH = OverlayHud.lastScreenH.toFloat().coerceAtLeast(1f)
            val ux = offX * (w / screenW)
            val uy = offY * (h / screenH)
            return wrapTo(ux, w) to wrapTo(uy, h)
        }

        private fun wrapTo(v: Float, size: Float): Float {
            if (size <= 0f) return 0f
            val m = v % size
            return if (m < 0f) m + size else m
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val animating = advance()
            if (trailCfg.on) recordSample()
            val circle = overlayCfg.shape == SHAPE_CIRCLE
            val radius = if (circle) 0f else min(overlayCfg.rectRadius * density, min(w, h) / 2f)

            clipPath.reset()
            if (circle) {
                rectF.set(0f, 0f, w, h)
                clipPath.addOval(rectF, Path.Direction.CW)
            } else {
                rectF.set(0f, 0f, w, h)
                clipPath.addRoundRect(rectF, radius, radius, Path.Direction.CW)
            }

            val save = canvas.save()
            canvas.clipPath(clipPath)
            val follow = trailCfg.mode == TrailOverlay.MODE_FOLLOW
            // 纯色底始终绘制
            fillPaint.color = tint(DEFAULT_SOLID_COLOR, overlayCfg.opacity)
            canvas.drawRect(0f, 0f, w, h, fillPaint)
            if (overlayCfg.checkerOn) {
                // 棋盘是叠加层，跟随模式随光标反向滚动；按世界坐标算格子奇偶，无限平铺不闪烁
                drawChecker(canvas, w, h, if (follow) offX else 0f, if (follow) offY else 0f)
            }
            if (trailCfg.on) {
                drawTrail(canvas)
                drawDot(canvas)
            }
            canvas.restoreToCount(save)
            if (overlayCfg.borderOn && overlayCfg.borderW > 0) {
                val bw = overlayCfg.borderW * density
                borderPath.reset()
                if (circle) {
                    rectF.set(bw / 2f, bw / 2f, w - bw / 2f, h - bw / 2f)
                    borderPath.addOval(rectF, Path.Direction.CW)
                } else {
                    val r = (radius - bw / 2f).coerceAtLeast(0f)
                    rectF.set(bw / 2f, bw / 2f, w - bw / 2f, h - bw / 2f)
                    borderPath.addRoundRect(rectF, r, r, Path.Direction.CW)
                }
                borderPaint.strokeWidth = bw
                borderPaint.color = tint(
                    overlayCfg.borderColor,
                    overlayCfg.opacity * overlayCfg.borderOpacity,
                )
                canvas.drawPath(borderPath, borderPaint)
            } else {
                strokePaint.color = tint(0x33FFFFFF, overlayCfg.opacity)
                canvas.drawPath(clipPath, strokePaint)
            }
            // 只有需要动的时候才续帧：插值中，或还有未淡完的尾迹
            if (animating || (trailCfg.on && samples.isNotEmpty())) postInvalidateOnAnimation()
        }

        /** 按帧记录尾迹采样（存的是位移，绘制时再换算） */
        private fun recordSample() {
            val now = System.currentTimeMillis()
            val last = samples.lastOrNull()
            val follow = trailCfg.mode == TrailOverlay.MODE_FOLLOW
            val w = width.toFloat()
            val h = height.toFloat()
            val screenW = OverlayHud.lastScreenW.toFloat().coerceAtLeast(1f)
            val screenH = OverlayHud.lastScreenH.toFloat().coerceAtLeast(1f)
            val px: Float
            val py: Float
            if (follow) {
                px = offX
                py = offY
            } else {
                px = wrapTo(offX * (w / screenW), w)
                py = wrapTo(offY * (h / screenH), h)
            }
            // 固定模式回卷时新旧两点跨了半屏以上，标记断点，旧尾迹留在原处
            // 分轴判断：横向按窗口宽、纵向按窗口高，宽矩形窗口里纵向回卷也能被识别
            val brk = !follow && last != null &&
                (abs(px - last.sx) > w * 0.5f || abs(py - last.sy) > h * 0.5f)
            if (last == null || brk || abs(last.x - offX) > 1f || abs(last.y - offY) > 1f) {
                samples.add(Sample(offX, offY, now, px, py, brk))
                while (samples.size > MAX_SAMPLES) samples.removeAt(0)
            }
            while (samples.isNotEmpty() && now - samples[0].t > trailCfg.fadeMs) {
                samples.removeAt(0)
            }
        }

        /**
         * 黑白棋盘。shift 为图案相对窗口的位移（跟随模式下 = 光标位移）。
         * 颜色由"世界坐标"决定：floor(wx/s)+floor(wy/s) 的奇偶，
         * 因此图案偏移任意距离都不会出现整块翻转的闪烁。
         */
        private fun drawChecker(canvas: Canvas, w: Float, h: Float, shiftX: Float, shiftY: Float) {
            val cell = (overlayCfg.checkSize * density).coerceAtLeast(3f)
            val cellInt = cell.roundToInt().coerceAtLeast(3)
            val key = "$cellInt|${overlayCfg.checkColorA}|${overlayCfg.checkColorB}|${overlayCfg.opacity}"
            if (key != checkerKey || checkerBitmap == null) {
                val size = cellInt * 2
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                val p = Paint()
                p.color = tint(overlayCfg.checkColorA, overlayCfg.opacity)
                c.drawRect(0f, 0f, cellInt.toFloat(), cellInt.toFloat(), p)
                p.color = tint(overlayCfg.checkColorB, overlayCfg.opacity)
                c.drawRect(cellInt.toFloat(), 0f, size.toFloat(), cellInt.toFloat(), p)
                c.drawRect(0f, cellInt.toFloat(), cellInt.toFloat(), size.toFloat(), p)
                p.color = tint(overlayCfg.checkColorA, overlayCfg.opacity)
                c.drawRect(cellInt.toFloat(), cellInt.toFloat(), size.toFloat(), size.toFloat(), p)
                checkerBitmap?.recycle()
                checkerBitmap = bmp
                checkerShader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                checkerKey = key
            }
            val shader = checkerShader ?: return
            val period = (cellInt * 2).toFloat()
            checkerMatrix.setTranslate(
                -((shiftX % period + period) % period),
                -((shiftY % period + period) % period),
            )
            shader.setLocalMatrix(checkerMatrix)
            shaderPaint.shader = shader
            canvas.drawRect(0f, 0f, w, h, shaderPaint)
        }

        private fun drawTrail(canvas: Canvas) {
            if (samples.size < 2) return
            val now = System.currentTimeMillis()
            val fade = trailCfg.fadeMs.toFloat().coerceAtLeast(1f)
            val follow = trailCfg.mode == TrailOverlay.MODE_FOLLOW
            val w = width.toFloat()
            val h = height.toFloat()
            val screenW = OverlayHud.lastScreenW.toFloat().coerceAtLeast(1f)
            val screenH = OverlayHud.lastScreenH.toFloat().coerceAtLeast(1f)
            val radius = (trailCfg.trailW * density * 1.4f).coerceAtLeast(1.2f)
            val step = (radius * 0.55f).coerceAtLeast(1.2f)

            // 整段尾迹画在一个图层里，用 SRC 覆盖而不是叠加，
            // 半透明小球互相重叠时不会越叠越黑
            val layer = canvas.saveLayer(null, null)
            var prevX = 0f
            var prevY = 0f
            var prevLife = 0f
            var hasPrev = false
            var drawn = 0
            val first = maxOf(0, samples.size - MAX_SAMPLES)
            for (i in first until samples.size) {
                val sm = samples[i]
                val life = (1f - (now - sm.t) / fade).coerceIn(0f, 1f)
                if (life <= 0f) {
                    hasPrev = false
                    continue
                }
                val px: Float
                val py: Float
                if (follow) {
                    px = w / 2f + (sm.x - offX)
                    py = h / 2f + (sm.y - offY)
                } else {
                    px = sm.sx
                    py = sm.sy
                }
                if (sm.brk) hasPrev = false
                if (hasPrev) {
                    // 采样间隔大时在两点间补点，残影保持连续
                    val dist = kotlin.math.hypot(px - prevX, py - prevY)
                    val fill = trailCfg.fillPoints.coerceIn(0, 128)
                    val n = if (fill == 0) 1 else (dist / step).toInt().coerceIn(1, fill)
                    for (k in 1..n) {
                        if (drawn >= MAX_DOTS_PER_FRAME) break
                        drawn++
                        val t = k.toFloat() / n
                        val alpha = (prevLife + (life - prevLife) * t) * 190f
                        trailPaint.color = Color.argb(alpha.toInt().coerceIn(0, 255), 255, 255, 255)
                        canvas.drawCircle(
                            prevX + (px - prevX) * t,
                            prevY + (py - prevY) * t,
                            radius,
                            trailPaint,
                        )
                    }
                }
                prevX = px
                prevY = py
                prevLife = life
                hasPrev = true
            }
            canvas.restoreToCount(layer)
        }

        private fun drawDot(canvas: Canvas) {
            val (cx, cy) = if (trailCfg.mode == TrailOverlay.MODE_FIXED) {
                fixedPoint(offX, offY)
            } else {
                width / 2f to height / 2f
            }
            val r = (trailCfg.dotR * density).coerceAtLeast(2f)
            dotFill.color = trailCfg.dotColor
            canvas.drawCircle(cx, cy, r, dotFill)
            val bw = trailCfg.borderW * density
            if (bw > 0f) {
                dotStroke.strokeWidth = bw
                dotStroke.color = trailCfg.borderColor
                canvas.drawCircle(cx, cy, r, dotStroke)
            }
        }
    }
}

/** 按比例缩放颜色 alpha */
private fun tint(color: Int, alpha: Float): Int {
    val a = ((color ushr 24) * alpha).roundToInt().coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (a shl 24)
}

/**
 * CPS 文字。文字色、投影、发光分别可调；发光与投影各画一遍再叠正文，
 * 保证两层效果互不覆盖。
 */
private class CpsView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.product_sans_light)
        textSize = 22f * density
    }

    var text: String = "0"
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private var textColor: Int = Color.WHITE

    fun setColor(color: Int) {
        if (textColor == color) return
        textColor = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fm = paint.fontMetrics
        val w = paint.measureText(text).toInt() + 4
        val h = (fm.descent - fm.ascent).toInt() + 4
        setMeasuredDimension(w.coerceAtLeast(1), h.coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        val fm = paint.fontMetrics
        val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
        val x = (width - paint.measureText(text)) / 2f
        paint.color = textColor
        canvas.drawText(text, x, baseline, paint)
    }
}
