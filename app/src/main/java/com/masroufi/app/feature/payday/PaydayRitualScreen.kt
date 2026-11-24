package com.masroufi.app.feature.payday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatDayDate
import com.masroufi.app.data.repo.JarShare
import com.masroufi.app.data.repo.RitualView
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.plan.AmountSheet
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import kotlin.math.abs

private val RingColor = Color(0xFF19B8AA)
private val TicketInk = Color(0xFF3D2F00)

/** يوم القبض (Payday.dc.html): pay the jar first, see the fixed costs, then print the month's tickets. */
@Composable
fun PaydayRitualScreen(viewModel: PaydayRitualViewModel, onHome: () -> Unit, onFixedCosts: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(ui.finished) {
        if (ui.finished) {
            if (ui.printed) haptics.perform(HapticLevel.Milestone)
            onHome()
        }
    }
    val view = ui.view
    if (ui.loading || view == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { LocalDate.now() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CircleButton(TzIcons.close, stringResource(R.string.payday_close)) { haptics.perform(HapticLevel.Tap); viewModel.skip() }
            Text(formatDayDate(today, locale), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
            Spacer(Modifier.size(44.dp))
        }
        Hero(ui.salary, ui.pocketName)
        JarStep(ui, viewModel, view)
        // Step 2 "سدّد اللي عليك" only when the user owes someone; the fixed costs are then step 3.
        if (ui.debts.isNotEmpty()) DebtsStep(ui, viewModel)
        FixedStep(if (ui.debts.isNotEmpty()) 3 else 2, view, ui.salary, onFixedCosts)
        TicketCard(view)
        PrimaryButton(
            stringResource(R.string.payday_print), viewModel::print, Modifier.fillMaxWidth(), height = 60.dp,
            textStyle = TzType.button.copy(fontSize = 22.sp),
        )
    }
    if (ui.amountSheet) {
        AmountSheet(stringResource(R.string.payday_amount_title), viewModel::dismissAmount, AmountInput.of((ui.share as? JarShare.Amount)?.amount ?: 0)) { amount ->
            PrimaryButton(
                stringResource(R.string.jar_save), { haptics.perform(HapticLevel.Confirm); viewModel.setAmount(amount) },
                Modifier.fillMaxWidth(), enabled = amount in 0..ui.salary, height = 52.dp,
            )
        }
    }
}

@Composable
private fun Hero(salary: Long, pocket: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Tz.turquoise)
            .drawBehind {
                val c = Offset(30.dp.toPx(), 30.dp.toPx())
                listOf(40, 56, 72, 88, 104).forEach { drawCircle(RingColor, it.dp.toPx(), c, style = Stroke(1.2.dp.toPx())) }
            }
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.payday_title), style = TzType.titleTab, color = Tz.turquoiseInk)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text(poundsText(salary), style = TzType.hero.copy(fontSize = 64.sp, lineHeight = 64.sp), color = Tz.turquoiseInk)
                Text(stringResource(R.string.payday_currency), style = TzType.cardHeading, color = Tz.turquoiseInk, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                stringResource(R.string.payday_hero_sub, pocket),
                style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Tz.turquoiseInk,
            )
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.surface).border(1.dp, Tz.line, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Tz.yellow), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = TzType.button.copy(fontSize = 16.sp), color = Tz.night)
            }
            Text(title, style = TzType.cardHeading, color = Tz.text, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

@Composable
private fun JarStep(ui: RitualUi, vm: PaydayRitualViewModel, view: RitualView) {
    StepCard(1, stringResource(R.string.payday_step_jar)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.payday_jar_line, poundsText(view.jarShare)),
                    style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text,
                )
                val after = poundsText(ui.jarTotal + view.jarShare)
                val share = ui.share
                Text(
                    if (share is JarShare.Percent) stringResource(R.string.payday_jar_sub_pct, share.percent, after)
                    else stringResource(R.string.payday_jar_sub_amount, after),
                    style = TzType.caption, color = Tz.text2,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15).forEach { p ->
                    ShareChip("$p%", (ui.share as? JarShare.Percent)?.percent == p, true, Modifier.weight(1f)) { vm.setPercent(p) }
                }
                ShareChip(stringResource(R.string.payday_chip_amount), ui.share is JarShare.Amount, false, Modifier.weight(1f), vm::openAmount)
            }
        }
    }
}

/** 44 dp pill of the mock: Lalezar for the percents, Alexandria 12 for "مبلغ"; selected = turquoise 2 dp border on turquoise dim. */
@Composable
private fun ShareChip(label: String, selected: Boolean, display: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(44.dp).clip(shape)
            .background(if (selected) Tz.turquoiseDim else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) Tz.turquoise else Tz.line, shape)
            .clickable(role = Role.RadioButton) {
                if (!selected) haptics.perform(HapticLevel.Tick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (display) TzType.button.copy(fontSize = 16.sp) else TzType.caption.copy(fontSize = 12.sp),
            color = if (selected) Tz.text else Tz.text2,
        )
    }
}

