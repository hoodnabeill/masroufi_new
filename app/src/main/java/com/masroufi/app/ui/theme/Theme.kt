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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
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

/**
 * Surface tokens, Apple's grouped-content model (DESIGN.md). Field names are kept from the glass era so every
 * component reads the new values: [base] is systemGroupedBackground, [fill] the card/cell, [fillStrong] the tertiary
 * fill (inputs, inner tiles), [track] progress tracks, [strokeBottom] the in-card separator. [strokeTop] is the
 * specular edge floating glass catches; plain cards have no outline.
 */
@Immutable
data class GlassTokens(
    val base: Color,
    val fill: Color,
    val fillStrong: Color,
    val track: Color,
    val strokeTop: Color,
    val strokeBottom: Color,
    val shadow: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Accent sequence for decorative art (onboarding cards and similar). */
    val aurora: List<Color>,
    val dark: Boolean,
) {
    /** Cards and cells carry no outline in the grouped idiom; the brush stays so call sites keep compiling. */
    val rim: Brush = SolidColor(Color.Transparent)
}

private val DarkGlass = GlassTokens(
    base = Color(0xFF000000),
    fill = Color(0xFF1C1C1E),
    fillStrong = Color(0xFF2C2C2E),
    track = Color(0xFF38383A),
    strokeTop = Color.White.copy(alpha = 0.18f),
    strokeBottom = Color(0xFF38383A),
    shadow = Color.Black.copy(alpha = 0.6f),
    textSecondary = Color(0xFFAEAEB2),
    textTertiary = Color(0xFF8D8D93),
    aurora = listOf(Color(0xFF7D7AFF), Color(0xFFFF375F), Color(0xFF0A84FF), Color(0xFF30D158)),
    dark = true,
)

private val LightGlass = GlassTokens(
    base = Color(0xFFF2F2F7),
    fill = Color(0xFFFFFFFF),
    fillStrong = Color(0xFFE9E9EE),
    track = Color(0xFFE3E3E8),
    strokeTop = Color.White.copy(alpha = 0.9f),
    strokeBottom = Color(0xFFC6C6C8),
    shadow = Color.Black.copy(alpha = 0.12f),
    textSecondary = Color(0xFF6C6C70),
    textTertiary = Color(0xFF8A8A8E),
    aurora = listOf(Color(0xFF5856D6), Color(0xFFFF2D55), Color(0xFF007AFF), Color(0xFF34C759)),
    dark = false,
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/**
 * Green is money and the primary action, indigo links and selection, orange "soon" and streaks, red over-pace and
 * destructive. Light-mode accents use Apple's accessible (darker) variants so text on white stays at 4.5:1.
 * Background and surface stay transparent so the stage behind every screen shows through.
 */
private fun scheme(dark: Boolean, glass: GlassTokens): ColorScheme {
    val text = if (dark) Color.White else Color.Black
    val common = if (dark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = if (dark) Color(0xFF30D158) else Color(0xFF248A3D),
        onPrimary = if (dark) Color(0xFF00210B) else Color.White,
        primaryContainer = if (dark) Color(0xFF0E3B1C) else Color(0xFFDDF5E3),
        onPrimaryContainer = if (dark) Color(0xFF30D158) else Color(0xFF145A27),
        secondary = if (dark) Color(0xFF7D7AFF) else Color(0xFF5856D6),
        onSecondary = Color.White,
        secondaryContainer = if (dark) Color(0xFF26254D) else Color(0xFFE6E5FA),
        onSecondaryContainer = if (dark) Color(0xFFB9B7FF) else Color(0xFF3634A3),
        tertiary = if (dark) Color(0xFFFF9F0A) else Color(0xFFC93400),
        onTertiary = if (dark) Color(0xFF2A1700) else Color.White,
        tertiaryContainer = if (dark) Color(0xFF3D2600) else Color(0xFFFFEAD2),
        onTertiaryContainer = if (dark) Color(0xFFFF9F0A) else Color(0xFF7A2E00),
        background = Color.Transparent,
        onBackground = text,
        surface = Color.Transparent,
        onSurface = text,
        surfaceVariant = glass.fillStrong,
        onSurfaceVariant = glass.textSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = glass.base,
        surfaceContainerLow = glass.fill,
        surfaceContainer = glass.fill,
        surfaceContainerHigh = glass.fillStrong,
        surfaceContainerHighest = glass.track,
        inverseSurface = text,
        inverseOnSurface = glass.base,
        outline = glass.strokeBottom,
        outlineVariant = glass.strokeBottom,
        scrim = Color.Black.copy(alpha = if (dark) 0.5f else 0.3f),
        error = if (dark) Color(0xFFFF453A) else Color(0xFFD70015),
        onError = Color.White,
        errorContainer = if (dark) Color(0xFF3D0F0C) else Color(0xFFFFE1DF),
        onErrorContainer = if (dark) Color(0xFFFF453A) else Color(0xFF8A000E),
    )
}

/**
 * Semantic money colours stay fixed regardless of dynamic colour so their meaning never changes. Spending is shown
 * in the normal text colour, not red: spending is not shameful. [over] marks over-pace and overdue; [warning] the
 * "getting close" and "soon" states; [streak] the streak flame.
 */
@Immutable
data class FinanceColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val over: Color,
    val streak: Color,
    val categories: List<Color>,
)

