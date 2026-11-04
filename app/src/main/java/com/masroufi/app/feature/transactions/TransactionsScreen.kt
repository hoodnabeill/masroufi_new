@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.masroufi.app.feature.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassSheet
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.ui.components.glassFieldColors
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.core.Money
import com.masroufi.app.core.startMillis
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.CategoryKind
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.repo.TransactionFilter
import com.masroufi.app.data.repo.TransactionSort
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.ChipChoice
import com.masroufi.app.ui.components.DropdownField
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.OptionalDateChip
import com.masroufi.app.ui.components.TransactionItem
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.systemCategoryLabel
import com.masroufi.app.ui.navigation.DockClearance
import com.masroufi.app.ui.navigation.TabInsets
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The quick filters above the list: everything, one transaction type, or only entries a recurring rule made. */
private enum class QuickFilter(val label: Int, val type: TransactionType?) {
    ALL(R.string.filter_all, null),
    EXPENSE(R.string.type_expense, TransactionType.EXPENSE),
    INCOME(R.string.type_income, TransactionType.INCOME),
    TRANSFER(R.string.type_transfer, TransactionType.TRANSFER),
    RECURRING(R.string.filter_recurring, null),
}

/**
 * الحركة: one month at a time with its spending, income and daily average, quick type filters, and the entries
 * grouped by day with each day's spending. An empty today invites the first entry, since that keeps the streak.
 */
