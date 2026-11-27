package com.masroufi.app.feature.freelance

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import com.masroufi.app.ui.components.CappedFontScale
import com.masroufi.app.ui.components.LARGE_FONT_SCALE
import com.masroufi.app.ui.components.isLargeFont
import com.masroufi.app.ui.motion.pressClickable
import com.masroufi.app.feature.pockets.LoadingRows
import com.masroufi.app.feature.plan.SplitRow
import com.masroufi.app.feature.plan.SmallPill
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatDayMonth
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.db.ClientJobEntity
import com.masroufi.app.data.repo.ClientJobView
import com.masroufi.app.data.repo.SalarySuggestion
import com.masroufi.app.data.repo.SuggestionKind
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.installments.monthLabel
import com.masroufi.app.feature.people.rememberShareText
import com.masroufi.app.feature.plan.AmountKeypad
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.plan.FormBlock
import com.masroufi.app.feature.plan.NameField
import com.masroufi.app.feature.plan.PillButton
import com.masroufi.app.feature.plan.Stepper
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.paper.BlankSheet
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

internal sealed interface Sheet {
    data object Salary : Sheet
    data object Work : Sheet
    /** [id] null = a new job. */
    data class Job(val id: Long?) : Sheet
    data class Actions(val id: Long) : Sheet
}

private const val DEFAULT_SALARY_DAY = 1

/** مرتب لنفسك (Freelance.dc.html). While the mode is off the first visit is the turn-on block; then the screen. */
@Composable
fun FreelanceScreen(viewModel: FreelanceViewModel, onBack: () -> Unit) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val share = rememberShareText()
    val context = LocalContext.current
    FreelanceContent(
        s, onBack,
        FreelanceActions(
            viewModel::turnOn, viewModel::applySuggestion, viewModel::keepSalary, viewModel::workCame, viewModel::addJob, viewModel::editJob,
            viewModel::markPaid, viewModel::deleteJob,
            onRemind = { j ->
                val label = j.job.label.ifBlank { context.getString(R.string.free_remind_fallback_label) }
                share(context.getString(R.string.free_remind_text, j.job.client, label, formatPounds(j.job.amount)), context.getString(R.string.free_remind))
            },
        ),
    )
}

