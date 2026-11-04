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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
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

/** A bottom-bar destination: its label and which [TabGlyph] draws its icon. */
data class DockTab(val label: Int, val glyph: Int)

private val BarHeight = 72.dp
private val PlusSize = 60.dp

/** How far the + rises above the bar: a light lift, so it reads as part of the dock rather than floating off it. */
private val PlusLift = 14.dp

/**
 * The bar is the one place content scrolls under, so it is real glass when the device supports it: the live screen
 * blurred beneath a near-solid surface tint (handoff: surface #15131D, line, radius 28). Elsewhere it is solid.
 */
@Composable
private fun Modifier.dockGlass(): Modifier {
    val glass = LocalGlass.current
    val backdrop = LocalGlassBackdrop.current
    val live = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalSolidGlass.current
    val shape = MaterialTheme.shapes.extraLarge
    val tint = glass.fill.copy(alpha = if (live) 0.86f else 1f)
    if (!live || backdrop == null) {
        return this
            .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(tint)
            .border(1.dp, glass.rim, shape)
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(16.dp.toPx())
        },
        highlight = null,
        shadow = { Shadow(radius = 32.dp, color = Color.Black.copy(alpha = if (glass.dark) 0.55f else 0.14f)) },
        onDrawSurface = { drawRect(tint) },
    ).border(1.dp, glass.rim, shape)
}

/**
 * Bottom bar: الرئيسية · الحركة · [+] · الخطة · المزيد. The + is a raised lime circle lifted just above the bar;
 * the current tab is in the primary text colour with a small accent dot under its label.
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
    Box(
        modifier
            .widthIn(max = DockMaxWidth)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .height(BarHeight + PlusLift),
    ) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BarHeight)
                .dockGlass()
                .selectableGroup()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val middle = tabs.size / 2
            tabs.forEachIndexed { index, tab ->
                if (index == middle) Spacer(Modifier.width(PlusSize + 12.dp))
                DockItem(tab, index == selected, Modifier.weight(1f)) {
                    if (index != selected) haptics.tick()
                    onSelect(index)
                }
            }
        }
        PlusButton(Modifier.align(Alignment.TopCenter)) {
            haptics.confirm()
            onAdd()
        }
    }
}

@Composable
private fun DockItem(tab: DockTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        val target = if (selected) 1f else 0f
        if (reduce) progress.snapTo(target) else progress.animateTo(target, spring(dampingRatio = if (selected) 0.5f else 0.9f, stiffness = 320f))
    }
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else LocalGlass.current.textTertiary,
        Motion.effects(reduce),
        label = "tabColor",
    )
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TabGlyph(kind = tab.glyph, progress = { progress.value }, color = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        // Labels shrink (down to 8sp) rather than clip when a translation or font scale makes them longer than the slot.
        BasicText(
            stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = { color },
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(4.dp)
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1.3f)
                    scaleX = p
                    scaleY = p
                    alpha = progress.value.coerceIn(0f, 1f)
                }
                .clip(CircleShape)
                .background(accent),
        )
    }
}

@Composable
private fun PlusButton(modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(R.string.transaction_new)
    val lime = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(PlusSize)
            .liquidPress(interaction, CircleShape, scaleTo = 1.1f)
            .shadow(18.dp, CircleShape, ambientColor = lime, spotColor = lime)
            .clip(CircleShape)
            .background(lime)
            .border(4.dp, LocalGlass.current.base, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
    }
}
