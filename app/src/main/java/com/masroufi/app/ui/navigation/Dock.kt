package com.masroufi.app.ui.navigation

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.masroufi.app.R
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LocalGlassBackdrop
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.TabGlyph
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.components.liquidPress
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.LocalSolidGlass
import com.masroufi.app.ui.theme.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Space tab screens leave at the bottom so their last item can scroll clear of the floating dock. */
val DockClearance = 132.dp

/** On tablets and landscape the dock stays phone-sized and centred instead of stretching edge to edge. */
val DockMaxWidth = 560.dp

data class DockTab(val label: Int)

/**
 * The dock is the thickest glass in the app: it blurs and saturates the live screen beneath it and bends it at the
 * rim with a chromatic lens. Without live glass (older Android) it is a near-opaque frosted capsule.
 */
@Composable
private fun Modifier.dockGlass(): Modifier {
    val glass = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val live = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalSolidGlass.current
    val tint = when {
        glass.dark -> glass.base.copy(alpha = if (live) 0.4f else 0.94f)
        else -> Color.White.copy(alpha = if (live) 0.46f else 0.94f)
    }
    if (!live || backdrop == null) {
        return this
            .clip(CircleShape)
            .background(tint)
            .border(1.dp, Brush.verticalGradient(listOf(glass.strokeTop, glass.strokeBottom)), CircleShape)
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            vibrancy()
            blur(10.dp.toPx())
            lens(refractionHeight = 22.dp.toPx(), refractionAmount = 30.dp.toPx(), chromaticAberration = true)
        },
        highlight = { Highlight(width = 1.dp, style = HighlightStyle.Default(intensity = if (glass.dark) 0.5f else 0.8f)) },
        shadow = { Shadow(radius = 28.dp, color = Color.Black.copy(alpha = if (glass.dark) 0.5f else 0.14f)) },
        onDrawSurface = { drawRect(tint) },
    )
}

/** Moves the two edges of the liquid pill: the leading edge is quick, the trailing one lags, so the pill stretches. */
private suspend fun glide(lead: Animatable<Float, AnimationVector1D>, trail: Animatable<Float, AnimationVector1D>, to: Float, reduce: Boolean) =
    coroutineScope {
        if (reduce) {
            lead.snapTo(to)
            trail.snapTo(to)
        } else {
            launch { lead.animateTo(to, spring(dampingRatio = 0.72f, stiffness = 700f)) }
            launch { trail.animateTo(to, spring(dampingRatio = 0.9f, stiffness = 220f)) }
        }
    }

/**
 * Floating liquid-glass tab bar. Tap a tab, or press and slide along the dock: the selection drop follows the finger
 * 1:1, swells while held, ticks a haptic as it crosses each tab, and on release is thrown (velocity projection) to
 * the tab it was heading for.
 */
@Composable
fun Dock(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lead = remember { Animatable(selected.toFloat()) }
    val trail = remember { Animatable(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (dragging && !reduce) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dockLift")
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(selected) { if (!dragging) glide(lead, trail, selected.toFloat(), reduce) }
    val last = tabs.lastIndex

    Row(
        modifier.widthIn(max = DockMaxWidth).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(66.dp)
                .dockGlass()
                .pointerInput(tabs.size, rtl) {
                    val inset = 5.dp.toPx()
                    val tracker = VelocityTracker()
                    var position = 0f
                    var hovered = 0
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            tracker.resetTracking()
                            position = lead.value
                            hovered = position.roundToInt()
                        },
                        onDragEnd = {
                            dragging = false
                            val slot = (size.width - inset * 2) / tabs.size
                            val velocity = tracker.calculateVelocity().x / slot * (if (rtl) -1f else 1f)
                            // Project where the flick was heading (≈ Apple's deceleration projection), then snap.
                            val target = (position + velocity * 0.12f).roundToInt().coerceIn(0, last)
                            scope.launch { glide(lead, trail, target.toFloat(), reduce) }
                            if (target != currentSelected) currentOnSelect(target) else haptics.tick()
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { glide(lead, trail, currentSelected.toFloat(), reduce) }
                        },
                    ) { change, dx ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val slot = (size.width - inset * 2) / tabs.size
                        position = (position + (if (rtl) -dx else dx) / slot).coerceIn(-0.3f, last + 0.3f)
                        val to = position
                        scope.launch { lead.snapTo(to) }
                        scope.launch { trail.animateTo(to, spring(dampingRatio = 1f, stiffness = 600f)) }
                        val index = to.roundToInt().coerceIn(0, last)
                        if (index != hovered) {
                            hovered = index
                            haptics.tick()
                        }
                    }
                }
                .selectableGroup()
                .padding(5.dp),
        ) {
            val slotPx = constraints.maxWidth.toFloat() / tabs.size
            val primary = MaterialTheme.colorScheme.primary
            val dark = LocalGlass.current.dark
            // The selection drop. Read in layout and draw only, so its motion never recomposes the dock.
            Box(
                Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val start = minOf(lead.value, trail.value)
                        val span = 1f + abs(lead.value - trail.value)
                        val width = (slotPx * span).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative((slotPx * start).roundToInt(), 0) }
                    }
                    .graphicsLayer {
                        val s = 1f + 0.1f * lift
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(primary.copy(alpha = if (dark) 0.30f else 0.22f), primary.copy(alpha = if (dark) 0.16f else 0.12f)),
                        ),
                    )
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.28f else 0.8f), Color.Transparent)), CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    DockItem(tab, index, index == selected, Modifier.weight(1f)) {
                        if (index != selected) haptics.tick()
                        onSelect(index)
                    }
                }
            }
        }
        PlusButton(menuOpen, onClick = {
            haptics.confirm()
            onToggleMenu()
        })
    }
}

