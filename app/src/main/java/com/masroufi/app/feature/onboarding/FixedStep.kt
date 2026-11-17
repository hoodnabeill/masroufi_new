package com.masroufi.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.feature.add.Keypad
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.paper.QershBubble
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** ObFixed.dc.html: chips for what must be paid before payday; a tap opens an inline keypad, a tap on a filled chip removes it. */
@Composable
fun FixedStep(state: OnboardingUi, actions: OnboardingActions) {
    val haptics = LocalHaptics.current
    StepFrame(
        bars = 3, onBack = actions::back,
        bottom = {
            PrimaryButton(
                stringResource(R.string.ob_fixed_print),
                onClick = { haptics.perform(HapticLevel.Tap); actions.printTicket() },
                modifier = Modifier.fillMaxWidth(), height = 60.dp, textStyle = StepButtonText,
            )
            TextLink(stringResource(R.string.ob_fixed_none), { actions.skipFixed() })
        },
    ) {
        QershBubble(stringResource(R.string.ob_fixed_bubble))
        StepTitle(stringResource(R.string.ob_fixed_title), size = 38)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FixedChips.forEach { chip ->
                FixedChipView(stringResource(chip.label), state.fixed[chip.key], state.openFixed == chip.key, chip.key == FixedChips.last().key) {
                    actions.tapFixed(chip.key)
                }
            }
        }
        val open = FixedChips.firstOrNull { it.key == state.openFixed }
        if (open != null) {
            val amount = state.fixedInput.toPiastres()
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.ob_fixed_amount_title, stringResource(open.label)), style = TzType.cardHeading.copy(color = Tz.text))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                    Odometer(amount, TzType.hero.copy(fontSize = 48.sp, lineHeight = 1.em, color = Tz.yellow), showPiastres = state.fixedInput.hasPoint)
                    Text(stringResource(R.string.paper_currency), Modifier.padding(bottom = 6.dp), style = TzType.button.copy(fontSize = 18.sp, color = Tz.text2))
                }
                Keypad(actions::digit, actions::point, actions::backspace)
                PrimaryButton(
                    stringResource(R.string.ob_fixed_ok),
                    onClick = { haptics.perform(HapticLevel.Confirm); actions.confirmFixed() },
                    modifier = Modifier.fillMaxWidth(), enabled = amount > 0, height = 48.dp,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tz.turquoiseDim).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ob_fixed_total), style = TzType.body.copy(fontSize = 14.sp, color = Tz.turquoiseDimText))
            Text(formatPounds(state.fixedTotal), style = TzType.card.copy(fontSize = 26.sp, color = Tz.turquoise))
        }
    }
}

/** 56 dp pill: yellow with the amount once filled; Surface when empty; dashed with a plus for "something else". */
@Composable
private fun FixedChipView(label: String, amount: Long?, open: Boolean, dashed: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(28.dp)
    val filled = amount != null
    val look = when {
        filled -> Modifier.background(Tz.yellow)
        open -> Modifier.background(Tz.surface).border(1.5.dp, Tz.yellow, shape)
        dashed -> Modifier.dashedBorder(Tz.dashed, 28.dp)
        else -> Modifier.background(Tz.surface).border(1.dp, Tz.line, shape)
    }
    Row(
        Modifier.pressScale(source, PressScale.Chip, release = TzSpring.Snappy).height(56.dp).clip(shape).then(look)
            .semantics { selected = filled }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.perform(HapticLevel.Tick)
                onClick()
            }
            .padding(horizontal = if (filled) 16.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (dashed && !filled) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dashed && !filled) TzIcon(TzIcons.add, 16.dp, null, tint = Tz.text2)
        Text(
            label,
            style = TzType.body.copy(
                fontSize = 15.sp, lineHeight = 1.em,
                fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                color = when { filled -> Tz.night; dashed -> Tz.text2; else -> Tz.text },
            ),
        )
        if (amount != null) Text(formatPounds(amount), style = TzType.button.copy(fontSize = 18.sp, color = Tz.night))
    }
}
