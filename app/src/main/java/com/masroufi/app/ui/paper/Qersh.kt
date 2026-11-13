package com.masroufi.app.ui.paper

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.theme.Tz
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class QershMood { Happy, Delighted, Thinking, Surprised, Sleepy, Sad, Waving }

// Paths in the 72 grid, copied from the mocks (Main, Album, Paywall, BeforeBuy, Onboarding, EmptyStates).
private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

private val Fin = p("M30 16 Q37 -2 50 8 Q44 11 45 20 Z")
private val HappyMouth = p("M27 47 Q36 56 45 47")
private val DelightedEyes = p("M24 38 q4 -5 8 0M40 38 q4 -5 8 0")
private val DelightedMouth = p("M26 46 Q36 58 46 46 Z")
private val ThinkingMouth = p("M29 49 h14")
private val SadBrows = p("M23 33 l8 3M49 33 l-8 3")
private val SadMouth = p("M28 52 Q36 45 44 52")
private val SleepyEyes = p("M24 38 q4 4 8 0M40 38 q4 4 8 0")
private val SleepyMouth = p("M29 51 Q36 55 43 51")
private val SleepyZ = p("M60 24 h8 l-8 9 h8M64 11 h6 l-6 7 h6")
private val WaveArm = p("M61 40 Q69 34 66 26")

private val Round3 = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val Round26 = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val Outline3 = Stroke(3f)
private val RingStroke = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)))

/**
 * Qersh on the 72 grid of 13-ICON.md. Moods change only the face; changing [mood] cross-fades over 200 ms. Blinks
 * every 4-7 s (eyes squash for ~120 ms) unless motion is reduced. The dashed coin ring shows only above 64 dp (the
 * 52-64 dp Qersh in the mocks has none). [contentDescription] null = decorative.
 */
@Composable
fun Qersh(
    mood: QershMood,
    size: Dp,
    modifier: Modifier = Modifier,
    onYellow: Boolean = false,
    contentDescription: String? = null,
) {
    val blink = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(reduce) {
        if (reduce) {
            blink.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(4000L, 7001L))
            blink.animateTo(1f, tween(60))
            blink.animateTo(0f, tween(60))
        }
    }
    val sem = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    val ring = size > 64.dp
    val big = size >= 120.dp
    Crossfade(mood, modifier.size(size).then(sem), animationSpec = tween(200), label = "qershMood") { m ->
        Canvas(Modifier.size(size)) {
            scale(this.size.width / 72f, pivot = Offset.Zero) { drawQersh(m, onYellow, ring, big, blink.value) }
        }
    }
}

private fun DrawScope.drawQersh(mood: QershMood, onYellow: Boolean, ring: Boolean, highlight: Boolean, blink: Float) {
    // Everything below is in grid units; the caller scaled the canvas.
    val night = Tz.night
    val body = if (onYellow) Tz.qershOnYellow else Tz.yellow
    drawPath(Fin, Tz.turquoise)
    drawPath(Fin, night, style = Round3)
    drawCircle(body, 26f, Offset(36f, 42f))
    drawCircle(night, 26f, Offset(36f, 42f), style = Outline3)
    if (ring) {
        drawCircle(Tz.qershRing, 19f, Offset(36f, 42f), style = RingStroke)
    }
    val dotEyes = mood != QershMood.Delighted && mood != QershMood.Sleepy
    if (dotEyes) {
        val y = if (mood == QershMood.Sad) 39f else 38f
        val ry = 3.5f * (1f - 0.85f * blink)
        for (x in floatArrayOf(28f, 44f)) {
            drawOval(night, Offset(x - 3.5f, y - ry), Size(7f, ry * 2f))
            if (highlight && blink < 0.5f) drawCircle(Tz.qershOnYellow, 1f, Offset(x + 1.2f, y - 1.2f))
        }
    }
    when (mood) {
        QershMood.Happy, QershMood.Waving -> drawPath(HappyMouth, night, style = Round3)
        QershMood.Delighted -> {
            drawPath(DelightedEyes, night, style = Round3)
            drawPath(DelightedMouth, night)
        }
        QershMood.Thinking -> drawPath(ThinkingMouth, night, style = Round3)
        QershMood.Surprised -> drawCircle(night, 4f, Offset(36f, 51f))
        QershMood.Sleepy -> {
            drawPath(SleepyEyes, night, style = Round3)
            drawPath(SleepyMouth, night, style = Round3)
            drawPath(SleepyZ, Tz.text2, style = Round26)
        }
        QershMood.Sad -> {
            drawPath(SadBrows, night, style = Round26)
            drawPath(SadMouth, night, style = Round3)
        }
    }
    if (mood == QershMood.Waving) drawPath(WaveArm, night, style = Round3)
}
