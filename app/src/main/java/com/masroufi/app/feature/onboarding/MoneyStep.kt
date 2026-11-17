package com.masroufi.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.paper.QershBubble
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

private val Shortcuts = listOf(100_00L, 500_00L, 1_000_00L)

/** ObMoney.dc.html: the amount, +100 / +500 / +1,000, the add sheet's keypad; skipping is allowed. */
@Composable
fun MoneyStep(state: OnboardingUi, actions: OnboardingActions) {
    val haptics = LocalHaptics.current
    StepFrame(
        bars = 2, onBack = actions::back, gap = 16.dp,
        bottom = {
            PrimaryButton(
                stringResource(R.string.ob_money_next),
                onClick = { haptics.perform(HapticLevel.Tap); actions.next() },
                modifier = Modifier.fillMaxWidth(), enabled = state.moneyReady, height = 60.dp, textStyle = StepButtonText,
            )
            TextLink(stringResource(R.string.ob_money_skip), { actions.skipMoney() })
        },
    ) {
        QershBubble(stringResource(R.string.ob_money_bubble))
        StepTitle(stringResource(R.string.ob_money_title))
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Odometer(state.money.toPiastres(), TzType.hero.copy(fontSize = 76.sp, lineHeight = 1.em, color = Tz.yellow), showPiastres = state.money.hasPoint)
                Text(stringResource(R.string.paper_currency), Modifier.padding(bottom = 10.dp), style = TzType.button.copy(fontSize = 22.sp, color = Tz.text2))
            }
            Text(stringResource(R.string.ob_money_caption), style = TzType.label.copy(color = Tz.text2))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shortcuts.forEach { amount ->
                val source = remember { MutableInteractionSource() }
                val shape = RoundedCornerShape(22.dp)
                Box(
                    Modifier.weight(1f).pressScale(source, PressScale.Chip, release = TzSpring.Snappy).height(44.dp).clip(shape)
                        .background(Tz.surface).border(1.dp, Tz.line, shape)
                        .clickable(interactionSource = source, indication = null, role = Role.Button) {
                            haptics.perform(HapticLevel.Tick)
                            actions.addMoney(amount)
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("+" + formatPounds(amount), style = TzType.button.copy(fontSize = 16.sp, color = Tz.turquoise)) }
            }
        }
        Keypad(actions::digit, actions::point, actions::backspace)
    }
}
