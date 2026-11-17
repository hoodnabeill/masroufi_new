package com.masroufi.app.feature.people

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.GameyaView
import com.masroufi.app.data.repo.PeopleFilter
import com.masroufi.app.data.repo.PeopleTotals
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.PlusComingSheet
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.BlankSheet
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val LilacInk = Color(0xFF2A0E38)
private val LilacText = Color(0xFFF6E4FF)
private val LilacMuted = Color(0xFFD9B8EA)
private val CardShape = RoundedCornerShape(24.dp)

/** الناس (People.dc.html): the two totals, filter chips, the people, the duty-ledger card, the gameya card and the quick-split hint. */
@Composable
fun PeopleScreen(viewModel: PeopleViewModel, onOpenPerson: (Long) -> Unit, onOpenDuty: () -> Unit, onOpenGameya: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val remind = rememberReminder(viewModel::reminded)
    var plusSheet by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_people), style = TzType.titleTab, color = Tz.text)
                CircleButton(TzIcons.add, stringResource(R.string.people_add_cd)) { haptics.perform(HapticLevel.Tap); viewModel.openAdd() }
            }
        }
        if (ui.notice != null) item(key = "notice") { NoticeLine(ui.notice, viewModel::clearNotice) }
        item(key = "totals") { Totals(ui.totals) }
        item(key = "chips") { FilterChips(ui.filter, viewModel::setFilter) }
        when {
            ui.loading -> items(3, key = { "skeleton$it" }) { RowSkeleton() }
            ui.rows.isEmpty() -> item(key = "empty") {
                EmptyState({ BlankSheet() }, R.string.empty_people_title, R.string.empty_people_line, R.string.empty_people_action, viewModel::openAdd, primaryAction = true)
            }
            else -> items(ui.rows, key = { it.row.person.id }) { r ->
                val i = ui.rows.indexOf(r)
                PersonRowCard(
                    r, first = i == 0, last = i == ui.rows.lastIndex,
                    onOpen = { onOpenPerson(r.row.person.id) },
                    onAction = { if (r.action == RowAction.REMIND) remind(r.row.person.name, r.row.owedToMe) else viewModel.onAction(r.row) },
                )
            }
        }
        item(key = "duty") {
            if (ui.plus) DutyCard(ui.duty, onOpenDuty)
            else PlusLocked(R.string.people_duty_plus, { plusSheet = true }) { DutyCard(DutyCardUi(), {}) }
        }
        item(key = "gameya") { GameyaCard(ui.gameya, onOpenGameya) }
        item(key = "split") { SplitHint(viewModel::openSplit) }
    }
    PeopleSheets(ui, viewModel)
    if (plusSheet) PlusComingSheet { plusSheet = false }
}

@Composable
private fun Totals(t: PeopleTotals) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).clip(CardShape).background(Tz.turquoise).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.people_owed_title), style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Tz.turquoiseInk)
            Text(formatPounds(t.theyOwe), style = TzType.hero.copy(fontSize = 40.sp, lineHeight = 42.sp), color = Tz.turquoiseInk)
            Text(fromPeople(t.theyOweCount), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.turquoiseInk)
        }
        Column(Modifier.weight(1f).clip(CardShape).background(Tz.surface).border(1.dp, Tz.line, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.people_owe_title), style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Tz.text2)
            Text(formatPounds(t.iOwe), style = TzType.hero.copy(fontSize = 40.sp, lineHeight = 42.sp), color = Tz.text)
            Text(
                when {
                    t.onTab > 0 -> stringResource(R.string.people_owe_on_tab, formatPounds(t.onTab))
                    t.iOwe == 0L -> stringResource(R.string.people_owe_none)
                    else -> ""
                },
                style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2,
            )
        }
    }
}

@Composable
private fun fromPeople(n: Int): String = when {
    n <= 0 -> stringResource(R.string.people_from_none)
    n == 1 -> stringResource(R.string.people_from_one)
    n == 2 -> stringResource(R.string.people_from_two)
    n <= 10 -> stringResource(R.string.people_from_few, n.toString())
    else -> stringResource(R.string.people_from_many, n.toString())
}

@Composable
private fun FilterChips(selected: PeopleFilter, onSelect: (PeopleFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            PeopleFilter.ALL to R.string.people_filter_all, PeopleFilter.LOANS to R.string.people_filter_loans,
            PeopleFilter.TAB to R.string.people_filter_tab, PeopleFilter.DUTY to R.string.people_filter_duty,
        ).forEach { (f, label) -> TzChip(stringResource(label), selected == f, { onSelect(f) }, style = ChipStyle.Filter) }
    }
}

@Composable
private fun ageText(days: Long): String = when {
    days <= 0 -> stringResource(R.string.people_age_today)
    days < 14 -> stringResource(R.string.people_age_days, days.toString())
    days < 60 -> stringResource(R.string.people_age_weeks, (days / 7).toString())
    else -> stringResource(R.string.people_age_months, (days / 30).toString())
}

@Composable
internal fun formatShortDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("d MMMM", LocalConfiguration.current.locales[0]).withDecimalStyle(DecimalStyle.STANDARD).format(date)

