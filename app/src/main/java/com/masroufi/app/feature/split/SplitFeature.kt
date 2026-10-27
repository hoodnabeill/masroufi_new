@file:OptIn(ExperimentalMaterial3Api::class)

package com.masroufi.app.feature.split

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
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.masroufi.app.data.model.Allocation
import com.masroufi.app.data.model.GoalWithSaved
import com.masroufi.app.data.model.SplitItem
import com.masroufi.app.data.model.SplitKind
import com.masroufi.app.data.model.SplitRule
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.GoalRepository
import com.masroufi.app.data.repo.SplitRepository
import com.masroufi.app.domain.SplitCalculator
import com.masroufi.app.domain.SplitResult
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.DropdownField
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.LabeledRow
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.navigation.SplitEditorRoute
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
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class SplitListState(val loading: Boolean = true, val rules: List<SplitRule> = emptyList(), val allocations: List<Allocation> = emptyList())

data class SplitPreview(val rule: SplitRule, val incomeMinor: Long, val result: SplitResult, val allocationId: String = newId())

@HiltViewModel
class SplitRulesViewModel @Inject constructor(private val repo: SplitRepository) : ViewModel() {
    val state: StateFlow<SplitListState> = combine(repo.observeRules(), repo.observeAllocations()) { r, a -> SplitListState(false, r, a) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SplitListState())
    private val _preview = MutableStateFlow<SplitPreview?>(null)
    val preview: StateFlow<SplitPreview?> = _preview.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    fun items(rule: SplitRule) = repo.items(rule)

    fun preview(rule: SplitRule, incomeMinor: Long) {
        _preview.value = SplitPreview(rule, incomeMinor, SplitCalculator.calculate(incomeMinor, repo.items(rule)))
    }

    fun clearPreview() {
        _preview.value = null
    }

    /** Nothing is allocated until the user confirms the previewed lines. */
    fun apply() {
        val p = _preview.value ?: return
        val ok = p.result as? SplitResult.Ok ?: return
        viewModelScope.launch {
            effects.attempt { repo.apply(p.allocationId, p.rule, p.incomeMinor, ok) }
            _preview.value = null
        }
    }
}

@Composable
fun SplitRulesScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, viewModel: SplitRulesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var applying by remember { mutableStateOf<SplitRule?>(null) }
    val dates = rememberDateFormatter()
    val appliedText = stringResource(R.string.split_applied)
    val scope = rememberCoroutineScope()
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone = { scope.launch { snackbar.showSnackbar(appliedText) } })
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(R.string.split_title), onBack = onBack) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.split_add)) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        if (state.rules.isEmpty()) {
            EmptyState(Icons.Outlined.CallSplit, stringResource(R.string.split_empty_title), stringResource(R.string.split_empty_body), Modifier.padding(padding), stringResource(R.string.split_add), onAdd)
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.rules, key = { it.id }) { rule ->
                val items = viewModel.items(rule)
                PressableCard(onClick = { onEdit(rule.id) }) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    items.forEach { item -> Text("• ${item.label}: ${itemValueText(item, rule.currency)}", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { applying = rule }) { Text(stringResource(R.string.split_apply)) }
                }
            }
            if (state.allocations.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.split_history)) }
                items(state.allocations, key = { it.id }) { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.ruleName, style = MaterialTheme.typography.bodyMedium)
                            Text(a.createdAt.toLocalDate().format(dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MoneyText(a.incomeMinor, a.currency)
                    }
                }
            }
        }
    }

    applying?.let { rule ->
        IncomeSheet(rule, onDismiss = { applying = null }, onPreview = { amount -> viewModel.preview(rule, amount); applying = null })
    }
    preview?.let { p -> PreviewSheet(p, onDismiss = viewModel::clearPreview, onConfirm = viewModel::apply) }
}

@Composable
private fun itemValueText(item: SplitItem, currency: String): String = when (item.kind) {
    SplitKind.PERCENT -> "${BigDecimal.valueOf(item.value, 2).stripTrailingZeros().toPlainString()}%"
    SplitKind.FIXED -> LocalMoneyFormatter.current.format(item.value, currency)
}

@Composable
private fun IncomeSheet(rule: SplitRule, onDismiss: () -> Unit, onPreview: (Long) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.split_income_title, rule.name), style = MaterialTheme.typography.titleLarge)
            AmountField(amount, { amount = it }, rule.currency, label = stringResource(R.string.split_income_amount))
            val parsed = Money.parse(amount, rule.currency) ?: 0
            Button(onClick = { onPreview(parsed) }, enabled = parsed > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.split_preview)) }
        }
    }
}