/** What the screen can do; the view model's functions in the app, no-ops in the audit. */
internal class FreelanceActions(
    val turnOn: (Long, Int) -> Unit,
    val applySuggestion: (SalarySuggestion) -> Unit,
    val keepSalary: () -> Unit,
    val workCame: (Long, String, String) -> Unit,
    val addJob: (String, String, Long, Int) -> Unit,
    val editJob: (ClientJobEntity, String, String, Long, Int?) -> Unit,
    val markPaid: (Long) -> Unit,
    val deleteJob: (Long) -> Unit,
    val onRemind: (ClientJobView) -> Unit,
) {
    companion object { val None = FreelanceActions({ _, _ -> }, {}, {}, { _, _, _ -> }, { _, _, _, _ -> }, { _, _, _, _, _ -> }, {}, {}, {}) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FreelanceContent(s: FreelanceUi, onBack: () -> Unit, vm: FreelanceActions, initialSheet: Sheet? = null) {
    val haptics = LocalHaptics.current
    var sheet by remember { mutableStateOf(initialSheet) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.free_title), onBack)
        Text(stringResource(R.string.free_idea), style = TzType.body.copy(fontSize = 14.sp, lineHeight = 24.sp), color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp))
        when {
            s.loading -> LoadingRows()
            !s.on -> SalaryBody(null, DEFAULT_SALARY_DAY, R.string.free_on_confirm, inline = true) { salary, day -> haptics.perform(HapticLevel.Confirm); vm.turnOn(salary, day) }
            else -> {
                VaultCard(s)
                SalaryCard(s, onEdit = { sheet = Sheet.Salary })
                if (s.empty) {
                    EmptyState({ BlankSheet() }, R.string.free_empty_title, R.string.free_empty_line)
                } else {
                    ChartCard(s)
                    s.suggestion?.let { SuggestionCard(it, s.average, onApply = { haptics.perform(HapticLevel.Confirm); vm.applySuggestion(it) }, onKeep = vm.keepSalary) }
                }
                JobsCard(
                    s.jobs, s.owed,
                    onOpen = { sheet = Sheet.Actions(it) },
                    onAdd = { sheet = Sheet.Job(null) },
                    onRemind = vm.onRemind,
                )
                PrimaryButton(
                    stringResource(R.string.free_work_came),
                    { haptics.perform(HapticLevel.Tap); sheet = Sheet.Work },
                    Modifier.fillMaxWidth(), height = 60.dp, textStyle = TzType.button.copy(fontSize = 22.sp),
                )
            }
        }
    }
    when (val sh = sheet) {
        Sheet.Salary -> TzSheet({ sheet = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SalaryBody(s.salary, s.selfPayday ?: DEFAULT_SALARY_DAY, R.string.free_edit_save, inline = false) { salary, day ->
                    haptics.perform(HapticLevel.Confirm); vm.turnOn(salary, day); sheet = null
                }
            }
        }
        Sheet.Work -> WorkSheet(onDismiss = { sheet = null }) { amount, client, label ->
            haptics.perform(HapticLevel.Confirm); vm.workCame(amount, client, label); sheet = null
        }
        is Sheet.Job -> {
            val job = sh.id?.let { id -> s.jobs.firstOrNull { it.job.id == id }?.job }
            JobSheet(
                job?.client.orEmpty(), job?.label.orEmpty(), job?.amount, editing = job != null, onDismiss = { sheet = null },
            ) { client, label, amount, due ->
                haptics.perform(HapticLevel.Confirm)
                if (job == null) vm.addJob(client, label, amount, due ?: DEFAULT_DUE_DAYS)
                else vm.editJob(job, client, label, amount, due)
                sheet = null
            }
        }
        is Sheet.Actions -> s.jobs.firstOrNull { it.job.id == sh.id }?.let { j ->
            JobActionsSheet(
                j, onDismiss = { sheet = null },
                onPaid = { haptics.perform(HapticLevel.Confirm); vm.markPaid(j.job.id); sheet = null },
                onEdit = { sheet = Sheet.Job(j.job.id) },
                onDelete = { haptics.perform(HapticLevel.Heavy); vm.deleteJob(j.job.id); sheet = null },
            )
        }
        null -> Unit
    }
}

/** The two questions of the self-salary: how much and which day; also the turn-on block ([inline]) of a first visit. */
@Composable
private fun SalaryBody(initial: Long?, initialDay: Int, confirm: Int, inline: Boolean, onConfirm: (Long, Int) -> Unit) {
    var input by remember { mutableStateOf(AmountInput.of(initial ?: 0)) }
    var day by remember { mutableIntStateOf(initialDay) }
    val amount = input.toPiastres()
    val amountBlock: @Composable () -> Unit = { AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() }) }
    val dayBlock: @Composable () -> Unit = {
        Stepper(day.toString(), R.string.fixed_day_minus, R.string.fixed_day_plus, { day = (day - 1).coerceIn(1, 31) }, { day = (day + 1).coerceIn(1, 31) })
    }
    if (inline) {
        FormBlock(R.string.free_on_amount_q, amountBlock)
        FormBlock(R.string.free_on_day_q, dayBlock)
    } else {
        Text(stringResource(R.string.free_on_amount_q), style = TzType.cardHeading, color = Tz.text)
        amountBlock()
        Text(stringResource(R.string.free_on_day_q), style = TzType.cardHeading, color = Tz.text)
        dayBlock()
    }
    PrimaryButton(stringResource(confirm), { onConfirm(amount, day) }, Modifier.fillMaxWidth(), enabled = amount > 0, height = 52.dp)
}

@Composable
private fun bufferWords(months: Int, half: Boolean): String {
    val base = when {
        months == 0 -> stringResource(R.string.free_m_half)
        months == 1 -> stringResource(R.string.free_m_1)
        months == 2 -> stringResource(R.string.free_m_2)
        months <= 10 -> stringResource(R.string.free_m_few, months.toString())
        else -> stringResource(R.string.free_m_many, months.toString())
    }
    return if (half && months > 0) stringResource(R.string.free_and_half, base) else base
}

