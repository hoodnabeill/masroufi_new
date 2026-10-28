package com.masroufi.app.ui.components

import android.os.Build
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What liquid glass refracts at this point of the tree: inside screens it is the ambient light, for floating chrome
 * (dock, quick-add) it is the whole scene. Null means there is no live backdrop and glass draws a flat material.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** The window the backdrops were recorded in. Sheets and dialogs are other windows, so glass there stays flat. */
val LocalBackdropView = staticCompositionLocalOf<View?> { null }

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
    val alpha = if (glass.dark) 0.42f else 0.66f
    Canvas(modifier.fillMaxSize()) {
        drawRect(glass.base)
        val phase = drift?.value ?: 0f
        val radius = size.maxDimension * 0.6f
        blobs.forEachIndexed { i, blob ->
            val wobble = Offset(cos(phase + i * 1.7f) * 0.04f, sin(phase * 1.3f + i) * 0.035f)
            val center = Offset((blob.value.x + wobble.x) * size.width, (blob.value.y + wobble.y) * size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glass.aurora[i].copy(alpha = alpha), glass.aurora[i].copy(alpha = alpha * 0.32f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        // A small bright caustic orbits slowly; refractive glass bends it, which is what makes the glass read as glass.
        val caustic = Offset(size.width * (0.5f + 0.32f * cos(phase * 2f)), size.height * (0.3f + 0.18f * sin(phase * 3f)))
        val causticRadius = size.minDimension * 0.34f
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = if (glass.dark) 0.10f else 0.38f), Color.Transparent), caustic, causticRadius),
            causticRadius,
            caustic,
        )
        // A soft fade at the bottom keeps the dock and lists legible over the brightest blob.
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to glass.base.copy(alpha = 0.55f)))
    }
}

/**
 * The ambient light plus the backdrop that glass inside [content] refracts. Every full-screen surface that shows the
 * aurora (app shell, onboarding, lock) goes through here so its glass is live.
 */
@Composable
fun GlassStage(layout: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val ambient = rememberLayerBackdrop()
    val view = LocalView.current
    Box(modifier.fillMaxSize()) {
        AuroraBackground(layout, Modifier.layerBackdrop(ambient))
        CompositionLocalProvider(LocalGlassBackdrop provides ambient, LocalBackdropView provides view) { content() }
    }
}

/**
 * Liquid glass. On Android 12+ the surface samples what is behind it, boosts its saturation, bends it near the edges
 * like a thick lens (Android 13+) and catches a specular rim light. Elsewhere (older phones, dialogs) it is a
 * translucent fill with a light-catching hairline. [strong] adds frost for surfaces that carry text over busy content.
 */
fun Modifier.glass(shape: Shape, tint: Color = Color.Unspecified, strong: Boolean = false): Modifier = composed {
    val glass = LocalGlass.current
    val fill = if (strong) glass.fillStrong else glass.fill
    val top = if (tint.isSpecified()) tint.compositeOverGlass(fill, 1.6f) else fill.copy(alpha = (fill.alpha * 1.5f).coerceAtMost(1f))
    val bottom = if (tint.isSpecified()) tint.compositeOverGlass(fill, 0.8f) else fill
    val surface = Brush.verticalGradient(listOf(top, bottom))
    val backdrop = LocalGlassBackdrop.current
    val live = backdrop != null && LocalBackdropView.current === LocalView.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (!live || backdrop == null) {
        return@composed this
            .clip(shape)
            .background(surface)
            .border(1.dp, Brush.verticalGradient(listOf(glass.strokeTop, glass.strokeBottom)), shape)
    }
    val dark = glass.dark
    val shade = glass.shadow.copy(alpha = glass.shadow.alpha * 0.7f)
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (strong) blur(6.dp.toPx())
            // The lens shader needs corner radii; every app shape is corner-based, anything else just skips it.
            if (shape is CornerBasedShape) lens(refractionHeight = 14.dp.toPx(), refractionAmount = 22.dp.toPx(), chromaticAberration = true)
        },
        highlight = { Highlight(width = 1.dp, style = HighlightStyle.Default(intensity = if (dark) 0.45f else 0.7f)) },
        shadow = { Shadow(radius = 22.dp, color = shade) },
        onDrawSurface = { drawRect(surface) },
    )
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

/**
 * Liquid press: the surface swells to [scaleTo] on a bouncy spring and light blooms from the exact point the finger
 * landed, clipped to [shape]. Touches are only observed, never consumed, so the clickable underneath still fires.
 */
fun Modifier.liquidPress(interaction: InteractionSource, shape: Shape, scaleTo: Float = 1.06f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed && !reduce) 1f else 0f,
        animationSpec = if (pressed) spring(dampingRatio = 0.55f, stiffness = 700f) else spring(dampingRatio = 0.6f, stiffness = 260f),
        label = "liquidPress",
    )
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    this
        .pointerInput(Unit) { awaitEachGesture { touch = awaitFirstDown(requireUnconsumed = false).position } }
        .graphicsLayer {
            val s = 1f + (scaleTo - 1f) * progress
            scaleX = s
            scaleY = s
        }
        .drawWithCache {
            val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            onDrawWithContent {
                drawContent()
                val p = progress
                if (p > 0.01f) {
                    val origin = if (touch.isSpecified) touch else center
                    val radius = size.maxDimension * (0.55f + 0.45f * p)
                    clipPath(clip) {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f * p), Color.Transparent), origin, radius),
                            radius = radius,
                            center = origin,
                            blendMode = BlendMode.Plus,
                        )
                    }
                }
            }
        }
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