@Composable
private fun PreviewSheet(preview: SplitPreview, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val currency = preview.rule.currency
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.split_preview), style = MaterialTheme.typography.titleLarge)
            when (val r = preview.result) {
                is SplitResult.Ok -> {
                    r.lines.forEach { line ->
                        LabeledRow(line.item.label + if (line.item.goalId != null) " · ${stringResource(R.string.split_to_goal)}" else "") {
                            MoneyText(line.amountMinor, currency)
                        }
                    }
                    LabeledRow(stringResource(R.string.split_unallocated)) { MoneyText(r.unallocatedMinor, currency) }
                    Text(stringResource(R.string.split_preview_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.split_confirm)) }
                }
                is SplitResult.FixedExceedsIncome -> Text(
                    stringResource(R.string.split_error_fixed, money.format(r.fixedTotalMinor, currency), money.format(preview.incomeMinor, currency)),
                    color = LocalFinanceColors.current.expense,
                )
                is SplitResult.PercentOver100 -> Text(
                    stringResource(R.string.split_error_percent, BigDecimal.valueOf(r.totalBasisPoints, 2).stripTrailingZeros().toPlainString()),
                    color = LocalFinanceColors.current.expense,
                )
                SplitResult.Empty -> Text(stringResource(R.string.split_error_empty))
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

data class SplitItemDraft(val label: String = "", val kind: SplitKind = SplitKind.PERCENT, val value: String = "", val goalId: String? = null)

data class SplitForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
    val items: List<SplitItemDraft> = listOf(SplitItemDraft()),
    val goals: List<GoalWithSaved> = emptyList(),
)

@HiltViewModel
class SplitEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: SplitRepository,
    goals: GoalRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val id = savedState.toRoute<SplitEditorRoute>().id
    private var original: SplitRule? = null
    private val _form = MutableStateFlow(SplitForm())
    val form: StateFlow<SplitForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    init {
        viewModelScope.launch {
            val g = goals.observeGoals().first()
            val existing = id?.let { repo.get(it) }
            original = existing
            _form.value = if (existing != null) {
                SplitForm(
                    loaded = true, isNew = false, name = existing.name, currency = existing.currency,
                    items = repo.items(existing).map {
                        SplitItemDraft(
                            it.label, it.kind,
                            if (it.kind == SplitKind.PERCENT) BigDecimal.valueOf(it.value, 2).stripTrailingZeros().toPlainString() else Money.toPlainString(it.value, existing.currency),
                            it.goalId,
                        )
                    },
                    goals = g,
                )
            } else {
                SplitForm(loaded = true, currency = settings.current().primaryCurrency, goals = g)
            }
        }
    }

    fun update(block: (SplitForm) -> SplitForm) = _form.update(block)

    fun updateItem(index: Int, block: (SplitItemDraft) -> SplitItemDraft) = _form.update { f ->
        f.copy(items = f.items.mapIndexed { i, item -> if (i == index) block(item) else item })
    }

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val items = f.items.filter { it.label.isNotBlank() }.mapNotNull { d ->
                    val value = when (d.kind) {
                        SplitKind.PERCENT -> Money.normalizeDigits(d.value).toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.DOWN)?.toLong()
                        SplitKind.FIXED -> Money.parse(d.value, f.currency)
                    } ?: return@mapNotNull null
                    if (value <= 0) null else SplitItem(d.label.trim(), d.kind, value, d.goalId)
                }
                repo.save(original?.id ?: newId(), f.name, f.currency, items)
            }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch { effects.attempt { repo.delete(id) } }
    }
}

@Composable
fun SplitEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: SplitEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    val noGoal = stringResource(R.string.split_no_goal)
    Scaffold(
        topBar = {
            MasroufiTopBar(stringResource(if (form.isNew) R.string.split_add else R.string.split_edit), onBack = onBack) {
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
            TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_name))
            CurrencyField(form.currency, { c -> viewModel.update { it.copy(currency = c) } })
            Text(stringResource(R.string.split_rules_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            form.items.forEachIndexed { index, item ->
                PressableCard(onClick = null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextInput(item.label, { v -> viewModel.updateItem(index) { it.copy(label = v) } }, stringResource(R.string.split_item_label), modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.update { f -> f.copy(items = f.items.filterIndexed { i, _ -> i != index }) } }, enabled = form.items.size > 1) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        SplitKind.entries.toList(), item.kind, { k -> viewModel.updateItem(index) { it.copy(kind = k, value = "") } },
                        { stringResource(if (it == SplitKind.PERCENT) R.string.split_percent else R.string.split_fixed) },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (item.kind == SplitKind.PERCENT) {
                        TextInput(
                            item.value,
                            { v -> viewModel.updateItem(index) { it.copy(value = Money.normalizeDigits(v).filter { c -> c.isDigit() || c == '.' }.take(6)) } },
                            stringResource(R.string.split_percent_value),
                            keyboardType = KeyboardType.Decimal,
                        )
                    } else {
                        AmountField(item.value, { v -> viewModel.updateItem(index) { it.copy(value = v) } }, form.currency)
                    }
                    Spacer(Modifier.height(8.dp))
                    DropdownField(
                        stringResource(R.string.split_link_goal),
                        listOf<String?>(null) + form.goals.map { it.goal.id },
                        item.goalId,
                        { g -> viewModel.updateItem(index) { it.copy(goalId = g) } },
                        { gid -> form.goals.firstOrNull { it.goal.id == gid }?.goal?.name ?: noGoal },
                        placeholder = noGoal,
                    )
                }
            }
            OutlinedButton(onClick = { viewModel.update { it.copy(items = it.items + SplitItemDraft()) } }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.split_add_item), modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.split_delete_title), stringResource(R.string.split_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}
