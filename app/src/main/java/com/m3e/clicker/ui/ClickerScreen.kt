package com.m3e.clicker.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.lerp
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiDropdownColors
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiOpenMode
import ui.feice.mochi.basic.MochiTrigger
import ui.feice.mochi.basic.MochiAlign
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.theme.PaletteStyle
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import ui.feice.mochi.effect.glass.rememberMochiBackdrop
import ui.feice.mochi.effect.glass.mochiBackdropSource
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.picker.MochiColorWell
import androidx.compose.ui.graphics.toArgb
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.basic.MochiButtonStyle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import com.m3e.clicker.BridgeState
import com.m3e.clicker.ClickerBridge
import com.m3e.clicker.ClickerForegroundService
import com.m3e.clicker.ClickerParams
import com.m3e.clicker.GhostMode
import com.m3e.clicker.KeyFilterService
import com.m3e.clicker.R
import com.m3e.clicker.OverlayDaemon
import com.m3e.clicker.OverlayHud
import com.m3e.clicker.ServiceStatus
import com.m3e.clicker.SystemAccess
import com.m3e.clicker.TrailOverlay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PREFS_NAME = "lazy_clicker_prefs"

private fun loadParams(context: Context): ClickerParams {
    val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val method = sp.getString("method", "default") ?: "default"
    val lo = if (method == "dc") 15 else 1
    val hi = when (method) {
        "dbc" -> 16
        "dc" -> 150
        else -> 25
    }
    return ClickerParams(
        enabled = sp.getBoolean("enabled", true),
        toggleKey = sp.getInt("toggleKey", -1),
        overlayKey = sp.getInt("overlayKey", -1),
        calibKey = sp.getInt("calibKey", -1),
        method = method,
        minCps = sp.getInt("minCps", 8).coerceIn(lo, hi),
        maxCps = sp.getInt("maxCps", 12).coerceIn(lo, hi),
        defaultMinCps = sp.getInt("defaultMinCps", sp.getInt("minCps", 8)).coerceIn(1, 25),
        defaultMaxCps = sp.getInt("defaultMaxCps", sp.getInt("maxCps", 12)).coerceIn(1, 25),
        dbcMinCps = sp.getInt("dbcMinCps", sp.getInt("minCps", 8)).coerceIn(1, 16),
        dbcMaxCps = sp.getInt("dbcMaxCps", sp.getInt("maxCps", 12)).coerceIn(1, 16),
        multiMin = sp.getInt("multiMin", 20),
        multiMax = sp.getInt("multiMax", 40),
        doublePct = sp.getInt("doublePct", 60),
        triplePct = sp.getInt("triplePct", 20),
        quadPct = sp.getInt("quadPct", 10),
        gapMin = sp.getInt("gapMin", 2),
        gapMax = sp.getInt("gapMax", 4),
        dcCps = sp.getInt("dcCps", 60),
        dcDurMinMs = sp.getInt("dcDurMinMs", 800),
        dcDurMaxMs = sp.getInt("dcDurMaxMs", 1500),
        followCursor = sp.getBoolean("followCursor", true),
        fixedX = sp.getInt("fixedX", 540),
        fixedY = sp.getInt("fixedY", 1200),
        sensitivity = sp.getFloat("sensitivity", 1.0f),
        touchCompat = sp.getBoolean("touchCompat", false),
        acceleration = sp.getBoolean("acceleration", true),
    ).let {
        when (method) {
            "dbc" -> it.copy(dbcMinCps = it.minCps, dbcMaxCps = it.maxCps)
            "dc" -> it
            else -> it.copy(defaultMinCps = it.minCps, defaultMaxCps = it.maxCps)
        }
    }
}

private fun saveParams(context: Context, params: ClickerParams) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
        putBoolean("enabled", params.enabled)
        putInt("toggleKey", params.toggleKey)
        putInt("overlayKey", params.overlayKey)
        putInt("calibKey", params.calibKey)
        putString("method", params.method)
        putInt("minCps", params.minCps)
        putInt("maxCps", params.maxCps)
        putInt("defaultMinCps", params.defaultMinCps)
        putInt("defaultMaxCps", params.defaultMaxCps)
        putInt("dbcMinCps", params.dbcMinCps)
        putInt("dbcMaxCps", params.dbcMaxCps)
        putInt("multiMin", params.multiMin)
        putInt("multiMax", params.multiMax)
        putInt("doublePct", params.doublePct)
        putInt("triplePct", params.triplePct)
        putInt("quadPct", params.quadPct)
        putInt("gapMin", params.gapMin)
        putInt("gapMax", params.gapMax)
        putInt("dcCps", params.dcCps)
        putInt("dcDurMinMs", params.dcDurMinMs)
        putInt("dcDurMaxMs", params.dcDurMaxMs)
        putBoolean("followCursor", params.followCursor)
        putInt("fixedX", params.fixedX)
        putInt("fixedY", params.fixedY)
        putFloat("sensitivity", params.sensitivity)
        putBoolean("touchCompat", params.touchCompat)
        putBoolean("acceleration", params.acceleration)
        apply()
    }
}

