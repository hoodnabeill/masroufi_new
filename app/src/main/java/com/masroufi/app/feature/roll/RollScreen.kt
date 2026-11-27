package com.masroufi.app.feature.roll

import androidx.compose.foundation.layout.FlowRow
import com.masroufi.app.ui.components.isLargeFont
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatDayDate
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.repo.RollDay
import com.masroufi.app.data.repo.RollFilter
import com.masroufi.app.data.repo.RollSummary
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.normalize
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.add.EditTarget
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.UndoBar
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzRow
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.skeletonPulse
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.paper.BlankSheet
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Lalezar
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.TextStyle
import kotlin.math.abs

const val RollSkeletonTag = "rollSkeleton"
private const val SKELETON_AFTER_MS = 150L
private const val ENTRIES_FADE_DELAY_MS = 40

/** الدفتر (Roll.dc.html). Leaving the screen writes any delete still waiting its 5 s. */
@Composable
fun RollScreen(
    viewModel: RollViewModel,
    onOpenAddSheet: (AddPrefill?) -> Unit,
    onPayday: (Long) -> Unit = {},
    onOpenInstallments: () -> Unit = {},
    installmentIds: InstallmentIdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installments by installmentIds.ids.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val open by rememberUpdatedState(onOpenAddSheet)
    LaunchedEffect(viewModel) { viewModel.committed.collect { haptics.perform(HapticLevel.Heavy) } }
    DisposableEffect(viewModel) { onDispose { if (viewModel.flush()) haptics.perform(HapticLevel.Heavy) } }
    CompositionLocalProvider(LocalInstallmentLinks provides InstallmentLinks(installments, onOpenInstallments)) {
    RollContent(
        state,
        onFilter = viewModel::setFilter,
        onMonth = viewModel::selectMonth,
        onSearch = { if (state.searching) viewModel.closeSearch() else viewModel.openSearch() },
        onQuery = viewModel::setQuery,
        onEntry = { e -> open(e.editPrefill()) },
        onPayday = onPayday,
        onAdd = { open(null) },
        onUndo = viewModel::undo,
    )
    }
}

internal fun EntryRow.editPrefill() = AddPrefill(
    amount = amount,
    categoryId = categoryId,
    note = note,
    type = kind,
    pocketId = pocketId,
    toPocketId = toPocketId,
    fee = fee,
    edit = EditTarget(id, happenedAt, recurringId, roundUp, ticketPunch()),
)

private enum class Body { Skeleton, Content }

/** Stateless roll: skeleton after 150 ms of loading, 160 ms cross-fade to the content. */
@Composable
fun RollContent(
    state: RollUi,
    onFilter: (RollFilter) -> Unit,
    onMonth: (YearMonth) -> Unit,
    onSearch: () -> Unit,
    onQuery: (String) -> Unit,
    onEntry: (EntryRow) -> Unit,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    onPayday: (Long) -> Unit = {},
) {
    var skeletonDue by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        skeletonDue = false
        if (state.loading) {
            delay(SKELETON_AFTER_MS)
            skeletonDue = true
        }
    }
    var picking by remember { mutableStateOf(false) }
    // Days the user flipped from their default (today open, the rest closed).
    var flipped by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val searchActive = state.searching && state.query.isNotBlank()

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(state, onSearch, onPickMonth = { picking = true })
            AnimatedVisibility(state.searching) { SearchField(state.query, onQuery) }
            if (!state.searching) {
                Tiles(state.summary)
                FilterChips(state.filter, onFilter)
            }
            Crossfade(
                if (state.loading && skeletonDue) Body.Skeleton else Body.Content,
                Modifier.weight(1f),
                animationSpec = tween(160),
                label = "roll",
            ) { body ->
                if (body == Body.Skeleton) {
                    Column(Modifier.testTag(RollSkeletonTag).skeletonPulse(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        repeat(6) { RowSkeleton() }
                    }
                } else {
                    Entries(
                        state, searchActive, flipped,
                        { d -> flipped = if (d in flipped) flipped - d else flipped + d },
                        onQuery, onEntry, onAdd, onPayday,
                    )
                }
            }
        }
        UndoBar(state.undoVisible, onUndo, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    }
    if (picking) MonthSheet(state, onDismiss = { picking = false }, onPick = { onMonth(it); picking = false })
}

