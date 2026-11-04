package com.masroufi.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import com.masroufi.app.ui.theme.LocalSolidGlass
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

/**
 * The stage behind every screen: Midnight Ledger's flat ink. The moving aurora is gone (the handoff keeps light and
 * blur for floating chrome only), which also means nothing redraws while the app is idle. [layout] is kept so call
 * sites stay unchanged.
 */
@Composable
fun AuroraBackground(@Suppress("UNUSED_PARAMETER") layout: Int, modifier: Modifier = Modifier) {
    val base = LocalGlass.current.base
    Canvas(modifier.fillMaxSize()) { drawRect(base) }
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
 * Surface material. By default a solid card (or raised tile when [strong]) with a 1 px line, per the handoff.
 * [floating] chrome (dock, quick-add, snackbars) gets real glass on Android 12+: it blurs and saturates the live
 * content beneath, bends it at the rim (Android 13+) and catches a specular highlight.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color = Color.Unspecified,
    strong: Boolean = false,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    floating: Boolean = false,
): Modifier = composed {
    val glass = LocalGlass.current
    val front = if (onDrawFront != null) Modifier.drawWithContent { drawContent(); onDrawFront() } else Modifier
    val backdrop = LocalGlassBackdrop.current
    val live = floating && !LocalSolidGlass.current && backdrop != null &&
        LocalBackdropView.current === LocalView.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (!live || backdrop == null) {
        // Midnight Ledger: content surfaces are solid. A card is `surface`, a raised tile `raised`, both with a 1 px
        // line; a tint is laid on top as a wash so tinted cards stay legible.
        val panel = if (strong) glass.fillStrong else glass.fill
        return@composed this
            .clip(shape)
            .background(panel)
            .then(if (tint.isSpecified()) Modifier.background(tint.copy(alpha = (tint.alpha * 0.7f).coerceAtMost(0.9f))) else Modifier)
            .then(front)
    }
    // Floating chrome only (dock, quick-add, snackbars): real glass over the live content beneath.
    val surface = if (tint.isSpecified()) tint.copy(alpha = (tint.alpha * 0.85f).coerceAtMost(0.92f)) else glass.fill.copy(alpha = if (strong) 0.78f else 0.62f)
    val dark = glass.dark
    val shade = glass.shadow.copy(alpha = glass.shadow.alpha * 0.7f)
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(if (strong) 10.dp.toPx() else 6.dp.toPx())
            // The lens shader needs corner radii; every app shape is corner-based, anything else just skips it.
            if (shape is CornerBasedShape) lens(refractionHeight = 14.dp.toPx(), refractionAmount = 22.dp.toPx(), chromaticAberration = true)
        },
        highlight = { Highlight(width = 0.75.dp, style = HighlightStyle.Default(intensity = if (dark) 0.45f else 0.8f)) },
        shadow = if (dark) null else ({ Shadow(radius = 22.dp, color = shade) }),
        onDrawSurface = { drawRect(surface) },
        onDrawFront = onDrawFront,
    )
}

/**
 * A recessed tile for controls that sit on a card (segmented tracks, pills, inner stats): the raised layer, or a
 * 12% tint of [tint], with the hairline.
 */
fun Modifier.glassInset(shape: Shape, tint: Color = Color.Unspecified): Modifier = composed {
    val glass = LocalGlass.current
    this
        .clip(shape)
        .background(if (tint.isSpecified()) tint.copy(alpha = 0.12f) else glass.fillStrong)
}

/**
 * Scroll-edge effect: content dissolves into the light where it meets floating chrome instead of being cut by a hard
 * line. Lists keep at least [top] of content padding, so nothing is faded while scrolled to the top.
 */
fun Modifier.edgeFade(top: Dp = 16.dp, bottom: Dp = 0.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val t = top.toPx()
        val b = bottom.toPx()
        if (t > 0f) {
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = t), size = Size(size.width, t), blendMode = BlendMode.DstIn)
        }
        if (b > 0f) {
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - b, endY = size.height),
                topLeft = Offset(0f, size.height - b),
                size = Size(size.width, b),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * Frosts the app behind a dialog or sheet window (Android 12+, when the device allows cross-window blur). Dialogs are
 * separate windows, so in-app glass cannot reach them; the system compositor can.
 */
@Composable
fun BlurBehindWindow(radius: Dp = 28.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val px = with(LocalDensity.current) { radius.roundToPx() }
    SideEffect {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply { blurBehindRadius = px }
    }
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

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

/**
 * A tap target with the app's press: [liquidPress] at [scaleTo] (1 = bloom only, for rows inside a card) over a
 * ripple-free clickable. Put it first in the chain so the scale and bloom cover the background drawn after it.
 */
fun Modifier.pressClick(
    shape: Shape,
    scaleTo: Float = 0.97f,
    role: Role = Role.Button,
    label: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    liquidPress(interaction, shape, scaleTo).clickable(interactionSource = interaction, indication = null, role = role, onClickLabel = label, onClick = onClick)
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
