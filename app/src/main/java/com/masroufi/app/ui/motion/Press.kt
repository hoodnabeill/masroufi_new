package com.masroufi.app.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Press scales (05-MOTION.md). */
object PressScale {
    const val Primary = 0.97f
    const val Card = 0.98f
    const val Chip = 0.94f
    const val TabIcon = 0.9f
    const val Plus = 0.92f
}

/** Dips to [pressed] on touch-down with Press; goes back to 1 with [release]. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float, release: TzSpring = TzSpring.Press): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = motionSpec(if (isPressed) TzSpring.Press else release),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
