package com.masroufi.app.feature.add

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

private val KeyShape = RoundedCornerShape(18.dp)

/** AddPunch.dc.html: 3 x 4, left to right in every language, 56 dp keys on Raised, "." and backspace transparent. Tick per key. */
@Composable
fun Keypad(onDigit: (Int) -> Unit, onPoint: () -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val backspace = stringResource(R.string.add_backspace)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { d -> Key(Tz.raised, Modifier.weight(1f), onClick = { onDigit(d) }) { KeyText(d.toString()) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key(Color.Transparent, Modifier.weight(1f), onClick = onPoint) { KeyText(".") }
                Key(Tz.raised, Modifier.weight(1f), onClick = { onDigit(0) }) { KeyText("0") }
                Key(Color.Transparent, Modifier.weight(1f).semantics { contentDescription = backspace }, onClick = onBackspace) {
                    TzIcon(TzIcons.delete, 24.dp, null, tint = Tz.text)
                }
            }
        }
    }
}

@Composable
private fun KeyText(text: String) = Text(text, style = TzType.button.copy(fontSize = 26.sp, color = Tz.text))

@Composable
private fun Key(fill: Color, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source, PressScale.Chip, release = TzSpring.Snappy)
            .heightIn(min = 56.dp)
            .clip(KeyShape)
            .background(fill)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.perform(HapticLevel.Tick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