@Composable
private fun Entries(
    state: RollUi,
    searchActive: Boolean,
    flipped: List<Long>,
    onToggle: (Long) -> Unit,
    onQuery: (String) -> Unit,
    onEntry: (EntryRow) -> Unit,
    onAdd: () -> Unit,
    onPayday: (Long) -> Unit,
) {
    val results = state.results
    val listPadding = PaddingValues(bottom = 24.dp)
    when {
        searchActive && results != null && results.isEmpty() -> Column(Modifier.verticalScroll(rememberScrollState())) {
            EmptyState(
                { Qersh(QershMood.Thinking, 96.dp) }, R.string.empty_search_title, R.string.empty_search_line,
                R.string.empty_search_action, { onQuery("") },
            )
        }
        searchActive && results != null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = listPadding) {
            items(results, key = { it.id }) { e -> ResultCard(e, state.categories, state.query, onEntry) }
        }
        searchActive -> Unit
        !state.loading && state.days.isEmpty() ->
            if (state.filter == RollFilter.ALL) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    EmptyState(
                        { BlankSheet(width = 150.dp) }, R.string.empty_roll_title, R.string.empty_roll_line,
                        R.string.empty_roll_action, onAdd, primaryAction = true,
                    )
                }
            } else {
                Text(stringResource(R.string.roll_filter_empty), style = TzType.body, color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp))
            }
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = listPadding) {
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                val expanded = (day.date == state.today) != (day.date.toEpochDay() in flipped)
                DayStub(day, state.today, expanded, state.categories, { onToggle(day.date.toEpochDay()) }, onEntry, onPayday = onPayday)
            }
        }
    }
}

@Composable
private fun monthLabel(m: YearMonth, now: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    val name = m.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    return if (m.year == now.year) name else "$name ${m.year}"
}

