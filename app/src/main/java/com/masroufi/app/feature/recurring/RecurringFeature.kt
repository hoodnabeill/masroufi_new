package com.masroufi.app.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.masroufi.app.R
import com.masroufi.app.ui.components.GlassFab
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.CategoryKind
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.WalletWithBalance
import com.masroufi.app.data.repo.CategoryRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.domain.Frequency
import com.masroufi.app.feature.transactions.typeLabel
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.ChipChoice
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.DateChip
import com.masroufi.app.ui.components.DropdownField
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.OptionalDateChip
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.components.typeColor
import com.masroufi.app.ui.navigation.RecurringEditorRoute
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

@Composable
fun frequencyLabel(frequency: Frequency, interval: Int): String = when (frequency) {
    Frequency.DAILY -> pluralStringResource(R.plurals.every_days, interval, interval)
    Frequency.WEEKLY -> pluralStringResource(R.plurals.every_weeks, interval, interval)
    Frequency.MONTHLY -> pluralStringResource(R.plurals.every_months, interval, interval)
    Frequency.YEARLY -> pluralStringResource(R.plurals.every_years, interval, interval)
}

data class RecurringListState(val loading: Boolean = true, val rules: List<RecurringRule> = emptyList(), val wallets: Map<String, String> = emptyMap())

@HiltViewModel
class RecurringViewModel @Inject constructor(private val recurring: RecurringRepository, wallets: WalletRepository) : ViewModel() {
    val state: StateFlow<RecurringListState> = combine(recurring.observeAll(), wallets.observeWallets()) { r, w ->
        RecurringListState(false, r, w.associate { it.wallet.id to it.wallet.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringListState())

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch { recurring.setActive(id, active) }
    }
}

@Composable
fun RecurringScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, viewModel: RecurringViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dates = rememberDateFormatter()
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(R.string.recurring_title), onBack = onBack) },
        floatingActionButton = { GlassFab(onClick = onAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.recurring_add)) } },
    ) { padding ->
        if (state.loading) return@Scaffold
        if (state.rules.isEmpty()) {
            EmptyState(Icons.Outlined.Repeat, stringResource(R.string.recurring_empty_title), stringResource(R.string.recurring_empty_body), Modifier.padding(padding), stringResource(R.string.recurring_add), onAdd)
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.rules, key = { it.id }) { rule ->
                PressableCard(onClick = { onEdit(rule.id) }, modifier = Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.note.ifBlank { typeLabel(rule.type) }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                frequencyLabel(rule.frequency, rule.interval) + " · " + state.wallets[rule.walletId].orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                when {
                                    !rule.active -> stringResource(R.string.recurring_paused)
                                    rule.nextEpochDay != null -> stringResource(R.string.recurring_next, LocalDate.ofEpochDay(rule.nextEpochDay).format(dates))
                                    else -> stringResource(R.string.recurring_finished)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        MoneyText(rule.amountMinor, rule.currency, style = MaterialTheme.typography.titleMedium, color = typeColor(rule.type))
                        IconButton(onClick = { viewModel.setActive(rule.id, !rule.active) }) {
                            Icon(
                                if (rule.active) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                stringResource(if (rule.active) R.string.action_pause else R.string.action_resume),
                            )
                        }
                    }
                }
            }
        }
    }
}

data class RecurringForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val walletId: String? = null,
    val toWalletId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: String = "1",
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate? = null,
    val remind: Boolean = true,
    val wallets: List<WalletWithBalance> = emptyList(),
    val categories: List<Category> = emptyList(),
) {
    val currency: String get() = wallets.firstOrNull { it.wallet.id == walletId }?.wallet?.currency ?: Money.DEFAULT_CURRENCY
}

