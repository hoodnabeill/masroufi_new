package com.masroufi.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.launch

private val Pill = RoundedCornerShape(percent = 50)

/**
 * Yellow pill. Disabled keeps the yellow at 40% and stays tappable: a tap shakes 6 dp twice and fires Reject
 * (05-MOTION "Buttons and taps"); [onClick] is not called. Pressed: scale 0.97 and brightness -6%.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 58.dp,
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
            .height(height)
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
        Text(text, style = TzType.button.copy(color = Tz.night))
    }
}

/** 1.5 dp Line outline, same height as [PrimaryButton]. */
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
            .height(height)
            .clip(Pill)
            .border(BorderStroke(1.5.dp, Tz.line), Pill)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TzType.button.copy(color = Tz.text))
    }
}

/** Yellow Alexandria 13 link; 48 dp touch height. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TzType.label.copy(color = Tz.yellow))
    }
}