@Composable
private fun Header(state: RollUi, onSearch: () -> Unit, onPickMonth: () -> Unit) {
    val haptics = LocalHaptics.current
    val now = YearMonth.from(state.today)
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.tab_roll), style = TzType.titleTab, color = Tz.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = stringResource(if (state.searching) R.string.roll_search_close else R.string.roll_search)
            Box(
                Modifier.minimumInteractiveComponentSize()
                    .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onSearch() }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Tz.surface).border(1.dp, Tz.line, CircleShape), contentAlignment = Alignment.Center) {
                    TzIcon(if (state.searching) TzIcons.close else TzIcons.search, 20.dp, null, tint = Tz.text)
                }
            }
            val pill = RoundedCornerShape(22.dp)
            Row(
                Modifier.heightIn(min = 48.dp).clip(pill).background(Tz.surface).border(1.dp, Tz.line, pill)
                    .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onPickMonth() }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(monthLabel(state.month, now), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
                TzIcon(TzIcons.chevronDown, 14.dp, null, tint = Tz.text)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val hint = stringResource(R.string.roll_search_hint)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(Tz.surface).border(1.dp, Tz.yellow, shape)
            .focusRequester(focus).semantics { contentDescription = hint },
        textStyle = TzType.label.copy(fontWeight = FontWeight.Normal, color = Tz.text),
        singleLine = true,
        cursorBrush = SolidColor(Tz.yellow),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(hint, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
private fun Tiles(s: RollSummary) {
    val closed = stringResource(R.string.roll_tile_closed_value, s.ticketsUnder, s.ticketsClosed)
    if (isLargeFont()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(R.string.roll_tile_spent, poundsText(s.spent), Tz.text, Modifier.fillMaxWidth())
            Tile(R.string.roll_tile_received, poundsText(s.received), Tz.turquoise, Modifier.fillMaxWidth())
            Tile(R.string.roll_tile_closed, closed, Tz.yellow, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(R.string.roll_tile_spent, poundsText(s.spent), Tz.text, Modifier.weight(1f))
            Tile(R.string.roll_tile_received, poundsText(s.received), Tz.turquoise, Modifier.weight(1f))
            Tile(R.string.roll_tile_closed, closed, Tz.yellow, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(label: Int, value: String, color: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(label), style = TzType.caption.copy(fontSize = 11.sp), color = Tz.text2, maxLines = 1)
        Text(value, style = TzType.titleTab.copy(fontSize = 22.sp), color = color, maxLines = 1)
    }
}

@Composable
private fun FilterChips(selected: RollFilter, onFilter: (RollFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            RollFilter.ALL to R.string.roll_filter_all,
            RollFilter.SPENT to R.string.roll_filter_spent,
            RollFilter.INCOME to R.string.roll_filter_income,
            RollFilter.RECURRING to R.string.roll_filter_recurring,
        ).forEach { (f, label) -> TzChip(stringResource(label), f == selected, { onFilter(f) }, style = ChipStyle.Filter) }
    }
}

@Composable
private fun MonthSheet(state: RollUi, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    val now = YearMonth.from(state.today)
    TzSheet(onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).verticalScroll(rememberScrollState())) {
            state.months.forEach { m ->
                TzRow(
                    leading = { TzIcon(TzIcons.calendar, 20.dp, null) },
                    title = monthLabel(m, now),
                    caption = null,
                    trailing = { if (m == state.month) TzIcon(TzIcons.check, 20.dp, null, tint = Tz.yellow) },
                    onClick = { onPick(m) },
                )
            }
        }
    }
}

// ---- day stubs ----

private enum class Tone { Open, Under, Over, Neutral, Income }

private class Look(val block: Color, val ink: Color, val line: Color)

private fun Tone.look() = when (this) {
    Tone.Open -> Look(Tz.yellow, Tz.night, Tz.text2)
    Tone.Under -> Look(Tz.turquoise, Tz.turquoiseInk, Tz.turquoise)
    Tone.Over -> Look(Tz.carnelian, Tz.carnelianInk, Tz.carnelianText)
    Tone.Neutral -> Look(Tz.raised, Tz.text2, Tz.text2)
    Tone.Income -> Look(Tz.turquoise, Tz.turquoiseInk, Tz.turquoiseDimText)
}

private fun RollDay.tone(today: LocalDate): Tone {
    val t = ticket ?: return if (entries.any { it.kind == EntryKind.RECEIVED }) Tone.Income else Tone.Neutral
    return when {
        t.status == TicketStatus.OPEN && date == today -> Tone.Open
        t.spent > t.amount -> Tone.Over
        else -> Tone.Under
    }
}

/**
 * One day of the roll. Ticket days: a header (today's, or "صرفت X في N مرات" with its under/over line) that toggles
 * [expanded]; the entries open under it with a height spring and fade in 40 ms later. Days without a ticket
 * (a recurring bill, a salary) list their entries straight away.
 */
@Composable
internal fun DayStub(
    day: RollDay,
    today: LocalDate,
    expanded: Boolean,
    categories: Map<Long, CategoryEntity>,
    onToggle: () -> Unit,
    onEntry: (EntryRow) -> Unit,
    modifier: Modifier = Modifier,
    onPayday: (Long) -> Unit = {},
) {
    val tone = day.tone(today)
    val look = tone.look()
    val shape = RoundedCornerShape(24.dp)
    val salary = tone == Tone.Income
    val haptics = LocalHaptics.current
    val entries = remember(day.entries) { day.entries.sortedBy { it.happenedAt } }
    val ticket = day.ticket
    val dayNames = stringArrayResource(R.array.home_week_days)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape)
            .background(if (salary) Tz.turquoiseDim else Tz.surface)
            .then(if (salary) Modifier else Modifier.border(1.dp, Tz.line, shape)),
    ) {
        Column(
            Modifier.width(64.dp).fillMaxHeight().background(look.block)
                .drawBehind {
                    // The dashed tear line on the edge that faces the content.
                    val x = if (rtl) 1.dp.toPx() else size.width - 1.dp.toPx()
                    drawLine(Tz.night, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
                .padding(vertical = if (tone == Tone.Open) 16.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (tone == Tone.Open) Arrangement.Top else Arrangement.Center,
        ) {
            Text(dayNames[(day.date.dayOfWeek.value + 1) % 7], style = TzType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = look.ink)
            Text(
                day.date.dayOfMonth.toString(),
                style = TzType.titleTab.copy(fontSize = if (tone == Tone.Open) 34.sp else 30.sp, lineHeight = 1.em),
                color = look.ink,
            )
            if (tone == Tone.Open) {
                Text(
                    stringResource(R.string.roll_open),
                    Modifier.padding(top = 6.dp),
                    style = TzType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = look.ink,
                )
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (ticket != null) {
                Box(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onToggle() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    if (tone == Tone.Open) TodayHeader(ticket.spent, ticket.amount) else ClosedHeader(day, tone, look.line, expanded)
                }
                val reduce = LocalReduceMotion.current
                AnimatedVisibility(
                    expanded && entries.isNotEmpty(),
                    enter = if (reduce) EnterTransition.None else
                        expandVertically(TzSpring.Move.spec<IntSize>()) + fadeIn(tween(160, delayMillis = ENTRIES_FADE_DELAY_MS)),
                    exit = if (reduce) ExitTransition.None else shrinkVertically(TzSpring.Move.spec<IntSize>()) + fadeOut(tween(100)),
                ) {
                    EntryList(entries, categories, false, onEntry, Modifier.padding(horizontal = 14.dp))
                }
            } else {
                EntryList(entries, categories, true, onEntry, Modifier.padding(horizontal = 14.dp))
                // Phase 6 ruling: a salary day offers the ritual again (it never auto-offers twice for one income).
                if (salary) entries.filter { it.kind == EntryKind.RECEIVED }.forEach { e ->
                    TextLink(stringResource(R.string.payday_roll_link), { haptics.perform(HapticLevel.Tap); onPayday(e.id) }, Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TodayHeader(spent: Long, amount: Long) {
    val spentText = poundsText(spent)
    val text = stringResource(R.string.roll_today_of, spentText, poundsText(amount))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(stringResource(R.string.roll_today_label), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
        Text(
            buildAnnotatedString {
                append(text)
                addStyle(SpanStyle(fontFamily = Lalezar, fontSize = 22.sp, fontWeight = FontWeight.Normal, color = Tz.text), 0, spentText.length.coerceAtMost(text.length))
            },
            style = TzType.label.copy(fontWeight = FontWeight.Normal),
            color = Tz.text2,
        )
    }
}

@Composable
private fun ClosedHeader(day: RollDay, tone: Tone, lineColor: Color, expanded: Boolean) {
    val t = checkNotNull(day.ticket)
    val spent = poundsText(t.spent)
    val first = when (t.entryCount) {
        0 -> stringResource(R.string.roll_day_none)
        1 -> stringResource(R.string.roll_day_spent_once, spent)
        else -> stringResource(R.string.roll_day_spent, spent, t.entryCount)
    }
    val second = when {
        tone == Tone.Over -> stringResource(R.string.roll_over, poundsText(t.spent - t.amount))
        t.spent == t.amount -> stringResource(R.string.roll_exact)
        else -> {
            val x = poundsText(t.amount - t.spent)
            when (t.destination) {
                LeftoverDestination.JAR -> stringResource(R.string.roll_under_jar, x)
                LeftoverDestination.TOMORROW -> stringResource(R.string.roll_under_tomorrow, x)
                LeftoverDestination.EMERGENCY -> stringResource(R.string.roll_under_emergency, x)
                null -> stringResource(R.string.roll_under_plain, x)
            }
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val turn by animateFloatAsState(if (expanded) (if (rtl) -90f else 90f) else 0f, motionSpec(TzSpring.Snappy), label = "dayChevron")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(first, style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
            Text(second, style = TzType.caption.copy(fontSize = 12.sp), color = lineColor)
        }
        TzIcon(TzIcons.chevron, 18.dp, null, Modifier.rotate(turn))
    }
}

// ---- entry rows ----

@Composable
private fun EntryList(
    entries: List<EntryRow>,
    categories: Map<Long, CategoryEntity>,
    salaryDay: Boolean,
    onEntry: (EntryRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        entries.forEachIndexed { i, e -> EntryLine(e, categories, salaryDay, "", first = i == 0, onEntry) }
    }
}

@Composable
private fun entryTitle(e: EntryRow, salaryDay: Boolean): String {
    val cat = e.categoryName
    val note = e.note?.takeIf { it.isNotBlank() }
    return when (e.kind) {
        EntryKind.SPENT -> when {
            cat != null && note != null -> stringResource(R.string.roll_entry_title_note, cat, note)
            cat != null -> cat
            note != null -> note
            else -> stringResource(R.string.roll_entry_spent)
        }
        EntryKind.RECEIVED -> note ?: stringResource(if (salaryDay) R.string.roll_entry_salary else R.string.roll_entry_income)
        EntryKind.MOVED -> stringResource(R.string.add_transfer_line, e.pocketName, e.toPocketName.orEmpty())
        EntryKind.ADJUST -> stringResource(R.string.roll_entry_adjust)
        EntryKind.DEBT -> note ?: stringResource(R.string.roll_entry_debt)
    }
}

@Composable
private fun entrySub(e: EntryRow): String {
    if (e.recurringId != null) return stringResource(R.string.roll_recurring)
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(e.happenedAt, locale) {
        DateTimeFormatter.ofPattern("h:mm a", locale).withDecimalStyle(DecimalStyle.STANDARD)
            .format(Instant.ofEpochMilli(e.happenedAt).atZone(ZoneId.systemDefault()))
    }
    var s = stringResource(R.string.roll_entry_sub, e.pocketName, time)
    if (e.roundUp > 0) s += " · " + stringResource(R.string.roll_entry_roundup, poundsText(e.roundUp))
    if (e.fee > 0) s += " · " + stringResource(R.string.roll_entry_fee, poundsText(e.fee))
    return s
}

private fun categoryColor(c: CategoryEntity?): Color = c?.color?.takeIf { it ushr 24 != 0 }?.let { Color(it) } ?: Tz.turquoise

@Composable
internal fun EntryLine(
    e: EntryRow,
    categories: Map<Long, CategoryEntity>,
    salaryDay: Boolean,
    highlight: String,
    first: Boolean,
    onEntry: (EntryRow) -> Unit,
) {
    val haptics = LocalHaptics.current
    val cat = e.categoryId?.let(categories::get)
    val color = when (e.kind) {
        EntryKind.SPENT -> categoryColor(cat)
        EntryKind.RECEIVED -> Tz.turquoise
        EntryKind.MOVED -> Tz.sky
        EntryKind.ADJUST -> Tz.text2
        EntryKind.DEBT -> Tz.lilac
    }
    val icon = when (e.kind) {
        EntryKind.SPENT -> TzIcons.all.firstOrNull { it.name == cat?.icon } ?: TzIcons.grid
        EntryKind.RECEIVED -> TzIcons.arrowUp
        EntryKind.MOVED -> TzIcons.swap
        EntryKind.ADJUST -> TzIcons.refresh
        EntryKind.DEBT -> TzIcons.people
    }
    val amount = when (e.kind) {
        EntryKind.RECEIVED -> "+" + poundsText(e.amount)
        EntryKind.ADJUST, EntryKind.DEBT -> (if (e.amount < 0) "-" else "+") + poundsText(abs(e.amount))
        else -> poundsText(e.amount)
    }
    // An adjustment or a debt entry is the app's own record (a debt is edited from People), not something to edit or delete here.
    val open = if (e.kind == EntryKind.ADJUST || e.kind == EntryKind.DEBT) Modifier else Modifier.clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onEntry(e) }
    Row(
        Modifier.fillMaxWidth()
            .drawBehind { if (!first) drawLine(Tz.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .then(open)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(40.dp).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) { TzIcon(icon, 18.dp, null, tint = color) }
        Column(Modifier.weight(1f)) {
            Text(
                highlighted(entryTitle(e, salaryDay), highlight),
                style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = Tz.text,
            )
            Text(entrySub(e), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
            val links = LocalInstallmentLinks.current
            if (links != null && e.recurringId != null && e.recurringId in links.ids) {
                Text(
                    stringResource(R.string.roll_installments_link),
                    Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); links.onOpen() },
                    style = TzType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.yellow,
                )
            }
        }
        Text(amount, style = TzType.titleTab.copy(fontSize = 18.sp), color = if (e.kind == EntryKind.RECEIVED) Tz.turquoise else Tz.text, maxLines = 1)
    }
}

/** A search hit: the entry with its date above, the matched word in yellow. */
@Composable
private fun ResultCard(e: EntryRow, categories: Map<Long, CategoryEntity>, query: String, onEntry: (EntryRow) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(e.happenedAt, locale) {
        formatDayDate(Instant.ofEpochMilli(e.happenedAt).atZone(ZoneId.systemDefault()).toLocalDate(), locale)
    }
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(date, style = TzType.caption.copy(fontSize = 11.sp), color = Tz.text3)
        EntryLine(e, categories, false, query, first = true, onEntry)
    }
}

// ---- search highlight ----

/** Where [query] sits in [text] after the quick log's Arabic normalising (the rule the search itself uses), or null. */
internal fun matchRange(text: String, query: String): IntRange? {
    val q = normalize(query.trim()).lowercase()
    if (q.isEmpty()) return null
    val flat = StringBuilder()
    val origin = ArrayList<Int>() // index in the normalised text -> index in [text]
    text.forEachIndexed { i, c ->
        val n = normalize(c.toString()).lowercase()
        flat.append(n)
        repeat(n.length) { origin += i }
    }
    val at = flat.indexOf(q)
    return if (at < 0) null else origin[at]..origin[at + q.length - 1]
}

private fun highlighted(text: String, query: String): AnnotatedString {
    val r = if (query.isEmpty()) null else matchRange(text, query)
    return buildAnnotatedString {
        append(text)
        if (r != null) addStyle(SpanStyle(color = Tz.yellow), r.first, r.last + 1)
    }
}
