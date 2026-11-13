package com.masroufi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.masroufi.app.ui.haptics.AndroidHaptics
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.LocalReduceMotion
import androidx.compose.ui.graphics.Color

// Only so platform widgets do not show Material purple; screens use Tz directly.
private val TzColorScheme = darkColorScheme(
    primary = Tz.yellow,
    onPrimary = Tz.night,
    primaryContainer = Tz.yellowDim,
    onPrimaryContainer = Tz.yellow,
    inversePrimary = Tz.yellowInk,
    secondary = Tz.turquoise,
    onSecondary = Tz.turquoiseInk,
    secondaryContainer = Tz.turquoiseDim,
    onSecondaryContainer = Tz.turquoiseDimText,
    tertiary = Tz.lilac,
    onTertiary = Tz.night,
    tertiaryContainer = Tz.raised,
    onTertiaryContainer = Tz.text,
    background = Tz.night,
    onBackground = Tz.text,
    surface = Tz.surface,
    onSurface = Tz.text,
    surfaceVariant = Tz.raised,
    onSurfaceVariant = Tz.text2,
    surfaceTint = Color.Transparent,
    inverseSurface = Tz.text,
    inverseOnSurface = Tz.night,
    error = Tz.carnelian,
    onError = Tz.carnelianInk,
    errorContainer = Tz.carnelianDim,
    onErrorContainer = Tz.carnelianText,
    outline = Tz.line,
    outlineVariant = Tz.dashed,
    scrim = Tz.night,
    surfaceBright = Tz.raised,
    surfaceDim = Tz.night,
    surfaceContainerLowest = Tz.surface,
    surfaceContainerLow = Tz.surface,
    surfaceContainer = Tz.surface,
    surfaceContainerHigh = Tz.raised,
    surfaceContainerHighest = Tz.raised,
)

private val TzTypography = Typography(
    headlineMedium = TzType.titleTab,
    headlineSmall = TzType.cardHeading,
    titleLarge = TzType.titlePushed,
    titleMedium = TzType.label,
    bodyLarge = TzType.body,
    bodyMedium = TzType.body,
    bodySmall = TzType.caption,
    labelLarge = TzType.label,
    labelMedium = TzType.label,
    labelSmall = TzType.caption,
)

@Composable
fun MasroufiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    // 0f animator scale means the user turned animations off; observed so toggling it updates live.
    val reduceMotion by produceState(false, context) {
        val resolver = context.contentResolver
        fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        value = read()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { value = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
    val haptics = remember(view) { AndroidHaptics(view) }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion, LocalHaptics provides haptics) {
        MaterialTheme(colorScheme = TzColorScheme, typography = TzTypography, content = content)
    }
}
