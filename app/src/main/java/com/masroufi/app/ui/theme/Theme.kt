package com.masroufi.app.ui.theme

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.masroufi.app.R
import com.masroufi.app.data.prefs.ThemeMode

/** Surface tokens for the glass material; every translucent layer reads from here so light and dark stay consistent. */
@Immutable
data class GlassTokens(
    val base: Color,
    val fill: Color,
    val fillStrong: Color,
    val strokeTop: Color,
    val strokeBottom: Color,
    val shadow: Color,
    val textSecondary: Color,
    val aurora: List<Color>,
    val dark: Boolean,
)

private val DarkGlass = GlassTokens(
    base = Color(0xFF05070A),
    fill = Color.White.copy(alpha = 0.06f),
    fillStrong = Color.White.copy(alpha = 0.11f),
    strokeTop = Color.White.copy(alpha = 0.24f),
    strokeBottom = Color.White.copy(alpha = 0.04f),
    shadow = Color.Black.copy(alpha = 0.45f),
    textSecondary = Color(0xFF98A2B3),
    aurora = listOf(Color(0xFF0FCF8B), Color(0xFF1BA8E6), Color(0xFF7B5CFF), Color(0xFFF2A93B)),
    dark = true,
)

private val LightGlass = GlassTokens(
    base = Color(0xFFF1F3F7),
    fill = Color.White.copy(alpha = 0.55f),
    fillStrong = Color.White.copy(alpha = 0.78f),
    strokeTop = Color.White.copy(alpha = 0.95f),
    strokeBottom = Color.White.copy(alpha = 0.35f),
    shadow = Color(0xFF1B2A4A).copy(alpha = 0.10f),
    textSecondary = Color(0xFF5B6576),
    aurora = listOf(Color(0xFF7FE8C1), Color(0xFF9AD4FF), Color(0xFFC8B8FF), Color(0xFFFFD9A0)),
    dark = false,
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/** Background and plain surfaces are transparent: the ambient aurora behind every screen is the canvas. */
private fun scheme(dark: Boolean, glass: GlassTokens): ColorScheme {
    val onBase = if (dark) Color(0xFFF4F6FA) else Color(0xFF0B0F14)
    val primary = if (dark) Color(0xFF4BE3A5) else Color(0xFF0A9F67)
    val sheet = if (dark) Color(0xF2111519) else Color(0xF5FBFCFE)
    val common = if (dark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = primary,
        onPrimary = if (dark) Color(0xFF00281A) else Color.White,
        primaryContainer = primary.copy(alpha = 0.18f),
        onPrimaryContainer = onBase,
        secondary = if (dark) Color(0xFF7DB3FF) else Color(0xFF2F6FE0),
        onSecondary = Color.White,
        secondaryContainer = primary.copy(alpha = if (dark) 0.22f else 0.16f),
        onSecondaryContainer = onBase,
        tertiary = if (dark) Color(0xFFFFC56B) else Color(0xFFB57400),
        onTertiary = Color(0xFF2A1800),
        tertiaryContainer = Color(0xFFFFC56B).copy(alpha = 0.18f),
        onTertiaryContainer = onBase,
        background = Color.Transparent,
        onBackground = onBase,
        surface = Color.Transparent,
        onSurface = onBase,
        surfaceVariant = glass.fill,
        onSurfaceVariant = glass.textSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = sheet,
        surfaceContainerLow = sheet,
        surfaceContainer = sheet,
        surfaceContainerHigh = sheet,
        surfaceContainerHighest = glass.fillStrong,
        inverseSurface = onBase,
        inverseOnSurface = glass.base,
        outline = onBase.copy(alpha = 0.22f),
        outlineVariant = onBase.copy(alpha = 0.10f),
        scrim = Color.Black.copy(alpha = 0.55f),
        error = if (dark) Color(0xFFFF7A7A) else Color(0xFFD12C2C),
        errorContainer = Color(0xFFFF7A7A).copy(alpha = 0.18f),
    )
}

/** Semantic money colours stay fixed regardless of dynamic colour so income/expense never swap meaning. */
@Immutable
data class FinanceColors(val income: Color, val expense: Color, val transfer: Color, val warning: Color, val categories: List<Color>)

private val DarkFinance = FinanceColors(
    income = Color(0xFF4BE3A5),
    expense = Color(0xFFFF7A86),
    transfer = Color(0xFF7DB3FF),
    warning = Color(0xFFFFC56B),
    categories = listOf(
        Color(0xFFFF8A65), Color(0xFF4DD9C7), Color(0xFF7DB3FF), Color(0xFFFFD166), Color(0xFFC792FF), Color(0xFF4BE3A5),
        Color(0xFFFFA94D), Color(0xFF6EC8FF), Color(0xFFFF7A86), Color(0xFFD7B899), Color(0xFF45D0B0), Color(0xFFFF8FC7),
    ),
)

private val LightFinance = FinanceColors(
    income = Color(0xFF0A9F67),
    expense = Color(0xFFE0394B),
    transfer = Color(0xFF2F6FE0),
    warning = Color(0xFFC07A00),
    categories = listOf(
        Color(0xFFE8613C), Color(0xFF14A897), Color(0xFF2F6FE0), Color(0xFFD9A400), Color(0xFF8E4FE0), Color(0xFF0A9F67),
        Color(0xFFE07A10), Color(0xFF168ED6), Color(0xFFE0394B), Color(0xFF9C7550), Color(0xFF0E9C80), Color(0xFFD9468F),
    ),
)

val LocalFinanceColors = staticCompositionLocalOf { DarkFinance }

/** True when the user turned animations off in system settings; motion then snaps instead of springing. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Springs follow Apple's damping/response model: stiffness = (2π / response)². Critically damped by default;
 * bounce only for things the finger threw or that should feel physical.
 */
object Motion {
    private fun stiffness(response: Float): Float = (2 * Math.PI / response).let { (it * it).toFloat() }

    fun <T> spatial(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 1f, stiffness = stiffness(0.38f))

    fun <T> snappy(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 1f, stiffness = stiffness(0.24f))

    fun <T> effects(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 1f, stiffness = stiffness(0.5f))

    fun <T> bouncy(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 0.72f, stiffness = stiffness(0.34f))
}

private val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
    Font(R.font.inter, FontWeight.ExtraBold),
)

