package com.masroufi.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.theme.Tz

/** Same shape as the content, no spinner (EmptyStates.dc.html): pulses between 60% and 100% unless motion is reduced. */
@Composable
fun Modifier.skeletonPulse(): Modifier {
    if (LocalReduceMotion.current) return this
    val pulse = rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.6f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeletonAlpha",
    )
    return graphicsLayer { alpha = pulse.value }
}

/** A grey placeholder block. */
@Composable
fun SkeletonBlock(modifier: Modifier, color: Color = Tz.raised, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).background(color))
}

/** A list row on hold: round icon, two text lines (widths as fractions) and a trailing number of width [end]. */
@Composable
fun RowSkeleton(top: Float = 0.6f, bottom: Float = 0.36f, end: Dp = 44.dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(Modifier.size(40.dp), shape = CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(top).height(12.dp), Tz.line, RoundedCornerShape(6.dp))
            SkeletonBlock(Modifier.fillMaxWidth(bottom).height(10.dp), shape = RoundedCornerShape(5.dp))
        }
        SkeletonBlock(Modifier.size(width = end, height = 16.dp), Tz.line)
    }
}
