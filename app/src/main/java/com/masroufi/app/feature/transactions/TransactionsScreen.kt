@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.masroufi.app.feature.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
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
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.components.systemCategoryLabel
import com.masroufi.app.ui.navigation.DockClearance
import com.masroufi.app.ui.navigation.TabInsets
import java.time.format.FormatStyle

@Composable
fun TransactionsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filtering by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val headerFormat = rememberDateFormatter(FormatStyle.FULL)

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

    Scaffold(
        contentWindowInsets = TabInsets,
        topBar = {
            MasroufiTopBar(stringResource(R.string.tab_transactions)) {
                IconButton(onClick = { filtering = true }) {
                    BadgedBox(badge = { if (state.filter.isActive) Badge() }) {
                        Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.filter_title))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            when {
                state.loading -> Unit
                state.rows.isEmpty() && !state.filter.isActive -> EmptyState(
                    Icons.Outlined.Receipt,
                    stringResource(R.string.transactions_empty_title),
                    stringResource(R.string.transactions_empty_body),
                    actionLabel = if (state.wallets.isNotEmpty()) stringResource(R.string.transaction_new) else null,
                    onAction = onAdd,
                )
                visible.isEmpty() -> EmptyState(
                    Icons.Outlined.SearchOff,
                    stringResource(R.string.search_no_results),
                    stringResource(R.string.search_no_results_body),
                    actionLabel = stringResource(R.string.filter_clear),
                    onAction = { viewModel.setQuery(""); viewModel.setFilter(TransactionFilter()) },
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = DockClearance)) {
                    grouped.forEach { (date, rows) ->
                        stickyHeader(key = "h-$date") {
                            Text(
                                date.format(headerFormat),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(rows, key = { it.transaction.id }) { row: TransactionRow ->
                            TransactionItem(row, onClick = { onOpen(row.transaction.id) }, showDate = false, modifier = Modifier.animateItem())
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

@Composable
private fun FilterSheet(state: TransactionsUiState, onApply: (TransactionFilter) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(state.filter) }
    var min by remember { mutableStateOf(draft.minMinor?.let { Money.toPlainString(it, Money.DEFAULT_CURRENCY) }.orEmpty()) }
    var max by remember { mutableStateOf(draft.maxMinor?.let { Money.toPlainString(it, Money.DEFAULT_CURRENCY) }.orEmpty()) }
    val anyLabel = stringResource(R.string.filter_any)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
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
                OutlinedButton(onClick = { onApply(TransactionFilter()) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.filter_clear)) }
                Button(
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