private fun loadLang(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("lang", "zh") ?: "zh"

/** evdev 键码显示名 */
internal fun keyName(code: Int): String {
    val map = mapOf(
        1 to "Esc", 2 to "1", 3 to "2", 4 to "3", 5 to "4", 6 to "5", 7 to "6",
        8 to "7", 9 to "8", 10 to "9", 11 to "0", 12 to "-", 13 to "=", 14 to "Backspace",
        15 to "Tab", 16 to "Q", 17 to "W", 18 to "E", 19 to "R", 20 to "T", 21 to "Y",
        22 to "U", 23 to "I", 24 to "O", 25 to "P", 28 to "Enter", 29 to "Ctrl",
        30 to "A", 31 to "S", 32 to "D", 33 to "F", 34 to "G", 35 to "H", 36 to "J",
        37 to "K", 38 to "L", 39 to ";", 40 to "'", 41 to "`", 42 to "Shift", 43 to "\\",
        44 to "Z", 45 to "X", 46 to "C", 47 to "V", 48 to "B", 49 to "N", 50 to "M",
        51 to ",", 52 to ".", 53 to "/", 54 to "RightShift", 55 to "*", 56 to "Alt",
        57 to "Space", 58 to "CapsLock", 59 to "F1", 60 to "F2", 61 to "F3", 62 to "F4",
        63 to "F5", 64 to "F6", 65 to "F7", 66 to "F8", 67 to "F9", 68 to "F10",
        87 to "F11", 88 to "F12", 97 to "RightCtrl", 100 to "RightAlt",
        272 to "Mouse Left", 273 to "Mouse Right", 274 to "Mouse Middle",
        275 to "Mouse Side", 276 to "Mouse Extra",
    )
    return map[code] ?: "Key $code"
}

@Composable
fun ClickerScreen(bridge: ClickerBridge) {
    val context = LocalContext.current
    var lang by remember { mutableStateOf(loadLang(context)) }

    CompositionLocalProvider(LocalLang provides lang) {
        ScreenContent(bridge, lang) { newLang ->
            lang = newLang
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString("lang", newLang).apply()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenContent(bridge: ClickerBridge, lang: String, onLangChange: (String) -> Unit) {
    val context = LocalContext.current
    val state by bridge.state.collectAsState()
    val status by bridge.status.collectAsState()

    var params by remember { mutableStateOf(loadParams(context)) }
    var showSettings by remember { mutableStateOf(false) }
    val settingsSheet = rememberMochiSheetState()
    LaunchedEffect(showSettings) {
        if (showSettings) settingsSheet.show() else settingsSheet.hide()
    }

    var showOverlaySettings by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }

    // 三张页面（设置面板 / 悬浮窗设置 / 配色）互斥，避免叠在一起
    fun openSettings() {
        showOverlaySettings = false
        showPalette = false
        showSettings = true
    }

    fun openOverlaySettings() {
        showSettings = false
        showPalette = false
        showOverlaySettings = true
    }

    fun openPalette() {
        showSettings = false
        showOverlaySettings = false
        showPalette = true
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var testMode by remember { mutableStateOf(false) }
    var showBind by remember { mutableStateOf(false) }
    var overlayWarn by remember { mutableStateOf(false) }

    var bindOverlayKey by remember { mutableStateOf(false) }
    var bindCalibKey by remember { mutableStateOf(false) }

    // 整页打开时，系统返回键先关页面，而不是退出 App
    BackHandler(enabled = !landscape && (showOverlaySettings || showPalette)) {
        if (showPalette) showPalette = false else showOverlaySettings = false
    }
    val uiScope = rememberCoroutineScope()

    /**
     * 面板是独立窗口（InWindowOverlay），要等它真正收起再弹绑定对话框，
     * 否则对话框会被面板挡住、按键也收不到。
     */
    fun bindAfterPanelClosed(block: () -> Unit) {
        uiScope.launch {
            delay(400)
            block()
        }
    }
    var ghostMode by remember { mutableStateOf(GhostMode.enabled(context)) }
    var overlayCfg by remember { mutableStateOf(OverlayHud.config(context)) }
    var trailCfg by remember { mutableStateOf(TrailOverlay.config(context)) }

    val connected = state == BridgeState.CONNECTED

    val notifyGranted = SystemAccess.hasNotificationPermission(context)
    val batteryFree = SystemAccess.isIgnoringBatteryOptimizations(context)

    fun update(new: ClickerParams) {
        val synced = when (new.method) {
            "dbc" -> new.copy(dbcMinCps = new.minCps, dbcMaxCps = new.maxCps)
            "dc" -> new
            else -> new.copy(defaultMinCps = new.minCps, defaultMaxCps = new.maxCps)
        }
        params = synced
        saveParams(context, synced)
        if (connected) bridge.applyParams(synced)
    }

    fun setEnabled(value: Boolean) {
        val next = params.copy(enabled = value)
        params = next
        saveParams(context, next)
        if (!connected) return
        if (value) bridge.startTrigger(next) else bridge.stopTrigger()
    }

    fun commitOverlay(next: OverlayHud.Config) {
        val wasOn = overlayCfg.on
        overlayCfg = next
        OverlayHud.saveConfig(context, next)
        if (next.on != wasOn) {
            val action = if (next.on) {
                ClickerForegroundService.ACTION_OVERLAY_ON
            } else {
                ClickerForegroundService.ACTION_OVERLAY_OFF
            }
            ClickerForegroundService.overlay(context, action)
        } else if (next.on) {
            // 大小 / 圆角 / 背景等改动直接在同一进程里重绘，省掉一次服务往返
            if (OverlayHud.isShowing()) {
                OverlayHud.apply(context)
            } else {
                ClickerForegroundService.overlay(context, ClickerForegroundService.ACTION_OVERLAY_ON)
            }
        }
    }

    fun setOverlay(on: Boolean) {
        if (!on) {
            commitOverlay(overlayCfg.copy(on = false))
            return
        }
        var ok = SystemAccess.canDrawOverlays(context)
        if (!ok) ok = SystemAccess.grantOverlayByShizuku(context)
        if (!ok) SystemAccess.openOverlaySettings(context)
        if (ok) commitOverlay(overlayCfg.copy(on = true)) else overlayWarn = true
    }

    fun commitTrail(next: TrailOverlay.Config) {
        val wasOn = trailCfg.on
        trailCfg = next
        TrailOverlay.saveConfig(context, next)
        if (next.on != wasOn) {
            val action = if (next.on) {
                ClickerForegroundService.ACTION_TRAIL_ON
            } else {
                ClickerForegroundService.ACTION_TRAIL_OFF
            }
            ClickerForegroundService.overlay(context, action)
        } else if (next.on) {
            // 轨迹画在悬浮窗里：窗口在就直接重绘，不在就交给服务拉起来
            if (OverlayHud.isShowing()) {
                OverlayHud.apply(context)
            } else if (overlayCfg.on) {
                ClickerForegroundService.overlay(context, ClickerForegroundService.ACTION_OVERLAY_ON)
            }
        }
    }

    /** 幽灵模式切换：停掉两边的服务，再按需在目标进程重新拉起悬浮层 */
    fun setGhostMode(on: Boolean) {
        if (on == ghostMode) return
        ghostMode = on
        GhostMode.setEnabled(context, on)
        OverlayDaemon.stopAll(context)
        if (overlayCfg.on) {
            OverlayDaemon.overlay(context, ClickerForegroundService.ACTION_OVERLAY_ON)
        } else if (params.enabled) {
            OverlayDaemon.start(context)
        }
    }

    fun setTrail(on: Boolean) {
        if (!on) {
            commitTrail(trailCfg.copy(on = false))
            return
        }
        var ok = SystemAccess.canDrawOverlays(context)
        if (!ok) ok = SystemAccess.grantOverlayByShizuku(context)
        if (!ok) SystemAccess.openOverlaySettings(context)
        if (!ok) {
            overlayWarn = true
            return
        }
        commitTrail(trailCfg.copy(on = true))
        // 轨迹画在悬浮窗里，悬浮窗没开就先打开
        if (!overlayCfg.on) commitOverlay(overlayCfg.copy(on = true))
    }

    LaunchedEffect(state) {
        if (state == BridgeState.CONNECTED) {
            // 绑定键可能在上一次界面退出后切换过总开关：以服务端状态为准，
            // 否则这里一上来就用旧快照覆盖，按过的键看起来像"失效"
            val live = bridge.currentEnabled()
            val next = if (live != null && live != params.enabled) {
                params.copy(enabled = live).also {
                    params = it
                    saveParams(context, it)
                }
            } else {
                params
            }
            bridge.applyParams(next)
            if (next.enabled) bridge.startTrigger(next)
        }
    }

    var lastOverlaySeq by remember { mutableStateOf(status.overlaySeq) }
    LaunchedEffect(status.overlaySeq) {
        if (status.overlaySeq != lastOverlaySeq) {
            lastOverlaySeq = status.overlaySeq
            overlayCfg = OverlayHud.config(context)
        }
    }

    // 只有绑定键位触发（seq 变化）时才把开关状态回灌到界面，避免本地点击被旧快照覆盖
    var lastSeq by remember { mutableStateOf(status.switchSeq) }
    LaunchedEffect(status.switchSeq) {
        // 服务端还没收到过配置时 switchSeq 会归零，用它的默认状态回灌会把用户的关闭状态带偏
        if (status.configured && status.switchSeq != lastSeq) {
            lastSeq = status.switchSeq
            val next = params.copy(enabled = status.enabled)
            params = next
            saveParams(context, next)
            // 键位改了服务端开关后，顺手把整份配置同步回去，避免下次改任意设置时又用旧值覆盖
            if (connected) bridge.applyParams(next)
            if (status.enabled) ClickerForegroundService.start(context)
        }
    }

    if (testMode) {
        CpsTestScreen(
            bridge = bridge,
            connected = connected,
            onExit = {
                bridge.stopManual()
                testMode = false
            },
        )
        return
    }

    val haptic = remember { Haptics(context) }

    // 玻璃菜单要取样它底下的页面：这里把整屏录成背景来源，再通过 LocalMochiBackdrop 交给浮层
    val backdrop = rememberMochiBackdrop(MaterialTheme.colorScheme.surface)
    CompositionLocalProvider(LocalMochiBackdrop provides backdrop) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize().mochiBackdropSource(backdrop),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 18.dp)
                    .padding(top = 6.dp, bottom = 18.dp)
            ) {
                TopRow(
                    showMenu = !landscape,
                    active = showOverlaySettings,
                    ghostOn = ghostMode,
                    onClickMenu = ::openSettings,
                    onClickGhost = { setGhostMode(!ghostMode) },
                    onClickOverlay = {
                        if (showOverlaySettings) showOverlaySettings = false else openOverlaySettings()
                    },
                    onClickPalette = { if (showPalette) showPalette = false else openPalette() },
                )
                Spacer(Modifier.height(10.dp))

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                  GroupCard(s("启用", "Enable")) {
                    MasterSwitch(
                        on = connected && params.enabled,
                        enabled = connected,
                        onToggle = {
                            haptic.tick()
                            setEnabled(!params.enabled)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    BindRow(
                        key = params.toggleKey,
                        onClick = { showSettings = false; showOverlaySettings = false; showBind = true },
                    )
                  }
                    Spacer(Modifier.height(12.dp))
                  GroupCard(s("点击方式", "Click method")) {
                    MethodSelector(params.method) { next ->
                        if (next != params.method) {
                            when (next) {
                                "dbc" -> update(
                                    params.copy(
                                        method = next,
                                        minCps = params.dbcMinCps.coerceIn(1, 16),
                                        maxCps = params.dbcMaxCps.coerceIn(1, 16),
                                    )
                                )
                                "dc" -> update(params.copy(method = next))
                                else -> update(
                                    params.copy(
                                        method = next,
                                        minCps = params.defaultMinCps.coerceIn(1, 25),
                                        maxCps = params.defaultMaxCps.coerceIn(1, 25),
                                    )
                                )
                            }
                        }
                    }

                  }
                    Spacer(Modifier.height(12.dp))
                  GroupCard(s("参数", "Parameters")) {
                    when (params.method) {
                        "dbc" -> DbcSection(params, ::update)
                        "dc" -> DcSection(params, ::update)
                        else -> DefaultSection(params, ::update)
                    }
                  }
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.height(10.dp))
                MochiButton(
                    onClick = {
                        haptic.tick()
                        testMode = true
                    },
                    style = MochiButtonStyle.ProminentGlass,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("TEST", style = MaterialTheme.typography.titleMedium)
                }
            }


            if (landscape) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    if (showOverlaySettings) {
                        OverlaySettingsContent(
                            overlay = overlayCfg,
                            overlayKey = params.overlayKey,
                            onOverlayToggle = ::setOverlay,
                            onOverlayChange = ::commitOverlay,
                            onBindOverlayKey = { showOverlaySettings = false; bindAfterPanelClosed { bindOverlayKey = true } },
                            trail = trailCfg,
                            calibKey = params.calibKey,
                            onTrailToggle = ::setTrail,
                            onTrailChange = ::commitTrail,
                            onBindCalibKey = { showOverlaySettings = false; bindAfterPanelClosed { bindCalibKey = true } },
                            onCalibrate = { bridge.resetCursor(); OverlayDaemon.recenter(context) },
                            onClose = { showOverlaySettings = false },
                        )
                    } else {
                        SettingsContent(
                            params = params,
                            onChange = ::update,
                            status = status,
                            state = state,
                            bridge = bridge,
                            notifyGranted = notifyGranted,
                            batteryFree = batteryFree,
                            lang = lang,
                            onLangChange = onLangChange,
                            onClose = null,
                        )
                    }
                }
            }
        }

        // 设置面板：mochi 的半屏/全屏底部面板，自带玻璃模糊与高光
        if (!landscape) {
            MochiSheet(
                state = settingsSheet,
                onDismiss = { showSettings = false },
            ) {
                SettingsContent(
                    params = params,
                    onChange = ::update,
                    status = status,
                    state = state,
                    bridge = bridge,
                    notifyGranted = notifyGranted,
                    batteryFree = batteryFree,
                    lang = lang,
                    onLangChange = onLangChange,
                    onClose = { settingsSheet.hide() },
                )
            }

        }

        // 竖屏：悬浮窗设置与配色都是整页，从右侧侧推进入（mochi 的侧推手感）
        if (!landscape) {
            SidePage(visible = showOverlaySettings) {
                OverlaySettingsContent(
                    overlay = overlayCfg,
                    overlayKey = params.overlayKey,
                    onOverlayToggle = ::setOverlay,
                    onOverlayChange = ::commitOverlay,
                    onBindOverlayKey = { showOverlaySettings = false; bindOverlayKey = true },
                    trail = trailCfg,
                    calibKey = params.calibKey,
                    onTrailToggle = ::setTrail,
                    onTrailChange = ::commitTrail,
                    onBindCalibKey = { showOverlaySettings = false; bindCalibKey = true },
                    onCalibrate = { bridge.resetCursor(); OverlayDaemon.recenter(context) },
                    onClose = { showOverlaySettings = false },
                )
            }
            SidePage(visible = showPalette) {
                PalettePage(onClose = { showPalette = false })
            }
        }

        if (showBind) {
            KeyBindDialog(
                captured = status.capturedKey,
                current = params.toggleKey,
                connected = connected,
                keyFilterOn = KeyFilterService.isEnabled(context),
                onOpen = { bridge.beginKeyCapture() },
                onEndCapture = { bridge.endKeyCapture() },
                onClose = {
                    bridge.endKeyCapture()
                    showBind = false
                },
                onConfirm = { code ->
                    bridge.endKeyCapture()
                    update(params.copy(toggleKey = code))
                    showBind = false
                },
                onClear = {
                    bridge.endKeyCapture()
                    update(params.copy(toggleKey = -1))
                    showBind = false
                },
            )
        }

        if (bindOverlayKey) {
            KeyBindDialog(
                captured = status.capturedKey,
                current = params.overlayKey,
                title = s("悬浮窗开关键", "Overlay toggle key"),
                hint = s(
                    "键盘或鼠标侧键均可，按一次即可开关悬浮窗",
                    "Keyboard or mouse side button; one press toggles the overlay",
                ),
                keyFilterOn = KeyFilterService.isEnabled(context),
                onOpen = { bridge.beginKeyCapture() },
                onEndCapture = { bridge.endKeyCapture() },
                onClose = {
                    bridge.endKeyCapture()
                    bindOverlayKey = false
                    showOverlaySettings = true
                },
                onConfirm = { code ->
                    bridge.endKeyCapture()
                    update(params.copy(overlayKey = code))
                    bindOverlayKey = false
                    showOverlaySettings = true
                },
                onClear = {
                    bridge.endKeyCapture()
                    update(params.copy(overlayKey = -1))
                    bindOverlayKey = false
                    showOverlaySettings = true
                },
            )
        }

        if (bindCalibKey) {
            KeyBindDialog(
                captured = status.capturedKey,
                current = params.calibKey,
                title = s("校准键位", "Calibrate key"),
                hint = s(
                    "键盘或鼠标侧键均可，按一次即可让光标回中",
                    "Keyboard or mouse side button; one press recenters the cursor",
                ),
                keyFilterOn = KeyFilterService.isEnabled(context),
                onOpen = { bridge.beginKeyCapture() },
                onEndCapture = { bridge.endKeyCapture() },
                onClose = {
                    bridge.endKeyCapture()
                    bindCalibKey = false
                    showOverlaySettings = true
                },
                onConfirm = { code ->
                    bridge.endKeyCapture()
                    update(params.copy(calibKey = code))
                    bindCalibKey = false
                    showOverlaySettings = true
                },
                onClear = {
                    bridge.endKeyCapture()
                    update(params.copy(calibKey = -1))
                    bindCalibKey = false
                    showOverlaySettings = true
                },
            )
        }

        if (overlayWarn) {
            MochiDialog(
                visible = true,
                onDismissRequest = { overlayWarn = false },
                title = s("需要悬浮窗权限", "Overlay permission required"),
                message = s(
                    "已在系统里打开授权页。允许“显示在其他应用上层”后，回来重新打开开关即可。",
                    "The system permission page is open. Allow \"display over other apps\", then enable the switch again.",
                ),
                actions = persistentListOf(MochiDialogAction(s("好", "OK")) { overlayWarn = false }),
            )
        }
    }
    }
}

