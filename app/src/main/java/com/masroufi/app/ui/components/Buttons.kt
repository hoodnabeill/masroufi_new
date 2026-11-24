package com.masroufi.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.launch

private val Pill = RoundedCornerShape(percent = 50)

/**
 * Yellow pill, [height] at least. Disabled keeps the yellow at 40% and stays tappable: a tap shakes 6 dp twice and fires Reject
 * (05-MOTION "Buttons and taps"); [onClick] is not called. Pressed: scale 0.97 and brightness -6%.
 * Fires no haptic when enabled: the caller fires the action's own (Confirm, Heavy...), so there is never two.
 * [onDisabledClick] runs on a disabled tap (to pulse the missing field). [icon] sits before the label, 22 dp, gap 10.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 58.dp,
    textStyle: TextStyle = TzType.button,
    icon: TzIconDef? = null,
    onDisabledClick: () -> Unit = {},
) {
    val haptics = LocalHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed = source.collectIsPressedAsState()
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .pressScale(source, PressScale.Primary)
            .heightIn(min = height) // a minimum, so the label grows the pill at 200% font size instead of clipping
            .alpha(if (enabled) 1f else 0.4f)
            .clip(Pill)
            .background(Tz.yellow)
            .drawWithContent {
                drawContent()
                if (pressed.value) drawRect(Tz.night.copy(alpha = 0.06f))
            }
            .semantics { if (!enabled) disabled() }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                if (enabled) {
                    onClick()
                } else {
                    haptics.perform(HapticLevel.Reject)
                    onDisabledClick()
                    if (!reduceMotion) scope.launch {
                        shake.snapTo(0f)
                        shake.animateTo(0f, keyframes {
                            durationMillis = 260
                            6f at 40
                            -6f at 100
                            6f at 160
                            -6f at 220
                            0f at 260
                        })
                    }
                }
            }
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) TzIcon(icon, 22.dp, null, tint = Tz.night, filled = false)
            Text(text, style = textStyle.copy(color = Tz.night))
        }
    }
}

/** 1.5 dp Line outline, same height as [PrimaryButton]. Fires no haptic: the caller fires the action's own. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(source, PressScale.Primary)
            .heightIn(min = height) // a minimum, so the label grows the pill at 200% font size instead of clipping
            .clip(Pill)
            .border(BorderStroke(1.5.dp, Tz.line), Pill)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TzType.button.copy(color = Tz.text))
    }
}

/** Yellow Alexandria 13 link; 48 dp touch height; dips to 0.94 on press. Fires no haptic: the caller fires the action's own. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, Modifier.pressScale(source, PressScale.Chip, release = TzSpring.Snappy), style = TzType.label.copy(color = Tz.yellow))
    }
}