/** Step 2 (Payday.dc.html): a tick row per person the user owes; ticked ones are repaid in full when the tickets print. */
@Composable
private fun DebtsStep(ui: RitualUi, vm: PaydayRitualViewModel) {
    val haptics = LocalHaptics.current
    StepCard(2, stringResource(R.string.payday_step_debts)) {
        Column {
            ui.debts.forEach { d ->
                val on = d.debt.id in ui.repay
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.Checkbox) { haptics.perform(HapticLevel.Tick); vm.toggleDebt(d.debt.id) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    val box = RoundedCornerShape(6.dp)
                    Box(
                        Modifier.size(22.dp).clip(box).background(if (on) Tz.yellow else Color.Transparent).border(1.5.dp, if (on) Tz.yellow else Tz.line, box),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) TzIcon(TzIcons.check, 14.dp, null, tint = Tz.night)
                    }
                    Text(d.person.name, style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(poundsText(d.debt.amount), style = TzType.rowNumber.copy(fontSize = 18.sp), color = Tz.text)
                }
            }
        }
    }
}

@Composable
private fun FixedStep(number: Int, view: RitualView, salary: Long, onEdit: () -> Unit) {
    StepCard(number, stringResource(R.string.payday_step_fixed), trailing = { TextLink(stringResource(R.string.payday_edit), onEdit) }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (view.fixedItems.isEmpty()) {
                    Text(stringResource(R.string.payday_fixed_none), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
                }
                view.fixedItems.forEach {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            when {
                                it.count == 0 -> stringResource(R.string.payday_fixed_row, it.name, it.day)
                                it.count == 1 -> it.name
                                else -> stringResource(R.string.payday_fixed_times, it.name, it.count)
                            },
                            style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal), color = Tz.text2, modifier = Modifier.weight(1f),
                        )
                        Text(poundsText(it.amount), style = TzType.rowNumber.copy(fontSize = 18.sp), color = Tz.text)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
            Text(stringResource(R.string.payday_where), style = TzType.caption, color = Tz.text2)
            val total = salary.coerceAtLeast(1).toFloat()
            TzProgressBar(
                listOf(
                    view.jarShare / total to Tz.turquoise,
                    view.debts / total to Tz.lilac, // 0 without ticked debts: not drawn
                    view.fixed / total to Tz.sky,
                    view.forTickets / total to Tz.yellow,
                ),
                height = 14.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (view.debts > 0) {
                    // The mock's two-column grid: jar and repayments, then fixed costs and tickets.
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Tz.turquoise, stringResource(R.string.payday_leg_jar), view.jarShare, Tz.text2, Modifier.weight(1f))
                        Legend(Tz.lilac, stringResource(R.string.payday_leg_debts), view.debts, Tz.text2, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Tz.sky, stringResource(R.string.payday_leg_fixed), view.fixed, Tz.text2, Modifier.weight(1f))
                        Legend(Tz.yellow, stringResource(R.string.payday_leg_tickets), view.forTickets, Tz.yellow, Modifier.weight(1f), bold = true)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Tz.turquoise, stringResource(R.string.payday_leg_jar), view.jarShare, Tz.text2, Modifier.weight(1f))
                        Legend(Tz.sky, stringResource(R.string.payday_leg_fixed), view.fixed, Tz.text2, Modifier.weight(1f))
                    }
                    Legend(Tz.yellow, stringResource(R.string.payday_leg_tickets), view.forTickets, Tz.yellow, Modifier.fillMaxWidth(), bold = true)
                }
            }
        }
    }
}

@Composable
private fun Legend(dot: Color, label: String, amount: Long, labelColor: Color, modifier: Modifier, bold: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, style = TzType.caption.copy(fontSize = 13.sp), color = labelColor, modifier = Modifier.weight(1f))
        Text(
            poundsText(amount),
            style = TzType.caption.copy(fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold),
            color = if (bold) Tz.yellow else Tz.text,
        )
    }
}

@Composable
private fun TicketCard(view: RitualView) {
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.yellow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.payday_ticket_title), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.night)
            val last = view.lastCycleTicket
            val diff = if (last == null) 0L else view.ticket - last
            if (diff != 0L) {
                Text(
                    stringResource(if (diff > 0) R.string.payday_more else R.string.payday_less, poundsText(abs(diff))),
                    style = TzType.caption.copy(fontWeight = FontWeight.SemiBold), color = TicketInk,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Text(poundsText(view.ticket), style = TzType.hero.copy(fontSize = 64.sp, lineHeight = 64.sp), color = Tz.night)
            Text(stringResource(R.string.payday_per_day), style = TzType.cardHeading, color = Tz.night, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(view.days.coerceIn(0, 10)) { Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(4.dp)).background(Tz.night)) }
        }
        view.nextPayday?.let { next ->
            val date = DateTimeFormatter.ofPattern("d MMMM", locale).withDecimalStyle(DecimalStyle.STANDARD).format(next)
            Text(
                stringResource(R.string.payday_count, view.days, date),
                style = TzType.caption.copy(fontWeight = FontWeight.SemiBold), color = TicketInk,
            )
        }
    }
}
