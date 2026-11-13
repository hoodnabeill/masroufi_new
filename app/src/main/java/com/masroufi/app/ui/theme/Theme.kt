package com.masroufi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Only so platform widgets do not show Material purple; screens use Tz directly.
private val TzColorScheme = darkColorScheme(
    primary = Tz.yellow,
    onPrimary = Tz.yellowInk,
    secondary = Tz.turquoise,
    onSecondary = Tz.turquoiseInk,
    tertiary = Tz.lilac,
    background = Tz.night,
    onBackground = Tz.text,
    surface = Tz.surface,
    onSurface = Tz.text,
    surfaceVariant = Tz.raised,
    onSurfaceVariant = Tz.text2,
    outline = Tz.line,
    outlineVariant = Tz.dashed,
    error = Tz.carnelian,
    onError = Tz.carnelianInk,
)

private val TzTypography = Typography().let {
    it.copy(
        bodyLarge = TzType.body,
        bodyMedium = TzType.body,
        bodySmall = TzType.caption,
        labelLarge = TzType.label,
        labelMedium = TzType.label,
        labelSmall = TzType.caption,
        titleLarge = TzType.titlePushed,
        headlineMedium = TzType.titleTab,
    )
}

@Composable
fun MasroufiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TzColorScheme, typography = TzTypography, content = content)
}
