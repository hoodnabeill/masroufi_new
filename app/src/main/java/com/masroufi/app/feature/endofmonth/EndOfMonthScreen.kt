package com.masroufi.app.feature.endofmonth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.EndOfMonthState
import com.masroufi.app.data.repo.Habit
import com.masroufi.app.data.repo.NO_SPEND_GOAL
import com.masroufi.app.data.repo.OwedToMe
import com.masroufi.app.data.repo.SmallTicketDay
import com.masroufi.app.feature.home.dashedBorder
import com.masroufi.app.feature.people.rememberReminder
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzPanel
import com.masroufi.app.ui.components.TzToggle
import com.masroufi.app.ui.components.pluralText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType

/** Days per row of the small tickets strip (the mock's nine). */
private const val STRIP_COLUMNS = 9

/**
 * وضع آخر الشهر (LastDays.dc.html), a pushed screen. [onOpenJar] opens the jar (the Plan tab, where its withdraw sheet
 * lives). The ticket on Home keeps its normal look in the mode: 03-DESIGN-SYSTEM gives carnelian only to this hero.
 */
@Composable
fun EndOfMonthScreen(viewModel: EndOfMonthViewModel, onBack: () -> Unit, onOpenJar: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    // The share sheet is the whole reminder: nothing to confirm when it closes.
    val remind = rememberReminder(onReturned = {})
    EndOfMonthContent(
        ui, onBack, viewModel::setPaused, viewModel::toggleMode, onOpenJar,
        onRemind = { remind(it.name, it.amount) },
    )
}

@Composable
fun EndOfMonthContent(
    ui: EndOfMonthUi,
    onBack: () -> Unit,
    onPause: (Long, Boolean) -> Unit,
    onToggleMode: () -> Unit,
    onOpenJar: () -> Unit,
    onRemind: (OwedToMe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val s = ui.state
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PushedHeader(stringResource(R.string.eom_title), onBack)
            if (s != null) {
                Hero(ui, s)
                if (ui.shortfall) {
                    // Short of money: where it could come from goes first, and the small tickets have nothing to show.
                    Rescue(ui, onOpenJar, onRemind)
                    Habits(s.habits, onPause)
                } else {
                    Strip(ui, s)
                    Habits(s.habits, onPause)
                    Challenge(ui, s)
                    Rescue(ui, onOpenJar, onRemind)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        if (s != null) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)) {
                PrimaryButton(
                    stringResource(if (ui.modeOn) R.string.eom_switch_off else R.string.eom_switch_on, s.payday.dayOfMonth.toString()),
                    { haptics.perform(if (ui.modeOn) HapticLevel.Tap else HapticLevel.Confirm); onToggleMode() },
                    Modifier.fillMaxWidth(),
                    height = 56.dp,
                )
            }
        }
    }
}

@Composable
private fun Hero(ui: EndOfMonthUi, s: EndOfMonthState) {
    val ink = Tz.carnelianInk
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(TzRadius.hero)).background(Tz.carnelian).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val label = pluralText(if (ui.shortfall) R.plurals.eom_days_left_short else R.plurals.eom_days_left, s.daysLeft)
        Text(label, style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Odometer(ui.heroAmount, TzType.hero.copy(fontSize = 84.sp, lineHeight = 0.95.em, color = ink))
            Text(stringResource(R.string.eom_unit), style = TzType.card.copy(fontSize = 24.sp), color = ink, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            if (ui.shortfall) stringResource(R.string.eom_short_line) else stringResource(R.string.eom_hero_line, formatPounds(s.ticket)),
            style = TzType.body.copy(fontWeight = FontWeight.SemiBold),
            color = ink,
        )
    }
}