// ------------------------------------------------------------------ 顶栏

@Composable
private fun TopRow(
    showMenu: Boolean,
    active: Boolean,
    ghostOn: Boolean,
    onClickMenu: () -> Unit,
    onClickGhost: () -> Unit,
    onClickOverlay: () -> Unit,
    onClickPalette: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (showMenu) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClickMenu),
                contentAlignment = Alignment.Center,
            ) {
                val barColor = MaterialTheme.colorScheme.onSurface
                Canvas(modifier = Modifier.size(19.dp)) {
                    val barH = 2.dp.toPx()
                    repeat(3) { i ->
                        val y = (size.height / 4) * (i + 1)
                        drawRect(
                            color = barColor,
                            topLeft = Offset(0f, y - barH / 2),
                            size = androidx.compose.ui.geometry.Size(size.width, barH),
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            "LazyClicker",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        GhostEntryButton(on = ghostOn, onClick = onClickGhost)
        Spacer(Modifier.width(8.dp))
        PaletteEntryButton(onClick = onClickPalette)
        Spacer(Modifier.width(8.dp))
        OverlayEntryButton(active = active, onClick = onClickOverlay)
    }
}

/** 右上角配色入口：调色盘图标（Material Design Icons: palette） */
@Composable
private fun PaletteEntryButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(cs.onSurface.copy(alpha = 0.05f))
            .clickable(onClickLabel = s("自定义配色", "Custom colors"), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_palette),
            contentDescription = null,
            tint = cs.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** 幽灵模式开关：开启后悬浮窗与连点跑在独立前台进程，清后台也继续 */
@Composable
private fun GhostEntryButton(on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint = if (on) cs.primary else cs.onSurface
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (on) cs.primary.copy(alpha = 0.12f) else cs.onSurface.copy(alpha = 0.05f))
            .clickable(
                onClickLabel = s("幽灵模式", "Ghost mode"),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ghost),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** 右上角悬浮窗设置入口：叠放的“窗口”图标 */
@Composable
private fun OverlayEntryButton(active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint = if (active) cs.primary else cs.onSurface
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) cs.primary.copy(alpha = 0.12f) else cs.onSurface.copy(alpha = 0.05f))
            .clickable(
                onClickLabel = s("悬浮窗设置", "Floating window settings"),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(19.dp)) {
            val stroke = 1.4.dp.toPx()
            // 屏幕外框
            drawRoundRect(
                color = tint,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height),
                cornerRadius = CornerRadius(3.5.dp.toPx()),
                style = Stroke(width = stroke),
            )
            // 右下角叠着的悬浮窗
            val innerW = size.width * 0.42f
            val innerH = size.height * 0.36f
            val inset = 2.6.dp.toPx()
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width - innerW - inset, size.height - innerH - inset),
                size = androidx.compose.ui.geometry.Size(innerW, innerH),
                cornerRadius = CornerRadius(1.6.dp.toPx()),
            )
        }
    }
}

// ------------------------------------------------------------------ 总开关

@Composable
private fun MasterSwitch(on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    var warn by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            s("启用", "Enable"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        MochiSwitch(
            checked = on,
            onCheckedChange = { if (enabled) onToggle() else warn = true },
        )
    }
    if (warn) {
        MochiDialog(
            visible = true,
            onDismissRequest = { warn = false },
            title = s("未启用Shizuku", "Shizuku is not running"),
            message = s(
                "请启动Shizuku并授予LazyClicker权限",
                "Start Shizuku and grant LazyClicker permission",
            ),
            actions = persistentListOf(MochiDialogAction(s("好", "OK")) { warn = false }),
        )
    }
}