@HiltViewModel
class RecurringEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val recurring: RecurringRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<RecurringEditorRoute>().id
    private var original: RecurringRule? = null
    private val _form = MutableStateFlow(RecurringForm())
    val form: StateFlow<RecurringForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    init {
        viewModelScope.launch {
            val w = wallets.observeWallets().first().filter { !it.wallet.archived }
            val c = categories.observeAll().first()
            val existing = id?.let { recurring.get(it) }
            original = existing
            _form.value = existing?.let {
                RecurringForm(
                    loaded = true, isNew = false, type = it.type, amount = Money.toPlainString(it.amountMinor, it.currency),
                    walletId = it.walletId, toWalletId = it.toWalletId, categoryId = it.categoryId, note = it.note,
                    frequency = it.frequency, interval = it.interval.toString(), start = LocalDate.ofEpochDay(it.startEpochDay),
                    end = it.endEpochDay?.let(LocalDate::ofEpochDay), remind = it.remind, wallets = w, categories = c,
                )
            } ?: RecurringForm(loaded = true, walletId = w.firstOrNull()?.wallet?.id, start = LocalDate.now(clock), wallets = w, categories = c)
        }
    }

    fun update(block: (RecurringForm) -> RecurringForm) = _form.update(block)

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val base = original
                recurring.save(
                    RecurringRule(
                        id = base?.id ?: newId(),
                        type = f.type,
                        amountMinor = Money.parse(f.amount, f.currency) ?: 0,
                        currency = f.currency,
                        walletId = f.walletId.orEmpty(),
                        toWalletId = if (f.type == TransactionType.TRANSFER) f.toWalletId else null,
                        categoryId = if (f.type == TransactionType.TRANSFER) null else f.categoryId,
                        note = f.note,
                        frequency = f.frequency,
                        interval = f.interval.toIntOrNull()?.coerceIn(1, 365) ?: 1,
                        startEpochDay = f.start.toEpochDay(),
                        endEpochDay = f.end?.toEpochDay(),
                        // Changing the schedule restarts generation from the new start, without re-creating past entries.
                        lastGeneratedEpochDay = base?.lastGeneratedEpochDay,
                        active = base?.active ?: true,
                        remind = f.remind,
                        updatedAt = clock.millis(),
                    ),
                )
                recurring.processDue()
            }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch { effects.attempt { recurring.delete(id) } }
    }
}

@Composable
fun RecurringEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: RecurringEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    Scaffold(
        topBar = {
            MasroufiTopBar(stringResource(if (form.isNew) R.string.recurring_add else R.string.recurring_edit), onBack = onBack) {
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
            SegmentedChoice(TransactionType.entries.toList(), form.type, { t -> viewModel.update { it.copy(type = t, categoryId = null) } }, { typeLabel(it) })
            AmountField(form.amount, { v -> viewModel.update { it.copy(amount = v) } }, form.currency)
            TextInput(form.note, { v -> viewModel.update { it.copy(note = v) } }, stringResource(R.string.field_description))
            DropdownField(
                stringResource(if (form.type == TransactionType.TRANSFER) R.string.field_from_wallet else R.string.field_wallet),
                form.wallets, form.wallets.firstOrNull { it.wallet.id == form.walletId },
                { w -> viewModel.update { it.copy(walletId = w.wallet.id) } }, { it.wallet.name },
            )
            if (form.type == TransactionType.TRANSFER) {
                val targets = form.wallets.filter { it.wallet.id != form.walletId }
                DropdownField(
                    stringResource(R.string.field_to_wallet), targets, targets.firstOrNull { it.wallet.id == form.toWalletId },
                    { w -> viewModel.update { it.copy(toWalletId = w.wallet.id) } }, { it.wallet.name },
                )
            } else {
                val kind = if (form.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val cats = form.categories.filter { it.kind == kind }
                DropdownField(
                    stringResource(R.string.field_category), cats, cats.firstOrNull { it.id == form.categoryId },
                    { c -> viewModel.update { it.copy(categoryId = c.id) } }, { categoryLabel(it) },
                )
            }
            Text(stringResource(R.string.field_frequency), style = MaterialTheme.typography.titleSmall)
            ChipChoice(Frequency.entries.toList(), form.frequency, { fr -> viewModel.update { it.copy(frequency = fr) } }, { frequencyLabel(it, 1) })
            TextInput(
                form.interval,
                { v -> viewModel.update { it.copy(interval = v.filter(Char::isDigit).take(3)) } },
                stringResource(R.string.field_interval),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                supportingText = frequencyLabel(form.frequency, form.interval.toIntOrNull()?.coerceAtLeast(1) ?: 1),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip(form.start, { d -> viewModel.update { it.copy(start = d) } }, label = stringResource(R.string.field_starts))
            }
            OptionalDateChip(form.end, { d -> viewModel.update { it.copy(end = d) } }, stringResource(R.string.field_ends_optional))
            ListItem(
                headlineContent = { Text(stringResource(R.string.recurring_remind)) },
                trailingContent = { Switch(form.remind, { v -> viewModel.update { it.copy(remind = v) } }) },
            )
            Text(stringResource(R.string.recurring_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GlassButton(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.recurring_delete_title), stringResource(R.string.recurring_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}
