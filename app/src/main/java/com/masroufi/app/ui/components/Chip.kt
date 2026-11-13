package com.masroufi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/**
 * Filter (Roll.dc.html: 40 dp, Text 2 label, selected Text fill with Night label) and Choice (Profile.dc.html:
 * 44 dp, Text label, selected yellow fill with Night label). Both: transparent with a 1 dp Line border when off,
 * 700 weight when on.
 */
enum class ChipStyle { Choice, Filter }

/** Ticks only when the selection changes; a re-tap on the selected chip calls [onClick] silently. */
@Composable
fun TzChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Choice,
) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val pop = remember { Animatable(1f) }
    val popSpec = motionSpec<Float>(TzSpring.Snappy)
    // Select pops 1.0 -> 1.04 -> 1.0; deselecting mid-pop settles back to 1; no pop on first composition.
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(selected) {
        if (selected && !first[0]) {
            pop.animateTo(1.04f, popSpec)
        }
        first[0] = false
        pop.animateTo(1f, popSpec)
    }
    val onFill = if (style == ChipStyle.Choice) Tz.yellow else Tz.text
    val colorSpec = motionSpec<Color>(TzSpring.Snappy)
    val fill = animateColorAsState(if (selected) onFill else Color.Transparent, colorSpec, label = "chipFill").value
    val border = animateColorAsState(if (selected) onFill else Tz.line, colorSpec, label = "chipBorder").value
    val content = animateColorAsState(
        when {
            selected -> Tz.night
            style == ChipStyle.Choice -> Tz.text
            else -> Tz.text2
        },
        colorSpec,
        label = "chipLabel",
    ).value
    val height = if (style == ChipStyle.Choice) 44.dp else 40.dp
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = if (style == ChipStyle.Choice) Role.RadioButton else Role.Button,
            ) {
                if (!selected) haptics.perform(HapticLevel.Tick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .pressScale(source, PressScale.Chip, release = TzSpring.Snappy)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .height(height)
                .widthIn(min = 48.dp)
                .clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = TzType.label.copy(
                    color = content,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
    }
}
