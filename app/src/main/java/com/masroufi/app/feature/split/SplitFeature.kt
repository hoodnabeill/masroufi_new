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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.FilterChip
import com.masroufi.app.ui.components.glassChipColors
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
import com.masroufi.app.ui.components.FitAmount
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.masroufi.app.ui.components.GlassSwitch
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.GlassSheet
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.GlassButton
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
        topBar = { MasroufiTopBar(stringResource(R.string.split_title), onBack = onBack) { GlassIconButton(onClick = onAdd) { Icon(MIcons.Plus, stringResource(R.string.split_add)) } } },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        if (state.rules.isEmpty()) {
            EmptyState(MIcons.Split, stringResource(R.string.split_empty_title), stringResource(R.string.split_empty_body), Modifier.padding(padding), stringResource(R.string.split_add), onAdd)
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.rules, key = { it.id }) { rule ->
                val items = viewModel.items(rule)
                PressableCard(onClick = { onEdit(rule.id) }) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    items.forEach { item -> Text("• ${item.label}: ${itemValueText(item, rule.currency)}", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    GlassSecondaryButton(onClick = { applying = rule }) { Text(stringResource(R.string.split_apply)) }
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
    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.split_income_title, rule.name), style = MaterialTheme.typography.titleLarge)
            AmountField(amount, { amount = it }, rule.currency, label = stringResource(R.string.split_income_amount))
            val parsed = Money.parse(amount, rule.currency) ?: 0
            GlassButton(onClick = { onPreview(parsed) }, enabled = parsed > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.split_preview)) }
        }
    }
}

@Composable
private fun PreviewSheet(preview: SplitPreview, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val currency = preview.rule.currency
    GlassSheet(onDismissRequest = onDismiss) {
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
                    GlassButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.split_confirm)) }
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
            GlassSecondaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
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
    /** Split every new income with this rule on its own (stored as the settings' auto rule). */
    val auto: Boolean = false,
) {
    /** One draft as a stored item, or null while it is too incomplete to split with. */
    fun itemOf(d: SplitItemDraft): SplitItem? {
        if (d.label.isBlank()) return null
        val value = when (d.kind) {
            SplitKind.PERCENT -> Money.normalizeDigits(d.value).toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.DOWN)?.toLong()
            SplitKind.FIXED -> Money.parse(d.value, currency)
        } ?: return null
        return if (value <= 0) null else SplitItem(d.label.trim(), d.kind, value, d.goalId)
    }

    /** The drafts that are complete enough to split with, as stored items. */
    fun toItems(): List<SplitItem> = items.mapNotNull(::itemOf)
}

/** One ±5 step on a typed percentage, kept within 0–100 and shown without trailing zeros. */
internal fun stepPercent(value: String, delta: Int): String {
    val next = ((Money.normalizeDigits(value).toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) + java.math.BigDecimal(delta))
        .coerceIn(java.math.BigDecimal.ZERO, java.math.BigDecimal(100))
    return next.stripTrailingZeros().toPlainString()
}

@HiltViewModel
class SplitEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: SplitRepository,
    goals: GoalRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val id = savedState.toRoute<SplitEditorRoute>().id
    // One id for a new rule, so a second tap on save updates it instead of creating a twin.
    private val newRuleId = newId()
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
                    auto = settings.current().autoSplitRuleId == existing.id,
                )
            } else {
                val current = settings.current()
                SplitForm(loaded = true, currency = current.primaryCurrency, goals = g, auto = current.autoSplitRuleId == null)
            }
        }
    }

    fun update(block: (SplitForm) -> SplitForm) = _form.update(block)

    fun updateItem(index: Int, block: (SplitItemDraft) -> SplitItemDraft) = _form.update { f ->
        f.copy(items = f.items.mapIndexed { i, item -> if (i == index) block(item) else item })
    }

    /** A new rule starts as the handoff's 50/30/20: needs, personal spending, and savings into the first goal. */
    fun seed(name: String, labels: List<String>) = _form.update { f ->
        if (!f.isNew || f.items.any { it.label.isNotBlank() || it.value.isNotBlank() }) f
        else f.copy(
            name = f.name.ifBlank { name },
            items = labels.mapIndexed { i, l -> SplitItemDraft(l, SplitKind.PERCENT, listOf(50, 30, 20)[i].toString(), if (i == 2) f.goals.firstOrNull { !it.goal.archived }?.goal?.id else null) },
        )
    }

    /** Puts a preset's percentages on the first buckets, adding the default ones when there are fewer. */
    fun applyPreset(preset: List<Int>, labels: List<String>) = _form.update { f ->
        val items = f.items.toMutableList()
        while (items.size < preset.size) items += SplitItemDraft(labels.getOrElse(items.size) { "" })
        preset.forEachIndexed { i, pct ->
            items[i] = items[i].copy(kind = SplitKind.PERCENT, value = pct.toString(), label = items[i].label.ifBlank { labels.getOrElse(i) { "" } })
        }
        f.copy(items = items)
    }

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val ruleId = original?.id ?: newRuleId
                repo.save(ruleId, f.name, f.currency, f.toItems())
                if (f.auto) settings.setAutoSplitRule(ruleId)
                else if (settings.current().autoSplitRuleId == ruleId) settings.setAutoSplitRule(null)
            }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch {
            effects.attempt {
                repo.delete(id)
                if (settings.current().autoSplitRuleId == id) settings.setAutoSplitRule(null)
            }
        }
    }
}

