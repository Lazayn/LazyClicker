package com.m3e.clicker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    return ClickerParams(
        minCps = sp.getInt("minCps", 8),
        maxCps = sp.getInt("maxCps", 12),
        followCursor = sp.getBoolean("followCursor", true),
        fixedX = sp.getInt("fixedX", 540),
        fixedY = sp.getInt("fixedY", 1200),
        sensitivity = sp.getFloat("sensitivity", 1.0f),
        touchCompat = sp.getBoolean("touchCompat", false),
        outputMode = sp.getString("outputMode", "key") ?: "key",
        keyCode = sp.getInt("keyCode", 66),
        acceleration = sp.getBoolean("acceleration", true),
    )
}

private fun saveParams(context: Context, params: ClickerParams) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
        putInt("minCps", params.minCps)
        putInt("maxCps", params.maxCps)
        putBoolean("followCursor", params.followCursor)
        putInt("fixedX", params.fixedX)
        putInt("fixedY", params.fixedY)
        putFloat("sensitivity", params.sensitivity)
        putBoolean("touchCompat", params.touchCompat)
        putString("outputMode", params.outputMode)
        putInt("keyCode", params.keyCode)
        putBoolean("acceleration", params.acceleration)
        apply()
    }
}

private fun loadLang(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("lang", "zh") ?: "zh"

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

@Composable
private fun ScreenContent(bridge: ClickerBridge, lang: String, onLangChange: (String) -> Unit) {
    val context = LocalContext.current
    val state by bridge.state.collectAsState()
    val status by bridge.status.collectAsState()

    var params by remember { mutableStateOf(loadParams(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var testMode by remember { mutableStateOf(false) }

    val connected = state == BridgeState.CONNECTED
    val running = status.trigger

    val notifyGranted = SystemAccess.hasNotificationPermission(context)
    val batteryFree = SystemAccess.isIgnoringBatteryOptimizations(context)
    val a11yEnabled = SystemAccess.isAccessibilityEnabled(context)

    fun update(new: ClickerParams) {
        params = new
        saveParams(context, new)
        if (connected) bridge.applyParams(new)
    }

    LaunchedEffect(state) {
        if (state == BridgeState.CONNECTED) bridge.applyParams(params)
    }

    if (testMode) {
        CpsTestScreen(
            bridge = bridge,
            params = params,
            connected = connected,
            onExit = {
                bridge.stopManual()
                testMode = false
            },
        )
        return
    }

    val haptic = remember { Haptics(context) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = 24.dp)
        ) {
            TopRow(running = running, connected = connected, onClickMenu = { showSettings = true })

            Spacer(Modifier.height(16.dp))
            OutputBar(params = params, onChange = ::update)

            Spacer(Modifier.height(14.dp))
            BigCps(params = params, onChange = ::update)

            Spacer(Modifier.height(10.dp))
            StatusLine(state = state, status = status, connected = connected)

            Spacer(Modifier.weight(1f))

            StartButton(
                active = running,
                enabled = connected,
                label = when {
                    !connected -> s("Shizuku 未启用", "Shizuku not running")
                    running -> s("停止", "Stop")
                    else -> s("开始", "Start")
                },
                onClick = {
                    haptic.tick()
                    if (running) bridge.stopTrigger() else bridge.startTrigger(params)
                },
            )

            Spacer(Modifier.height(10.dp))

            FilledTonalButton(
                onClick = {
                    haptic.tick()
                    testMode = true
                },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(s("TEST", "TEST"), style = MaterialTheme.typography.titleMedium)
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showSettings = false }
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(animationSpec = tween(260)) { -it },
            exit = slideOutHorizontally(animationSpec = tween(200)) { -it },
        ) {
            SettingsPanel(
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

// ------------------------------------------------------------------ 顶栏

@Composable
private fun TopRow(running: Boolean, connected: Boolean, onClickMenu: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onClickMenu),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val barH = 2.dp.toPx()
                repeat(3) { i ->
                    val y = (size.height / 4) * (i + 1)
                    drawRect(
                        color = Color(0xFF44474A),
                        topLeft = Offset(0f, y - barH / 2),
                        size = androidx.compose.ui.geometry.Size(size.width, barH),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "LazyClicker",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when {
                        running -> Color(0xFF2E7D32)
                        connected -> Color(0xFF3182CE)
                        else -> Color(0xFFB3261E)
                    }
                )
        )
    }
}

// ------------------------------------------------------------------ 输出（主界面）

@Composable
private fun OutputBar(params: ClickerParams, onChange: (ClickerParams) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            s("输出", "Output"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
                selected = params.outputMode == "key",
                onClick = { onChange(params.copy(outputMode = "key")) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(s("键值", "Key")) }
            SegmentedButton(
                selected = params.outputMode == "mouse",
                onClick = { onChange(params.copy(outputMode = "mouse")) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(s("左键", "Left")) }
        }
        if (params.outputMode == "key") {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { picker = true },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(38.dp),
            ) {
                Text(keyLabel(params.keyCode), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (picker) {
        KeyPickerDialog(
            current = params.keyCode,
            onPick = { code ->
                onChange(params.copy(keyCode = code, outputMode = "key"))
                picker = false
            },
            onDismiss = { picker = false },
        )
    }
}

// ------------------------------------------------------------------ CPS

@Composable
private fun BigCps(params: ClickerParams, onChange: (ClickerParams) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${params.minCps}",
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
            "${params.maxCps}",
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
    RangeSlider(
        value = params.minCps.toFloat()..params.maxCps.toFloat(),
        onValueChange = { range ->
            val lo = range.start.roundToInt().coerceIn(1, 30)
            val hi = range.endInclusive.roundToInt().coerceIn(1, 30)
            onChange(params.copy(minCps = lo, maxCps = hi))
        },
        valueRange = 1f..30f,
        steps = 28,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusLine(state: BridgeState, status: ServiceStatus, connected: Boolean) {
    val text = when {
        !connected -> s("Shizuku 未启用", "Shizuku not running")
        status.clicking -> s("连点中 ${status.measuredCps} cps", "Clicking ${status.measuredCps} cps")
        status.trigger -> s("监听中", "Listening")
        else -> s("就绪", "Ready")
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (status.devices.isNotEmpty()) {
            Text(
                s("${status.devices.size} 设备", "${status.devices.size} device(s)"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ 主按钮

@Composable
private fun StartButton(active: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
        label = "press",
    )
    val corner by animateFloatAsState(
        targetValue = if (active) 26f else 34f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "corner",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().height(84.dp).scale(scale),
        shape = RoundedCornerShape(corner.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primary,
            contentColor = if (active) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        ),
    ) {
        Text(
            label,
            style = if (enabled) MaterialTheme.typography.headlineMedium
            else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ------------------------------------------------------------------ 设置抽屉

@Composable
private fun SettingsPanel(
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
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.88f)
            .widthIn(max = 360.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s("设置", "Settings"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text(s("关闭", "Close")) }
            }
            Spacer(Modifier.height(12.dp))

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

            // 语言切换（放在最下面）
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    s("语言", "Language"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AdvancedGroup(params: ClickerParams, onChange: (ClickerParams) -> Unit) {
    GroupCard(s("高级", "Advanced")) {
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
    GroupCard(s("后台", "Background")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state == BridgeState.CONNECTED) Color(0xFF2E7D32) else Color(0xFFB3261E))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                s("Shizuku", "Shizuku"),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
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
            TextButton(onClick = { ClickerForegroundService.start(context) }) { Text(s("启动前台服务", "Start service")) }
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
    GroupCard(s("诊断", "Diagnostics")) {
        InfoLine(s("注入", "Injection"), if (status.inject) status.injectMode else s("不可用", "unavailable"))
        InfoLine(s("输出", "Output"), if (status.outputMode == "mouse") s("左键", "Left") else keyDisplay(status.keyCode))
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
    GroupCard(s("关于", "About")) {
        Text(
            s("使用的代码", "Code used"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            s(
                "Shizuku API（权限与 UserService）\nKeyMapper（evdev 检测与注入思路）\nJetpack Compose · Material 3",
                "Shizuku API (permission & user service)\nKeyMapper (evdev detection & injection)\nJetpack Compose · Material 3"
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text("GitHub", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinkRow("Shizuku-API", "https://github.com/RikkaApps/Shizuku-API", ::open)
        LinkRow("KeyMapper", "https://github.com/keymapperorg/KeyMapper", ::open)
        LinkRow(s("ImageToolbox（UI 参考）", "ImageToolbox (UI reference)"), "https://github.com/T8RIN/ImageToolbox", ::open)
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

// ------------------------------------------------------------------ TEST：全屏 CPS

private data class TapRecord(val time: Long, val x: Float, val y: Float)

@Composable
private fun CpsTestScreen(
    bridge: ClickerBridge,
    params: ClickerParams,
    connected: Boolean,
    onExit: () -> Unit,
) {
    val status by bridge.status.collectAsState()
    var taps by remember { mutableStateOf(listOf<TapRecord>()) }
    var tapCps by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf(List(20) { 0 }) }

    // 每 100ms 刷新一次 CPS 统计
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val now = android.os.SystemClock.elapsedRealtime()
            val recent = taps.filter { now - it.time <= 1000L }
            if (taps.size != recent.size) taps = recent
            tapCps = recent.size
            history = (history.drop(1) + recent.size)
        }
    }

    val autoActive = connected && (status.trigger || status.manual)
    val shownCps = if (autoActive) status.measuredCps else tapCps
    val posX = if (autoActive) status.cursorX else (taps.lastOrNull()?.x?.roundToInt() ?: 0)
    val posY = if (autoActive) status.cursorY else (taps.lastOrNull()?.y?.roundToInt() ?: 0)
    val average = if (history.isEmpty()) 0 else history.sum() / history.size

    Surface(color = Color(0xFF101216), modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        taps = taps + TapRecord(android.os.SystemClock.elapsedRealtime(), offset.x, offset.y)
                    }
                }
        ) {
            // 点击位置可视化
            taps.lastOrNull()?.let { tap ->
                Box(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (tap.x - 14f * density).roundToInt(),
                                (tap.y - 14f * density).roundToInt(),
                            )
                        }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7FD1C1).copy(alpha = 0.35f))
                )
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
                        if (autoActive) s("输出", "OUTPUT") else s("触摸", "TAP"),
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

                Spacer(Modifier.height(18.dp))
                Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                    val max = history.maxOrNull()?.coerceAtLeast(1) ?: 1
                    val barWidth = size.width / history.size
                    history.forEachIndexed { index, value ->
                        val h = size.height * (value.toFloat() / max)
                        drawRect(
                            color = Color(0xFF7FD1C1).copy(alpha = 0.35f + 0.65f * (index.toFloat() / history.size)),
                            topLeft = Offset(index * barWidth + barWidth * 0.2f, size.height - h),
                            size = androidx.compose.ui.geometry.Size(barWidth * 0.6f, h),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                Text(
                    s("avg $average · total ${status.clicks}", "avg $average · total ${status.clicks}"),
                    color = Color(0xFF6B7076),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ 键值选择

@Composable
private fun KeyPickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var showScan by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(current.toString()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(s("输出键值", "Output key"), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    COMMON_KEYS.forEach { option ->
                        FilterChip(
                            selected = current == option.code,
                            onClick = { onPick(option.code) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(3) },
                    label = { Text("keyCode") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showScan = true }) { Text(s("扫描", "Scan")) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(s("取消", "Cancel")) }
                    TextButton(
                        onClick = { text.toIntOrNull()?.let { if (it in 1..400) onPick(it) } },
                        enabled = text.toIntOrNull()?.let { it in 1..400 } == true,
                    ) { Text(s("确定", "OK")) }
                }
            }
        }
        if (showScan) {
            KeyScanDialog(
                onPick = { code ->
                    onPick(code)
                    showScan = false
                },
                onDismiss = { showScan = false },
            )
        }
    }
}

@Composable
private fun KeyScanDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var captured by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            captured = event.key.nativeKeyCode
                            true
                        } else {
                            false
                        }
                    },
            ) {
                Text(s("扫描按键", "Scan key"), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                Text(
                    captured?.let { keyDisplay(it) } ?: s("等待按键…", "Waiting for key…"),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(s("取消", "Cancel")) }
                    TextButton(onClick = { captured?.let(onPick) }, enabled = captured != null) {
                        Text(s("使用", "Use"))
                    }
                }
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
