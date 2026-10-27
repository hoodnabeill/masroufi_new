package com.masroufi.app.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.data.prefs.ThemeMode

private val Emerald = Color(0xFF0B6E4F)
private val EmeraldLight = Color(0xFF6FD6A8)
private val Gold = Color(0xFFC8962E)
private val GoldLight = Color(0xFFF3C969)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F3CC),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4D6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF0A1F15),
    tertiary = Gold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF2A1800),
    background = Color(0xFFF7F9F6),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF7F9F6),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F0),
    surfaceContainer = Color(0xFFEBEFEA),
    surfaceContainerHigh = Color(0xFFE5E9E4),
    surfaceContainerHighest = Color(0xFFE0E3DF),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA6F3CC),
    secondary = Color(0xFFB3CCBD),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = GoldLight,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF634000),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF262B28),
    surfaceContainerHighest = Color(0xFF313633),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

/** Semantic money colours stay fixed across dynamic colour so income/expense never swap meaning. */
@Immutable
data class FinanceColors(val income: Color, val expense: Color, val transfer: Color, val warning: Color, val categories: List<Color>)

private val LightFinance = FinanceColors(
    income = Color(0xFF13804F),
    expense = Color(0xFFC0392B),
    transfer = Color(0xFF3867D6),
    warning = Color(0xFFB7791F),
    categories = listOf(
        Color(0xFFE76F51), Color(0xFF2A9D8F), Color(0xFF3867D6), Color(0xFFE9C46A), Color(0xFF8E44AD), Color(0xFF16A085),
        Color(0xFFD35400), Color(0xFF2980B9), Color(0xFFC0392B), Color(0xFF6D4C41), Color(0xFF00897B), Color(0xFFAD1457),
    ),
)

private val DarkFinance = FinanceColors(
    income = Color(0xFF6FD6A8),
    expense = Color(0xFFFF8A80),
    transfer = Color(0xFF9DB7FF),
    warning = Color(0xFFF3C969),
    categories = listOf(
        Color(0xFFF4A261), Color(0xFF64D2C4), Color(0xFF9DB7FF), Color(0xFFF3D98B), Color(0xFFCE93D8), Color(0xFF5FD3B5),
        Color(0xFFFFAB73), Color(0xFF7FC3F0), Color(0xFFFF8A80), Color(0xFFBCAAA4), Color(0xFF4DD0C1), Color(0xFFF48FB1),
    ),
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinance }

/** True when the user turned animations off in system settings; motion then snaps instead of springing. */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Motion {
    fun <T> spatial(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> effects(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> bouncy(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 0.55f, stiffness = 500f)
}

// System fonts: Roboto for Latin and Noto Naskh/Sans Arabic for Arabic are present on every Android device, so
// glyph coverage and shaping are correct without bundling font files.
private val Base = Typography()
private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 22.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val AmountStyle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun MasroufiTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalFinanceColors provides if (dark) DarkFinance else LightFinance,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
