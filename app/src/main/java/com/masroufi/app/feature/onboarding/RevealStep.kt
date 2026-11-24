package com.masroufi.app.feature.onboarding

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.paper.PrintIn
import com.masroufi.app.ui.paper.QershBubble
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.paper.SLOW_STEP_MS
import com.masroufi.app.ui.paper.Ticket
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

private val Reminders = listOf<Pair<Int?, Int>>(
    20 to R.string.ob_reminder_8,
    21 to R.string.ob_reminder_9,
    22 to R.string.ob_reminder_10,
    null to R.string.ob_reminder_none,
)

/** ObTicket.dc.html: the ticket prints slowly, the working in three facts, the loop, the reminder, then the first hole. */
@Composable
fun RevealStep(state: OnboardingUi, actions: OnboardingActions) {
    val haptics = LocalHaptics.current
    val reveal = state.reveal
    if (state.askNear) {
        NearQuestion(actions)
        return
    }
    if (reveal == null) {
        Box(Modifier.fillMaxSize().background(Tz.night))
        return
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* the choice is stored either way */ }
    StepFrame(
        bars = null, onBack = actions::back, top = 36.dp,
        bottom = {
            PrimaryButton(
                stringResource(R.string.ob_first_punch),
                onClick = { haptics.perform(HapticLevel.Confirm); actions.finish() },
                modifier = Modifier.fillMaxWidth(), enabled = !state.finishing, height = 60.dp, textStyle = StepButtonText, icon = TzIcons.punch,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.ob_ticket_kicker), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tz.turquoise))
            StepTitle(stringResource(R.string.ob_ticket_title), size = 40)
        }
        // Room for Qersh peeking over the ticket.
        Box(Modifier.fillMaxWidth().padding(top = 30.dp)) {
            // 05-MOTION "A new ticket", slow: 700 ms, three steps, a Tick each.
            PrintIn(play = true, stepMs = SLOW_STEP_MS) { slide, qershScale ->
                Ticket(
                    label = stringResource(R.string.ob_ticket_label, reveal.paydayDay.toString()),
                    amountLabel = null,
                    leftPiastres = reveal.amount,
                    subLine = if (reveal.endOfMonth) stringResource(R.string.home_ticket_short, poundsText(reveal.shortfall)) else stringResource(R.string.ob_ticket_line),
                    holes = 0,
                    caption = null,
                    stub = { Facts(state, reveal) },
                    contentDescription = stringResource(R.string.ob_ticket_cd, poundsText(reveal.amount)),
                    qershScale = qershScale,
                    modifier = Modifier.graphicsLayer(slide).rotate(-2f),
                    showHoles = false,
                    qershMood = if (reveal.endOfMonth) QershMood.Thinking else QershMood.Delighted,
                )
            }
        }
        Text(
            stringResource(R.string.ob_ticket_loop),
            Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
            style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.8.em, color = Tz.text2),
        )
        val card = RoundedCornerShape(24.dp)
        Column(
            Modifier.fillMaxWidth().clip(card).background(Tz.surface).border(1.dp, Tz.line, card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.ob_reminder_title), style = TzType.cardHeading.copy(color = Tz.text))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Reminders.forEach { (hour, label) ->
                    ReminderChip(stringResource(label), state.reminderHour == hour, hour == null, Modifier.weight(1f)) {
                        actions.pickReminder(hour)
                        // The permission is asked only now, and only for a time; the answer does not change the choice.
                        if (hour != null && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            Text(stringResource(R.string.ob_reminder_note), style = TzType.caption.copy(color = Tz.text2))
        }
    }
}

@Composable
private fun Facts(state: OnboardingUi, reveal: RevealUi) {
    val style = TzType.label.copy(fontWeight = FontWeight.SemiBold, color = Tz.yellowInk)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // A skipped money question has no number to show.
        if (!state.moneySkipped) Text(stringResource(R.string.ob_fact_have, poundsText(state.money.toPiastres())), style = style)
        Text(stringResource(R.string.ob_fact_reserved, poundsText(state.fixedTotal)), style = style)
        Text(stringResource(R.string.ob_days, reveal.days.toString()), style = style)
    }
}

@Composable
private fun ReminderChip(label: String, selected: Boolean, none: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    val style = if (none) TzType.caption else TzType.button.copy(fontSize = 17.sp)
    Box(
        modifier.pressScale(source, PressScale.Chip, release = TzSpring.Snappy).heightIn(min = 48.dp).clip(shape)
            .then(if (selected) Modifier.background(Tz.turquoise) else Modifier.background(Tz.night).border(1.dp, Tz.line, shape))
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton) {
                if (!selected) haptics.perform(HapticLevel.Tick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, Modifier.padding(horizontal = 4.dp), style = style.copy(color = if (selected) Tz.turquoiseInk else if (none) Tz.text2 else Tz.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
    }
}

/** 08-ONBOARDING edge case: payday today or tomorrow. */
@Composable
private fun NearQuestion(actions: OnboardingActions) {
    val haptics = LocalHaptics.current
    StepFrame(
        bars = null, onBack = actions::back, top = 36.dp,
        bottom = {
            PrimaryButton(
                stringResource(R.string.ob_near_yes),
                onClick = { haptics.perform(HapticLevel.Tap); actions.answerNear(true) },
                modifier = Modifier.fillMaxWidth(), height = 60.dp, textStyle = StepButtonText,
            )
            SecondaryButton(
                stringResource(R.string.ob_near_no),
                onClick = { haptics.perform(HapticLevel.Tap); actions.answerNear(false) },
                modifier = Modifier.fillMaxWidth(), height = 60.dp,
            )
        },
    ) {
        QershBubble(stringResource(R.string.ob_near_title), mood = QershMood.Thinking)
    }
}
