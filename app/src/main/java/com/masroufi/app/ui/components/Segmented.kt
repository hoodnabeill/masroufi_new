package com.masroufi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/**
 * Surface track, Line border, yellow thumb sliding with Snappy, Lalezar 17. Geometry from Envelopes.dc.html
 * (48 dp track, 4 dp thumb inset, thumb radius 20); AddPunch.dc.html uses a 44 dp Night track, the brief's surface
 * track wins here. Cells are the full 48 dp tall; the inset lives on the thumb only. Ticks only on a change.
 * [compact] is the add sheet's switch (AddPunch.dc.html): 44 dp Night track with no border, thumb radius 18, Lalezar 16.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (options.isEmpty()) return
    val haptics = LocalHaptics.current
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val position = animateFloatAsState(selected.toFloat(), motionSpec(TzSpring.Snappy), label = "segmentedThumb")
    val track = RoundedCornerShape(if (compact) 22.dp else 24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .height(IntrinsicSize.Min)
            .clip(track)
            .background(if (compact) Tz.night else Tz.surface)
            .then(if (compact) Modifier else Modifier.border(1.dp, Tz.line, track))
            .selectableGroup(),
    ) {
        Box(
            Modifier
                .fillMaxWidth(1f / options.size)
                .fillMaxHeight()
                .graphicsLayer { translationX = position.value * size.width * sign }
                .padding(4.dp)
                .clip(RoundedCornerShape(if (compact) 18.dp else 20.dp))
                .background(Tz.yellow),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val source = remember { MutableInteractionSource() }
                val color = animateColorAsState(
                    if (i == selected) Tz.night else Tz.text2,
                    motionSpec(TzSpring.Snappy),
                    label = "segmentLabel",
                ).value
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = i == selected, interactionSource = source, indication = null, role = Role.Tab) {
                            if (i != selected) haptics.perform(HapticLevel.Tick)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        Modifier.pressScale(source, PressScale.Chip, release = TzSpring.Snappy).padding(vertical = 12.dp),
                        style = TzType.button.copy(fontSize = if (compact) 16.sp else 17.sp, color = color),
                    )
                }
            }
        }
    }
}
