package com.masroufi.app.feature.debts

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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Color
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
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.GlassSwitch
import com.masroufi.app.ui.components.FitAmount
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.Debt
import com.masroufi.app.data.model.DebtDirection
import com.masroufi.app.data.model.DebtPayment
import com.masroufi.app.data.model.DebtWithPaid
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.DebtRepository
import com.masroufi.app.domain.DebtMath
import com.masroufi.app.domain.DebtStatus
import com.masroufi.app.feature.goals.AmountSheet
import com.masroufi.app.feature.home.debtLabel
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.OptionalDateChip
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.navigation.DebtDetailRoute
import com.masroufi.app.ui.navigation.DebtEditorRoute
import com.masroufi.app.ui.theme.AmountStyle
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

@Composable
private fun StatusChip(status: DebtStatus) {
    val finance = LocalFinanceColors.current
    val (label, color) = when (status) {
        DebtStatus.OPEN -> stringResource(R.string.debt_status_open) to MaterialTheme.colorScheme.primary
        DebtStatus.OVERDUE -> stringResource(R.string.debt_status_overdue) to finance.over
        DebtStatus.SETTLED -> stringResource(R.string.debt_status_settled) to finance.income
    }
    AssistChip(onClick = {}, label = { Text(label, color = color) })
}

@HiltViewModel
class DebtsViewModel @Inject constructor(debts: DebtRepository, private val clock: Clock) : ViewModel() {
    val today: LocalDate get() = LocalDate.now(clock)
    val debts: StateFlow<List<DebtWithPaid>?> = debts.observeDebts()
        .map<List<DebtWithPaid>, List<DebtWithPaid>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class DebtDetailState(val debt: DebtWithPaid? = null, val payments: List<DebtPayment> = emptyList())

@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val debts: DebtRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<DebtDetailRoute>().id
    val today: LocalDate get() = LocalDate.now(clock)
    val state: StateFlow<DebtDetailState> = combine(debts.observeDebt(id), debts.observePayments(id)) { d, p -> DebtDetailState(d, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtDetailState())
    val effects = EffectChannel<EditorEffect>()
    val closeEffects = EffectChannel<EditorEffect>()

    fun addPayment(amount: Long, note: String) {
        viewModelScope.launch { effects.attempt { debts.addPayment(id, amount, note) } }
    }

    fun deletePayment(payment: DebtPayment) {
        viewModelScope.launch { effects.attempt { debts.deletePayment(payment) } }
    }

    fun delete() {
        viewModelScope.launch { closeEffects.attempt { debts.delete(id) } }
    }
}

@Composable
fun DebtDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: DebtDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var paying by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dates = rememberDateFormatter()
    val money = LocalMoneyFormatter.current
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone = { paying = false })
    HandleEditorEffects(viewModel.closeEffects.flow, snackbar, onDone = onBack)
    val item = state.debt
    Scaffold(
        topBar = {
            MasroufiTopBar(item?.debt?.personName.orEmpty(), onBack = onBack) {
                if (item != null) {
                    GlassIconButton(onClick = { onEdit(item.debt.id) }) { Icon(MIcons.Pencil, stringResource(R.string.action_edit)) }
                    GlassIconButton(onClick = { confirmDelete = true }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (item == null) return@Scaffold
        val debt = item.debt
        val remaining = DebtMath.remaining(debt.originalMinor, item.paidMinor)
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PressableCard(onClick = null, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(stringResource(debtLabel(debt.direction)), style = MaterialTheme.typography.labelLarge)
                    FitAmount(remaining, debt.currency, style = AmountStyle)
                    Text(stringResource(R.string.debt_original, money.format(debt.originalMinor, debt.currency)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.debt_paid, money.format(item.paidMinor, debt.currency)), style = MaterialTheme.typography.bodyMedium)
                    debt.dueEpochDay?.let { Text(stringResource(R.string.debt_due, LocalDate.ofEpochDay(it).format(dates)), style = MaterialTheme.typography.bodyMedium) }
                    if (debt.note.isNotBlank()) Text(debt.note, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(DebtMath.status(debt.originalMinor, item.paidMinor, debt.dueEpochDay, viewModel.today))
                    }
                    Spacer(Modifier.height(8.dp))
                    GlassButton(onClick = { paying = true }, enabled = remaining > 0, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.debt_record_payment))
                    }
                    debt.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                        Spacer(Modifier.height(8.dp))
                        com.masroufi.app.ui.components.ContactActions(phone, Modifier.fillMaxWidth())
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.debt_payments)) }
            if (state.payments.isEmpty()) item { Text(stringResource(R.string.debt_payments_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.payments, key = { it.id }) { payment ->
                Row(Modifier.fillMaxWidth().animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(payment.occurredAt.toLocalDate().format(dates), style = MaterialTheme.typography.bodyMedium)
                        if (payment.note.isNotBlank()) Text(payment.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyText(payment.amountMinor, debt.currency)
                    IconButton(onClick = { viewModel.deletePayment(payment) }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
        }
    }
    if (paying && item != null) {
        AmountSheet(
            title = stringResource(R.string.debt_record_payment),
            currency = item.debt.currency,
            onDismiss = { paying = false },
            onConfirm = viewModel::addPayment,
            initial = Money.toPlainString(DebtMath.remaining(item.debt.originalMinor, item.paidMinor), item.debt.currency),
        )
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.debt_delete_title), stringResource(R.string.debt_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}

data class DebtForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val direction: DebtDirection = DebtDirection.I_OWE,
    val person: String = "",
    val amount: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
    val due: LocalDate? = null,
    val note: String = "",
    val remind: Boolean = false,
    val phone: String = "",
)

@HiltViewModel
class DebtEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val debts: DebtRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedState.toRoute<DebtEditorRoute>()
    private val id = route.id
    private var original: Debt? = null
    private val _form = MutableStateFlow(DebtForm())
    val form: StateFlow<DebtForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    init {
        viewModelScope.launch {
            val existing = id?.let { debts.get(it) }
            original = existing
            _form.value = existing?.let {
                DebtForm(true, false, it.direction, it.personName, Money.toPlainString(it.originalMinor, it.currency), it.currency, it.dueEpochDay?.let(LocalDate::ofEpochDay), it.note, it.remind, it.phone.orEmpty())
            } ?: DebtForm(loaded = true, amount = route.amount.orEmpty(), currency = settings.current().primaryCurrency)
        }
    }

    fun update(block: (DebtForm) -> DebtForm) = _form.update(block)

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val now = clock.millis()
                debts.save(
                    Debt(
                        id = original?.id ?: newId(),
                        direction = f.direction,
                        personName = f.person,
                        originalMinor = Money.parse(f.amount, f.currency) ?: 0,
                        currency = original?.currency ?: f.currency,
                        dueEpochDay = f.due?.toEpochDay(),
                        note = f.note,
                        remind = f.remind,
                        phone = f.phone.trim().takeIf { it.isNotEmpty() },
                        createdAt = original?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}

@Composable
fun DebtEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: DebtEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(if (form.isNew) R.string.debt_add else R.string.debt_edit), onBack = onBack) },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SegmentedChoice(DebtDirection.entries.toList(), form.direction, { d -> viewModel.update { it.copy(direction = d) } }, { stringResource(debtLabel(it)) })
            val pick = com.masroufi.app.ui.components.rememberContactPicker { c -> viewModel.update { it.copy(person = it.person.ifBlank { c.name }, phone = c.phone) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(form.person, { v -> viewModel.update { it.copy(person = v) } }, stringResource(R.string.field_person), modifier = Modifier.weight(1f))
                GlassIconButton(onClick = pick, modifier = Modifier.padding(start = 8.dp)) { Icon(MIcons.Person, stringResource(R.string.contact_pick)) }
            }
            TextInput(form.phone, { v -> viewModel.update { it.copy(phone = v) } }, stringResource(R.string.field_phone_optional), keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            AmountField(form.amount, { v -> viewModel.update { it.copy(amount = v) } }, form.currency)
            CurrencyField(form.currency, { c -> viewModel.update { it.copy(currency = c) } }, enabled = form.isNew)
            OptionalDateChip(form.due, { d -> viewModel.update { it.copy(due = d) } }, stringResource(R.string.field_due_date))
            TextInput(form.note, { v -> viewModel.update { it.copy(note = v) } }, stringResource(R.string.field_note), singleLine = false)
            ListItem(
                headlineContent = { Text(stringResource(R.string.debt_remind)) },
                trailingContent = { GlassSwitch(form.remind, { v -> viewModel.update { it.copy(remind = v) } }) },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            GlassButton(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}
