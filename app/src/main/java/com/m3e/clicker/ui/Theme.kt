package com.m3e.clicker.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3e.clicker.R
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.MochiTextStyles
import ui.feice.mochi.theme.PaletteStyle
import ui.feice.mochi.theme.darkColorScheme as mochiDark
import ui.feice.mochi.theme.defaultTextStyles
import ui.feice.mochi.theme.lightColorScheme as mochiLight

/** 默认种子色：沿用之前的青绿 */
val DefaultSeed = Color(0xFF00696B)

/** 全局字体：Product Sans Light（仅含拉丁字形，中文按系统回退） */
val ProductSans = FontFamily(Font(R.font.product_sans_light, FontWeight.Light))

/**
 * 自定义配色的全局状态：种子色 + 配色风格 + 对比度。
 * 这一组参数就是 MaterialKolor 里 DynamicMaterialColors 的输入（seedColor / style / contrastLevel），
 * 配色由 mochi 的 HCT 生成器算出，MaterialTheme 与 mochi 组件共用同一套结果。
 */
object AppTheme {
    private const val PREFS = "m3e_appearance"
    private const val KEY_SEED = "seed"
    private const val KEY_STYLE = "palette_style"
    private const val KEY_CONTRAST = "contrast"

    var seed by mutableStateOf(DefaultSeed)
        private set
    var style by mutableStateOf(PaletteStyle.TonalSpot)
        private set
    var contrast by mutableFloatStateOf(0f)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        seed = Color(prefs.getInt(KEY_SEED, DefaultSeed.toArgb()))
        style = prefs.getString(KEY_STYLE, null)
            ?.let { name -> runCatching { PaletteStyle.valueOf(name) }.getOrNull() }
            ?: PaletteStyle.TonalSpot
        contrast = prefs.getFloat(KEY_CONTRAST, 0f)
    }

    /** 只改内存里的配色（拖动取色时用，避免每帧生成配色 + 写盘） */
    fun set(seed: Color, style: PaletteStyle, contrast: Float) {
        this.seed = seed
        this.style = style
        this.contrast = contrast
    }

    /** 把当前配色写盘 */
    fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SEED, seed.toArgb())
            .putString(KEY_STYLE, style.name)
            .putFloat(KEY_CONTRAST, contrast)
            .apply()
    }

    fun apply(context: Context, seed: Color, style: PaletteStyle, contrast: Float) {
        set(seed, style, contrast)
        persist(context)
    }

    fun reset(context: Context) = apply(context, DefaultSeed, PaletteStyle.TonalSpot, 0f)
}

/** M3E 的形状语言：更大的圆角与"胶囊化"控件 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** 把 Product Sans 铺到全部字阶上 */
private fun Typography.withProductSans(): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = ProductSans),
    displayMedium = displayMedium.copy(fontFamily = ProductSans),
    displaySmall = displaySmall.copy(fontFamily = ProductSans),
    headlineLarge = headlineLarge.copy(fontFamily = ProductSans),
    headlineMedium = headlineMedium.copy(fontFamily = ProductSans),
    headlineSmall = headlineSmall.copy(fontFamily = ProductSans),
    titleLarge = titleLarge.copy(fontFamily = ProductSans),
    titleMedium = titleMedium.copy(fontFamily = ProductSans),
    titleSmall = titleSmall.copy(fontFamily = ProductSans),
    bodyLarge = bodyLarge.copy(fontFamily = ProductSans),
    bodyMedium = bodyMedium.copy(fontFamily = ProductSans),
    bodySmall = bodySmall.copy(fontFamily = ProductSans),
    labelLarge = labelLarge.copy(fontFamily = ProductSans),
    labelMedium = labelMedium.copy(fontFamily = ProductSans),
    labelSmall = labelSmall.copy(fontFamily = ProductSans),
)

/** mochi 组件自己的字阶也要换字体，否则开关/列表/按钮仍是系统字体 */
fun productSansTextStyles(): MochiTextStyles {
    val d = defaultTextStyles()
    fun t(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = ProductSans)
    return d.copy(
        displayLarge = t(d.displayLarge),
        displayMedium = t(d.displayMedium),
        displaySmall = t(d.displaySmall),
        headlineLarge = t(d.headlineLarge),
        headlineMedium = t(d.headlineMedium),
        headlineSmall = t(d.headlineSmall),
        titleLarge = t(d.titleLarge),
        titleMedium = t(d.titleMedium),
        titleSmall = t(d.titleSmall),
        bodyLarge = t(d.bodyLarge),
        bodyMedium = t(d.bodyMedium),
        bodySmall = t(d.bodySmall),
        labelLarge = t(d.labelLarge),
        labelMedium = t(d.labelMedium),
        labelSmall = t(d.labelSmall),
        largeTitle = t(d.largeTitle),
        navTitle = t(d.navTitle),
        body = t(d.body),
        subhead = t(d.subhead),
        footnote = t(d.footnote),
    )
}

/** M3E：大字重、紧字距的 expressive 字体系统 */
private val ExpressiveTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(
            fontSize = 62.sp,
            lineHeight = 64.sp,
            letterSpacing = (-1.8).sp,
            fontWeight = FontWeight.Black,
        ),
        displayMedium = base.displayMedium.copy(
            fontSize = 44.sp,
            lineHeight = 48.sp,
            letterSpacing = (-1.0).sp,
            fontWeight = FontWeight.Black,
        ),
        headlineLarge = base.headlineLarge.copy(
            fontSize = 30.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.4).sp,
            fontWeight = FontWeight.Bold,
        ),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
    )
}.withProductSans()

/** mochi 生成的配色直接映射成 Material3 的 ColorScheme，两套组件共用同一套颜色 */
private fun MochiColorScheme.toMaterial3(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}

@Composable
fun M3EClickerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    AppTheme.load(context)
    val mochiScheme = if (darkTheme) {
        mochiDark(AppTheme.seed, AppTheme.style, AppTheme.contrast.toDouble())
    } else {
        mochiLight(AppTheme.seed, AppTheme.style, AppTheme.contrast.toDouble())
    }
    MaterialTheme(
        colorScheme = mochiScheme.toMaterial3(),
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}
