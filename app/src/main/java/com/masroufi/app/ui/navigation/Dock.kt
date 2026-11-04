package com.masroufi.app.ui.navigation

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.masroufi.app.R
import com.masroufi.app.feature.transactions.projectMomentum
import com.masroufi.app.feature.transactions.rubberBand
import com.masroufi.app.ui.components.LocalGlassBackdrop
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.TabGlyph
import com.masroufi.app.ui.components.liquidPress
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.LocalSolidGlass
import com.masroufi.app.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Space tab screens leave at the bottom so their last item can scroll clear of the floating bar. */
val DockClearance = 108.dp

/** On tablets and landscape the bar stays phone-sized and centred instead of stretching edge to edge. */
val DockMaxWidth = 560.dp

/** A bottom-bar destination: its label and which [TabGlyph] draws its icon. */
data class DockTab(val label: Int, val glyph: Int)

private val BarHeight = 64.dp

/**
 * Liquid Glass for floating chrome: the live screen blurred and bent at the rim beneath a light tint, with a
 * specular edge and a soft drop shadow (Android 12+). Elsewhere, or with "Solid surfaces", an opaque pane.
 */
@Composable
private fun Modifier.liquidGlass(shape: Shape, tint: Color): Modifier {
    val glass = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val live = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalSolidGlass.current
    if (!live || backdrop == null) {
        return this
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(tint.copy(alpha = 1f))
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            lens(refractionHeight = 16.dp.toPx(), refractionAmount = 24.dp.toPx(), chromaticAberration = true)
        },
        highlight = { Highlight(width = 0.75.dp, style = HighlightStyle.Default(intensity = if (glass.dark) 0.5f else 0.85f)) },
        shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = if (glass.dark) 0.45f else 0.12f)) },
        onDrawSurface = { drawRect(tint) },
    )
}

/**
 * The iOS 26 tab bar: a floating glass capsule (الرئيسية · الحركة · الخطة · المزيد) and, beside it, a round
 * glass + for a new entry. The selected tab sits under a lens that glides between tabs; the lens can also be
 * grabbed and dragged along the bar, and a throw lands where its momentum projects.
 */
@Composable
fun Dock(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier
            .widthIn(max = DockMaxWidth)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .height(BarHeight),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabCapsule(tabs, selected, Modifier.weight(1f).fillMaxHeight()) { index ->
            if (index != selected) haptics.tick()
            onSelect(index)
        }
        AddOrb {
            haptics.confirm()
            onAdd()
        }
    }
}

@Composable
private fun TabCapsule(tabs: List<DockTab>, selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    val glass = LocalGlass.current
    val reduce = LocalReduceMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val currentSelect by rememberUpdatedState(onSelect)
    // The lens position in tab units; it follows the selection unless the finger is holding it.
    val lens = remember { Animatable(selected.toFloat()) }
    var holding by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (holding && !reduce) 1f else 0f, Motion.snappy(reduce), label = "lensLift")
    LaunchedEffect(selected) {
        if (!holding) {
            if (reduce) lens.snapTo(selected.toFloat()) else lens.animateTo(selected.toFloat(), Motion.spatial(false))
        }
    }
    BoxWithConstraints(modifier.liquidGlass(CircleShape, glass.fill.copy(alpha = if (glass.dark) 0.62f else 0.7f))) {
        val density = LocalDensity.current
        val slot = with(density) { maxWidth.toPx() } / tabs.size
        val last = (tabs.size - 1).toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(tabs.size, slot, rtl) {
                    val tracker = VelocityTracker()
                    var raw = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            holding = true
                            tracker.resetTracking()
                            raw = lens.value
                            scope.launch { lens.stop() }
                        },
                        onHorizontalDrag = { change, delta ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            raw += (if (rtl) -delta else delta) / slot
                            // 1:1 inside the bar, progressive resistance past either end.
                            val shown = when {
                                raw < 0f -> -rubberBand(-raw * slot, size.width.toFloat()) / slot
                                raw > last -> last + rubberBand((raw - last) * slot, size.width.toFloat()) / slot
                                else -> raw
                            }
                            scope.launch { lens.snapTo(shown) }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x.let { if (rtl) -it else it } / slot
                            val target = (lens.value + projectMomentum(v * slot) / slot).roundToInt().coerceIn(0, tabs.size - 1)
                            holding = false
                            scope.launch {
                                if (reduce) lens.snapTo(target.toFloat())
                                else lens.animateTo(target.toFloat(), spring(dampingRatio = 0.78f, stiffness = 380f), initialVelocity = v)
                            }
                            currentSelect(target)
                        },
                        onDragCancel = {
                            holding = false
                            scope.launch { lens.animateTo(selected.toFloat(), Motion.spatial(reduce)) }
                        },
                    )
                },
        ) {
        // The lens: a brighter pill of glass under the selected tab; it swells a little while held.
        val inset = 4.dp
        Box(
            Modifier
                .offset {
                    val x = (lens.value * slot).roundToInt()
                    IntOffset(if (rtl) ((slot * tabs.size).roundToInt() - slot.roundToInt() - x) else x, 0)
                }
                .width(with(density) { slot.toDp() })
                .fillMaxHeight()
                .padding(inset)
                .graphicsLayer {
                    val s = 1f + 0.08f * lift
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (glass.dark) 0.14f + 0.06f * lift else 0.07f + 0.04f * lift)),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            tabs.forEachIndexed { index, tab ->
                // Colour follows the lens, so dragging across tabs lights each one as the lens passes over it.
                val near = (1f - kotlin.math.abs(lens.value - index)).coerceIn(0f, 1f)
                TabItem(tab, index == selected, near, Modifier.weight(1f)) { onSelect(index) }
            }
        }
        }
    }
}

@Composable
private fun TabItem(tab: DockTab, selected: Boolean, near: Float, modifier: Modifier, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        val target = if (selected) 1f else 0f
        if (reduce) progress.snapTo(target) else progress.animateTo(target, spring(dampingRatio = if (selected) 0.6f else 1f, stiffness = 420f))
    }
    val tint = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurface
    val color by animateColorAsState(
        androidx.compose.ui.graphics.lerp(rest, tint, near),
        if (reduce) androidx.compose.animation.core.snap() else spring(stiffness = 1_400f),
        label = "tabColor",
    )
    Column(
        modifier
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TabGlyph(kind = tab.glyph, progress = { progress.value }, color = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        // Labels shrink (down to 8sp) rather than clip when a translation or font scale makes them longer than the slot.
        BasicText(
            stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = { color },
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** The round glass + beside the bar, in the app tint; it swells and glows under the finger like Liquid Glass. */
@Composable
private fun AddOrb(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(R.string.transaction_new)
    val tint = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(BarHeight)
            .liquidPress(interaction, CircleShape, scaleTo = 1.1f)
            .liquidGlass(CircleShape, tint.copy(alpha = 0.92f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
    }
}
