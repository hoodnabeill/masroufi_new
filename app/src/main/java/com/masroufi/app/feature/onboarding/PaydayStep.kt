package com.masroufi.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.core.Payday
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.paper.QershBubble
import com.masroufi.app.ui.theme.Lalezar
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

private val CommonDays = listOf(1, 5, 10, 15, 25, 28)
private val Tile = RoundedCornerShape(20.dp)

/** ObPayday.dc.html: eight one-tap answers, the dashed "not fixed" row, and the echo that updates at once. */
@Composable
fun PaydayStep(state: OnboardingUi, actions: OnboardingActions) {
    val haptics = LocalHaptics.current
    var pickingDay by remember { mutableStateOf(false) }
    val payday = state.payday
    val customDay = (payday as? Payday.Day)?.day?.takeIf { it !in CommonDays }

    StepFrame(
        bars = 1, onBack = actions::back,
        bottom = {
            PrimaryButton(
                stringResource(R.string.ob_payday_next),
                onClick = { haptics.perform(HapticLevel.Tap); actions.next() },
                modifier = Modifier.fillMaxWidth(), height = 60.dp, textStyle = StepButtonText,
            )
        },
    ) {
        QershBubble(stringResource(R.string.ob_payday_bubble))
        StepTitle(stringResource(R.string.ob_payday_title))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // null = "another day", which opens the picker.
            val items = buildList<Payday?> {
                CommonDays.forEach { add(Payday.Day(it)) }
                add(Payday.LastDay)
                add(null)
            }
            items.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        when (item) {
                            null -> PaydayTile(customDay != null, { pickingDay = true }, Modifier.weight(1f)) {
                                Text(stringResource(R.string.ob_payday_other), style = TzType.button.copy(fontSize = 16.sp, color = if (customDay != null) Tz.night else Tz.text))
                                if (customDay != null) Text(customDay.toString(), style = TzType.caption.copy(fontWeight = FontWeight.Bold, color = Tz.night))
                            }
                            Payday.LastDay -> PaydayTile(payday == Payday.LastDay, { actions.pickPayday(Payday.LastDay) }, Modifier.weight(1f)) {
                                Text(stringResource(R.string.ob_payday_last), style = TzType.button.copy(fontSize = 16.sp, color = if (payday == Payday.LastDay) Tz.night else Tz.text))
                            }
                            else -> {
                                val day = item as Payday.Day
                                val on = payday == day
                                PaydayTile(on, { actions.pickPayday(day) }, Modifier.weight(1f)) {
                                    Text(day.day.toString(), style = TzType.button.copy(fontSize = 26.sp, color = if (on) Tz.night else Tz.text))
                                }
                            }
                        }
                    }
                }
            }
        }
        NotFixedRow(payday == Payday.NotFixed) { actions.pickPayday(Payday.NotFixed) }
        val days = stringResource(R.string.ob_days, state.daysLeft.toString())
        val echo = stringResource(R.string.ob_payday_echo, days)
        Text(
            buildAnnotatedString {
                append(echo)
                val at = echo.indexOf(days)
                if (at >= 0) addStyle(SpanStyle(fontFamily = Lalezar, fontSize = 20.sp, color = Tz.turquoise), at, at + days.length)
            },
            Modifier.fillMaxWidth().clip(Tile).background(Tz.turquoiseDim).padding(horizontal = 16.dp, vertical = 14.dp),
            style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.7.em, color = Tz.turquoiseDimText),
        )
    }

    if (pickingDay) {
        TzSheet(onDismiss = { pickingDay = false }) {
            Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ob_payday_pick), style = TzType.cardHeading.copy(color = Tz.text))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 1..31) {
                        TzChip(d.toString(), selected = payday == Payday.Day(d), onClick = {
                            actions.pickPayday(Payday.Day(d))
                            pickingDay = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun PaydayTile(selected: Boolean, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(source, PressScale.Chip, release = TzSpring.Snappy).height(64.dp).clip(Tile)
            .background(if (selected) Tz.yellow else Tz.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, Tz.line, Tile))
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton) {
                if (!selected) haptics.perform(HapticLevel.Tick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun NotFixedRow(selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().pressScale(source, PressScale.Chip, release = TzSpring.Snappy).height(64.dp).clip(Tile)
            .then(if (selected) Modifier.border(1.5.dp, Tz.yellow, Tile) else Modifier.dashedBorder(Tz.dashed, 20.dp))
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton) {
                if (!selected) haptics.perform(HapticLevel.Tick)
                onClick()
            }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.ob_payday_notfixed), style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tz.text))
            Text(stringResource(R.string.ob_payday_notfixed_sub), style = TzType.caption.copy(color = Tz.text2))
        }
        TzIcon(TzIcons.chevron, 18.dp, null, tint = Tz.text2)
    }
}