/** The handoff's split presets (needs / wants / savings) and the sample incomes to try them on, in whole units. */
private val splitPresets = listOf(listOf(50, 30, 20), listOf(60, 20, 20), listOf(70, 20, 10))
private val sampleIncomes = listOf(5_000L, 8_000L, 12_000L)

/** Bucket colours by position: blue, pink, lime, then the rest of the palette. */
private val bucketPalette = listOf(1, 3, 6, 4, 5, 0, 2, 7, 8)

/**
 * التقسيم التلقائي, laid out as the handoff: a sample income, the 50/30/20-style presets, the split as one bar, each
 * bucket with ± steps and what it gets, what that means for the daily number and the linked goal, and whether the
 * split runs on its own whenever income is logged. Tapping a bucket opens its details (name, % or fixed, goal).
 */
@Composable
fun SplitEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: SplitEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var income by rememberSaveable { mutableStateOf(8_000L) }
    var expanded by rememberSaveable { mutableStateOf(-1) }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    val seedName = stringResource(R.string.split_default_name)
    val seedLabels = listOf(stringResource(R.string.split_seed_needs), stringResource(R.string.split_seed_wants), stringResource(R.string.split_seed_save))
    LaunchedEffect(form.loaded) { if (form.loaded) viewModel.seed(seedName, seedLabels) }
    Scaffold(
        topBar = {
            MasroufiTopBar(stringResource(if (form.isNew) R.string.split_title else R.string.split_edit), onBack = onBack) {
                if (!form.isNew) GlassIconButton(onClick = { confirmDelete = true }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        val incomeMinor = income * Money.unit(form.currency)
        val parsed = form.items.map(form::itemOf)
        val result = SplitCalculator.calculate(incomeMinor, parsed.filterNotNull())
        val lines = (result as? SplitResult.Ok)?.lines
        var k = 0
        val amounts = parsed.map { p -> if (p == null || lines == null) null else lines[k++].amountMinor }
        val colors = form.items.mapIndexed { i, d -> if (d.goalId != null) MaterialTheme.colorScheme.primary else categoryColor(bucketPalette[i % bucketPalette.size]) }
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.split_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IncomeCard(income, form.currency) { income = it }
            PresetRow(form.items) { viewModel.applyPreset(it, seedLabels) }
            SplitBar(parsed, amounts, colors, (result as? SplitResult.Ok)?.unallocatedMinor ?: 0)
            form.items.forEachIndexed { index, draft ->
                BucketCard(
                    draft = draft,
                    amount = amounts[index],
                    color = colors[index],
                    icon = when {
                        draft.goalId != null -> MIcons.Savings
                        index == 0 -> MIcons.Home
                        index == 1 -> MIcons.Cafe
                        index == 2 -> MIcons.Savings
                        else -> MIcons.Shapes
                    },
                    sub = bucketSub(draft, seedLabels, form.goals),
                    currency = form.currency,
                    expanded = expanded == index,
                    onToggle = { expanded = if (expanded == index) -1 else index },
                    onStep = { delta -> viewModel.updateItem(index) { it.copy(value = stepPercent(it.value, delta)) } },
                ) {
                    BucketDetails(
                        draft, form, canDelete = form.items.size > 1,
                        onChange = { block -> viewModel.updateItem(index, block) },
                        onDelete = { expanded = -1; viewModel.update { f -> f.copy(items = f.items.filterIndexed { i, _ -> i != index }) } },
                    )
                }
            }
            AddBucketButton { expanded = form.items.size; viewModel.update { it.copy(items = it.items + SplitItemDraft()) } }
            MeansCard(form, result)
            AutoCard(form.auto) { on -> viewModel.update { it.copy(auto = on) } }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.split_rule_name))
                CurrencyField(form.currency, { c -> viewModel.update { it.copy(currency = c) } })
            }
            GlassButton(
                onClick = viewModel::save,
                enabled = result !is SplitResult.PercentOver100,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.split_save_rule)) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.split_delete_title), stringResource(R.string.split_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}