@Composable
private fun DockItem(tab: DockTab, index: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        val target = if (selected) 1f else 0f
        if (reduce) progress.snapTo(target) else progress.animateTo(target, spring(dampingRatio = if (selected) 0.5f else 0.9f, stiffness = 320f))
    }
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        Motion.effects(reduce),
        label = "tabColor",
    )
    Column(
        modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TabGlyph(
            kind = index,
            progress = { progress.value },
            color = color,
            modifier = Modifier.graphicsLayer {
                val p = progress.value
                translationY = -2.dp.toPx() * p
            },
        )
        // Labels shrink (down to 8sp) rather than clip when a translation or font scale makes them longer than the slot.
        BasicText(
            stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
            color = { color },
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun PlusButton(open: Boolean, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val rotation by animateFloatAsState(if (open) 135f else 0f, Motion.bouncy(reduce), label = "plusRotate")
    val description = stringResource(R.string.transaction_new)
    Box(
        Modifier
            .size(66.dp)
            .liquidPress(interaction, CircleShape, scaleTo = 1.12f)
            .glass(CircleShape, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f), strong = true)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp).graphicsLayer { rotationZ = rotation })
    }
}

/**
 * Quick-add bloom: three glass drops grow out of the + button (their transform origin is the button's corner) with a
 * staggered bounce, over a dim that closes the menu when tapped. The drops refract the undimmed scene.
 */
@Composable
fun QuickAddMenu(open: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val reduce = LocalReduceMotion.current
    val finance = LocalFinanceColors.current
    AnimatedVisibility(open, enter = fadeIn(Motion.effects(reduce)), exit = fadeOut(Motion.snappy(reduce))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    val items = listOf(
        Triple("EXPENSE", R.string.type_expense, MIcons.Expense) to finance.expense,
        Triple("INCOME", R.string.type_income, MIcons.Income) to finance.income,
        Triple("TRANSFER", R.string.type_transfer, MIcons.Transfer) to finance.transfer,
    )
    // Anchored to the + button: the column lives in the same centred, width-capped frame as the dock.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = DockMaxWidth).fillMaxWidth().navigationBarsPadding().padding(end = 16.dp, bottom = 92.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEachIndexed { index, (item, color) ->
                val (type, label, icon) = item
                val order = items.size - 1 - index
                val interaction = remember { MutableInteractionSource() }
                AnimatedVisibility(
                    open,
                    enter = scaleIn(
                        if (reduce) Motion.snappy<Float>(true) else spring(dampingRatio = 0.6f, stiffness = 420f - order * 70f),
                        initialScale = 0.15f,
                        transformOrigin = TransformOrigin(1f, 1f),
                    ) + fadeIn(Motion.snappy(reduce)),
                    exit = scaleOut(Motion.snappy(reduce), targetScale = 0.4f, transformOrigin = TransformOrigin(1f, 1f)) + fadeOut(Motion.snappy(reduce)),
                ) {
                    Row(
                        Modifier
                            .liquidPress(interaction, CircleShape, scaleTo = 1.06f)
                            .glass(CircleShape, strong = true)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onPick(type) }
                            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(12.dp))
                        IconBadge(icon, color, size = 42.dp)
                    }
                }
            }
        }
    }
}
