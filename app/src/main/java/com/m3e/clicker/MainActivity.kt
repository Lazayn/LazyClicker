package com.m3e.clicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.m3e.clicker.ui.AppTheme
import com.m3e.clicker.ui.ClickerScreen
import com.m3e.clicker.ui.M3EClickerTheme
import com.m3e.clicker.ui.productSansTextStyles
import ui.feice.mochi.theme.ColorSchemeMode
import ui.feice.mochi.theme.MochiGlassDefaults
import ui.feice.mochi.theme.MochiMaterials
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.ThemeController

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppTheme.load(applicationContext)
        val bridge = ClickerBridge.get(applicationContext)
        setContent {
            M3EClickerTheme {
                val controller = remember {
                    ThemeController(
                        colorSchemeMode = ColorSchemeMode.System,
                        keyColor = AppTheme.seed,
                    )
                }
                // 配色页改种子色 / 风格 / 对比度后，mochi 组件跟着重算
                SideEffect {
                    controller.keyColor = AppTheme.seed
                    controller.paletteStyle = AppTheme.style
                    controller.contrastLevel = AppTheme.contrast.toDouble()
                }
                MochiTheme(
                    controller = controller,
                    // mochi 组件统一使用 Product Sans Light
                    textStyles = productSansTextStyles(),
                    // 菜单/警告框用厚玻璃，栏里的控件（按钮）用标准玻璃，面板保持纯色
                    materials = MochiMaterials(
                        menu = MochiGlassDefaults.Thick,
                        alert = MochiGlassDefaults.Thick,
                        control = MochiGlassDefaults.Regular,
                    ),
                ) {
                    ClickerScreen(bridge)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ClickerBridge.get(applicationContext).refresh()
        // 从系统授权页回来时补挂一次悬浮窗
        if (OverlayHud.enabled(this)) {
            ClickerForegroundService.overlay(this, ClickerForegroundService.ACTION_OVERLAY_ON)
        }
    }
}