// The twelve iOS system hues (dark and accessible-light variants) in the order stored colour indices expect.
private val DarkFinance = FinanceColors(
    income = Color(0xFF30D158),
    expense = Color.White,
    transfer = Color(0xFF0A84FF),
    warning = Color(0xFFFF9F0A),
    over = Color(0xFFFF453A),
    streak = Color(0xFFFF9F0A),
    categories = listOf(
        Color(0xFFFF453A), Color(0xFF0A84FF), Color(0xFF63E6E2), Color(0xFFFF375F), Color(0xFFFFD60A), Color(0xFFFF9F0A),
        Color(0xFF30D158), Color(0xFF5E5CE6), Color(0xFFE5A50A), Color(0xFF40C8E0), Color(0xFFBF5AF2), Color(0xFFAC8E68),
    ),
)

private val LightFinance = FinanceColors(
    income = Color(0xFF248A3D),
    expense = Color.Black,
    transfer = Color(0xFF0066CC),
    warning = Color(0xFFC93400),
    over = Color(0xFFD70015),
    streak = Color(0xFFC93400),
    categories = listOf(
        Color(0xFFFF3B30), Color(0xFF007AFF), Color(0xFF00C7BE), Color(0xFFFF2D55), Color(0xFFFFCC00), Color(0xFFFF9500),
        Color(0xFF34C759), Color(0xFF5856D6), Color(0xFFE08700), Color(0xFF32ADE6), Color(0xFFAF52DE), Color(0xFFA2845E),
    ),
)

val LocalFinanceColors = staticCompositionLocalOf { DarkFinance }

/** True when the user turned animations off in system settings; motion then snaps instead of springing. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** The user asked for solid surfaces (Settings): glass draws an opaque frosted fill, no refraction. */
val LocalSolidGlass = staticCompositionLocalOf { false }

/**
 * Navigation transitions are built outside composition, so they cannot read [LocalReduceMotion]; the theme mirrors
 * the setting here. shortcut: read once per theme composition, so a change applies on the next recomposition.
 */
object MotionFlags {
    @Volatile var reduce: Boolean = false
}

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

/** Display and numbers: Readex Pro (one variable font; Compose drives the weight axis from FontWeight). */
private val Readex = FontFamily(
    Font(R.font.readex, FontWeight.Normal),
    Font(R.font.readex, FontWeight.Medium),
    Font(R.font.readex, FontWeight.SemiBold),
    Font(R.font.readex, FontWeight.Bold),
)

