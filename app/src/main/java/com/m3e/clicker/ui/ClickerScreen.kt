package com.m3e.clicker.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.window.Dialog
import com.m3e.clicker.BridgeState
import com.m3e.clicker.ClickerBridge
import com.m3e.clicker.ClickerForegroundService
import com.m3e.clicker.ClickerParams
import com.m3e.clicker.ServiceStatus
import com.m3e.clicker.SystemAccess
import kotlinx.coroutines.delay
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
        method = method,
        minCps = sp.getInt("minCps", 8).coerceIn(lo, hi),
        maxCps = sp.getInt("maxCps", 12).coerceIn(lo, hi),
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
    )
}

private fun saveParams(context: Context, params: ClickerParams) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
        putBoolean("enabled", params.enabled)
        putInt("toggleKey", params.toggleKey)
        putString("method", params.method)
        putInt("minCps", params.minCps)
        putInt("maxCps", params.maxCps)
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var testMode by remember { mutableStateOf(false) }
    var showBind by remember { mutableStateOf(false) }

    val connected = state == BridgeState.CONNECTED

    val notifyGranted = SystemAccess.hasNotificationPermission(context)
    val batteryFree = SystemAccess.isIgnoringBatteryOptimizations(context)
    val a11yEnabled = SystemAccess.isAccessibilityEnabled(context)

    fun update(new: ClickerParams) {
        params = new
        saveParams(context, new)
        if (connected) bridge.applyParams(new)
    }

    fun setEnabled(value: Boolean) {
        val next = params.copy(enabled = value)
        params = next
        saveParams(context, next)
        if (!connected) return
        if (value) bridge.startTrigger(next) else bridge.stopTrigger()
    }

    LaunchedEffect(state) {
        if (state == BridgeState.CONNECTED) bridge.applyParams(params)
    }

    // 只有绑定键位触发（seq 变化）时才把开关状态回灌到界面，避免本地点击被旧快照覆盖
    var lastSeq by remember { mutableStateOf(status.switchSeq) }
    LaunchedEffect(status.switchSeq) {
        if (status.switchSeq != lastSeq) {
            lastSeq = status.switchSeq
            params = params.copy(enabled = status.enabled)
            saveParams(context, params)
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

    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize(),
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
                TopRow(showMenu = !landscape, onClickMenu = { showSettings = true })
                Spacer(Modifier.height(10.dp))

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    MasterSwitch(
                        on = params.enabled,
                        enabled = connected,
                        onToggle = {
                            haptic.tick()
                            setEnabled(!params.enabled)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    BindRow(
                        key = params.toggleKey,
                        onClick = { showBind = true },
                    )

                    Spacer(Modifier.height(12.dp))
                    MethodSelector(params.method) { next ->
                        val lo: Int
                        val hi: Int
                        when (next) {
                            "dbc" -> {
                                lo = 1; hi = 16
                            }
                            "dc" -> {
                                lo = 15; hi = 150
                            }
                            else -> {
                                lo = 1; hi = 25
                            }
                        }
                        update(
                            params.copy(
                                method = next,
                                minCps = params.minCps.coerceIn(lo, hi),
                                maxCps = params.maxCps.coerceIn(lo, hi),
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    when (params.method) {
                        "dbc" -> DbcSection(params, ::update)
                        "dc" -> DcSection(params, ::update)
                        else -> DefaultSection(params, ::update)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        haptic.tick()
                        testMode = true
                    },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("TEST", style = MaterialTheme.typography.titleMedium)
                }
            }


            if (landscape) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    SettingsContent(
                        params = params,
                        onChange = ::update,
                        status = status,
                        state = state,
                        bridge = bridge,
                        notifyGranted = notifyGranted,
                        batteryFree = batteryFree,
                        a11yEnabled = a11yEnabled,
                        lang = lang,
                        onLangChange = onLangChange,
                        onClose = null,
                    )
                }
            }
        }

        if (showSettings && !landscape) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.66f)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    SettingsContent(
                        params = params,
                        onChange = ::update,
                        status = status,
                        state = state,
                        bridge = bridge,
                        notifyGranted = notifyGranted,
                        batteryFree = batteryFree,
                        a11yEnabled = a11yEnabled,
                        lang = lang,
                        onLangChange = onLangChange,
                        onClose = { showSettings = false },
                    )
                }
            }
        }

        if (showBind) {
            KeyBindDialog(
                captured = status.capturedKey,
                current = params.toggleKey,
                onOpen = { bridge.beginKeyCapture() },
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
    }
}

// ------------------------------------------------------------------ 顶栏

@Composable
private fun TopRow(showMenu: Boolean, onClickMenu: () -> Unit) {
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
    }
}

// ------------------------------------------------------------------ 总开关

