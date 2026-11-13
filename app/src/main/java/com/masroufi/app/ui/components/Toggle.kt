package com.masroufi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz

/**
 * 48 x 28 track, 22 dp knob with 3 dp inset (Settings/LastDays mocks). On = turquoise track, dark knob at the end;
 * off = Dashed track, Text 2 knob at the start. The 48 x 28 visual sits in a 48 dp touch target and dips to 0.94
 * on press.
 */
@Composable
fun TzToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val knobX = animateFloatAsState(if (checked) 1f else 0f, motionSpec(TzSpring.Snappy), label = "toggleKnob")
    val track = animateColorAsState(if (checked) Tz.turquoise else Tz.dashed, motionSpec(TzSpring.Snappy), label = "toggleTrack")
    val knob = animateColorAsState(if (checked) Tz.turquoiseInk else Tz.text2, motionSpec(TzSpring.Snappy), label = "toggleKnobColor")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch) {
                haptics.perform(HapticLevel.Tick)
                onCheckedChange(it)
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .pressScale(source, PressScale.Chip, release = TzSpring.Snappy)
                .size(width = 48.dp, height = 28.dp)
                .background(track.value, RoundedCornerShape(14.dp))
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .graphicsLayer { translationX = knobX.value * 20.dp.toPx() * sign }
                    .background(knob.value, CircleShape),
            )
        }
    }
}
