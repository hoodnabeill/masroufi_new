package com.masroufi.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** From here on the user's font size is large enough that a row of side-by-side tiles must stack (Phase 12, 200% audit). */
const val LARGE_FONT_SCALE = 1.3f

@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale > LARGE_FONT_SCALE

/**
 * Text inside grows with the system font size only up to [max] (default: not at all). For fixed-size art that cannot grow:
 * the printed proverb card, the seven-day strip, a round avatar. TalkBack reads the same words either way.
 * shortcut: those few places stay small for low-vision users; revisit with layouts that can grow.
 */
@Composable
fun CappedFontScale(max: Float = 1f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, max))) { content() }
}
