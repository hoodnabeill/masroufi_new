package com.masroufi.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.masroufi.app.R
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LocalHazeState
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Space tab screens leave at the bottom so their last item can scroll clear of the floating dock. */
val DockClearance = 132.dp

data class DockTab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun Modifier.dockMaterial(): Modifier {
    val glass = LocalGlass.current
    val haze = LocalHazeState.current ?: return glass(CircleShape, strong = true)
    val tint = if (glass.dark) Color(0xFF0B0F14).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.62f)
    return this
        .clip(CircleShape)
        .hazeEffect(
            haze,
            HazeStyle(
                backgroundColor = glass.base,
                tint = HazeTint(tint),
                blurRadius = 30.dp,
                noiseFactor = 0.06f,
                fallbackTint = HazeTint(if (glass.dark) Color(0xF2111519) else Color(0xF5FBFCFE)),
            ),
        )
        .border(1.dp, Brush.verticalGradient(listOf(glass.strokeTop, glass.strokeBottom)), CircleShape)
}

/**
 * Floating glass tab bar. The selection pill is "liquid": its leading edge follows a snappy spring and its trailing
 * edge a softer one, so it stretches toward the destination and settles back into a pill.
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
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).height(64.dp).dockMaterial().selectableGroup().padding(5.dp)) {
            val slot = maxWidth / tabs.size
            val lead = remember { Animatable(selected.toFloat()) }
            val trail = remember { Animatable(selected.toFloat()) }
            LaunchedEffect(selected) {
                if (reduce) {
                    lead.snapTo(selected.toFloat()); trail.snapTo(selected.toFloat())
                } else {
                    kotlinx.coroutines.coroutineScope {
                        launch { lead.animateTo(selected.toFloat(), spring(dampingRatio = 0.86f, stiffness = 900f)) }
                        launch { trail.animateTo(selected.toFloat(), spring(dampingRatio = 1f, stiffness = 260f)) }
                    }
                }
            }
            val start = minOf(lead.value, trail.value)
            val span = 1f + kotlin.math.abs(lead.value - trail.value)
            Box(
                Modifier
                    .offset { IntOffset((slot * start).roundToPx(), 0) }
                    .width(slot * span)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (LocalGlass.current.dark) 0.20f else 0.16f)),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    DockItem(tab, index == selected, Modifier.weight(1f)) {
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
private fun DockItem(tab: DockTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val pop by animateFloatAsState(if (selected) 1f else 0f, Motion.bouncy(reduce), label = "tabPop")
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) tab.selectedIcon else tab.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp).graphicsLayer {
                val s = 1f + 0.12f * pop
                scaleX = s
                scaleY = s
                translationY = -2.dp.toPx() * pop
            },
        )
        Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun PlusButton(open: Boolean, onClick: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rotation by animateFloatAsState(if (open) 135f else 0f, Motion.bouncy(reduce), label = "plusRotate")
    val scale by animateFloatAsState(if (pressed && !reduce) 0.88f else 1f, Motion.snappy(reduce), label = "plusPress")
    val primary = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.transaction_new)
    Box(
        Modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.72f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)), CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp).graphicsLayer { rotationZ = rotation })
    }
}

/**
 * Quick-add bloom: three bubbles grow out of the + button (their transform origin is the button's corner) with a
 * staggered bounce, over a dim that closes the menu when tapped.
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
        Triple("EXPENSE", R.string.type_expense, Icons.Outlined.ArrowUpward) to finance.expense,
        Triple("INCOME", R.string.type_income, Icons.Outlined.ArrowDownward) to finance.income,
        Triple("TRANSFER", R.string.type_transfer, Icons.Outlined.SwapHoriz) to finance.transfer,
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.navigationBarsPadding().padding(end = 16.dp, bottom = 90.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEachIndexed { index, (item, color) ->
                val (type, label, icon) = item
                val order = items.size - 1 - index
                AnimatedVisibility(
                    open,
                    enter = scaleIn(
                        if (reduce) Motion.snappy<Float>(true) else spring(dampingRatio = 0.62f, stiffness = 420f - order * 60f),
                        initialScale = 0.2f,
                        transformOrigin = TransformOrigin(1f, 1f),
                    ) + fadeIn(Motion.snappy(reduce)),
                    exit = scaleOut(Motion.snappy(reduce), targetScale = 0.4f, transformOrigin = TransformOrigin(1f, 1f)) + fadeOut(Motion.snappy(reduce)),
                ) {
                    Row(
                        Modifier
                            .glass(CircleShape, strong = true)
                            .clickable(role = Role.Button) { onPick(type) }
                            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(12.dp))
                        IconBadge(icon, color, size = 40.dp)
                    }
                }
            }
        }
    }
}
