package com.masroufi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType

/**
 * 44 dp leading tile (Raised, radius 14, Text 2 content as in Main.dc.html), title 15/600, caption 12 Text 2,
 * trailing slot at the end. With [onClick]: scale 0.98 on press, Tap haptic.
 */
@Composable
fun TzRow(
    leading: @Composable () -> Unit,
    title: String,
    caption: String?,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    var m = modifier
    if (onClick != null) {
        m = m.pressScale(source, PressScale.Card)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.perform(HapticLevel.Tap)
                onClick()
            }
    }
    Row(
        m.defaultMinSize(minHeight = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(TzRadius.tile)).background(Tz.raised),
            contentAlignment = Alignment.Center,
        ) { CompositionLocalProvider(LocalContentColor provides Tz.text2) { leading() } }
        Column(Modifier.weight(1f)) {
            Text(title, style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tz.text))
            if (caption != null) Text(caption, style = TzType.caption.copy(color = Tz.text2))
        }
        trailing()
    }
}
