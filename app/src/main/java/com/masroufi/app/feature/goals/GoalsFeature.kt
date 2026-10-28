@file:OptIn(ExperimentalMaterial3Api::class)

package com.masroufi.app.feature.goals

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.GlassSheet
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.Goal
import com.masroufi.app.data.model.GoalEntry
import com.masroufi.app.data.model.GoalWithSaved
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.GoalRepository
import com.masroufi.app.domain.GoalEstimate
import com.masroufi.app.domain.GoalMath
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.ChipChoice
import com.masroufi.app.ui.components.ColorPicker
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.DonutChart
import com.masroufi.app.ui.components.ChartSlice
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.OptionalDateChip
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.navigation.GoalDetailRoute
import com.masroufi.app.ui.navigation.GoalEditorRoute
import com.masroufi.app.ui.navigation.DockClearance
import com.masroufi.app.ui.navigation.TabInsets
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.navigation.sharedContainer
import com.masroufi.app.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(goals: GoalRepository) : ViewModel() {
    val goals: StateFlow<List<GoalWithSaved>?> = goals.observeGoals()
        .map<List<GoalWithSaved>, List<GoalWithSaved>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun GoalsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = TabInsets,
        topBar = { MasroufiTopBar(stringResource(R.string.tab_goals)) { GlassIconButton(onClick = onAdd) { Icon(MIcons.Plus, stringResource(R.string.goal_add)) } } },
    ) { padding ->
        val list = goals ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(MIcons.Goals, stringResource(R.string.goals_empty_title), stringResource(R.string.goals_empty_body), Modifier.padding(padding), stringResource(R.string.goal_add), onAdd)
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).edgeFade(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, DockClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.goals_earmark_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.goal.id }) { item -> GoalCard(item, onOpen, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun GoalCard(item: GoalWithSaved, onOpen: (String) -> Unit, modifier: Modifier) {
    val fraction = GoalMath.fraction(item.savedMinor, item.goal.targetMinor)
    val money = LocalMoneyFormatter.current
    PressableCard(onClick = { onOpen(item.goal.id) }, modifier = modifier.sharedContainer("goal-${item.goal.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(CategoryIcons.of(item.goal.iconKey), categoryColor(item.goal.colorIndex))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    stringResource(R.string.goal_saved_of, money.format(item.savedMinor, item.goal.currency), money.format(item.goal.targetMinor, item.goal.currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        AnimatedProgressBar(fraction, categoryColor(item.goal.colorIndex))
    }
}

data class GoalDetailState(
    val goal: GoalWithSaved? = null,
    val entries: List<GoalEntry> = emptyList(),
    val estimate: GoalEstimate? = null,
)

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val goals: GoalRepository,
    clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<GoalDetailRoute>().id
    val state: StateFlow<GoalDetailState> = combine(goals.observeGoal(id), goals.observeEntries(id)) { g, e ->
        val today = LocalDate.now(clock)
        GoalDetailState(g, e, g?.let { GoalMath.estimate(e.map { entry -> entry.occurredAt.toLocalDate(clock.zone) to entry.amountMinor }, it.goal.targetMinor, today) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailState())
    val effects = EffectChannel<EditorEffect>()
    val closeEffects = EffectChannel<EditorEffect>()

    fun addEntry(amountMinor: Long, note: String) {
        viewModelScope.launch { effects.attempt { goals.addEntry(id, amountMinor, note) } }
    }

    fun deleteEntry(entry: GoalEntry) {
        viewModelScope.launch { effects.attempt { goals.deleteEntry(entry) } }
    }

    fun delete() {
        viewModelScope.launch { closeEffects.attempt { goals.delete(id) } }
    }
}

@Composable
fun GoalDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: GoalDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var entrySheet by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dates = rememberDateFormatter()
    val money = LocalMoneyFormatter.current
    val finance = LocalFinanceColors.current
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone = { entrySheet = null })
    HandleEditorEffects(viewModel.closeEffects.flow, snackbar, onDone = onBack)
    val item = state.goal
    Scaffold(
        topBar = {
            MasroufiTopBar(item?.goal?.name.orEmpty(), onBack = onBack) {
                if (item != null) {
                    GlassIconButton(onClick = { onEdit(item.goal.id) }) { Icon(MIcons.Pencil, stringResource(R.string.action_edit)) }
                    GlassIconButton(onClick = { confirmDelete = true }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (item == null) return@Scaffold
        val goal = item.goal
        val fraction = GoalMath.fraction(item.savedMinor, goal.targetMinor)
        val color = categoryColor(goal.colorIndex)
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PressableCard(onClick = null, modifier = Modifier.sharedContainer("goal-${goal.id}")) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DonutChart(
                            slices = listOf(ChartSlice(goal.name, item.savedMinor.coerceAtLeast(0), color), ChartSlice("", (goal.targetMinor - item.savedMinor).coerceAtLeast(0), androidx.compose.ui.graphics.Color.Transparent)),
                            modifier = Modifier.size(180.dp),
                            description = "${(fraction * 100).toInt()}%",
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                                AnimatedAmount(item.savedMinor, goal.currency, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.goal_target, money.format(goal.targetMinor, goal.currency)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.goal_remaining, money.format((goal.targetMinor - item.savedMinor).coerceAtLeast(0), goal.currency)), style = MaterialTheme.typography.bodyMedium)
                    goal.targetEpochDay?.let { Text(stringResource(R.string.goal_target_date, LocalDate.ofEpochDay(it).format(dates)), style = MaterialTheme.typography.bodyMedium) }
                    goal.plannedMonthlyMinor?.let { Text(stringResource(R.string.goal_planned_monthly, money.format(it, goal.currency)), style = MaterialTheme.typography.bodyMedium) }
                    val estimate = state.estimate
                    Text(
                        when {
                            item.savedMinor >= goal.targetMinor -> stringResource(R.string.goal_reached)
                            estimate != null -> stringResource(R.string.goal_estimate, estimate.estimatedDate.format(dates), money.format(estimate.monthlyRateMinor, goal.currency))
                            else -> stringResource(R.string.goal_estimate_unavailable)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton(onClick = { entrySheet = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.goal_contribute)) }
                        GlassSecondaryButton(onClick = { entrySheet = false }, enabled = item.savedMinor > 0, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.goal_withdraw)) }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.goal_history)) }
            if (state.entries.isEmpty()) item { Text(stringResource(R.string.goal_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.entries, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth().animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.note.ifBlank { stringResource(if (entry.amountMinor > 0) R.string.goal_contribution else R.string.goal_withdrawal) }, style = MaterialTheme.typography.bodyMedium)
                        Text(entry.occurredAt.toLocalDate().format(dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyText(entry.amountMinor, goal.currency, color = if (entry.amountMinor > 0) finance.income else finance.expense, signed = true)
                    IconButton(onClick = { viewModel.deleteEntry(entry) }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
        }
    }

    entrySheet?.let { contribute ->
        AmountSheet(
            title = stringResource(if (contribute) R.string.goal_contribute else R.string.goal_withdraw),
            currency = item?.goal?.currency ?: Money.DEFAULT_CURRENCY,
            onDismiss = { entrySheet = null },
            onConfirm = { amount, note -> viewModel.addEntry(if (contribute) amount else -amount, note) },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.goal_delete_title), stringResource(R.string.goal_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}

/** Bottom sheet for a single amount plus optional note; reused by goals and debt repayments. */
@Composable
fun AmountSheet(title: String, currency: String, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit, initial: String = "") {
    var amount by rememberSaveable { mutableStateOf(initial) }
    var note by rememberSaveable { mutableStateOf("") }
    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            AmountField(amount, { amount = it }, currency)
            TextInput(note, { note = it }, stringResource(R.string.field_note))
            val parsed = Money.parse(amount, currency) ?: 0
            GlassSecondaryButton(onClick = { onConfirm(parsed, note) }, enabled = parsed > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

data class GoalForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val target: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
    val targetDate: LocalDate? = null,
    val monthly: String = "",
    val iconKey: String = "savings",
    val colorIndex: Int = 1,
)

@HiltViewModel
class GoalEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val goals: GoalRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<GoalEditorRoute>().id
    private var original: Goal? = null
    private val _form = MutableStateFlow(GoalForm())
    val form: StateFlow<GoalForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    init {
        viewModelScope.launch {
            val existing = id?.let { goals.get(it) }
            original = existing
            _form.value = existing?.let {
                GoalForm(
                    true, false, it.name, Money.toPlainString(it.targetMinor, it.currency), it.currency,
                    it.targetEpochDay?.let(LocalDate::ofEpochDay), it.plannedMonthlyMinor?.let { m -> Money.toPlainString(m, it.currency) }.orEmpty(),
                    it.iconKey, it.colorIndex,
                )
            } ?: GoalForm(loaded = true, currency = settings.current().primaryCurrency)
        }
    }

    fun update(block: (GoalForm) -> GoalForm) = _form.update(block)

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val now = clock.millis()
                goals.save(
                    Goal(
                        id = original?.id ?: newId(),
                        name = f.name,
                        targetMinor = Money.parse(f.target, f.currency) ?: 0,
                        currency = original?.currency ?: f.currency,
                        targetEpochDay = f.targetDate?.toEpochDay(),
                        iconKey = f.iconKey,
                        colorIndex = f.colorIndex,
                        plannedMonthlyMinor = Money.parse(f.monthly, f.currency)?.takeIf { it > 0 },
                        archived = original?.archived ?: false,
                        createdAt = original?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}

private val goalIcons = listOf("savings", "home", "car", "travel", "school", "health", "phone", "gift", "family", "shopping")

@Composable
fun GoalEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: GoalEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(if (form.isNew) R.string.goal_add else R.string.goal_edit), onBack = onBack) },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_name))
            AmountField(form.target, { v -> viewModel.update { it.copy(target = v) } }, form.currency, label = stringResource(R.string.field_target_amount))
            CurrencyField(form.currency, { c -> viewModel.update { it.copy(currency = c) } }, enabled = form.isNew)
            OptionalDateChip(form.targetDate, { d -> viewModel.update { it.copy(targetDate = d) } }, stringResource(R.string.field_target_date))
            AmountField(form.monthly, { v -> viewModel.update { it.copy(monthly = v) } }, form.currency, label = stringResource(R.string.field_planned_monthly))
            Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.titleSmall)
            ChipChoice(goalIcons, form.iconKey, { k -> viewModel.update { it.copy(iconKey = k) } }, { "" }, leading = { Icon(CategoryIcons.of(it), contentDescription = it) })
            Text(stringResource(R.string.field_color), style = MaterialTheme.typography.titleSmall)
            ColorPicker(form.colorIndex, { i -> viewModel.update { it.copy(colorIndex = i) } })
            GlassButton(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}