/** "سلفة من 3 أسابيع · ميعادها 20 ديسمبر": what the row is about, from the data. */
@Composable
private fun contextLine(ui: PersonRowUi): String {
    val r = ui.row
    val age = ageText(ui.days ?: 0)
    val first = when {
        r.tabItems > 0 && r.openDebts == 0 -> stringResource(R.string.people_ctx_tab, r.tabItems.toString())
        r.owedToMe > 0 && r.iOweLoans > 0 -> stringResource(R.string.people_ctx_mixed, age)
        r.owedToMe > 0 -> stringResource(R.string.people_ctx_lent, age)
        r.iOweLoans > 0 -> stringResource(R.string.people_ctx_borrowed, age)
        else -> stringResource(R.string.people_ctx_duty)
    }
    val next = r.nextDue
    val due = when {
        ui.overdue -> stringResource(R.string.people_ctx_overdue)
        next != null -> stringResource(R.string.people_ctx_due, formatShortDate(next))
        else -> null
    }
    return listOfNotNull(first, due).joinToString(" · ")
}

/** One row of the list card; neighbours overlap by their 1 dp borders so the card reads as one. */
@Composable
private fun PersonRowCard(ui: PersonRowUi, first: Boolean, last: Boolean, onOpen: () -> Unit, onAction: () -> Unit) {
    val r = ui.row
    val top = if (first) 24.dp else 0.dp
    val bottom = if (last) 24.dp else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    Row(
        Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.offset(y = (-1).dp)).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
            .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(r.person)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.person.name, style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text, maxLines = 1)
            Text(contextLine(ui), style = TzType.caption.copy(fontSize = 12.sp), color = if (ui.overdue) Tz.carnelianText else Tz.text2, maxLines = 2)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    r.balance > 0 -> stringResource(R.string.people_they_owe, formatPounds(r.balance))
                    r.balance < 0 -> stringResource(R.string.people_i_owe, formatPounds(abs(r.balance)))
                    else -> stringResource(R.string.people_even)
                },
                style = TzType.rowNumber, color = if (r.balance > 0) Tz.turquoise else Tz.text,
            )
            when (ui.action) {
                RowAction.REMIND -> SmallPill(stringResource(R.string.people_act_remind), false, onAction)
                RowAction.SETTLE_TAB -> SmallPill(stringResource(R.string.people_act_settle_tab), true, onAction)
                RowAction.REPAY -> SmallPill(stringResource(R.string.people_act_repay), true, onAction)
                null -> Unit
            }
        }
    }
}

/** The lilac card; [duty] is empty behind the Plus lock. Tapping it opens the ledger (Task 38). */
@Composable
private fun DutyCard(duty: DutyCardUi, onOpen: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Tz.lilac).clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpen() }.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.people_duty_title), style = TzType.cardHeading.copy(fontSize = 24.sp), color = LilacInk)
            Text(
                stringResource(R.string.people_duty_badge),
                style = TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.lilac,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(LilacInk).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Text(stringResource(R.string.people_duty_line), style = TzType.body.copy(fontSize = 13.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium), color = LilacInk)
        duty.upcoming?.let { u ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LilacInk).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${u.occasion.name} · ${formatShortDate(u.occasion.date)}",
                        style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = LilacText, modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (u.daysUntil == 0L) stringResource(R.string.people_duty_today) else stringResource(R.string.people_duty_in_days, u.daysUntil.toString()),
                        style = TzType.caption.copy(fontSize = 12.sp), color = Tz.lilac,
                    )
                }
                u.gaveMe?.let { gave ->
                    DutyLine(stringResource(R.string.people_duty_gave), formatPounds(gave), LilacText)
                    DutyLine(stringResource(R.string.people_duty_return), stringResource(R.string.people_duty_or_more, formatPounds(gave)), Tz.yellow)
                }
            }
        }
        if (duty.ownGivers > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.people_duty_own, formatPounds(duty.ownTotal), duty.ownGivers.toString()),
                    style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = LilacInk, modifier = Modifier.weight(1f),
                )
                TzIcon(TzIcons.chevron, 18.dp, null, tint = LilacInk)
            }
        }
    }
}

@Composable
private fun DutyLine(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TzType.caption.copy(fontSize = 13.sp), color = LilacMuted)
        Text(value, style = TzType.rowNumber.copy(fontSize = 18.sp), color = valueColor)
    }
}

@Composable
private fun GameyaCard(g: GameyaView?, onOpen: () -> Unit) {
    val haptics = LocalHaptics.current
    val progress = if (g == null) 0f else (g.monthNumber.toFloat() / g.people).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(Tz.surface).border(1.dp, Tz.line, CardShape)
            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpen() }.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(56.dp)) {
            val stroke = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
            val inset = 7.dp.toPx()
            val box = Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(Tz.line, 0f, 360f, false, Offset(inset, inset), box, style = stroke)
            if (progress > 0f) drawArc(Tz.yellow, -90f, 360f * progress, false, Offset(inset, inset), box, style = stroke)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(g?.gameya?.name ?: stringResource(R.string.people_gameya_empty), style = TzType.cardHeading, color = Tz.text)
            if (g != null) {
                val locale: Locale = LocalConfiguration.current.locales[0]
                Text(
                    if (g.finished) stringResource(R.string.people_gameya_done)
                    else stringResource(
                        R.string.people_gameya_line, g.monthNumber.toString(), g.people.toString(), g.gameya.myTurn.toString(),
                        g.myTurnMonth.month.getDisplayName(TextStyle.FULL, locale), formatPounds(g.payout),
                    ),
                    style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2,
                )
            } else {
                Text(stringResource(R.string.people_gameya_empty_line), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
            }
        }
        TzIcon(TzIcons.chevron, 18.dp, null, tint = Tz.text2)
    }
}

@Composable
private fun SplitHint(onOpen: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpen() }
            .drawBehind {
                drawRoundRect(
                    Tz.dashed, cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.people_split_title), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
        Text(stringResource(R.string.people_split_line), style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = Tz.text2)
    }
}
