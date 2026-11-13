package com.masroufi.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.theme.Tz
import kotlin.math.abs
import kotlin.math.floor

/**
 * A number whose digits roll. Lalezar has proportional digits and no tnum, so every digit sits in a fixed cell as
 * wide as the widest digit and changing values never shift the layout. [valuePiastres] shows as whole pounds
 * (floored) unless [showPiastres]. Digits run left to right even inside RTL. A digit that changes rolls over 300 ms,
 * up when the value rose and down when it fell, and carries on from where it is when retargeted. Reduced motion is
 * a 120 ms cross-fade. Read as one number by TalkBack.
 */
@Composable
fun Odometer(
    valuePiastres: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    showPiastres: Boolean = false,
) {
    val prev = remember { LongArray(1) { valuePiastres } }
    val rising = valuePiastres >= prev[0]
    SideEffect { prev[0] = valuePiastres }

    val styled = if (style.color.isSpecified) style else style.copy(color = Tz.text)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Widest digit cell, and each digit's layout, measured once per style.
    val glyphs: List<TextLayoutResult> = remember(styled, measurer) { (0..9).map { measurer.measure(it.toString(), styled) } }
    val cellW = glyphs.maxOf { it.size.width }
    val cellH = glyphs.maxOf { it.size.height }

    val negative = valuePiastres < 0
    val magnitude = abs(valuePiastres)
    val pounds = magnitude / 100
    val display = buildString {
        if (negative && (pounds > 0 || showPiastres)) append('-')
        append(withThousands(pounds))
        if (showPiastres) append('.').append((magnitude % 100).toString().padStart(2, '0'))
    }

    Box(modifier.clearAndSetSemantics { text = AnnotatedString(display) }) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row {
                // Keyed from the right so a digit keeps its cell when the number grows or shrinks.
                display.forEachIndexed { index, ch ->
                    key(display.length - 1 - index) {
                        if (ch.isDigit()) {
                            DigitCell(ch.digitToInt(), rising, glyphs, cellW, cellH)
                        } else {
                            val g = remember(ch, styled, measurer) { measurer.measure(ch.toString(), styled) }
                            Canvas(Modifier.size(with(density) { g.size.width.toDp() }, with(density) { cellH.toDp() })) {
                                drawText(g, topLeft = Offset(0f, (size.height - g.size.height) / 2f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun withThousands(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
private fun DigitCell(digit: Int, rising: Boolean, glyphs: List<TextLayoutResult>, cellW: Int, cellH: Int) {
    val density = LocalDensity.current
    val w = with(density) { cellW.toDp() }
    val h = with(density) { cellH.toDp() }
    if (LocalReduceMotion.current) {
        Crossfade(digit, animationSpec = tween(120), label = "odometerFade") { d ->
            Canvas(Modifier.size(w, h)) {
                val g = glyphs[d]
                drawText(g, topLeft = Offset((size.width - g.size.width) / 2f, 0f))
            }
        }
        return
    }
    // Continuous position: floor(p) mod 10 is the digit on show, the fraction is how far it has rolled.
    val position = remember { Animatable(digit.toFloat()) }
    LaunchedEffect(digit) {
        var target = floor(position.value / 10f) * 10f + digit
        if (rising) {
            while (target < position.value - 0.5f) target += 10f
        } else {
            while (target > position.value + 0.5f) target -= 10f
        }
        position.animateTo(target, tween(300, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(w, h).clipToBounds()) {
        val p = position.value
        val base = floor(p).toInt()
        val frac = p - base
        for (k in 0..1) {
            val g = glyphs[Math.floorMod(base + k, 10)]
            drawText(g, topLeft = Offset((size.width - g.size.width) / 2f, (k - frac) * size.height))
        }
    }
}