@Composable
private fun bucketSub(draft: SplitItemDraft, seeds: List<String>, goals: List<GoalWithSaved>): String? {
    draft.goalId?.let { id -> goals.firstOrNull { it.goal.id == id }?.let { return stringResource(R.string.split_sub_goal, it.goal.name) } }
    return when (draft.label.trim()) {
        seeds[0] -> stringResource(R.string.split_seed_needs_sub)
        seeds[1] -> stringResource(R.string.split_seed_wants_sub)
        seeds[2] -> stringResource(R.string.split_seed_save_sub)
        else -> if (draft.kind == SplitKind.FIXED) stringResource(R.string.split_fixed_sub) else null
    }
}

/** "لما الدخل ينزل": the sample income in large type with three amounts to try. */
@Composable
private fun IncomeCard(income: Long, currency: String, onPick: (Long) -> Unit) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val unit = Money.unit(currency)
    val lime = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier.fillMaxWidth().clip(shape).background(glass.fill).border(1.dp, glass.rim, shape).padding(18.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.split_when_income), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FitAmount(income * unit, currency, style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 40.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sampleIncomes.forEach { v ->
                Pill(money.formatCompact(v * unit, currency), v == income, lime, lime.copy(alpha = 0.1f), Modifier.weight(1f).height(38.dp)) { onPick(v) }
            }
        }
    }
}

/** The three presets as pills; the one matching the first three % buckets is lit. */
@Composable
private fun PresetRow(items: List<SplitItemDraft>, onPick: (List<Int>) -> Unit) {
    val violet = MaterialTheme.colorScheme.secondary
    val current = items.take(3).map { if (it.kind == SplitKind.PERCENT) Money.normalizeDigits(it.value).toIntOrNull() else null }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        splitPresets.forEach { p ->
            Pill(p.joinToString(" / "), current == p, violet, violet.copy(alpha = 0.16f), Modifier.weight(1f).height(40.dp), ltr = true) { onPick(p) }
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, accent: Color, fill: Color, modifier: Modifier, ltr: Boolean = false, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) fill else Color.Transparent)
            .border(if (ltr) 1.5.dp else 1.dp, if (selected) accent else glass.strokeBottom, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val text = @Composable {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (ltr) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { text() } else text()
    }
}

/** Every bucket's share as one 14 dp bar (track colour for what is left over), with the % total under it. */
@Composable
private fun SplitBar(parsed: List<SplitItem?>, amounts: List<Long?>, colors: List<Color>, unallocated: Long) {
    val glass = LocalGlass.current
    val finance = LocalFinanceColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val total = amounts.sumOf { it ?: 0 } + unallocated
            if (total <= 0) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)).background(glass.track))
            } else {
                amounts.forEachIndexed { i, a ->
                    if (a != null && a > 0) Box(Modifier.weight(a.toFloat()).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(colors[i]))
                }
                if (unallocated > 0) Box(Modifier.weight(unallocated.toFloat()).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(glass.track))
            }
        }
        val percent = parsed.filterNotNull().filter { it.kind == SplitKind.PERCENT }
        if (percent.isNotEmpty()) {
            val bp = percent.sumOf { it.value }
            val diff = BigDecimal.valueOf(kotlin.math.abs(10_000 - bp), 2).stripTrailingZeros().toPlainString()
            Text(
                when {
                    bp == 10_000L -> stringResource(R.string.split_sum_ok)
                    bp < 10_000L -> stringResource(R.string.split_sum_left, diff)
                    else -> stringResource(R.string.split_sum_over, diff)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (bp == 10_000L) MaterialTheme.colorScheme.primary else finance.over,
            )
        }
    }
}