/** Body text in both languages: IBM Plex Sans Arabic, which also carries Latin. */
private val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
    Font(R.font.plex_arabic_bold, FontWeight.ExtraBold),
)

/**
 * Apple's type ramp (DESIGN.md) in IBM Plex Sans Arabic; Readex Pro, a rounded face like SF Rounded, for big
 * numbers. Latin tracking is size-specific (tighter as it grows); Arabic is a joined script, so it keeps zero
 * tracking and ~18% taller leading for its ascenders.
 */
private fun typography(arabic: Boolean): Typography {
    fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, tracking: Float = 0f): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (if (arabic) line * 1.18f else line.toFloat()).sp,
        letterSpacing = if (arabic || tracking == 0f) TextUnit.Unspecified else tracking.em,
    )
    return Typography(
        displayLarge = style(Readex, 64, 68, FontWeight.Bold, -0.03f),
        displayMedium = style(Readex, 44, 50, FontWeight.Bold, -0.025f),
        displaySmall = style(Readex, 28, 34, FontWeight.Bold, -0.02f),
        headlineLarge = style(PlexArabic, 34, 41, FontWeight.Bold, -0.02f),
        headlineMedium = style(PlexArabic, 22, 28, FontWeight.Bold, -0.015f),
        headlineSmall = style(PlexArabic, 20, 25, FontWeight.SemiBold, -0.012f),
        titleLarge = style(PlexArabic, 17, 22, FontWeight.SemiBold, -0.01f),
        titleMedium = style(PlexArabic, 17, 22, FontWeight.SemiBold, -0.01f),
        titleSmall = style(PlexArabic, 15, 20, FontWeight.SemiBold, -0.008f),
        bodyLarge = style(PlexArabic, 17, 22, FontWeight.Normal, -0.01f),
        bodyMedium = style(PlexArabic, 15, 20, FontWeight.Normal, -0.008f),
        bodySmall = style(PlexArabic, 13, 18, FontWeight.Normal),
        labelLarge = style(PlexArabic, 17, 22, FontWeight.SemiBold, -0.01f),
        labelMedium = style(PlexArabic, 12, 16, FontWeight.Medium),
        labelSmall = style(PlexArabic, 11, 13, FontWeight.Medium, 0.006f),
    )
}

/**
 * Card-level money: Readex Pro with tabular figures, so rolling and changing amounts never jitter sideways. The
 * negative tracking applies to the digits; amount components render the currency in its own smaller, untracked style.
 */
val AmountStyle: TextStyle
    @Composable get() = TextStyle(
        fontFamily = Readex,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum",
    )

/** Continuous corners (DESIGN.md): controls 12, tiles and cells 16, cards 22, sheets and hero 28. */
private val AppShapes = Shapes(
    extraSmall = continuous(12),
    small = continuous(16),
    medium = continuous(16),
    large = continuous(22),
    extraLarge = continuous(28),
)

/** Spacing steps from the handoff (4/8/12/16/20/28) with the 16 dp screen gutter. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val gutter = 16.dp
}

@Composable
fun MasroufiTheme(solidGlass: Boolean = false, mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    // Light and dark are both first-class; the system decides unless Settings forces one.
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val glass = if (dark) DarkGlass else LightGlass
    val colors = remember(dark) { scheme(dark, glass) }
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    SideEffect { MotionFlags.reduce = reduceMotion }
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val typography = remember(arabic) { typography(arabic) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // The window behind Compose matches the screen colour, so a theme switch or a slow frame never flashes.
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(glass.base.toArgb()))
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalFinanceColors provides if (dark) DarkFinance else LightFinance,
        LocalReduceMotion provides reduceMotion,
        LocalSolidGlass provides solidGlass,
        LocalGlass provides glass,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = AppShapes, content = content)
    }
}