@Composable
fun TransactionsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filtering by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    // Text search runs over the localised labels the user actually sees, so Arabic and English both match.
    val visible = remember(state.rows, state.query) {
        val q = state.query.trim()
        if (q.isEmpty()) {
            state.rows
        } else {
            state.rows.filter { row ->
                val label = row.categorySystemKey?.let(::systemCategoryLabel)?.let { context.getString(it) } ?: row.categoryName.orEmpty()
                listOf(row.transaction.note, row.transaction.tags, label, row.walletName.orEmpty(), Money.toPlainString(row.transaction.amountMinor, row.transaction.currency))
                    .any { it.contains(q, ignoreCase = true) }
            }
        }
    }
    val grouped = remember(visible) { visible.groupBy { it.transaction.occurredAt.toLocalDate() } }
    val quick = when {
        state.recurringOnly -> QuickFilter.RECURRING
        else -> QuickFilter.entries.first { it != QuickFilter.RECURRING && it.type == state.filter.type }
    }
    val narrowed = state.query.isNotBlank() || state.filter.isActive || state.recurringOnly
    // Today's invitation only makes sense while browsing the current month with nothing filtered out.
    val inviteToday = !state.loading && !narrowed && state.month == YearMonth.from(today) && today !in grouped && state.wallets.isNotEmpty()

    val largeTitle = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(largeTitle.nestedScrollConnection),
        contentWindowInsets = TabInsets,
        topBar = {
            MasroufiTopBar(stringResource(R.string.dock_transactions), scrollBehavior = largeTitle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIconButton(onClick = { searching = !searching }) {
                        Icon(MIcons.Search, contentDescription = stringResource(R.string.activity_search))
                    }
                    GlassIconButton(onClick = { filtering = true }) {
                        BadgedBox(badge = { if (state.filter.isActive) Badge() }) {
                            Icon(MIcons.Filter, contentDescription = stringResource(R.string.filter_title))
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(top = 16.dp, bottom = DockClearance)) {
            if (searching || state.query.isNotEmpty()) {
                item(key = "search") {
                    OutlinedTextField(colors = glassFieldColors(),
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(MIcons.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
            }
            item(key = "month") { MonthCard(state, today, onShift = viewModel::shiftMonth) }
            item(key = "chips") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickFilter.entries.forEach { option ->
                        FilterPill(stringResource(option.label), option == quick) {
                            viewModel.setRecurringOnly(option == QuickFilter.RECURRING)
                            viewModel.setFilter(state.filter.copy(type = option.type))
                        }
                    }
                }
            }
            if (inviteToday) {
                item(key = "today-invite") {
                    Column(Modifier.padding(bottom = 10.dp)) {
                        DayHeader(today, today, trailing = stringResource(R.string.activity_today_empty))
                        TodayInvite(onAdd)
                    }
                }
            }
            when {
                state.loading -> Unit
                state.rows.isEmpty() && !narrowed && !inviteToday -> item(key = "empty") {
                    EmptyState(
                        MIcons.Activity,
                        stringResource(R.string.transactions_empty_title),
                        stringResource(R.string.transactions_empty_body),
                        actionLabel = if (state.wallets.isNotEmpty()) stringResource(R.string.transaction_new) else null,
                        onAction = onAdd,
                    )
                }
                visible.isEmpty() && narrowed -> item(key = "none") {
                    EmptyState(
                        MIcons.SearchOff,
                        stringResource(R.string.search_no_results),
                        stringResource(R.string.search_no_results_body),
                        actionLabel = stringResource(R.string.filter_clear),
                        onAction = { viewModel.setQuery(""); viewModel.setRecurringOnly(false); viewModel.setFilter(TransactionFilter()) },
                    )
                }
                else -> grouped.forEach { (date, rows) ->
                    item(key = "d-$date") {
                        Column(Modifier.animateItem().padding(bottom = 10.dp)) {
                            DayHeader(date, today, trailing = daySpent(rows))
                            DayCard(rows, onOpen)
                        }
                    }
                }
            }
        }
    }

    if (filtering) {
        FilterSheet(state, onApply = { viewModel.setFilter(it); filtering = false }, onDismiss = { filtering = false })
    }
}

/** "صرفت 1,105" for the day: expenses only, each currency on its own. Empty when nothing was spent. */
@Composable
private fun daySpent(rows: List<TransactionRow>): String? {
    val formatter = LocalMoneyFormatter.current
    val totals = rows.filter { it.transaction.type == TransactionType.EXPENSE }
        .groupBy { it.transaction.currency }
        .map { (currency, list) -> formatter.format(list.sumOf { it.transaction.amountMinor }, currency) }
    return if (totals.isEmpty()) null else stringResource(R.string.home_day_spent, totals.joinToString(" · "))
}

@Composable
private fun MonthCard(state: TransactionsUiState, today: LocalDate, onShift: (Long) -> Unit) {
    val glass = LocalGlass.current
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val current = YearMonth.from(today)
    val custom = state.customRange
    // The daily average counts the days that have happened: the whole month in the past, up to today in this one.
    val days = when {
        custom -> state.rows.map { it.transaction.occurredAt.toLocalDate() }.distinct().size.coerceAtLeast(1)
        state.month == current -> today.dayOfMonth
        else -> state.month.lengthOfMonth()
    }
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(glass.fill)
            .border(1.dp, glass.rim, MaterialTheme.shapes.large)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(enabled = !custom, back = true, description = stringResource(R.string.activity_prev_month)) { onShift(-1) }
            Text(
                if (custom) stringResource(R.string.activity_custom_range) else state.month.format(monthFormat),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthArrow(enabled = !custom && state.month < current, back = false, description = stringResource(R.string.activity_next_month)) { onShift(1) }
        }
        Row {
            MonthStat(stringResource(R.string.activity_spent), state.expenseMinor, state.currency, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), divider = false)
            MonthStat(stringResource(R.string.activity_income), state.incomeMinor, state.currency, LocalFinanceColors.current.income, Modifier.weight(1f))
            MonthStat(stringResource(R.string.activity_daily_avg), state.expenseMinor / days, state.currency, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MonthArrow(enabled: Boolean, back: Boolean, description: String, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) glass.fillStrong else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MIcons.ChevronForward,
            contentDescription = description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else glass.textTertiary.copy(alpha = 0.5f),
            // Forward already mirrors for RTL; "previous" is simply forward turned around.
            modifier = Modifier.size(18.dp).graphicsLayer { if (back) scaleX = -1f },
        )
    }
}

@Composable
private fun MonthStat(label: String, minor: Long, currency: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier, divider: Boolean = true) {
    val line = LocalGlass.current.strokeBottom
    Column(
        modifier
            .then(
                if (divider) Modifier.drawBehind {
                    val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width else 0f
                    drawLine(line, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
                }.padding(start = 12.dp) else Modifier,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textTertiary)
        MoneyText(minor, currency, style = MaterialTheme.typography.headlineSmall, color = color)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.onSurface else glass.fill)
            .then(if (selected) Modifier else Modifier.border(1.dp, glass.rim, CircleShape))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) glass.base else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "النهارده · الاتنين", "امبارح · الأحد 1 نوفمبر" or "الجمعة 23 أكتوبر", with the day's spending opposite. */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, trailing: String?) {
    val glass = LocalGlass.current
    val locale = LocalConfiguration.current.locales[0]
    val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }
    val full = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val label = when (date) {
        today -> stringResource(R.string.day_today) + " · " + date.format(weekday)
        today.minusDays(1) -> stringResource(R.string.day_yesterday) + " · " + date.format(full)
        else -> date.format(full)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (date == today) LocalFinanceColors.current.streak else glass.textTertiary,
            modifier = Modifier.weight(1f),
        )
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = glass.textTertiary) }
    }
}

