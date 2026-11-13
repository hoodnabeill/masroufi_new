package com.masroufi.app.ui.motion

import androidx.compose.animation.core.SpringSpec
import kotlin.math.PI

/** Named springs from 05-MOTION.md. Damping 1 = no overshoot; response = seconds to arrive. */
enum class TzSpring(val damping: Float, val response: Float) {
    Press(1f, .15f),
    Snappy(1f, .24f),
    Move(1f, .38f),
    Sheet(.86f, .32f),
    Reward(.62f, .45f);

    val stiffness: Float get() = ((2 * PI / response) * (2 * PI / response)).toFloat()
}

fun <T> TzSpring.spec(): SpringSpec<T> = SpringSpec(dampingRatio = damping, stiffness = stiffness)
