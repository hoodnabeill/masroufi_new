package com.masroufi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius

/** Surface, 1 dp Line border, radius 24. With [onClick]: scale 0.98 on press, Tap haptic (opens something). */
@Composable
fun TzCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(TzRadius.card)
    var m = modifier
    if (onClick != null) m = m.pressScale(source, PressScale.Card)
    m = m.clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
    if (onClick != null) {
        m = m.clickable(interactionSource = source, indication = null, role = Role.Button) {
            haptics.perform(HapticLevel.Tap)
            onClick()
        }
    }
    Column(m.padding(padding), content = content)
}