@Composable
private fun BindRow(key: Int, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        MochiButton(onClick = onClick, style = MochiButtonStyle.Filled) {
            Text(
                if (key > 0) "${s("绑定键位", "Toggle key")}: ${keyName(key)}"
                else s("绑定键位", "Toggle key"),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// ------------------------------------------------------------------ 模式与参数

/** 选择列表（iOS 设置式单选行），替代除主界面之外的分段选择器 */
/**
 * 选择列表：整行就是一个按钮，靠左摆放，按钮上固定显示「标题：当前选项」，
 * 点击后从按钮下沿弹出 mochi 的玻璃菜单（liquidCover = false，等价 iOS Picker .menu）。
 * 面板宽度固定 208dp，与 mochi 的选择列表一致，左对齐展开。
 */
@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = remember(options) { options.map { MochiItem(it) }.toPersistentList() }
    val current = options.getOrNull(selectedIndex) ?: options.firstOrNull().orEmpty()
    val cs = MaterialTheme.colorScheme
    val chevronTint = cs.onSurfaceVariant.copy(alpha = 0.75f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        MochiDropdown(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            items = items,
            onItemClick = { onSelect(it) },
            selectedIndex = selectedIndex,
            colors = choiceColors(),
            behavior = MochiDropdownDefaults.behavior(
                openMode = MochiOpenMode.Detach,
                trigger = MochiTrigger.Press,
                align = MochiAlign.Start,
                liquidCover = false,
            ),
            menuWidth = 208.dp,
            buttonMinHeight = 38.dp,
            buttonCornerRadius = 19.dp,
            buttonPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        ) {
            Text(
                text = "$title：$current",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = chevronTint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 选择行的配色：按钮底色与卡片容器同色，展开时看起来是这一行被抬起变成玻璃面板 */
@Composable
private fun choiceColors(): MochiDropdownColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        MochiDropdownColors(
            buttonColor = scheme.surfaceVariant.copy(alpha = 0.42f).compositeOver(scheme.surfaceContainer),
            buttonContentColor = scheme.onSurface,
            panelColor = scheme.surfaceContainerHigh,
            panelContentColor = scheme.onSurface,
            selectedColor = scheme.primary.copy(alpha = 0.14f),
            selectedContentColor = scheme.onSurface,
            highlightColor = scheme.onSurface.copy(alpha = 0.08f),
            destructiveColor = scheme.error,
            shadowColor = Color.Black.copy(alpha = 0.18f),
            glowColor = scheme.primary.copy(alpha = 0.10f),
        )
    }
}

@Composable
private fun MethodSelector(method: String, onSelect: (String) -> Unit) {
    val idx = when (method) { "dbc" -> 1; "dc" -> 2; else -> 0 }
    MochiSegmentedControl(
        selectedIndex = idx,
        onSelect = { i -> onSelect(when (i) { 1 -> "dbc"; 2 -> "dc"; else -> "default" }) },
        items = persistentListOf(s("默认", "Default"), "DBC", "DC"),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    )
}

@Composable
private fun DefaultSection(params: ClickerParams, onChange: (ClickerParams) -> Unit) {
    CpsHeader(params.minCps, params.maxCps)
    RangeSlider(
        value = params.minCps.toFloat()..params.maxCps.toFloat(),
        onValueChange = { range ->
            val lo = range.start.roundToInt().coerceIn(1, 25)
            val hi = range.endInclusive.roundToInt().coerceIn(1, 25)
            onChange(params.copy(minCps = lo, maxCps = hi))
        },
        valueRange = 1f..25f,
        steps = 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DbcSection(params: ClickerParams, onChange: (ClickerParams) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    CpsHeader(params.minCps, params.maxCps)
    RangeSlider(
        value = params.minCps.toFloat()..params.maxCps.toFloat(),
        onValueChange = { range ->
            val lo = range.start.roundToInt().coerceIn(1, 16)
            val hi = range.endInclusive.roundToInt().coerceIn(1, 16)
            onChange(params.copy(minCps = lo, maxCps = hi))
        },
        valueRange = 1f..16f,
        steps = 0,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(10.dp))
    Text(
        "${s("多倍点概率", "Multi-click chance")}  ${params.multiMin}% – ${params.multiMax}%",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    RangeSlider(
        value = params.multiMin.toFloat()..params.multiMax.toFloat(),
        onValueChange = { range ->
            val lo = range.start.roundToInt().coerceIn(0, 100)
            val hi = range.endInclusive.roundToInt().coerceIn(0, 100)
            onChange(params.copy(multiMin = lo, multiMax = hi))
        },
        valueRange = 0f..100f,
        steps = 0,
        modifier = Modifier.fillMaxWidth(),
    )

    ExpandableHeader(
        title = s("进阶选项", "Advanced options"),
        expanded = expanded,
        onToggle = { expanded = !expanded },
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(160)),
        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120)),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            PercentSlider(s("二倍点概率", "Double click chance"), params.doublePct) {
                onChange(params.copy(doublePct = it))
            }
            PercentSlider(s("三倍点概率", "Triple click chance"), params.triplePct) {
                onChange(params.copy(triplePct = it))
            }
            PercentSlider(s("四倍点概率", "Quad click chance"), params.quadPct) {
                onChange(params.copy(quadPct = it))
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        "${s("多倍点间隔", "Multi-click interval")}  ${params.gapMin} – ${params.gapMax} ms",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    RangeSlider(
        value = params.gapMin.toFloat()..params.gapMax.toFloat(),
        onValueChange = { range ->
            val lo = range.start.roundToInt().coerceIn(0, 10)
            val hi = range.endInclusive.roundToInt().coerceIn(0, 10)
            onChange(params.copy(gapMin = lo, gapMax = hi))
        },
        valueRange = 0f..10f,
        steps = 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExpandableHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "chevron",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
    ) {
        Canvas(modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = rotation }) {
            val stroke = 2.dp.toPx()
            drawLine(
                color = Color(0xFF9AA0A6),
                start = Offset(size.width * 0.15f, size.height * 0.35f),
                end = Offset(size.width * 0.5f, size.height * 0.7f),
                strokeWidth = stroke,
            )
            drawLine(
                color = Color(0xFF9AA0A6),
                start = Offset(size.width * 0.5f, size.height * 0.7f),
                end = Offset(size.width * 0.85f, size.height * 0.35f),
                strokeWidth = stroke,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PercentSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Text(
        "$label  $value%",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    MochiSlider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt().coerceIn(0, 100)) },
        valueRange = 0f..100f,
        steps = 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DcSection(params: ClickerParams, onChange: (ClickerParams) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${params.dcCps}",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "CPS",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
    MochiSlider(
        value = params.dcCps.toFloat(),
        onValueChange = { onChange(params.copy(dcCps = it.roundToInt().coerceIn(15, 150))) },
        valueRange = 15f..150f,
        steps = 0,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))
    Text(
        "${s("持续时间", "Duration")}  ${"%.1f".format(params.dcDurMinMs / 1000f)} – " +
            "${"%.1f".format(params.dcDurMaxMs / 1000f)} s",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    RangeSlider(
        value = (params.dcDurMinMs / 1000f)..(params.dcDurMaxMs / 1000f),
        onValueChange = { range ->
            val lo = (range.start.coerceIn(0.1f, 5f) * 1000f).roundToInt()
            val hi = (range.endInclusive.coerceIn(0.1f, 5f) * 1000f).roundToInt()
            onChange(params.copy(dcDurMinMs = lo, dcDurMaxMs = hi))
        },
        valueRange = 0.1f..5f,
        steps = 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CpsHeader(min: Int, max: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "$min",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "—",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp, start = 6.dp, end = 6.dp),
        )
        Text(
            "$max",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "CPS",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

// ------------------------------------------------------------------ 绑定键位

@Composable
private fun KeyBindDialog(
    captured: Int,
    current: Int,
    title: String? = null,
    hint: String? = null,
    connected: Boolean = true,
    keyFilterOn: Boolean = true,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onEndCapture: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    // 连接成功后再开始捕获；桥接断开时按下按键不会被服务端记录。
    // 每 15s 续一次租约：服务端 45s 没收到续租才结束捕获，所以对话框开着多久都能正常绑键。
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        while (true) {
            onOpen()
            delay(15_000)
        }
    }
    // 面板意外消失（旋转、返回、进程重建）时也要结束捕获，否则服务端一直吞掉按键
    DisposableEffect(Unit) { onDispose { onEndCapture() } }
    val cs = MaterialTheme.colorScheme
    val panelShape = RoundedCornerShape(28.dp)
    val swallow = remember { MutableInteractionSource() }
    val scrimSource = remember { MutableInteractionSource() }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val rise by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(220),
    )

    val keyText = when {
        captured > 0 -> keyName(captured)
        current > 0 -> keyName(current)
        else -> s("按下任意键…", "Press any key…")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.scrim.copy(alpha = 0.30f * scrimAlpha))
            .clickable(interactionSource = scrimSource, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .graphicsLayer {
                    val sc = 0.92f + 0.08f * rise
                    scaleX = sc
                    scaleY = sc
                    translationY = (1f - rise) * 18.dp.toPx()
                    alpha = rise
                }
                .shadow(20.dp, panelShape)
                .clip(panelShape)
                .background(cs.surfaceContainerHigh)
                .border(1.dp, cs.primary.copy(alpha = 0.18f), panelShape)
                .clickable(interactionSource = swallow, indication = null, onClick = {})
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            Text(
                title ?: s("绑定键位", "Bind key"),
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = keyText,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "bindKey",
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp),
                    color = cs.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    captured > 0 -> s("已捕获，按确定写入", "Captured — press OK to save")
                    current > 0 -> s("当前已绑定", "Currently bound")
                    else -> s("等待按键…", "Waiting for a key…")
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                hint ?: s("键盘或鼠标侧键均可，绑定后按一次即可开关", "Keyboard or mouse side button; one press toggles"),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            )
            if (!keyFilterOn) {
                Spacer(Modifier.height(6.dp))
                Text(
                    s(
                        "键盘按键需要先在「设置 → 后台 → 按键拦截」里开启无障碍拦截，否则只能绑鼠标侧键。",
                        "Turn on \"Settings → Background → Key filter\" first, otherwise only mouse side buttons can be bound.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.primary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MochiButton(
                    onClick = onClear,
                    style = MochiButtonStyle.Glass,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(s("清除", "Clear"), style = MaterialTheme.typography.labelLarge)
                }
                MochiButton(
                    onClick = onClose,
                    style = MochiButtonStyle.Glass,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(s("取消", "Cancel"), style = MaterialTheme.typography.labelLarge)
                }
                MochiButton(
                    onClick = { if (captured > 0) onConfirm(captured) },
                    enabled = captured > 0,
                    style = MochiButtonStyle.ProminentGlass,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(s("确定", "OK"), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 设置

@Composable
private fun SettingsContent(
    params: ClickerParams,
    onChange: (ClickerParams) -> Unit,
    status: ServiceStatus,
    state: BridgeState,
    bridge: ClickerBridge,
    notifyGranted: Boolean,
    batteryFree: Boolean,
    lang: String,
    onLangChange: (String) -> Unit,
    onClose: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                s("设置", "Settings"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))

        AdvancedGroup(params, onChange)
        Spacer(Modifier.height(12.dp))
        BackgroundGroup(
            bridge = bridge,
            state = state,
            notifyGranted = notifyGranted,
            batteryFree = batteryFree,
        )
        Spacer(Modifier.height(12.dp))
        DiagnosticsGroup(status)
        Spacer(Modifier.height(12.dp))
        AboutGroup()
        Spacer(Modifier.height(16.dp))

        GroupCard(s("语言", "Language"), Icons.Default.Person) {
            ChoiceRow(
                title = s("语言", "Language"),
                options = listOf("中文", "English"),
                selectedIndex = if (lang == "zh") 0 else 1,
                onSelect = { onLangChange(if (it == 0) "zh" else "en") },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 竖屏整页容器：从右侧侧推进入，返回键/返回箭头关闭 */
@Composable
private fun SidePage(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        ) + fadeIn(tween(durationMillis = 200)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 260, easing = FastOutLinearInEasing),
        ) + fadeOut(tween(durationMillis = 180)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val swallow = remember { MutableInteractionSource() }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    // 页面在上面时不要再点到下面的主界面
                    .clickable(interactionSource = swallow, indication = null, onClick = {}),
            ) {
                content()
            }
        }
    }
}

/** 配色页：种子色（色相/饱和度/明度）+ 配色风格 + 对比度，等价 MaterialKolor 的三组输入 */
@Composable
private fun PalettePage(onClose: () -> Unit) {
    val context = LocalContext.current
    val hsv0 = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(AppTheme.seed.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var saturation by remember { mutableFloatStateOf(hsv0[1]) }
    var brightness by remember { mutableFloatStateOf(hsv0[2].coerceAtLeast(0.35f)) }
    var style by remember { mutableStateOf(AppTheme.style) }
    var contrast by remember { mutableFloatStateOf(AppTheme.contrast) }

    val seedColor = remember(hue, saturation, brightness) {
        Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), brightness.coerceIn(0.15f, 1f))
    }

    // 拖动时只改内存，停手 180ms 再落到主题与磁盘
    LaunchedEffect(seedColor, style, contrast) {
        delay(180)
        AppTheme.set(seedColor, style, contrast)
        AppTheme.persist(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                s("配色", "Colors"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            MochiButton(onClick = { AppTheme.reset(context); onClose() }, style = MochiButtonStyle.Glass) {
                Text(s("恢复默认", "Reset"))
            }
        }
        Spacer(Modifier.height(4.dp))

        GroupCard(s("种子色", "Seed color")) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(seedColor),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    String.format("#%06X", 0xFFFFFF and seedColor.toArgb()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${s("色相", "Hue")}  ${hue.roundToInt()}°",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = hue,
                onValueChange = { hue = it },
                valueRange = 0f..360f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${s("饱和度", "Saturation")}  ${(saturation * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = saturation,
                onValueChange = { saturation = it },
                valueRange = 0f..1f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${s("明度", "Brightness")}  ${(brightness * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = brightness,
                onValueChange = { brightness = it },
                valueRange = 0.15f..1f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palettePresets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(preset)
                            .clickable {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(preset.toArgb(), hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                brightness = hsv[2].coerceAtLeast(0.35f)
                            },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        GroupCard(s("配色风格", "Palette style")) {
            ChoiceRow(
                title = s("风格", "Style"),
                options = paletteStyleLabels,
                selectedIndex = PaletteStyle.entries.indexOf(style).coerceAtLeast(0),
                onSelect = { index ->
                    style = PaletteStyle.entries.getOrElse(index) { PaletteStyle.TonalSpot }
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${s("对比度", "Contrast")}  ${"%.2f".format(contrast)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = contrast,
                onValueChange = { contrast = it },
                valueRange = -1f..1f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))

        GroupCard(s("当前配色", "Current colors")) {
            val cs = MaterialTheme.colorScheme
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    s("主色", "Primary") to cs.primary,
                    s("次色", "Secondary") to cs.secondary,
                    "Tertiary" to cs.tertiary,
                    s("容器", "Container") to cs.primaryContainer,
                ).forEach { (label, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(color),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val paletteStyleLabels: List<String> = listOf(
    "柔和 TonalSpot", "表现力 Expressive", "鲜艳 Vibrant", "内容 Content", "忠实 Fidelity", "中性 Neutral",
)

private val palettePresets: List<Color> = listOf(
    Color(0xFF00696B),
    Color(0xFF4E9F63),
    Color(0xFF4C6FF0),
    Color(0xFF8E5BF0),
    Color(0xFFE0703A),
    Color(0xFFD9455F),
    Color(0xFFB08A2E),
)

@Composable
private fun OverlaySettingsContent(
    overlay: OverlayHud.Config,
    overlayKey: Int,
    onOverlayToggle: (Boolean) -> Unit,
    onOverlayChange: (OverlayHud.Config) -> Unit,
    onBindOverlayKey: () -> Unit,
    trail: TrailOverlay.Config,
    calibKey: Int,
    onTrailToggle: (Boolean) -> Unit,
    onTrailChange: (TrailOverlay.Config) -> Unit,
    onBindCalibKey: () -> Unit,
    onCalibrate: () -> Unit,
    onClose: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                s("悬浮窗设置", "Floating window"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))

        OverlayDisplayGroup(
            cfg = overlay,
            overlayKey = overlayKey,
            onToggle = onOverlayToggle,
            onChange = onOverlayChange,
            onBindKey = onBindOverlayKey,
        )
        Spacer(Modifier.height(12.dp))
        OverlayCpsGroup(cfg = overlay, onChange = onOverlayChange)
        Spacer(Modifier.height(12.dp))
        OverlaySurfaceGroup(cfg = overlay, onChange = onOverlayChange)
        Spacer(Modifier.height(12.dp))
        GroupCard(s("光标轨迹", "Cursor track")) {
            TrailSection(
                cfg = trail,
                calibKey = calibKey,
                onToggle = onTrailToggle,
                onChange = onTrailChange,
                onBindCalibKey = onBindCalibKey,
                onCalibrate = onCalibrate,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OverlayDisplayGroup(
    cfg: OverlayHud.Config,
    overlayKey: Int,
    onToggle: (Boolean) -> Unit,
    onChange: (OverlayHud.Config) -> Unit,
    onBindKey: () -> Unit,
) {
    val radiusMax = (minOf(cfg.rectW, cfg.rectH) / 2).coerceAtLeast(1)
    GroupCard(s("显示与外形", "Display & shape")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                s("显示悬浮窗", "Show overlay"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            MochiSwitch(checked = cfg.on, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(2.dp))
        MochiButton(onClick = onBindKey, style = MochiButtonStyle.Glass) {
            Text(
                if (overlayKey > 0) {
                    "${s("绑定键位", "Bind key")}: ${keyName(overlayKey)}"
                } else {
                    s("绑定键位", "Bind key")
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(6.dp))
        ChoiceRow(
            title = s("形状", "Shape"),
            options = listOf(s("长方形", "Rectangle"), s("圆形", "Circle")),
            selectedIndex = if (cfg.shape == OverlayHud.SHAPE_CIRCLE) 1 else 0,
            onSelect = { index ->
                onChange(
                    cfg.copy(
                        shape = if (index == 1) OverlayHud.SHAPE_CIRCLE else OverlayHud.SHAPE_RECT,
                    ),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        if (cfg.shape == OverlayHud.SHAPE_CIRCLE) {
            Text(
                "${s("直径", "Diameter")}  ${cfg.circleD}dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.circleD.toFloat(),
                onValueChange = { onChange(cfg.copy(circleD = it.roundToInt())) },
                valueRange = 40f..480f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "${s("宽度", "Width")}  ${cfg.rectW}dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.rectW.toFloat(),
                onValueChange = { onChange(cfg.copy(rectW = it.roundToInt())) },
                valueRange = 40f..480f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${s("高度", "Height")}  ${cfg.rectH}dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.rectH.toFloat(),
                onValueChange = { onChange(cfg.copy(rectH = it.roundToInt())) },
                valueRange = 40f..480f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${s("圆角", "Corner radius")}  ${cfg.rectRadius}dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.rectRadius.coerceIn(0, radiusMax).toFloat(),
                onValueChange = { onChange(cfg.copy(rectRadius = it.roundToInt().coerceIn(0, radiusMax))) },
                valueRange = 0f..radiusMax.toFloat(),
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            "${s("不透明度", "Opacity")}  ${(cfg.opacity * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MochiSlider(
            value = cfg.opacity,
            onValueChange = { onChange(cfg.copy(opacity = it.coerceIn(0f, 1f))) },
            valueRange = 0f..1f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OverlayCpsGroup(
    cfg: OverlayHud.Config,
    onChange: (OverlayHud.Config) -> Unit,
) {
    GroupCard(s("CPS 显示", "CPS readout")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                s("显示 CPS", "Show CPS"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            MochiSwitch(
                checked = cfg.cpsOn,
                onCheckedChange = { onChange(cfg.copy(cpsOn = it)) },
            )
        }
        if (cfg.cpsOn) {
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                title = s("显示方式", "Format"),
                options = listOf(s("前缀", "Prefix"), s("后缀", "Suffix"), s("仅数字", "Number")),
                selectedIndex = when (cfg.cpsMode) {
                    OverlayHud.MODE_PREFIX -> 0
                    OverlayHud.MODE_SUFFIX -> 1
                    else -> 2
                },
                onSelect = { index ->
                    onChange(
                        cfg.copy(
                            cpsMode = when (index) {
                                0 -> OverlayHud.MODE_PREFIX
                                1 -> OverlayHud.MODE_SUFFIX
                                else -> OverlayHud.MODE_NUMBER
                            },
                        ),
                    )
                },
            )
            when (cfg.cpsMode) {
                OverlayHud.MODE_PREFIX -> {
                    Spacer(Modifier.height(6.dp))
                    MochiTextField(
                        value = cfg.prefix,
                        onValueChange = { onChange(cfg.copy(prefix = it)) },
                        placeholder = s("前缀，如 CPS ", "Prefix, e.g. CPS "),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OverlayHud.MODE_SUFFIX -> {
                    Spacer(Modifier.height(6.dp))
                    MochiTextField(
                        value = cfg.suffix,
                        onValueChange = { onChange(cfg.copy(suffix = it)) },
                        placeholder = s("后缀，如  CPS", "Suffix, e.g.  CPS"),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                "${s("水平位置", "Horizontal")}  ${(cfg.cpsX * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.cpsX,
                onValueChange = { onChange(cfg.copy(cpsX = it.coerceIn(0f, 1f))) },
                valueRange = 0f..1f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${s("垂直位置", "Vertical")}  ${(cfg.cpsY * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.cpsY,
                onValueChange = { onChange(cfg.copy(cpsY = it.coerceIn(0f, 1f))) },
                valueRange = 0f..1f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    s("文字颜色", "Text color"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                MochiColorWell(
                    color = Color(cfg.cpsColor),
                    onColorChange = { onChange(cfg.copy(cpsColor = it.toArgb())) },
                    title = s("文字颜色", "Text color"),
                    size = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun OverlaySurfaceGroup(
    cfg: OverlayHud.Config,
    onChange: (OverlayHud.Config) -> Unit,
) {
    GroupCard(s("背景与边框", "Background & border")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                s("黑白棋盘", "Checker"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            MochiSwitch(
                checked = cfg.checkerOn,
                onCheckedChange = { onChange(cfg.copy(checkerOn = it)) },
            )
        }
        if (cfg.checkerOn) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    s("格子颜色", "Checker colors"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                MochiColorWell(
                    color = Color(cfg.checkColorA),
                    onColorChange = { onChange(cfg.copy(checkColorA = it.toArgb())) },
                    title = s("格子颜色 1", "Checker color 1"),
                    size = 40.dp,
                )
                Spacer(Modifier.width(8.dp))
                MochiColorWell(
                    color = Color(cfg.checkColorB),
                    onColorChange = { onChange(cfg.copy(checkColorB = it.toArgb())) },
                    title = s("格子颜色 2", "Checker color 2"),
                    size = 40.dp,
                )
            }
            Text(
                "${s("方块大小", "Square size")}  ${cfg.checkSize}dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.checkSize.toFloat(),
                onValueChange = { onChange(cfg.copy(checkSize = it.roundToInt().coerceIn(4, 96))) },
                valueRange = 4f..96f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                s("边框", "Border"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            MochiSwitch(
                checked = cfg.borderOn,
                onCheckedChange = { onChange(cfg.copy(borderOn = it)) },
            )
        }
        if (cfg.borderOn) {
            Text(
                "${s("边框不透明度", "Border opacity")}  ${(cfg.borderOpacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.borderOpacity,
                onValueChange = { onChange(cfg.copy(borderOpacity = it.coerceIn(0.05f, 1f))) },
                valueRange = 0.05f..1f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${s("边框粗细", "Border width")}  ${cfg.borderW}dp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiSlider(
                value = cfg.borderW.toFloat(),
                onValueChange = { onChange(cfg.copy(borderW = it.roundToInt().coerceIn(0, 16))) },
                valueRange = 0f..16f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    s("边框颜色", "Border color"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                MochiColorWell(
                    color = Color(cfg.borderColor),
                    onColorChange = { onChange(cfg.copy(borderColor = it.toArgb())) },
                    title = s("边框颜色", "Border color"),
                    size = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun TrailSection(
    cfg: TrailOverlay.Config,
    calibKey: Int,
    onToggle: (Boolean) -> Unit,
    onChange: (TrailOverlay.Config) -> Unit,
    onBindCalibKey: () -> Unit,
    onCalibrate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                s("显示轨迹", "Show trail"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            MochiSwitch(checked = cfg.on, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(6.dp))
        ChoiceRow(
            title = s("轨迹模式", "Trail mode"),
            options = listOf(s("跟随", "Follow"), s("固定", "Fixed")),
            selectedIndex = if (cfg.mode == TrailOverlay.MODE_FIXED) 1 else 0,
            onSelect = { index ->
                onChange(
                    cfg.copy(mode = if (index == 1) TrailOverlay.MODE_FIXED else TrailOverlay.MODE_FOLLOW),
                )
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${s("轨迹灵敏度", "Trail sensitivity")}  ${"%.2f".format(cfg.sensitivity)}×",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MochiSlider(
            value = cfg.sensitivity,
            onValueChange = { onChange(cfg.copy(sensitivity = (it * 100f).roundToInt() / 100f)) },
            valueRange = 0.01f..3f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MochiButton(onClick = onBindCalibKey, style = MochiButtonStyle.Glass) {
                Text(
                    if (calibKey > 0) {
                        "${s("绑定键位", "Bind key")}: ${keyName(calibKey)}"
                    } else {
                        s("绑定键位", "Bind key")
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.weight(1f))
            MochiButton(
                onClick = onCalibrate,
                style = MochiButtonStyle.Glass,
            ) {
                Text(s("校准", "Calibrate"), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "${s("光标大小", "Cursor size")}  ${cfg.dotR}dp",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MochiSlider(
            value = cfg.dotR.toFloat(),
            onValueChange = { onChange(cfg.copy(dotR = it.roundToInt())) },
            valueRange = 2f..40f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                s("光标颜色", "Cursor color"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            MochiColorWell(
                color = Color(cfg.dotColor),
                onColorChange = { onChange(cfg.copy(dotColor = it.toArgb())) },
                title = s("光标颜色", "Cursor color"),
                size = 44.dp,
            )
        }
        Text(
            "${s("边框粗细", "Border width")}  ${cfg.borderW}dp",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MochiSlider(
            value = cfg.borderW.toFloat(),
            onValueChange = { onChange(cfg.copy(borderW = it.roundToInt())) },
            valueRange = 0f..8f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                s("边框颜色", "Border color"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            MochiColorWell(
                color = Color(cfg.borderColor),
                onColorChange = { onChange(cfg.copy(borderColor = it.toArgb())) },
                title = s("边框颜色", "Border color"),
                size = 44.dp,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "${s("轨迹粗细", "Trail width")}  ${cfg.trailW}dp",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MochiSlider(
            value = cfg.trailW.toFloat(),
            onValueChange = { onChange(cfg.copy(trailW = it.roundToInt())) },
            valueRange = 1f..10f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${s("消失延迟", "Fade delay")}  ${cfg.fadeMs}ms",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MochiSlider(
            value = cfg.fadeMs.toFloat(),
            onValueChange = { onChange(cfg.copy(fadeMs = it.roundToInt())) },
            valueRange = 100f..3000f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${s("补点数量", "Fill points")}  ${cfg.fillPoints}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MochiSlider(
            value = cfg.fillPoints.toFloat(),
            onValueChange = { onChange(cfg.copy(fillPoints = it.roundToInt().coerceIn(0, 128))) },
            valueRange = 0f..128f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )

    }
}

@Composable
private fun GroupCard(
    title: String,
    icon: ImageVector? = null,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (collapsible) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { expanded = !expanded }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 6.dp, horizontal = 2.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (collapsible) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = arrow },
                )
            }
        }
        if (collapsible) {
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                GroupCardShell(content)
            }
        } else {
            GroupCardShell(content)
        }
    }
}

@Composable
private fun GroupCardShell(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun AdvancedGroup(params: ClickerParams, onChange: (ClickerParams) -> Unit) {
    GroupCard(s("高级", "Advanced"), Icons.Default.Build) {
        Text(
            "${s("模拟鼠标灵敏度", "Simulated mouse sensitivity")}  ${"%.1f".format(params.sensitivity)}×",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MochiSlider(
            value = params.sensitivity,
            onValueChange = { onChange(params.copy(sensitivity = (it * 10f).roundToInt() / 10f)) },
            valueRange = 0f..3f,
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun BackgroundGroup(
    bridge: ClickerBridge,
    state: BridgeState,
    notifyGranted: Boolean,
    batteryFree: Boolean,
) {
    val context = LocalContext.current
    GroupCard(s("后台", "Background"), Icons.Default.Notifications) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state == BridgeState.CONNECTED) Color(0xFF2E7D32) else Color(0xFFB3261E))
            )
            Spacer(Modifier.width(8.dp))
            Text("Shizuku", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            MochiButton(
                onClick = {
                    when (state) {
                        BridgeState.CONNECTED -> Unit
                        BridgeState.NO_SHIZUKU,
                        BridgeState.SHIZUKU_NOT_RUNNING -> if (!bridge.openShizuku()) bridge.refresh()
                        else -> bridge.requestPermission()
                    }
                },
                style = MochiButtonStyle.Filled,
            ) {
                Text(
                    when (state) {
                        BridgeState.CONNECTED -> s("已连接", "Connected")
                        BridgeState.NO_SHIZUKU -> s("安装", "Install")
                        BridgeState.SHIZUKU_NOT_RUNNING -> s("启动", "Launch")
                        else -> s("授权", "Grant")
                    }
                )
            }
        }
        PermissionLine(s("通知", "Notifications"), notifyGranted, s("授权", "Grant"), onAction = {
            (context as? android.app.Activity)?.let { SystemAccess.requestNotificationPermission(it) }
                ?: SystemAccess.openAppDetails(context)
        })
        PermissionLine(s("电池优化", "Battery"), batteryFree, s("白名单", "Allow"), onAction = {
            SystemAccess.requestIgnoreBatteryOptimizations(context)
        })
        // 参考 KeyMapper：键盘由无障碍按键过滤在系统层拦下，evdev 读不到键盘节点时绑键也能用
        var keyFilter by remember { mutableStateOf(KeyFilterService.isEnabled(context)) }
        LaunchedEffect(Unit) {
            while (true) {
                keyFilter = KeyFilterService.isEnabled(context)
                delay(1000)
            }
        }
        PermissionLine(
            s("按键拦截", "Key filter"),
            keyFilter,
            if (keyFilter) s("已开启", "On") else s("开启", "Turn on"),
            onAction = { if (!keyFilter) KeyFilterService.openSettings(context) },
        )
    }
}

@Composable
private fun PermissionLine(
    title: String,
    ok: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) Color(0xFF2E7D32) else Color(0xFFB3261E))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        MochiButton(onClick = onAction, style = MochiButtonStyle.Outlined) { Text(action) }
    }
}

@Composable
private fun DiagnosticsGroup(status: ServiceStatus) {
    GroupCard(s("诊断", "Diagnostics"), Icons.Default.List) {
        InfoLine(
            s("注入", "Injection"),
            if (!status.inject) {
                s("不可用", "unavailable")
            } else when (status.injectMode) {
                "mouse" -> s("鼠标按键", "mouse button")
                "touch" -> s("触摸兜底", "touch fallback")
                else -> s("未知", "unknown")
            },
        )
        InfoLine(s("模式", "Method"), when (status.method) {
            "dbc" -> "DBC"
            "dc" -> "DC"
            else -> s("默认", "Default")
        })
        InfoLine(s("开关", "Switch"), if (status.enabled) s("开", "On") else s("关", "Off"))
        InfoLine(s("绑定键", "Bound key"), if (status.toggleKey > 0) keyName(status.toggleKey) else "—")
        InfoLine(s("最后检测", "Last input"), status.lastKey.ifBlank { "—" })
        InfoLine(s("光标", "Cursor"), "${status.cursorX}, ${status.cursorY}")
        InfoLine(s("输出次数", "Clicks"), "${status.clicks}")
        InfoLine(s("实测 CPS", "Measured CPS"), "${status.measuredCps}")
        if (status.err.isNotBlank()) InfoLine(s("错误", "Error"), status.err)
        if (status.scanError.isNotBlank()) InfoLine(s("扫描", "Scan"), status.scanError)
        if (status.nodes.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            status.nodes.forEach { node ->
                Text(
                    "· ${node.name.ifBlank { node.path.substringAfterLast('/') }}" +
                        if (node.ok) " · ${node.events}" else " · ${node.err.take(30)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AboutGroup() {
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
        }
    }
    GroupCard(s("关于", "About"), Icons.Default.Info) {
        Text(
            s("使用的代码", "Code used"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            s(
                "Shizuku API（权限与 UserService）\nKeyMapper（evdev 检测与注入思路）\nBASpark（TEST 点击特效，按其 canvas 实现逐帧复刻）\nOperit（设置分组样式参考）\nmochi-compose（按钮、开关、滑块、分段与主题）\nJetpack Compose · Material 3",
                "Shizuku API (permission & user service)\nKeyMapper (evdev detection & injection)\nBASpark (TEST click effect, ported frame-by-frame from its canvas implementation)\nOperit (settings grouping style reference)\nmochi-compose (buttons, switch, slider, segmented control & theme)\nJetpack Compose · Material 3"
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text("GitHub", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinkRow("mochi-compose", "https://github.com/feice11/mochi-compose", ::open)
        LinkRow("Shizuku-API", "https://github.com/RikkaApps/Shizuku-API", ::open)
        LinkRow("KeyMapper", "https://github.com/keymapperorg/KeyMapper", ::open)
        LinkRow(
            s("ImageToolbox（UI 参考）", "ImageToolbox (UI reference)"),
            "https://github.com/T8RIN/ImageToolbox",
            ::open,
        )
        LinkRow(
            s("BASpark（点击特效）", "BASpark (click effect)"),
            "https://github.com/DoomVoss/BASpark",
            ::open,
        )
        LinkRow(
            s("Operit（设置样式）", "Operit (settings style)"),
            "https://github.com/AAswordman/Operit",
            ::open,
        )
    }
}

@Composable
private fun LinkRow(label: String, url: String, onOpen: (String) -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(url) }
            .padding(vertical = 4.dp),
    )
}

// ------------------------------------------------------------------ TEST

// ------------------------------------------------- BA SPARK 点击特效
// 参数与几何取自 BASpark 的 canvas 实现（src/Web/index.html），按帧复刻。

private const val BA_SCALE = 1.5f
private const val BA_FILL_RATE = 26f
private const val BA_FILL_LIFE = 16f
private const val BA_RING_LIFE = 23f
private const val BA_RING_LEN = 1.1f * Math.PI.toFloat()
private const val BA_SEG_NUM = 10
private const val BA_MIN_W = 0.4f
private const val BA_MAX_W = 3.3f
private const val BA_LEN_DIM = 0.4f

private class BaWave(val x: Float, val y: Float) {
    var life = 0f
    var r = 0f
    var ang = 0f
    var rs = 0f
    val off = FloatArray(2)
    val round = FloatArray(2)
}

private class BaSpark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rot: Float,
    var rs: Float,
    var s: Float,
    var a: Float,
)

private fun randomPick(values: FloatArray): Float = values[(Math.random() * values.size).toInt().coerceIn(0, values.size - 1)]

private fun createBaWave(x: Float, y: Float): BaWave {
    val wave = BaWave(x, y)
    wave.ang = (Math.random() * Math.PI * 2).toFloat()
    wave.rs = randomPick(floatArrayOf(0f, 0.03f, 0.06f))
    for (i in 0..1) {
        wave.off[i] = if (i == 0) 0f else ((Math.random() * 3 - 1.5) * Math.PI).toFloat()
        wave.round[i] = randomPick(floatArrayOf(0f, 1f, 1.5f, 2f))
    }
    return wave
}

private fun createBaSpark(x: Float, y: Float): BaSpark {
    val a = (Math.random() * Math.PI * 2)
    val speed = (4.8 + Math.random() * 2).toFloat()
    return BaSpark(
        x = x,
        y = y,
        vx = (Math.cos(a) * speed).toFloat(),
        vy = (Math.sin(a) * speed).toFloat(),
        rot = (Math.random() * Math.PI * 2).toFloat(),
        rs = ((Math.random() - 0.5) * 0.28).toFloat(),
        s = ((4 + Math.random() * 3) * BA_SCALE).toFloat(),
        a = 1f,
    )
}

private fun stepBaFx(
    waves: MutableList<BaWave>,
    sparks: MutableList<BaSpark>,
    frameScale: Float,
    opacity: Float,
) {
    val it = waves.iterator()
    while (it.hasNext()) {
        val w = it.next()
        w.life += frameScale
        val p = minOf(w.life / BA_FILL_LIFE, 1f)
        val ease = 1f - (1f - p) * (1f - p) * (1f - p)
        w.r = BA_FILL_RATE * BA_SCALE * ease
        w.ang -= w.rs * frameScale
        if (w.life >= BA_RING_LIFE) it.remove()
    }
    val si = sparks.iterator()
    val decay = Math.pow(0.9, frameScale.toDouble()).toFloat()
    while (si.hasNext()) {
        val sp = si.next()
        sp.x += sp.vx * frameScale
        sp.y += sp.vy * frameScale
        sp.vx *= decay
        sp.vy *= decay
        sp.rot += sp.rs * frameScale
        sp.a -= 0.032f * frameScale
        if (sp.a <= 0f) si.remove()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBaFx(
    waves: List<BaWave>,
    sparks: List<BaSpark>,
    ringStart: Color,
    ringEnd: Color,
    sparkColor: Color,
) {
    val d = density
    waves.forEach { w ->
        val fillP = minOf(w.life / BA_FILL_LIFE, 1f)
        if (fillP < 1f) {
            drawCircle(
                color = ringStart.copy(alpha = 1f - fillP),
                radius = w.r * d,
                center = Offset(w.x, w.y),
            )
        }

        val p = minOf(w.life / BA_RING_LIFE, 1f)
        val lineMul = minOf(-0.8f * (p - 0.8f) + 1f, 1f)
        val ct = minOf(1.2f * p, 1f)
        val ringColor = lerp(ringStart, ringEnd, ct)
        val ringAlpha = minOf(1.1f - 0.3f * p, 1f)

        for (i in 0..1) {
            val base = w.ang + w.off[i]
            val startA: Float
            val endA: Float
            if (p <= 0.1f) {
                endA = base + BA_RING_LEN
                startA = endA - BA_RING_LEN * (p / 0.1f)
            } else if (p > BA_LEN_DIM) {
                startA = base
                endA = startA + BA_RING_LEN * (1f - (p - BA_LEN_DIM) / (1f - BA_LEN_DIM))
            } else {
                startA = base
                endA = startA + BA_RING_LEN
            }

            val radius = (w.r + w.round[i] * BA_SCALE) * d
            for (k in 0 until BA_SEG_NUM) {
                val t0 = k / BA_SEG_NUM.toFloat()
                val t1 = (k + 1) / BA_SEG_NUM.toFloat()
                val a0 = startA + (endA - startA) * t0
                val a1 = startA + (endA - startA) * t1
                if (Math.abs(a1 - a0) < 0.01f) continue
                val wT = minOf(2f - Math.abs(4f * (t0 - 0.5f)), 1f)
                val lw = (BA_MIN_W * (1f - wT) + BA_MAX_W * wT) * lineMul * d
                if (lw <= 0.05f) continue
                drawArc(
                    color = ringColor.copy(alpha = ringAlpha),
                    startAngle = (a0 * 180f / Math.PI.toFloat()),
                    sweepAngle = ((a1 - a0) * 180f / Math.PI.toFloat()),
                    useCenter = false,
                    topLeft = Offset(w.x - radius, w.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = lw),
                )
            }
        }
    }

    sparks.forEach { sp ->
        rotate(
            degrees = sp.rot * 180f / Math.PI.toFloat(),
            pivot = Offset(sp.x, sp.y),
        ) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(sp.x, sp.y - sp.s * d)
                lineTo(sp.x + sp.s * 0.6f * d, sp.y + sp.s * 0.6f * d)
                lineTo(sp.x - sp.s * 0.6f * d, sp.y + sp.s * 0.6f * d)
                close()
            }
            drawPath(path, sparkColor.copy(alpha = sp.a.coerceIn(0f, 1f)))
        }
    }
}

@Composable
private fun CpsTestScreen(
    bridge: ClickerBridge,
    connected: Boolean,
    onExit: () -> Unit,
) {
    val status by bridge.status.collectAsState()
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val accent = cs.primary
    val ringStart = if (dark) Color.White else accent
    val ringEnd = accent
    val sparkColor = if (dark) Color.White else accent
    val panelShape = RoundedCornerShape(28.dp)

    // 返回键退出（物理返回 / 手势返回）
    BackHandler { onExit() }

    // 进入动效：内容自下 26dp 收位（只做布局位移，不申请离屏图层）
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val settle by animateFloatAsState(
        targetValue = if (entered) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
    )

    val waves = remember { mutableListOf<BaWave>() }
    val sparks = remember { mutableListOf<BaSpark>() }
    val clickTimes = remember { mutableListOf<Long>() }
    var frame by remember { mutableStateOf(0) }
    var lastClicks by remember { mutableStateOf(status.clicks) }
    var tapX by remember { mutableStateOf(0f) }
    var tapY by remember { mutableStateOf(0f) }
    var cps by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val nanos = withFrameNanos { it }
            if (last == 0L) last = nanos
            val ms = (nanos - last) / 1_000_000.0
            last = nanos
            val fs = (ms / (1000.0 / 60.0)).coerceIn(0.05, 6.0).toFloat()

            stepBaFx(waves, sparks, fs, 1f)

            val now = android.os.SystemClock.elapsedRealtime()
            while (clickTimes.isNotEmpty() && now - clickTimes.first() > 1000L) clickTimes.removeAt(0)

            val live = clickTimes.size
            if (live != cps) cps = live
            frame++
        }
    }

    LaunchedEffect(status.clicks) {
        val delta = status.clicks - lastClicks
        lastClicks = status.clicks
        if (delta > 0 && connected) {
            val t = android.os.SystemClock.elapsedRealtime()
            repeat(delta.coerceAtMost(8L).toInt()) {
                createBaWave(status.cursorX.toFloat(), status.cursorY.toFloat()).let { w ->
                    waves.add(w)
                    if (waves.size > 32) waves.removeAt(0)
                }
                repeat(4) { sparks.add(createBaSpark(status.cursorX.toFloat(), status.cursorY.toFloat())) }
                clickTimes.add(t)
                if (clickTimes.size > 400) clickTimes.removeAt(0)
            }
        }
    }

    val autoActive = connected && status.clicking
    val shownCps = if (autoActive) status.measuredCps else cps
    val posX = if (autoActive) status.cursorX else tapX.roundToInt()
    val posY = if (autoActive) status.cursorY else tapY.roundToInt()

    Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (settle * 26f).dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        tapX = offset.x
                        tapY = offset.y
                        val t = android.os.SystemClock.elapsedRealtime()
                        createBaWave(offset.x, offset.y).let { w ->
                            waves.add(w)
                            if (waves.size > 32) waves.removeAt(0)
                        }
                        repeat(4) { sparks.add(createBaSpark(offset.x, offset.y)) }
                        clickTimes.add(t)
                        if (clickTimes.size > 400) clickTimes.removeAt(0)
                        frame++
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(50))
                            .background(cs.surfaceContainerHigh)
                            .border(1.dp, cs.primary.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .clickable(onClick = onExit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "✕",
                            color = cs.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "CPS TEST",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cs.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (autoActive) s("输出", "OUTPUT") else s("输入", "INPUT"),
                            color = accent,
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, panelShape)
                        .clip(panelShape)
                        .background(cs.surfaceContainer)
                        .border(1.dp, cs.primary.copy(alpha = 0.10f), panelShape)
                        .padding(horizontal = 20.dp, vertical = 26.dp),
                ) {
                    Text(
                        "$shownCps",
                        color = accent,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 84.sp,
                            lineHeight = 88.sp,
                        ),
                    )
                    Text(
                        "CPS",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "x $posX   y $posY",
                        color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (autoActive) s("总计 ${status.clicks}", "total ${status.clicks}")
                        else s("总计 ${status.clicks}", "total ${status.clicks}"),
                        color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (autoActive) s("正在读取实际输出", "Reading live output")
                        else s("点击任意位置测试", "Tap anywhere to test"),
                        color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 特效画在内容之上，避免 CPS 数字与面板盖住点击波纹
            Canvas(modifier = Modifier.fillMaxSize()) {
                // frame 读取用于驱动逐帧重绘
                if (frame >= 0) drawBaFx(waves, sparks, ringStart, ringEnd, sparkColor)
            }
        }
    }
}

/** 轻量触感反馈 */
class Haptics(private val context: Context) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    fun tick() {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(12)
            }
        } catch (_: Throwable) {
        }
    }
}

@Composable
private fun RangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    fun fmt(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)
    Column(modifier.fillMaxWidth()) {
        Text("min ${fmt(value.start)}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MochiSlider(
            value = value.start,
            onValueChange = { v ->
                onValueChange(v.coerceIn(valueRange.start, value.endInclusive)..value.endInclusive)
            },
            valueRange = valueRange.start..value.endInclusive,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("max ${fmt(value.endInclusive)}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MochiSlider(
            value = value.endInclusive,
            onValueChange = { v ->
                onValueChange(value.start..v.coerceIn(value.start, valueRange.endInclusive))
            },
            valueRange = value.start..valueRange.endInclusive,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