/** The drawn tank filled to the buffer level, the amount, how many months it covers and the day it is safe until. */
@Composable
private fun VaultCard(s: FreelanceUi) {
    val shape = RoundedCornerShape(28.dp)
    val level by animateFloatAsState(s.tank, motionSpec(TzSpring.Move), label = "vaultLevel")
    val covers = if (s.buffer == 0) stringResource(R.string.free_buffer_none) else stringResource(R.string.free_buffer, bufferWords(s.bufferMonths, s.bufferHalf))
    val cd = stringResource(R.string.free_tank_cd, covers)
    val tank: @Composable () -> Unit = { Tank(level, Modifier.size(width = 96.dp, height = 150.dp).semantics { contentDescription = cd }) }
    val texts: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.free_vault_label), style = TzType.label.copy(fontSize = 13.sp), color = Tz.text2)
            Odometer(s.vault, TzType.hero)
            Text(covers, style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.turquoise)
            s.safeUntil?.takeIf { s.buffer > 0 }?.let {
                Text(stringResource(R.string.free_safe_until, formatDayMonth(it, LocalConfiguration.current.locales[0])), style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 19.sp), color = Tz.text2)
            }
        }
    }
    val box = Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(20.dp)
    // At a large font the tank sits above the words instead of squeezing them.
    if (isLargeFont()) {
        Column(box, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { tank(); texts(Modifier.fillMaxWidth()) }
    } else {
        Row(box, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { tank(); texts(Modifier.weight(1f)) }
    }
}

/** 96 x 150 as drawn: night body, turquoise outline, the fill from the bottom, a yellow mark at three months and a dark one at one. */
@Composable
private fun Tank(level: Float, modifier: Modifier) {
    Canvas(modifier) {
        fun u(v: Float) = v * size.width / 96f
        val bodyTopLeft = Offset(u(6f), u(6f))
        val body = Size(u(84f), u(138f))
        drawRoundRect(Tz.night, bodyTopLeft, body, CornerRadius(u(18f)))
        val inner = u(126f)
        val bottom = u(138f)
        val fill = inner * level.coerceIn(0f, 1f)
        if (fill > 0f) drawRoundRect(Tz.turquoise, Offset(u(12f), bottom - fill), Size(u(72f), fill), CornerRadius(u(12f)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(u(5f), u(5f)))
        for ((months, color) in listOf(3f to Tz.yellow, 1f to Tz.turquoiseInk)) {
            val y = bottom - inner * months / TANK_MARK_MONTHS
            drawLine(color, Offset(u(12f), y), Offset(u(84f), y), u(2f), pathEffect = dash)
        }
        drawRoundRect(Tz.turquoise, bodyTopLeft, body, CornerRadius(u(18f)), style = Stroke(u(3f)))
    }
}

private const val TANK_MARK_MONTHS = 4f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalaryCard(s: FreelanceUi, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val haptics = LocalHaptics.current
    val day = s.selfPayday ?: DEFAULT_SALARY_DAY
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.yellow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Text(
                if (day == 1) stringResource(R.string.free_pay_first) else stringResource(R.string.free_pay_day, day.toString()),
                style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.night,
            )
            SmallPill(stringResource(R.string.free_edit), Tz.night, 2.dp, TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), onEdit)
        }
        CappedFontScale {
            FlowRow(itemVerticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Odometer(s.salary ?: 0L, TzType.hero.copy(fontSize = 64.sp, lineHeight = 64.sp, color = Tz.night))
                Text(stringResource(R.string.paper_currency), style = TzType.titlePushed.copy(fontSize = 20.sp), color = Tz.night, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        val ticket = poundsText(s.dailyTicket)
        val line = when (val d = s.daysToPayday) {
            null -> null
            0 -> stringResource(R.string.free_next_today, ticket)
            1 -> stringResource(R.string.free_next_tomorrow, ticket)
            else -> stringResource(R.string.free_next_days, d.toString(), ticket)
        }
        if (line != null) Text(line, style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Tz.yellowInk)
    }
}

/** Six months of income into the vault as bars against the dashed salary line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(s: FreelanceUi) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.free_chart_title), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.free_chart_avg, poundsText(s.average)), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
        // shortcut: the income chart is fixed-size art, its month labels stay at normal size (six columns a few dp wide).
        CappedFontScale { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
                    Row(Modifier.fillMaxWidth().height(CHART_HEIGHT), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        s.bars.forEach { bar ->
                            val f by animateFloatAsState(bar.fraction, motionSpec(TzSpring.Move), label = "incomeBar")
                            val color = when (bar.tone) {
                                BarTone.OVER -> Tz.turquoise
                                BarTone.UNDER -> Tz.line
                                BarTone.EMPTY -> Tz.carnelian
                            }
                            val h = if (bar.tone == BarTone.EMPTY) 4.dp else (CHART_HEIGHT * f).coerceAtLeast(4.dp)
                            val corner = if (bar.tone == BarTone.EMPTY) 2.dp else 8.dp
                            Box(Modifier.weight(1f).height(h).clip(RoundedCornerShape(corner)).background(color))
                        }
                    }
                    s.line?.let { line ->
                        Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
                            val y = size.height * (1f - line)
                            drawLine(Tz.yellow, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.bars.forEach { bar ->
                        Text(monthLabel(bar.month, false), Modifier.weight(1f), style = TzType.caption.copy(fontSize = 10.sp), color = Tz.text2, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                    }
                }
            }
        } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(width = 18.dp, height = 2.dp)) {
                drawLine(Tz.yellow, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            val salary = poundsText(s.salary ?: 0L)
            val empty = s.emptyMonth
            Text(
                if (empty != null) stringResource(R.string.free_chart_empty, salary, monthLabel(empty, false)) else stringResource(R.string.free_chart_plain, salary),
                style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 19.sp), color = Tz.text2,
            )
        }
    }
}

private val CHART_HEIGHT = 130.dp

/** Raise or lower: the amount is the repository's; applying only happens on the tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionCard(sg: SalarySuggestion, average: Long, onApply: () -> Unit, onKeep: () -> Unit) {
    val raise = sg.kind == SuggestionKind.RAISE
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.turquoiseDim).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (raise) R.string.free_raise_title else R.string.free_lower_title), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.text)
        Text(
            stringResource(if (raise) R.string.free_raise_body else R.string.free_lower_body, poundsText(average), poundsText(sg.amount)),
            style = TzType.body.copy(fontSize = 13.sp, lineHeight = 22.sp), color = Tz.turquoiseDimText,
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.free_suggest_yes, poundsText(sg.amount)), true, Tz.turquoise, Tz.turquoiseInk, onApply, Modifier.weight(1f))
            PillButton(stringResource(R.string.free_suggest_keep), false, Tz.turquoise, Tz.turquoiseInk, onKeep, Modifier.weight(1f))
        }
    }
}

private fun jobTitle(j: ClientJobView) = if (j.job.label.isBlank()) j.job.client else "${j.job.label} · ${j.job.client}"

@Composable
private fun lateText(days: Int): String = when {
    days == 1 -> stringResource(R.string.free_late_1)
    days == 2 -> stringResource(R.string.free_late_2)
    days <= 10 -> stringResource(R.string.free_late_few, days.toString())
    else -> stringResource(R.string.free_late_many, days.toString())
}

/** Money still with clients: overdue ones in carnelian with "فكّره بلُطف"; a tap opens what can be done with the job. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobsCard(jobs: List<ClientJobView>, owed: Long, onOpen: (Long) -> Unit, onAdd: () -> Unit, onRemind: (ClientJobView) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)) {
        FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.free_jobs_title), style = TzType.cardHeading, color = Tz.text)
            Text(poundsText(owed), style = TzType.cardHeading.copy(fontSize = 18.sp), color = Tz.yellow)
        }
        jobs.forEach { j ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
            SplitRow(
                Modifier.fillMaxWidth().pressClickable { haptics.perform(HapticLevel.Tap); onOpen(j.job.id) }.defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                info = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(jobTitle(j), style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
                        Text(
                            if (j.overdue) lateText(j.daysLate) else stringResource(R.string.free_due_day, j.job.dueDate.dayOfMonth.toString()),
                            style = TzType.caption.copy(fontSize = 12.sp), color = if (j.overdue) Tz.carnelianText else Tz.text2,
                        )
                    }
                },
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(poundsText(j.job.amount), style = TzType.rowNumber, color = Tz.text)
                    if (j.overdue) SmallPill(stringResource(R.string.free_remind), Tz.yellow, 1.dp, TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), { onRemind(j) })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
        TextLink(stringResource(R.string.free_job_add), { haptics.perform(HapticLevel.Tap); onAdd() }, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** "جالي شغل": the amount, and optionally who it is from and what for; it lands in the vault. */
@Composable
private fun WorkSheet(onDismiss: () -> Unit, onConfirm: (Long, String, String) -> Unit) {
    var input by remember { mutableStateOf(AmountInput()) }
    var client by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val amount = input.toPiastres()
    TzSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.free_work_title), style = TzType.cardHeading, color = Tz.text)
            AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            NameField(client, { client = it.take(FIELD_MAX) }, R.string.free_client_hint)
            NameField(label, { label = it.take(FIELD_MAX) }, R.string.free_label_hint)
            PrimaryButton(stringResource(R.string.free_work_confirm), { onConfirm(amount, client, label) }, Modifier.fillMaxWidth(), enabled = amount > 0, height = 52.dp)
        }
    }
}

