package com.masroufi.app.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** Filter selected = Text-on-Night fill; Choice selected = yellow (03-DESIGN-SYSTEM). */
enum class ChipStyle { Choice, Filter }

private val ChipShape = RoundedCornerShape(22.dp)

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
    // Select pops 1.0 -> 1.04 -> 1.0; skipped on first composition of an already-selected chip.
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(selected) {
        if (selected && !first[0]) {
            pop.animateTo(1.04f, popSpec)
            pop.animateTo(1f, popSpec)
        }
        first[0] = false
    }
    val fill = when {
        !selected -> Tz.surface
        style == ChipStyle.Choice -> Tz.yellow
        else -> Tz.text
    }
    Box(
        modifier
            .pressScale(source, PressScale.Chip, release = TzSpring.Snappy)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .height(44.dp)
            .widthIn(min = 48.dp)
            .clip(ChipShape)
            .background(fill)
            .border(1.dp, if (selected) fill else Tz.line, ChipShape)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = if (style == ChipStyle.Choice) Role.RadioButton else Role.Button,
            ) {
                haptics.perform(HapticLevel.Tick)
                onClick()
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TzType.label.copy(color = if (selected) Tz.night else Tz.text))
    }
}
