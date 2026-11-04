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
 * "Midnight Ledger" surface tokens (masroufi-v2-design/README.md §1). Field names are kept from the glass era so
 * every component reads the new values: [fill] is the card surface, [fillStrong] the raised layer (inputs, chips,
 * inner tiles), [strokeTop]/[strokeBottom] the 1 px line.
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
    /**
     * The liquid-glass rim: light catches the top edge of every surface and fades into the hairline by the middle,
     * so flat cards still read as a material with depth.
     */
    val rim: Brush = Brush.verticalGradient(0f to strokeTop, 0.55f to strokeBottom)
}

// Design accents. Dark values are from the handoff; the light theme's darker variants are derived for 4.5:1 text
// contrast on white, since the handoff is dark-only.
private val Lime = Color(0xFFC8F169)
private val Violet = Color(0xFFA99BFF)
private val Amber = Color(0xFFFFB648)
private val Coral = Color(0xFFFF8C73)
private val Blue = Color(0xFF7DB4FF)
private val Mint = Color(0xFF5EE3A1)
private val Pink = Color(0xFFE7A6FF)
private val Yellow = Color(0xFFFFD466)
private val Orange = Color(0xFFFFA36B)

private val DarkGlass = GlassTokens(
    base = Color(0xFF0B0A10),
    fill = Color(0xFF15131D),
    fillStrong = Color(0xFF1D1B28),
    track = Color(0xFF272435),
    strokeTop = Color(0xFF3E3A50),
    strokeBottom = Color(0xFF2A2737),
    shadow = Color.Black.copy(alpha = 0.5f),
    textSecondary = Color(0xFFB4AFC6),
    textTertiary = Color(0xFF8E89A3),
    aurora = listOf(Violet, Pink, Blue, Lime),
    dark = true,
)

private val LightGlass = GlassTokens(
    base = Color(0xFFF6F4FA),
    fill = Color(0xFFFFFFFF),
    fillStrong = Color(0xFFF0EDF6),
    track = Color(0xFFE4E0EE),
    strokeTop = Color(0xFFFFFFFF),
    strokeBottom = Color(0xFFE1DDEA),
    shadow = Color(0xFF15131D).copy(alpha = 0.08f),
    textSecondary = Color(0xFF4F4A62),
    textTertiary = Color(0xFF6C6782),
    aurora = listOf(Color(0xFF6A5AE0), Color(0xFFB25ACF), Color(0xFF2F6FE0), Color(0xFF4A7A00)),
    dark = false,
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/**
 * Lime is money and the primary action, violet is brand and navigation, amber is streaks and "soon", coral is
 * over-pace, overdue and destructive. Background and surface stay transparent so the stage behind every screen
 * (and the floating glass that refracts it) shows through.
 */
private fun scheme(dark: Boolean, glass: GlassTokens): ColorScheme {
    val text = if (dark) Color(0xFFF4F2FA) else Color(0xFF15131D)
    val common = if (dark) darkColorScheme() else lightColorScheme()
    return common.copy(
        primary = if (dark) Lime else Color(0xFF4A7A00),
        onPrimary = if (dark) Color(0xFF12160A) else Color.White,
        primaryContainer = if (dark) Color(0xFF24301A) else Color(0xFFE6F5C9),
        onPrimaryContainer = if (dark) Lime else Color(0xFF2E4D00),
        secondary = if (dark) Violet else Color(0xFF5B4BD6),
        onSecondary = if (dark) Color(0xFF17132E) else Color.White,
        secondaryContainer = if (dark) Color(0xFF262046) else Color(0xFFE7E3FF),
        onSecondaryContainer = if (dark) Violet else Color(0xFF3A2E8C),
        tertiary = if (dark) Amber else Color(0xFF8A5600),
        onTertiary = Color(0xFF2A1A00),
        tertiaryContainer = if (dark) Color(0xFF33260F) else Color(0xFFFFEBC7),
        onTertiaryContainer = if (dark) Amber else Color(0xFF5A3800),
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
        outline = glass.strokeTop,
        outlineVariant = glass.strokeTop,
        scrim = Color.Black.copy(alpha = 0.6f),
        error = if (dark) Coral else Color(0xFFC2410C),
        onError = if (dark) Color(0xFF2B0D06) else Color.White,
        errorContainer = if (dark) Color(0xFF33201C) else Color(0xFFFFE4DC),
        onErrorContainer = if (dark) Coral else Color(0xFF7A2A0A),
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

private val DarkFinance = FinanceColors(
    income = Lime,
    expense = Color(0xFFF4F2FA),
    transfer = Blue,
    warning = Amber,
    over = Coral,
    streak = Amber,
    categories = listOf(
        Coral, Blue, Mint, Pink, Yellow, Orange,
        Lime, Violet, Amber, Color(0xFF5ED6E3), Color(0xFFFF9EBB), Color(0xFFD9C3A0),
    ),
)

private val LightFinance = FinanceColors(
    income = Color(0xFF4A7A00),
    expense = Color(0xFF15131D),
    transfer = Color(0xFF2F6FE0),
    warning = Color(0xFF8A5600),
    over = Color(0xFFC2410C),
    streak = Color(0xFF8A5600),
    categories = listOf(
        Color(0xFFD9542F), Color(0xFF2F6FE0), Color(0xFF0E9C6B), Color(0xFFA64BC9), Color(0xFFB58800), Color(0xFFD96A1F),
        Color(0xFF4A7A00), Color(0xFF5B4BD6), Color(0xFF8A5600), Color(0xFF0F8C99), Color(0xFFD9467A), Color(0xFF8C7350),
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
 * Handoff scale: H1 28, H2 18, titles 15–17, body 15, caption 12. Large Latin display text gets slight negative
 * tracking; Arabic is a joined script, so it keeps zero tracking and ~18% taller leading for its ascenders.
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
        displaySmall = style(Readex, 28, 34, FontWeight.SemiBold, -0.015f),
        headlineLarge = style(Readex, 24, 30, FontWeight.SemiBold, -0.01f),
        headlineMedium = style(Readex, 20, 26, FontWeight.SemiBold),
        headlineSmall = style(Readex, 18, 24, FontWeight.SemiBold),
        titleLarge = style(PlexArabic, 17, 24, FontWeight.SemiBold),
        titleMedium = style(PlexArabic, 16, 22, FontWeight.SemiBold),
        titleSmall = style(PlexArabic, 15, 20, FontWeight.SemiBold),
        bodyLarge = style(PlexArabic, 15, 22, FontWeight.Normal),
        bodyMedium = style(PlexArabic, 14, 20, FontWeight.Normal),
        bodySmall = style(PlexArabic, 12, 16, FontWeight.Normal),
        labelLarge = style(PlexArabic, 14, 18, FontWeight.SemiBold),
        labelMedium = style(PlexArabic, 12, 16, FontWeight.Medium),
        labelSmall = style(PlexArabic, 11, 14, FontWeight.Medium),
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

/** Radii from the handoff: icon tile 14, tile 18, card 24, hero and sheets 28; chips and buttons are full pills. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
fun MasroufiTheme(solidGlass: Boolean = false, content: @Composable () -> Unit) {
    // Midnight Ledger is a dark-only design (the handoff has no light theme): no system, light or dynamic variants.
    val dark = true
    val context = LocalContext.current
    val glass = DarkGlass
    val colors = scheme(dark, glass)
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