@Composable
private fun MasterSwitch(on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Switch(
            checked = on,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            modifier = Modifier.size(width = 64.dp, height = 40.dp),
        )
        Spacer(Modifier.width(12.dp))
        if (!enabled) {
            Text(
                s("Shizuku 未启用", "Shizuku not running"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BindRow(key: Int, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onClick) {
            Text(
                if (key > 0) "${s("绑定键位", "Toggle key")}: ${keyName(key)}"
                else s("绑定键位", "Toggle key"),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// ------------------------------------------------------------------ 模式与参数

@Composable
private fun MethodSelector(method: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = method == "default",
            onClick = { onSelect("default") },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
        ) { Text(s("默认", "Default")) }
        SegmentedButton(
            selected = method == "dbc",
            onClick = { onSelect("dbc") },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
        ) { Text("DBC") }
        SegmentedButton(
            selected = method == "dc",
            onClick = { onSelect("dc") },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
        ) { Text("DC") }
    }
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
        steps = 23,
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
        steps = 14,
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
        steps = 19,
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
        steps = 10,
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
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt().coerceIn(0, 100)) },
        valueRange = 0f..100f,
        steps = 19,
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
    Slider(
        value = params.dcCps.toFloat(),
        onValueChange = { onChange(params.copy(dcCps = it.roundToInt().coerceIn(15, 150))) },
        valueRange = 15f..150f,
        steps = 134,
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
        steps = 48,
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
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    LaunchedEffect(Unit) { onOpen() }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(s("绑定键位", "Bind key"), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (captured > 0) keyName(captured)
                    else if (current > 0) "${s("当前", "Current")}: ${keyName(current)}"
                    else s("按下任意键…", "Press any key…"),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    s("键盘或鼠标侧键均可，绑定后按一次即可开关", "Keyboard or mouse side button; press once to toggle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClear) { Text(s("清除", "Clear")) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text(s("取消", "Cancel")) }
                    TextButton(onClick = { if (captured > 0) onConfirm(captured) }, enabled = captured > 0) {
                        Text(s("确定", "OK"))
                    }
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
    a11yEnabled: Boolean,
    lang: String,
    onLangChange: (String) -> Unit,
    onClose: (() -> Unit)?,
) {
    val context = LocalContext.current
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
            a11yEnabled = a11yEnabled,
        )
        Spacer(Modifier.height(12.dp))
        DiagnosticsGroup(status)
        Spacer(Modifier.height(12.dp))
        AboutGroup()
        Spacer(Modifier.height(16.dp))

        GroupCard(s("语言", "Language"), Icons.Default.Person) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = lang == "zh",
                    onClick = { onLangChange("zh") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("中文") }
                SegmentedButton(
                    selected = lang == "en",
                    onClick = { onLangChange("en") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("English") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp), content = content)
        }
    }
}

@Composable
private fun AdvancedGroup(params: ClickerParams, onChange: (ClickerParams) -> Unit) {
    GroupCard(s("高级", "Advanced"), Icons.Default.Build) {
        Text(
            "${s("光标灵敏度", "Sensitivity")} ${"%.1f".format(params.sensitivity)}×",
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = params.sensitivity,
            onValueChange = { onChange(params.copy(sensitivity = (it * 10f).roundToInt() / 10f)) },
            valueRange = 0f..3f,
        )
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = params.acceleration,
                onClick = { onChange(params.copy(acceleration = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(s("速度加速", "Accelerated")) }
            SegmentedButton(
                selected = !params.acceleration,
                onClick = { onChange(params.copy(acceleration = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(s("原始输入", "Raw")) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s("触摸兼容", "Touch fallback"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = params.touchCompat,
                onCheckedChange = { onChange(params.copy(touchCompat = it)) },
            )
        }
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = params.followCursor,
                onClick = { onChange(params.copy(followCursor = true)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(s("跟随光标", "Follow")) }
            SegmentedButton(
                selected = !params.followCursor,
                onClick = { onChange(params.copy(followCursor = false)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(s("固定坐标", "Fixed")) }
        }
        if (!params.followCursor) {
            Spacer(Modifier.height(6.dp))
            Text(
                "X ${params.fixedX}   Y ${params.fixedY}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = params.fixedX.toFloat(),
                onValueChange = { onChange(params.copy(fixedX = it.roundToInt())) },
                valueRange = 0f..3000f,
            )
            Slider(
                value = params.fixedY.toFloat(),
                onValueChange = { onChange(params.copy(fixedY = it.roundToInt())) },
                valueRange = 0f..3000f,
            )
        }
    }
}

@Composable
private fun BackgroundGroup(
    bridge: ClickerBridge,
    state: BridgeState,
    notifyGranted: Boolean,
    batteryFree: Boolean,
    a11yEnabled: Boolean,
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
            TextButton(onClick = {
                when (state) {
                    BridgeState.CONNECTED -> Unit
                    BridgeState.NO_SHIZUKU,
                    BridgeState.SHIZUKU_NOT_RUNNING -> if (!bridge.openShizuku()) bridge.refresh()
                    else -> bridge.requestPermission()
                }
            }) {
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
        PermissionLine(s("无障碍", "Accessibility"), a11yEnabled, s("开启", "Enable"), onAction = {
            SystemAccess.openAccessibilitySettings(context)
        })
        Spacer(Modifier.height(4.dp))
        Row {
            TextButton(onClick = { ClickerForegroundService.start(context) }) {
                Text(s("启动前台服务", "Start service"))
            }
            TextButton(onClick = { ClickerForegroundService.stop(context) }) { Text(s("停止", "Stop")) }
        }
    }
}

@Composable
private fun PermissionLine(title: String, granted: Boolean, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFF2E7D32) else Color(0xFFB3261E))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun DiagnosticsGroup(status: ServiceStatus) {
    GroupCard(s("诊断", "Diagnostics"), Icons.Default.List) {
        InfoLine(s("注入", "Injection"), if (status.inject) status.injectMode else s("不可用", "unavailable"))
        InfoLine(s("模式", "Method"), when (status.method) {
            "dbc" -> "DBC"
            "dc" -> "DC"
            else -> s("默认", "Default")
        })
        InfoLine(s("开关", "Switch"), if (status.enabled) s("开", "On") else s("关", "Off"))
        InfoLine(s("绑定键", "Bound key"), if (status.toggleKey > 0) keyName(status.toggleKey) else "—")
        InfoLine(s("最后检测", "Last input"), status.lastKey.ifBlank { "—" })
        InfoLine(s("光标", "Cursor"), "${status.cursorX}, ${status.cursorY}")
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
                "Shizuku API（权限与 UserService）\nKeyMapper（evdev 检测与注入思路）\nBASpark（TEST 点击特效，按其 canvas 实现逐帧复刻）\nOperit（设置分组样式参考）\nJetpack Compose · Material 3",
                "Shizuku API (permission & user service)\nKeyMapper (evdev detection & injection)\nBASpark (TEST click effect, ported frame-by-frame from its canvas implementation)\nOperit (settings grouping style reference)\nJetpack Compose · Material 3"
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text("GitHub", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private val BA_RING_START = floatArrayOf(250f, 252f, 252f)
private val BA_RING_END = floatArrayOf(
    (45f + 510f) / 3f,
    (175f + 510f) / 3f,
    (255f + 510f) / 3f,
)

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
) {
    val d = density
    waves.forEach { w ->
        val fillP = minOf(w.life / BA_FILL_LIFE, 1f)
        if (fillP < 1f) {
            drawCircle(
                color = Color(45f / 255f, 175f / 255f, 255f / 255f, alpha = 1f - fillP),
                radius = w.r * d,
                center = Offset(w.x, w.y),
            )
        }

        val p = minOf(w.life / BA_RING_LIFE, 1f)
        val lineMul = minOf(-0.8f * (p - 0.8f) + 1f, 1f)
        val ct = minOf(1.2f * p, 1f)
        val rr = (BA_RING_START[0] * (1f - ct) + BA_RING_END[0] * ct) / 255f
        val gg = (BA_RING_START[1] * (1f - ct) + BA_RING_END[1] * ct) / 255f
        val bb = (BA_RING_START[2] * (1f - ct) + BA_RING_END[2] * ct) / 255f
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
                    color = Color(rr, gg, bb, alpha = ringAlpha),
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
            drawPath(path, Color.White.copy(alpha = sp.a.coerceIn(0f, 1f)))
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

    Surface(color = Color(0xFF101216), modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                // frame 读取用于驱动逐帧重绘
                if (frame >= 0) drawBaFx(waves, sparks)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF23262B))
                            .clickable(onClick = onExit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = Color(0xFFE6E8EB), style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "CPS TEST",
                        color = Color(0xFF9AA0A6),
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (autoActive) s("输出", "OUTPUT") else s("输入", "INPUT"),
                        color = Color(0xFF7FD1C1),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(Modifier.weight(1f))

                Text(
                    "$shownCps",
                    color = Color(0xFF7FD1C1),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    "CPS",
                    color = Color(0xFF9AA0A6),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "x $posX  y $posY",
                    color = Color(0xFF9AA0A6),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                Spacer(Modifier.weight(1f))
                Text(
                    s("total ${status.clicks}", "total ${status.clicks}"),
                    color = Color(0xFF6B7076),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(10.dp))
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