private const val FIELD_MAX = 30
private val DUE_CHOICES = listOf(0 to R.string.free_due_today, 7 to R.string.free_due_week, 14 to R.string.free_due_2weeks, 30 to R.string.free_due_month)

/** A job: client, what for, how much, and when it is due (a chip; an edit keeps its date until one is tapped). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobSheet(client0: String, label0: String, amount0: Long?, editing: Boolean, onDismiss: () -> Unit, onConfirm: (String, String, Long, Int?) -> Unit) {
    var client by remember { mutableStateOf(client0) }
    var label by remember { mutableStateOf(label0) }
    var input by remember { mutableStateOf(AmountInput.of(amount0 ?: 0)) }
    var days by remember { mutableStateOf<Int?>(if (editing) null else DEFAULT_DUE_DAYS) }
    val amount = input.toPiastres()
    TzSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (editing) R.string.free_job_edit_title else R.string.free_job_new_title), style = TzType.cardHeading, color = Tz.text)
            NameField(client, { client = it.take(FIELD_MAX) }, R.string.free_job_client_hint)
            NameField(label, { label = it.take(FIELD_MAX) }, R.string.free_label_hint)
            AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            Text(stringResource(R.string.free_job_due_q), style = TzType.caption, color = Tz.text2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DUE_CHOICES.forEach { (d, text) -> TzChip(stringResource(text), days == d, { days = d }, style = ChipStyle.Choice) }
            }
            PrimaryButton(
                stringResource(R.string.subs_save),
                { onConfirm(client, label, amount, days) },
                Modifier.fillMaxWidth(), enabled = amount > 0 && client.isNotBlank(), height = 52.dp,
            )
        }
    }
}

@Composable
private fun JobActionsSheet(j: ClientJobView, onDismiss: () -> Unit, onPaid: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    TzSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(jobTitle(j), style = TzType.cardHeading, color = Tz.text)
            CappedFontScale(LARGE_FONT_SCALE) { Text(poundsText(j.job.amount), style = TzType.hero, color = Tz.text) }
            PrimaryButton(stringResource(R.string.free_job_paid), onPaid, Modifier.fillMaxWidth(), height = 52.dp)
            SecondaryButton(stringResource(R.string.free_job_edit), onEdit, Modifier.fillMaxWidth(), height = 52.dp)
            TextLink(stringResource(R.string.free_job_delete), onDelete, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
