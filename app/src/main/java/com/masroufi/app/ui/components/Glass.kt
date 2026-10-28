package com.masroufi.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The haze source behind the whole app shell; floating chrome blurs whatever scrolls beneath it. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Each tab has its own light: blob positions (fractions of the screen) the aurora glides to with a spring. */
private val auroraLayouts = listOf(
    listOf(Offset(0.15f, 0.05f), Offset(0.95f, 0.18f), Offset(0.05f, 0.62f), Offset(0.85f, 0.92f)),
    listOf(Offset(0.85f, 0.02f), Offset(0.10f, 0.30f), Offset(0.95f, 0.65f), Offset(0.20f, 0.95f)),
    listOf(Offset(0.50f, 0.00f), Offset(0.00f, 0.45f), Offset(1.00f, 0.40f), Offset(0.50f, 1.00f)),
    listOf(Offset(0.05f, 0.15f), Offset(0.70f, 0.05f), Offset(0.90f, 0.75f), Offset(0.10f, 0.85f)),
    listOf(Offset(0.95f, 0.08f), Offset(0.35f, 0.20f), Offset(0.15f, 0.80f), Offset(0.80f, 0.98f)),
)

/**
 * Ambient light behind every screen. Animated values are only read inside the draw lambda, so motion costs a
 * redraw, never a recomposition. The idle drift is deliberately slow and small (one cycle ≈ 30 s).
 */
@Composable
fun AuroraBackground(layout: Int, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val reduce = LocalReduceMotion.current
    val target = auroraLayouts[layout.mod(auroraLayouts.size)]
    val blobs = remember { List(4) { i -> Animatable(auroraLayouts[0][i], Offset.VectorConverter) } }
    target.forEachIndexed { i, offset ->
        LaunchedEffect(offset) { blobs[i].animateTo(offset, Motion.effects(reduce)) }
    }
    val drift = if (reduce) {
        null
    } else {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
            label = "drift",
        )
    }
    val alpha = if (glass.dark) 0.38f else 0.62f
    Canvas(modifier.fillMaxSize()) {
        drawRect(glass.base)
        val phase = drift?.value ?: 0f
        val radius = size.maxDimension * 0.62f
        blobs.forEachIndexed { i, blob ->
            val wobble = Offset(cos(phase + i * 1.7f) * 0.035f, sin(phase * 1.3f + i) * 0.03f)
            val center = Offset((blob.value.x + wobble.x) * size.width, (blob.value.y + wobble.y) * size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glass.aurora[i].copy(alpha = alpha), glass.aurora[i].copy(alpha = alpha * 0.35f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        // A soft fade at the bottom keeps the dock and lists legible over the brightest blob.
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to glass.base.copy(alpha = 0.55f)))
    }
}

/**
 * Glass material: a translucent fill brighter at the top and a hairline edge that catches light on top and fades
 * at the bottom. Cheap by design (no blur) so long lists of cards stay smooth on ordinary phones.
 */
fun Modifier.glass(shape: Shape, tint: Color = Color.Unspecified, strong: Boolean = false): Modifier = composed {
    val glass = LocalGlass.current
    val fill = if (strong) glass.fillStrong else glass.fill
    val top = if (tint.isSpecified()) tint.compositeOverGlass(fill, 1.6f) else fill.copy(alpha = (fill.alpha * 1.5f).coerceAtMost(1f))
    val bottom = if (tint.isSpecified()) tint.compositeOverGlass(fill, 0.8f) else fill
    this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .border(1.dp, Brush.verticalGradient(listOf(glass.strokeTop, glass.strokeBottom)), shape)
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

private fun Color.compositeOverGlass(fill: Color, strength: Float): Color =
    copy(alpha = (alpha * strength).coerceIn(0f, 1f)).let { tint ->
        // Blend manually so the tint stays translucent instead of flattening the glass.
        Color(
            red = tint.red * tint.alpha + fill.red * (1 - tint.alpha),
            green = tint.green * tint.alpha + fill.green * (1 - tint.alpha),
            blue = tint.blue * tint.alpha + fill.blue * (1 - tint.alpha),
            alpha = (tint.alpha + fill.alpha * (1 - tint.alpha)).coerceAtMost(0.92f),
        )
    }

/** Content rises and settles into place the first time it appears, staggered by [index] (capped so long lists never wait). */
fun Modifier.staggerIn(index: Int): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    val lift = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index.coerceIn(0, 7) * 45L)
            progress.animateTo(1f, Motion.effects(false))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1 - p) * lift
        val s = 0.97f + 0.03f * p
        scaleX = s
        scaleY = s
    }
}
