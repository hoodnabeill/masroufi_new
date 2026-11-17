package com.masroufi.app.ui.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.spec
import kotlinx.coroutines.launch

/** Slide's steps: 40 dp in three, a Tick each (05-MOTION "A new ticket"). */
private const val SLIDE_DP = 40f
private const val SLIDE_STEPS = 3

/** Home's step length (about 240 ms in all); the reveal's slow version uses [SLOW_STEP_MS] (700 ms in all). */
const val FAST_STEP_MS = 80
const val SLOW_STEP_MS = 233

/**
 * The new-ticket moment: the ticket feeds out from under its header in three steps of [stepMs] (a Tick each), then
 * Qersh pops over the corner with Reward. Reduced motion: no movement, one Tick. [onShown] runs once the steps are done.
 * [content] applies `slide` to the ticket (graphicsLayer) and passes `qershScale` to [Ticket].
 */
@Composable
fun PrintIn(
    play: Boolean,
    onShown: () -> Unit = {},
    stepMs: Int = FAST_STEP_MS,
    content: @Composable (slide: GraphicsLayerScope.() -> Unit, qershScale: () -> Float) -> Unit,
) {
    val haptics = LocalHaptics.current
    val reduce = LocalReduceMotion.current
    val moves = play && !reduce
    // Remembered once: the key is written when the steps end, which flips [play] off mid-composition.
    val animate = remember { moves }
    val up = remember { Animatable(if (animate) SLIDE_DP else 0f) }
    val pop = remember { Animatable(if (animate) 0f else 1f) }
    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(Unit) {
        if (!play) return@LaunchedEffect
        if (!animate) {
            haptics.perform(HapticLevel.Tick)
        } else {
            for (i in 1..SLIDE_STEPS) {
                haptics.perform(HapticLevel.Tick)
                up.animateTo(SLIDE_DP * (SLIDE_STEPS - i) / SLIDE_STEPS, tween(stepMs, easing = LinearEasing))
            }
            launch { pop.animateTo(1f, TzSpring.Reward.spec()) }
        }
        shown()
    }
    content({
        translationY = -up.value.dp.toPx()
        alpha = 1f - up.value / SLIDE_DP
    }) { pop.value }
}
