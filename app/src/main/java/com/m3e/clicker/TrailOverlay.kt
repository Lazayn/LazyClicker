package com.m3e.clicker

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 鼠标轨迹测试的配置（渲染在悬浮窗内部，由 OverlayHud 的 HudCanvas 绘制）。
 *
 * 位置数据来自 UserService 上报的原始相对位移（未经“高级”里的灵敏度处理），
 * 所以这里的灵敏度是独立的一套。两种模式：
 * - 跟随：光标固定在窗口中心，棋盘随鼠标移动；
 * - 固定：棋盘不动，光标在窗口内移动，越界后从另一侧回卷；校准回到中心。
 */
object TrailOverlay {

    const val PREFS_NAME = "lazy_clicker_prefs"

    const val MODE_FOLLOW = "follow"

    const val MODE_FIXED = "fixed"

    private const val KEY_ON = "trailOn"
    private const val KEY_MODE = "trailMode"
    private const val KEY_SENS = "trailSens"
    private const val KEY_DOT_R = "trailDotR"
    private const val KEY_DOT_COLOR = "trailDotColor"
    private const val KEY_BORDER_W = "trailBorderW"
    private const val KEY_BORDER_COLOR = "trailBorderColor"
    private const val KEY_TRAIL_W = "trailTrailW"
    private const val KEY_FADE = "trailFadeMs"
    private const val KEY_FILL_POINTS = "trailFillPoints"

    data class Config(
        val on: Boolean = false,
        val mode: String = MODE_FOLLOW,
        val sensitivity: Float = 1f,
        val dotR: Int = 6,
        val dotColor: Int = 0xFFFFFFFF.toInt(),
        val borderW: Int = 0,
        val borderColor: Int = 0xFF111111.toInt(),
        val trailW: Int = 2,
        val fadeMs: Int = 700,
        /** 每段最多补点（0–128），0 表示不补 */
        val fillPoints: Int = 12,
    )

    fun config(context: Context): Config = readSnapshot(context) ?: readPrefs(context)

    private fun readPrefs(context: Context): Config {
        val sp = prefs(context)
        return Config(
            on = sp.getBoolean(KEY_ON, false),
            mode = if (sp.getString(KEY_MODE, MODE_FOLLOW) == MODE_FIXED) MODE_FIXED else MODE_FOLLOW,
            sensitivity = sp.getFloat(KEY_SENS, 1f).coerceIn(0.01f, 3f),
            dotR = sp.getInt(KEY_DOT_R, 6).coerceIn(2, 40),
            dotColor = sp.getInt(KEY_DOT_COLOR, 0xFFFFFFFF.toInt()),
            borderW = sp.getInt(KEY_BORDER_W, 0).coerceIn(0, 8),
            borderColor = sp.getInt(KEY_BORDER_COLOR, 0xFF111111.toInt()),
            trailW = sp.getInt(KEY_TRAIL_W, 2).coerceIn(1, 10),
            fadeMs = sp.getInt(KEY_FADE, 700).coerceIn(100, 3000),
            fillPoints = sp.getInt(KEY_FILL_POINTS, 12).coerceIn(0, 128),
        )
    }

    fun saveConfig(context: Context, config: Config) {
        prefs(context).edit()
            .putBoolean(KEY_ON, config.on)
            .putString(KEY_MODE, config.mode)
            .putFloat(KEY_SENS, config.sensitivity.coerceIn(0.01f, 3f))
            .putInt(KEY_DOT_R, config.dotR.coerceIn(2, 40))
            .putInt(KEY_DOT_COLOR, config.dotColor)
            .putInt(KEY_BORDER_W, config.borderW.coerceIn(0, 8))
            .putInt(KEY_BORDER_COLOR, config.borderColor)
            .putInt(KEY_TRAIL_W, config.trailW.coerceIn(1, 10))
            .putInt(KEY_FADE, config.fadeMs.coerceIn(100, 3000))
            .putInt(KEY_FILL_POINTS, config.fillPoints.coerceIn(0, 128))
            .apply()
        writeSnapshot(context, config)
    }

    private fun snapshotFile(context: Context) = File(context.filesDir, "trail_cfg.json")

    private fun writeSnapshot(context: Context, c: Config) {
        try {
            val json = JSONObject()
                .put("on", c.on)
                .put("mode", c.mode)
                .put("sensitivity", c.sensitivity)
                .put("dotR", c.dotR)
                .put("dotColor", c.dotColor)
                .put("borderW", c.borderW)
                .put("borderColor", c.borderColor)
                .put("trailW", c.trailW)
                .put("fadeMs", c.fadeMs)
                .put("fillPoints", c.fillPoints)
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
                mode = if (o.optString("mode", MODE_FOLLOW) == MODE_FIXED) MODE_FIXED else MODE_FOLLOW,
                sensitivity = o.optDouble("sensitivity", 1.0).toFloat().coerceIn(0.01f, 3f),
                dotR = o.optInt("dotR", 6).coerceIn(2, 40),
                dotColor = o.optInt("dotColor", 0xFFFFFFFF.toInt()),
                borderW = o.optInt("borderW", 0).coerceIn(0, 8),
                borderColor = o.optInt("borderColor", 0xFF111111.toInt()),
                trailW = o.optInt("trailW", 2).coerceIn(1, 10),
                fadeMs = o.optInt("fadeMs", 700).coerceIn(100, 3000),
                fillPoints = o.optInt("fillPoints", 12).coerceIn(0, 128),
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun enabled(context: Context): Boolean = config(context).on

    /** 校准：光标回中、清空尾迹 */
    fun recenter() {
        OverlayHud.recenterTrail()
    }

    fun update(status: ServiceStatus) {
        if (status.trailX == 0 && status.trailY == 0) {
            OverlayHud.feedCursor(status.cursorX.toFloat(), status.cursorY.toFloat())
        } else {
            OverlayHud.feedCursor(status.trailX.toFloat(), status.trailY.toFloat())
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
