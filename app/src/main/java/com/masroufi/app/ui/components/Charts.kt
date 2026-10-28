package com.masroufi.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion

data class ChartSlice(val label: String, val value: Long, val color: Color)

/** Ring chart; the sweep grows in on first show and morphs when the data changes. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    description: String,
    center: @Composable () -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, Motion.effects(reduce)) }
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            var start = -90f
            val gap = if (slices.size > 1) 2f else 0f
            val single = slices.count { it.value > 0 && it.color != Color.Transparent } == 1
            val cap = if (single) StrokeCap.Round else StrokeCap.Butt
            slices.forEach { slice ->
                val sweep = 360f * slice.value / total * reveal.value
                if (sweep > gap) {
                    // A wide, faint arc underneath reads as the ring glowing.
                    drawArc(slice.color.copy(alpha = slice.color.alpha * 0.18f), start + gap / 2, sweep - gap, false, topLeft, arcSize, style = Stroke(stroke * 1.9f, cap = cap))
                    drawArc(slice.color, start + gap / 2, sweep - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
                }
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun ChartLegend(slices: List<ChartSlice>, valueText: (ChartSlice) -> String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(valueText(slice), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AnimatedProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.spatial(reduce), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))),
        )
    }
}

data class BarGroup(val label: String, val values: List<Long>)

/** Grouped vertical bars, e.g. income vs expense per month. Bars grow with a spring when the data changes. */
@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    colors: List<Color>,
    description: String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 160.dp,
) {
    val reduce = LocalReduceMotion.current
    val max = groups.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        groups.forEach { group ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.height(chartHeight), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    group.values.forEachIndexed { index, value ->
                        val target = value.toFloat() / max
                        val animated by animateFloatAsState(target, Motion.spatial(reduce), label = "bar")
                        Box(
                            Modifier
                                .width(10.dp)
                                .fillMaxHeight(animated.coerceAtLeast(0.01f))
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(colors[index % colors.size].let { c -> Brush.verticalGradient(listOf(c, c.copy(alpha = 0.45f))) }),
                        )
                    }
                }
                Text(group.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