@Composable
private fun BucketCard(
    draft: SplitItemDraft,
    amount: Long?,
    color: Color,
    icon: ImageVector,
    sub: String?,
    currency: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onStep: (Int) -> Unit,
    details: @Composable () -> Unit,
) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(glass.fill).border(1.dp, glass.rim, shape)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(14.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(icon, color, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(draft.label.ifBlank { stringResource(R.string.split_item_label) }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = glass.textTertiary, maxLines = 1) }
                Text(amount?.let { money.format(it, currency) } ?: "—", style = MaterialTheme.typography.titleMedium, color = color)
            }
            if (draft.kind == SplitKind.PERCENT) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepButton(MIcons.Plus, stringResource(R.string.split_more)) { onStep(5) }
                    Text(
                        (Money.normalizeDigits(draft.value).ifBlank { "0" }) + "%",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(42.dp),
                    )
                    StepButton(MIcons.Minus, stringResource(R.string.split_less)) { onStep(-5) }
                }
            } else {
                Text(
                    stringResource(R.string.split_fixed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).background(glass.fillStrong).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(14.dp, 0.dp, 14.dp, 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { details() }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(glass.fillStrong)
            .border(1.dp, glass.strokeBottom, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun BucketDetails(
    draft: SplitItemDraft,
    form: SplitForm,
    canDelete: Boolean,
    onChange: ((SplitItemDraft) -> SplitItemDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val noGoal = stringResource(R.string.split_no_goal)
    TextInput(draft.label, { v -> onChange { it.copy(label = v) } }, stringResource(R.string.split_item_label))
    SegmentedChoice(
        SplitKind.entries.toList(), draft.kind, { k -> onChange { it.copy(kind = k, value = "") } },
        { stringResource(if (it == SplitKind.PERCENT) R.string.split_percent else R.string.split_fixed) },
    )
    if (draft.kind == SplitKind.PERCENT) {
        TextInput(
            draft.value,
            { v -> onChange { it.copy(value = Money.normalizeDigits(v).filter { c -> c.isDigit() || c == '.' }.take(6)) } },
            stringResource(R.string.split_percent_value),
            keyboardType = KeyboardType.Decimal,
        )
    } else {
        AmountField(draft.value, { v -> onChange { it.copy(value = v) } }, form.currency)
    }
    DropdownField(
        stringResource(R.string.split_link_goal),
        listOf<String?>(null) + form.goals.map { it.goal.id },
        draft.goalId,
        { g -> onChange { it.copy(goalId = g) } },
        { gid -> form.goals.firstOrNull { it.goal.id == gid }?.goal?.name ?: noGoal },
        placeholder = noGoal,
    )
    if (canDelete) {
        TextButton(onClick = onDelete) {
            Icon(MIcons.Trash, contentDescription = null, tint = LocalFinanceColors.current.over, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.split_remove_item), color = LocalFinanceColors.current.over)
        }
    }
}

@Composable
private fun AddBucketButton(onClick: () -> Unit) {
    val dash = LocalGlass.current.track
    val violet = MaterialTheme.colorScheme.secondary
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MIcons.Plus, contentDescription = null, tint = violet, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.split_add_item), style = MaterialTheme.typography.labelLarge, color = violet)
    }
}

/**
 * "ده يعني": the daily number the spendable buckets leave (spendable ÷ 30), and each goal-linked bucket against that
 * goal's own monthly plan (README: compare savings with the goal's plan).
 */
@Composable
private fun MeansCard(form: SplitForm, result: SplitResult) {
    val money = LocalMoneyFormatter.current
    val finance = LocalFinanceColors.current
    val violet = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1B1830)).border(1.dp, Color(0xFF332C55), shape).padding(16.dp, 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.split_means), style = MaterialTheme.typography.labelLarge, color = violet)
        when (result) {
            is SplitResult.Ok -> {
                val spendable = result.lines.filter { it.item.goalId == null }.sumOf { it.amountMinor } + result.unallocatedMinor
                MeansRow(stringResource(R.string.split_daily_label)) {
                    Text(
                        stringResource(R.string.split_per_day, money.format(spendable / 30, form.currency)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                result.lines.filter { it.item.goalId != null }.forEach { line ->
                    val goal = form.goals.firstOrNull { it.goal.id == line.item.goalId }?.goal ?: return@forEach
                    val plan = goal.plannedMonthlyMinor ?: return@forEach
                    val onTime = line.amountMinor >= plan
                    MeansRow(stringResource(R.string.split_goal_label, goal.name)) {
                        Text(
                            if (onTime) stringResource(R.string.split_goal_on_time)
                            else stringResource(R.string.split_goal_less, money.format(plan - line.amountMinor, form.currency)),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (onTime) MaterialTheme.colorScheme.primary else finance.warning,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            is SplitResult.FixedExceedsIncome -> Text(stringResource(R.string.split_fixed_over), color = finance.over, style = MaterialTheme.typography.bodySmall)
            is SplitResult.PercentOver100 -> Text(stringResource(R.string.split_percent_over), color = finance.over, style = MaterialTheme.typography.bodySmall)
            SplitResult.Empty -> Text(stringResource(R.string.split_error_empty), color = LocalGlass.current.textTertiary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MeansRow(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { value() }
    }
}

/** "اعملها لوحدك كل مرة": split every new income with this rule as soon as it is logged. */
@Composable
private fun AutoCard(auto: Boolean, onChange: (Boolean) -> Unit) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.fill)
            .border(1.dp, glass.rim, shape)
            .toggleable(auto, role = Role.Switch, onValueChange = onChange)
            .padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.split_auto_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (auto) R.string.split_auto_on else R.string.split_auto_off), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        }
        GlassSwitch(checked = auto, onCheckedChange = null)
    }
}
