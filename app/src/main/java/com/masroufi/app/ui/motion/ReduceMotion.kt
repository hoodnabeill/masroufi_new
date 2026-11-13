package com.masroufi.app.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable

/** True when the system "Remove animations" setting is on. */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** The spring, or a 120 ms fade-length tween when motion is reduced. */
@Composable
@ReadOnlyComposable
fun <T> motionSpec(spring: TzSpring): AnimationSpec<T> =
    if (LocalReduceMotion.current) tween(120) else spring.spec()
