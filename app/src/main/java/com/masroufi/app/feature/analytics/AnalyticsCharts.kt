package com.masroufi.app.feature.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.ChartSlice
import com.masroufi.app.ui.components.FitAmount
import com.masroufi.app.ui.components.glassInset
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalReduceMotion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Income and spending as two smooth areas that draw themselves in, left to right. Touch or drag across the chart to
 * scrub months: a guide line snaps to the nearest month with a tick, and the readout above rolls to its values.
 * Time always runs left to right, in Arabic too, like the numbers.
 */
@Composable
fun CashFlowChart(
    labels: List<String>,
    income: List<Long>,
    expense: List<Long>,
    currency: String,
    incomeColor: Color,
    expenseColor: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val count = labels.size
    var selected by remember(count) { mutableIntStateOf(count - 1) }
    val draw = remember(labels) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(labels) { if (!reduce) draw.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing)) }
    val max = (income + expense).maxOrNull()?.coerceAtLeast(1) ?: 1
    val guide = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val point = MaterialTheme.colorScheme.surfaceContainer

    fun pick(x: Float, width: Int) {
        if (count == 0) return
        val index = if (count == 1) 0 else ((x / width.coerceAtLeast(1)) * (count - 1)).roundToInt().coerceIn(0, count - 1)
        if (index != selected) {
            selected = index
            haptics.tick()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
            Row(
                Modifier.fillMaxWidth().glassInset(MaterialTheme.shapes.medium).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(labels.getOrElse(selected) { "" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Dot(incomeColor)
                Spacer(Modifier.width(6.dp))
                AnimatedAmount(income.getOrElse(selected) { 0 }, currency, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(14.dp))
                Dot(expenseColor)
                Spacer(Modifier.width(6.dp))
                AnimatedAmount(expense.getOrElse(selected) { 0 }, currency, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(14.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .pointerInput(count) { detectTapGestures { pick(it.x, size.width) } }
                    .pointerInput(count) {
                        detectHorizontalDragGestures(onDragStart = { pick(it.x, size.width) }) { change, _ ->
                            change.consume()
                            pick(change.position.x, size.width)
                        }
                    },
            ) {
                if (count == 0) return@Canvas
                val top = 12.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                fun x(i: Int) = if (count == 1) size.width / 2 else size.width * i / (count - 1)
                fun y(v: Long) = bottom - (bottom - top) * v / max
                fun series(values: List<Long>): List<Offset> = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
                fun smooth(points: List<Offset>): Path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val p0 = points[i - 1]
                        val p1 = points[i]
                        val mid = (p0.x + p1.x) / 2
                        cubicTo(mid, p0.y, mid, p1.y, p1.x, p1.y)
                    }
                }
                clipRect(right = size.width * draw.value) {
                    listOf(income to incomeColor, expense to expenseColor).forEach { (values, color) ->
                        val points = series(values)
                        val line = smooth(points)
                        val area = Path().apply {
                            addPath(line)
                            lineTo(points.last().x, bottom)
                            lineTo(points.first().x, bottom)
                            close()
                        }
                        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), startY = top, endY = bottom))
                        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                val gx = x(selected)
                drawLine(guide, Offset(gx, top), Offset(gx, bottom), 1.5.dp.toPx())
                listOf(income to incomeColor, expense to expenseColor).forEach { (values, color) ->
                    val p = Offset(gx, y(values.getOrElse(selected) { 0 }))
                    drawCircle(color.copy(alpha = 0.25f), 10.dp.toPx(), p)
                    drawCircle(point, 5.dp.toPx(), p)
                    drawCircle(color, 3.5.dp.toPx(), p)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEachIndexed { i, label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/**
 * Spending ring that sweeps in, then responds to touch: tap a slice (or its legend row) and it thickens while the
 * others dim, and the centre rolls to that category's amount and share. Tap again to return to the total.
 */
@Composable
fun SpendingDonut(
    slices: List<ChartSlice>,
    currency: String,
    totalLabel: String,
    shareLabel: (Int) -> String,
    valueText: (ChartSlice) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    var selected by remember(slices) { mutableIntStateOf(-1) }
    val reveal = remember(slices) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(slices) { if (!reduce) reveal.animateTo(1f, spring(dampingRatio = 1f, stiffness = 60f)) }
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    fun toggle(index: Int) {
        haptics.tick()
        selected = if (selected == index) -1 else index
    }

    Column(modifier.fillMaxWidth().semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(slices) {
                    detectTapGestures { tap ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val distance = hypot(tap.x - c.x, tap.y - c.y)
                        if (distance < size.width * 0.28f || distance > size.width * 0.52f) return@detectTapGestures
                        // Angle measured clockwise from 12 o'clock, matching how the slices are drawn.
                        var angle = (atan2(tap.y - c.y, tap.x - c.x) * 180 / PI).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var start = 0f
                        slices.forEachIndexed { i, slice ->
                            val sweep = 360f * slice.value / total
                            if (angle >= start && angle < start + sweep) {
                                toggle(i)
                                return@detectTapGestures
                            }
                            start += sweep
                        }
                    }
                },
            ) {
                val stroke = size.minDimension * 0.13f
                val diameter = size.minDimension - stroke * 1.4f
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                val arc = Size(diameter, diameter)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                var start = -90f
                val gap = if (slices.size > 1) 2.5f else 0f
                slices.forEachIndexed { i, slice ->
                    val sweep = 360f * slice.value / total * reveal.value
                    val active = selected == -1 || selected == i
                    if (sweep > gap) {
                        val width = if (selected == i) stroke * 1.35f else stroke
                        drawArc(
                            slice.color.copy(alpha = if (active) 1f else 0.28f),
                            start + gap / 2, sweep - gap, false, topLeft, arc,
                            style = Stroke(width, cap = if (slices.size == 1) StrokeCap.Round else StrokeCap.Butt),
                        )
                    }
                    start += sweep
                }
            }
            AnimatedContent(
                targetState = selected,
                transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.9f)) togetherWith fadeOut(tween(120)) },
                label = "donutCentre",
            ) { index ->
                Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val slice = slices.getOrNull(index)
                    Text(
                        slice?.label ?: totalLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    FitAmount(slice?.value ?: total, currency, style = MaterialTheme.typography.titleLarge)
                    if (slice != null) Text(shareLabel((slice.value * 100 / total).toInt()), style = MaterialTheme.typography.labelSmall, color = slice.color)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        slices.forEachIndexed { i, slice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { toggle(i) }
                    .background(if (selected == i) slice.color.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(slice.color)
                Spacer(Modifier.width(10.dp))
                Text(slice.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                Text(valueText(slice), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
            }
        }
    }
}

/**
 * The last twelve weeks as a grid of days (weeks start on Saturday, as in Egypt), each tinted by how much was spent.
 * Columns fade in from the oldest week to this one.
 */
@Composable
fun SpendHeatmap(daily: Map<LocalDate, Long>, today: LocalDate, color: Color, description: String, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val weeks = 12
    val first = today.minusWeeks((weeks - 1).toLong()).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    val max = daily.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) reveal.animateTo(1f, tween(900)) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }) {
            val gap = 4.dp.toPx()
            val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
            val startX = (size.width - (cell * weeks + gap * (weeks - 1))) / 2
            for (w in 0 until weeks) {
                val columnAlpha = (reveal.value * weeks - w).coerceIn(0f, 1f)
                for (d in 0 until 7) {
                    val date = first.plusDays((w * 7 + d).toLong())
                    if (date.isAfter(today)) continue
                    val spent = daily[date] ?: 0L
                    val intensity = if (spent <= 0) 0f else 0.18f + 0.82f * sqrt(spent.toFloat() / max)
                    drawRoundRect(
                        if (spent <= 0) empty else color.copy(alpha = intensity),
                        topLeft = Offset(startX + w * (cell + gap), d * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.28f),
                        alpha = columnAlpha,
                    )
                }
            }
        }
    }
}