private val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
    Font(R.font.plex_arabic_bold, FontWeight.ExtraBold),
)

/**
 * Latin display sizes get Apple-style negative tracking. Arabic is a joined script, so letter-spacing would break
 * the connections: it keeps zero tracking and slightly taller leading for its ascenders and descenders.
 */
private fun typography(arabic: Boolean): Typography {
    val family = if (arabic) PlexArabic else Inter
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Float): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (if (arabic) line * 1.18f else line.toFloat()).sp,
        letterSpacing = if (arabic) TextUnit.Unspecified else tracking.em,
    )
    return Typography(
        displayLarge = style(56, 60, FontWeight.Bold, -0.035f),
        displayMedium = style(44, 50, FontWeight.Bold, -0.03f),
        displaySmall = style(34, 40, FontWeight.Bold, -0.025f),
        headlineLarge = style(30, 36, FontWeight.Bold, -0.022f),
        headlineMedium = style(26, 32, FontWeight.SemiBold, -0.02f),
        headlineSmall = style(22, 28, FontWeight.SemiBold, -0.016f),
        titleLarge = style(20, 26, FontWeight.SemiBold, -0.012f),
        titleMedium = style(17, 22, FontWeight.SemiBold, -0.008f),
        titleSmall = style(15, 20, FontWeight.SemiBold, -0.004f),
        bodyLarge = style(17, 24, FontWeight.Normal, -0.006f),
        bodyMedium = style(15, 21, FontWeight.Normal, -0.002f),
        bodySmall = style(13, 18, FontWeight.Normal, 0.004f),
        labelLarge = style(15, 20, FontWeight.SemiBold, -0.002f),
        labelMedium = style(13, 18, FontWeight.Medium, 0.006f),
        labelSmall = style(11, 14, FontWeight.Medium, 0.02f),
    )
}

/** Tabular figures keep every digit the same width, so rolling and changing amounts never jitter sideways. */
val AmountStyle: TextStyle
    @Composable get() = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp, fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
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
    val glass = if (dark) DarkGlass else LightGlass
    val base = scheme(dark, glass)
    // Dynamic colour only re-tints the accents; the glass surfaces stay ours.
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(primary = dynamic.primary, onPrimary = dynamic.onPrimary, secondary = dynamic.secondary, tertiary = dynamic.tertiary)
    } else {
        base
    }
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val typography = remember(arabic) { typography(arabic) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalFinanceColors provides if (dark) DarkFinance else LightFinance,
        LocalReduceMotion provides reduceMotion,
        LocalGlass provides glass,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = AppShapes, content = content)
    }
}
