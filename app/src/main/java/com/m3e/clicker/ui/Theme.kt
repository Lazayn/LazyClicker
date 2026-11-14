package com.m3e.clicker.ui

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2F3),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF45646A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC9E7EE),
    onSecondaryContainer = Color(0xFF002028),
    tertiary = Color(0xFF8A4E00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FBFC),
    onBackground = Color(0xFF161D1E),
    surface = Color(0xFFF4FBFC),
    onSurface = Color(0xFF161D1E),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F494A),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF6F6),
    surfaceContainer = Color(0xFFE8F1F1),
    surfaceContainerHigh = Color(0xFFE2ECEC),
    surfaceContainerHighest = Color(0xFFDCE6E6),
    inverseSurface = Color(0xFF2B3132),
    inverseOnSurface = Color(0xFFECF2F2),
    inversePrimary = Color(0xFF81D5D6),
    surfaceTint = Color(0xFF00696B),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF81D5D6),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F50),
    onPrimaryContainer = Color(0xFFA6F2F3),
    secondary = Color(0xFFADCCD3),
    onSecondary = Color(0xFF16343B),
    secondaryContainer = Color(0xFF2D4A51),
    onSecondaryContainer = Color(0xFFC9E7EE),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1415),
    onBackground = Color(0xFFDCE4E5),
    surface = Color(0xFF0D1415),
    onSurface = Color(0xFFDCE4E5),
    surfaceVariant = Color(0xFF3F494A),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF889394),
    outlineVariant = Color(0xFF3F494A),
    surfaceContainerLowest = Color(0xFF080F10),
    surfaceContainerLow = Color(0xFF151C1D),
    surfaceContainer = Color(0xFF192021),
    surfaceContainerHigh = Color(0xFF242B2C),
    surfaceContainerHighest = Color(0xFF2E3637),
    inverseSurface = Color(0xFFDCE4E5),
    inverseOnSurface = Color(0xFF2B3132),
    inversePrimary = Color(0xFF00696B),
    surfaceTint = Color(0xFF81D5D6),
)

/** M3E 的动效语言：以弹簧物理为核心（按压回弹、形状形变、入场错落） */
object M3EMotion {
    fun <T> press(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.42f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> shape(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessLow,
    )

    fun <T> enter(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
    )
}

/** M3E 的形状语言：更大的圆角与"胶囊化"控件 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

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
}

@Composable
fun M3EClickerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}