@Composable
private fun Strip(ui: EndOfMonthUi, s: EndOfMonthState) {
    if (s.strip.isEmpty()) return
    TzPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(pluralText(R.plurals.eom_tickets, s.strip.size), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.eom_each, formatPounds(s.ticket)), style = TzType.caption, color = Tz.text2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            s.strip.chunked(STRIP_COLUMNS).forEachIndexed { rowIndex, days ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    days.forEachIndexed { i, d -> DayCell(d, today = rowIndex == 0 && i == 0, Modifier.weight(1f)) }
                    repeat(STRIP_COLUMNS - days.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // The first bill by name; with more, a second line counts the rest.
        val bills = ui.bills
        bills.firstOrNull()?.let { b ->
            val line = stringResource(R.string.eom_bill, b.date.dayOfMonth.toString(), b.name, formatPounds(b.amount), formatPounds(s.pool))
            Text(line, style = TzType.caption.copy(lineHeight = 1.7.em), color = Tz.text2)
        }
        if (bills.size > 1) {
            Text(pluralText(R.plurals.eom_bill_more, bills.size - 1), style = TzType.caption.copy(lineHeight = 1.7.em), color = Tz.text2)
        }
    }
}

/** Today is a yellow stub; the rest are dashed, and a day with a reserved bill is dashed carnelian. */
@Composable
private fun DayCell(day: SmallTicketDay, today: Boolean, modifier: Modifier) {
    val bill = day.bills.isNotEmpty()
    val shape = RoundedCornerShape(8.dp)
    val number = day.date.dayOfMonth.toString()
    val base = modifier.height(48.dp)
    val look = when {
        today -> base.clip(shape).background(Tz.yellow)
        bill -> base.dashedBorder(1.5.dp, Tz.carnelian, 8.dp)
        else -> base.dashedBorder(1.5.dp, Tz.dashed, 8.dp)
    }
    val description = if (bill) stringResource(R.string.eom_bill_day_cd, number) else null
    Box(
        look.semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(number, style = TzType.rowNumber.copy(fontSize = 14.sp), color = if (today) Tz.night else if (bill) Tz.carnelianText else Tz.text2)
    }
}

@Composable
private fun Habits(habits: List<Habit>, onPause: (Long, Boolean) -> Unit) {
    if (habits.isEmpty()) return
    TzPanel {
        Text(stringResource(R.string.eom_habits_title), style = TzType.cardHeading, color = Tz.text)
        habits.forEachIndexed { i, h ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(TzRadius.tile)).background(Tz.raised), contentAlignment = Alignment.Center) {
                    TzIcon(TzIcons.all.firstOrNull { it.name == h.icon } ?: TzIcons.grid, 20.dp, null, tint = Tz.yellow)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(h.name, style = TzType.body.copy(fontWeight = FontWeight.SemiBold), color = Tz.text)
                    Text(stringResource(R.string.eom_habit_line, formatPounds(h.spent)), style = TzType.caption, color = Tz.text2)
                }
                TzToggle(h.paused, { onPause(h.categoryId, it) }, stringResource(R.string.eom_habit_cd, h.name))
            }
        }
        Text(stringResource(R.string.eom_habits_note), style = TzType.caption.copy(lineHeight = 1.7.em), color = Tz.text2)
    }
}

@Composable
private fun Challenge(ui: EndOfMonthUi, s: EndOfMonthState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(TzRadius.card)).background(Tz.turquoiseDim).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.eom_challenge_title), style = TzType.cardHeading, color = Tz.text, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.eom_challenge_count, ui.challengeDone, NO_SPEND_GOAL),
                style = TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                color = Tz.turquoiseInk,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Tz.turquoise).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        val line = when {
            ui.challengeEarned -> stringResource(R.string.eom_challenge_done)
            s.boost > 0 -> stringResource(R.string.eom_challenge_line, formatPounds(s.boost))
            else -> stringResource(R.string.eom_challenge_line_flat)
        }
        Text(line, style = TzType.label.copy(fontWeight = FontWeight.Normal, lineHeight = 1.7.em), color = Tz.turquoiseDimText)
    }
}

@Composable
private fun Rescue(ui: EndOfMonthUi, onOpenJar: () -> Unit, onRemind: (OwedToMe) -> Unit) {
    val r = ui.state?.rescue ?: return
    if (!ui.hasRescue) return
    val haptics = LocalHaptics.current
    TzPanel(spacing = 4.dp) {
        Text(stringResource(R.string.eom_rescue_title), style = TzType.cardHeading, color = Tz.text, modifier = Modifier.padding(bottom = 6.dp))
        r.freeJar?.let {
            RescueRow(stringResource(R.string.eom_rescue_jar), stringResource(R.string.eom_rescue_jar_value, formatPounds(it))) {
                haptics.perform(HapticLevel.Tap)
                onOpenJar()
            }
        }
        r.owedToMe?.let { o ->
            RescueRow(stringResource(R.string.eom_rescue_owed, o.name), stringResource(R.string.eom_rescue_owed_value, formatPounds(o.amount))) {
                haptics.perform(HapticLevel.Tap)
                onRemind(o)
            }
        }
        r.emergency?.let { RescueRow(stringResource(R.string.eom_rescue_emergency), formatPounds(it), null) }
    }
}

/** A label and its value; with [onClick] the value reads as a link and the row is a 48 dp target. */
@Composable
private fun RescueRow(label: String, value: String, onClick: (() -> Unit)?) {
    val row = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        row.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TzType.body.copy(fontSize = 14.sp), color = Tz.text2, modifier = Modifier.weight(1f, fill = false))
        Text(
            value,
            style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = if (onClick != null) Tz.yellow else Tz.text,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