@Composable
private fun DayCard(rows: List<TransactionRow>, onOpen: (String) -> Unit) {
    val glass = LocalGlass.current
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(glass.fill)
            .border(1.dp, glass.rim, MaterialTheme.shapes.large)
            .padding(vertical = 6.dp),
    ) {
        rows.forEach { row -> TransactionItem(row, onClick = { onOpen(row.transaction.id) }, showDate = false) }
    }
}

@Composable
private fun TodayInvite(onAdd: () -> Unit) {
    val glass = LocalGlass.current
    val dash = glass.track
    val lime = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .drawBehind {
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(lime.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(MIcons.Plus, contentDescription = null, tint = lime, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.activity_today_cta), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.activity_today_cta_sub), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        }
    }
}

@Composable
private fun FilterSheet(state: TransactionsUiState, onApply: (TransactionFilter) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(state.filter) }
    var min by remember { mutableStateOf(draft.minMinor?.let { Money.toPlainString(it, Money.DEFAULT_CURRENCY) }.orEmpty()) }
    var max by remember { mutableStateOf(draft.maxMinor?.let { Money.toPlainString(it, Money.DEFAULT_CURRENCY) }.orEmpty()) }
    val anyLabel = stringResource(R.string.filter_any)
    GlassSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.titleSmall)
            ChipChoice(
                options = listOf<TransactionType?>(null) + TransactionType.entries,
                selected = draft.type,
                onSelect = { draft = draft.copy(type = it) },
                label = { it?.let { t -> typeLabel(t) } ?: anyLabel },
            )
            DropdownField(
                label = stringResource(R.string.field_wallet),
                options = listOf<String?>(null) + state.wallets.map { it.wallet.id },
                selected = draft.walletId,
                onSelect = { draft = draft.copy(walletId = it) },
                optionLabel = { id -> state.wallets.firstOrNull { it.wallet.id == id }?.wallet?.name ?: anyLabel },
                placeholder = anyLabel,
            )
            DropdownField(
                label = stringResource(R.string.field_category),
                options = listOf<String?>(null) + state.categories.filter { it.parentId == null }.sortedBy { it.kind != CategoryKind.EXPENSE }.map { it.id },
                selected = draft.categoryId,
                onSelect = { draft = draft.copy(categoryId = it) },
                optionLabel = { id -> id?.let { categoryLabel(state.categories.firstOrNull { c -> c.id == it }) } ?: anyLabel },
                placeholder = anyLabel,
            )
            Text(stringResource(R.string.filter_dates), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionalDateChip(draft.fromMillis?.toLocalDate(), { draft = draft.copy(fromMillis = it?.startMillis()) }, stringResource(R.string.filter_from))
                OptionalDateChip(draft.toMillis?.let { (it - 1).toLocalDate() }, { draft = draft.copy(toMillis = it?.plusDays(1)?.startMillis()) }, stringResource(R.string.filter_to))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(min, { min = it }, Money.DEFAULT_CURRENCY, Modifier.weight(1f), label = stringResource(R.string.filter_min))
                AmountField(max, { max = it }, Money.DEFAULT_CURRENCY, Modifier.weight(1f), label = stringResource(R.string.filter_max))
            }
            Text(stringResource(R.string.filter_sort), style = MaterialTheme.typography.titleSmall)
            ChipChoice(
                options = TransactionSort.entries.toList(),
                selected = draft.sort,
                onSelect = { draft = draft.copy(sort = it) },
                label = {
                    stringResource(
                        when (it) {
                            TransactionSort.NEWEST -> R.string.sort_newest
                            TransactionSort.OLDEST -> R.string.sort_oldest
                            TransactionSort.AMOUNT_DESC -> R.string.sort_amount_desc
                            TransactionSort.AMOUNT_ASC -> R.string.sort_amount_asc
                        },
                    )
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSecondaryButton(onClick = { onApply(TransactionFilter()) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.filter_clear)) }
                GlassButton(
                    onClick = {
                        onApply(draft.copy(minMinor = Money.parse(min, Money.DEFAULT_CURRENCY), maxMinor = Money.parse(max, Money.DEFAULT_CURRENCY)))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.filter_apply)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
