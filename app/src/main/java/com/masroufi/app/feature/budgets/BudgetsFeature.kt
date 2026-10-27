package com.masroufi.app.feature.budgets

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.masroufi.app.R
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.Budget
import com.masroufi.app.data.model.BudgetPeriod
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.CategoryKind
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.BudgetRepository
import com.masroufi.app.data.repo.BudgetWithProgress
import com.masroufi.app.data.repo.CategoryRepository
import com.masroufi.app.domain.BudgetStatus
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.DateChip
import com.masroufi.app.ui.components.DropdownField
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.navigation.BudgetEditorRoute
import com.masroufi.app.ui.navigation.TabInsets
import com.masroufi.app.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class BudgetsUiState(val loading: Boolean = true, val items: List<BudgetWithProgress> = emptyList(), val categories: Map<String, Category> = emptyMap())

@HiltViewModel
class BudgetsViewModel @Inject constructor(budgets: BudgetRepository, categories: CategoryRepository) : ViewModel() {
    val state: StateFlow<BudgetsUiState> = combine(budgets.observeWithProgress(), categories.observeAll()) { b, c ->
        BudgetsUiState(false, b, c.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())
}

@Composable
fun BudgetsScreen(onAdd: () -> Unit, onEdit: (String) -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = TabInsets,
        topBar = { MasroufiTopBar(stringResource(R.string.tab_budgets)) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.budget_add)) } },
    ) { padding ->
        if (state.loading) return@Scaffold
        if (state.items.isEmpty()) {
            EmptyState(Icons.Outlined.PieChart, stringResource(R.string.budgets_empty_title), stringResource(R.string.budgets_empty_body), Modifier.padding(padding), stringResource(R.string.budget_add), onAdd)
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = { it.budget.id }) { item -> BudgetCard(item, state.categories, onEdit, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun BudgetCard(item: BudgetWithProgress, categories: Map<String, Category>, onEdit: (String) -> Unit, modifier: Modifier) {
    val finance = LocalFinanceColors.current
    val money = LocalMoneyFormatter.current
    val dates = rememberDateFormatter()
    val p = item.progress
    val color = when (p.status) {
        BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
        BudgetStatus.WARNING -> finance.warning
        BudgetStatus.OVER -> finance.expense
    }
    PressableCard(onClick = { onEdit(item.budget.id) }, modifier = modifier) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(item.budget.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    item.budget.categoryId?.let { categoryLabel(categories[it]) } ?: stringResource(R.string.budget_all_spending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedAmount(p.remainingMinor, item.budget.currency, style = MaterialTheme.typography.titleMedium, color = if (p.remainingMinor < 0) finance.expense else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        AnimatedProgressBar(p.fraction, color)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.budget_spent_of, money.format(p.spentMinor, item.budget.currency), money.format(p.effectiveLimitMinor, item.budget.currency)),
            style = MaterialTheme.typography.bodySmall,
        )
        if (p.carriedMinor != 0L) {
            Text(stringResource(R.string.budget_rollover_amount, money.format(p.carriedMinor, item.budget.currency, signed = true)), style = MaterialTheme.typography.bodySmall)
        }
        item.previousSpentMinor?.let {
            Text(stringResource(R.string.budget_last_month, money.format(it, item.budget.currency)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.budget.period == BudgetPeriod.CUSTOM) {
            Text(
                "${item.window.startMillis.toLocalDate().format(dates)} – ${(item.window.endMillis - 1).toLocalDate().format(dates)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (p.status) {
            BudgetStatus.OVER -> Text(stringResource(R.string.budget_over), color = finance.expense, style = MaterialTheme.typography.labelLarge)
            BudgetStatus.WARNING -> Text(stringResource(R.string.budget_warning), color = finance.warning, style = MaterialTheme.typography.labelLarge)
            BudgetStatus.ON_TRACK -> Unit
        }
    }
}

data class BudgetForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val categoryId: String? = null,
    val limit: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate = LocalDate.now().plusDays(30),
    val rollover: Boolean = false,
    val categories: List<Category> = emptyList(),
)

@HiltViewModel
class BudgetEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<BudgetEditorRoute>().id
    private var original: Budget? = null
    private val _form = MutableStateFlow(BudgetForm())
    val form: StateFlow<BudgetForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    init {
        viewModelScope.launch {
            val cats = categories.observeAll().first().filter { it.kind == CategoryKind.EXPENSE && it.parentId == null }
            val existing = id?.let { budgets.get(it) }
            original = existing
            val today = LocalDate.now(clock)
            _form.value = if (existing != null) {
                BudgetForm(
                    loaded = true,
                    isNew = false,
                    name = existing.name,
                    categoryId = existing.categoryId,
                    limit = Money.toPlainString(existing.limitMinor, existing.currency),
                    currency = existing.currency,
                    period = existing.period,
                    start = LocalDate.ofEpochDay(existing.startEpochDay),
                    end = LocalDate.ofEpochDay(existing.endEpochDay ?: existing.startEpochDay),
                    rollover = existing.rollover,
                    categories = cats,
                )
            } else {
                BudgetForm(loaded = true, currency = settings.current().primaryCurrency, start = today, end = today.plusDays(29), categories = cats)
            }
        }
    }

    fun update(block: (BudgetForm) -> BudgetForm) = _form.update(block)

    fun save(defaultName: String) {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val start = if (f.period == BudgetPeriod.MONTHLY) (original?.let { LocalDate.ofEpochDay(it.startEpochDay) } ?: LocalDate.now(clock).withDayOfMonth(1)) else f.start
                budgets.save(
                    Budget(
                        id = original?.id ?: newId(),
                        name = f.name.ifBlank { defaultName },
                        categoryId = f.categoryId,
                        limitMinor = Money.parse(f.limit, f.currency) ?: 0,
                        currency = f.currency,
                        period = f.period,
                        startEpochDay = start.toEpochDay(),
                        endEpochDay = if (f.period == BudgetPeriod.CUSTOM) maxOf(f.end, f.start).toEpochDay() else null,
                        rollover = f.period == BudgetPeriod.MONTHLY && f.rollover,
                        updatedAt = clock.millis(),
                    ),
                )
            }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch { effects.attempt { budgets.delete(id) } }
    }
}

@Composable
fun BudgetEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: BudgetEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    val selectedCategory = form.categories.firstOrNull { it.id == form.categoryId }
    val defaultName = selectedCategory?.let { categoryLabel(it) } ?: stringResource(R.string.budget_all_spending)
    Scaffold(
        topBar = {
            MasroufiTopBar(stringResource(if (form.isNew) R.string.budget_add else R.string.budget_edit), onBack = onBack) {
                if (!form.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DropdownField(
                label = stringResource(R.string.field_category),
                options = listOf<Category?>(null) + form.categories,
                selected = selectedCategory,
                onSelect = { c -> viewModel.update { it.copy(categoryId = c?.id) } },
                optionLabel = { it?.let { c -> categoryLabel(c) } ?: stringResource(R.string.budget_all_spending) },
                placeholder = stringResource(R.string.budget_all_spending),
            )
            AmountField(form.limit, { v -> viewModel.update { it.copy(limit = v) } }, form.currency, label = stringResource(R.string.field_limit))
            TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_name_optional))
            CurrencyField(form.currency, { c -> viewModel.update { it.copy(currency = c) } })
            SegmentedChoice(
                listOf(BudgetPeriod.MONTHLY, BudgetPeriod.CUSTOM),
                form.period,
                { p -> viewModel.update { it.copy(period = p) } },
                { stringResource(if (it == BudgetPeriod.MONTHLY) R.string.budget_monthly else R.string.budget_custom) },
            )
            if (form.period == BudgetPeriod.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateChip(form.start, { d -> viewModel.update { it.copy(start = d) } }, label = stringResource(R.string.filter_from))
                    DateChip(form.end, { d -> viewModel.update { it.copy(end = d) } }, label = stringResource(R.string.filter_to))
                }
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.budget_rollover)) },
                    supportingContent = { Text(stringResource(R.string.budget_rollover_hint)) },
                    trailingContent = { Switch(form.rollover, { v -> viewModel.update { it.copy(rollover = v) } }) },
                )
            }
            Button(onClick = { viewModel.save(defaultName) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.budget_delete_title), stringResource(R.string.budget_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}
