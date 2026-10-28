package com.masroufi.app.feature.wallets

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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.masroufi.app.ui.components.GlassFab
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.data.model.Wallet
import com.masroufi.app.data.model.WalletType
import com.masroufi.app.data.model.WalletWithBalance
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.TransactionFilter
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.ChipChoice
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.TransactionItem
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.ColorPicker
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.navigation.WalletDetailRoute
import com.masroufi.app.ui.navigation.WalletEditorRoute
import com.masroufi.app.ui.navigation.sharedContainer
import com.masroufi.app.ui.theme.AmountStyle
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
import javax.inject.Inject

@Composable
fun walletTypeLabel(type: WalletType): String = stringResource(
    when (type) {
        WalletType.CASH -> R.string.wallet_type_cash
        WalletType.BANK -> R.string.wallet_type_bank
        WalletType.DIGITAL -> R.string.wallet_type_digital
        WalletType.CUSTOM -> R.string.wallet_type_custom
    },
)

@HiltViewModel
class WalletsViewModel @Inject constructor(wallets: WalletRepository) : ViewModel() {
    val wallets: StateFlow<List<WalletWithBalance>?> = wallets.observeWallets()
        .map<List<WalletWithBalance>, List<WalletWithBalance>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun WalletsScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: WalletsViewModel = hiltViewModel()) {
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(R.string.wallets_title), onBack = onBack) },
        floatingActionButton = { GlassFab(onClick = onAdd) { Icon(Icons.Outlined.Add, stringResource(R.string.wallet_add)) } },
    ) { padding ->
        val list = wallets ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(CategoryIcons.walletGeneric, stringResource(R.string.wallets_empty_title), stringResource(R.string.wallets_empty_body), Modifier.padding(padding), stringResource(R.string.wallet_add), onAdd)
            return@Scaffold
        }
        val (archived, active) = list.partition { it.wallet.archived }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(active, key = { it.wallet.id }) { WalletCard(it, onOpen) }
            if (archived.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.wallets_archived)) }
                items(archived, key = { it.wallet.id }) { WalletCard(it, onOpen) }
            }
        }
    }
}

@Composable
private fun WalletCard(item: WalletWithBalance, onOpen: (String) -> Unit) {
    PressableCard(onClick = { onOpen(item.wallet.id) }, modifier = Modifier.sharedContainer("wallet-${item.wallet.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(CategoryIcons.wallet(item.wallet.type), categoryColor(item.wallet.colorIndex))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.wallet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(walletTypeLabel(item.wallet.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedAmount(item.balanceMinor, item.wallet.currency, style = MaterialTheme.typography.titleMedium)
        }
    }
}

data class WalletDetailState(val wallet: WalletWithBalance? = null, val rows: List<TransactionRow> = emptyList())

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val wallets: WalletRepository,
    transactions: TransactionRepository,
) : ViewModel() {
    val id = savedState.toRoute<WalletDetailRoute>().id
    val state: StateFlow<WalletDetailState> = combine(
        wallets.observeWallets(),
        transactions.observeRows(TransactionFilter(walletId = id, limit = 200)),
    ) { list, rows -> WalletDetailState(list.firstOrNull { it.wallet.id == id }, rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletDetailState())
    val effects = EffectChannel<EditorEffect>()

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { wallets.setArchived(id, archived) }
    }

    fun delete() {
        viewModelScope.launch { effects.attempt { wallets.delete(id) } }
    }
}

@Composable
fun WalletDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: (String) -> Unit,
    viewModel: WalletDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone = onBack)
    val item = state.wallet
    Scaffold(
        topBar = {
            MasroufiTopBar(item?.wallet?.name.orEmpty(), onBack = onBack) {
                if (item != null) {
                    IconButton(onClick = { onEdit(item.wallet.id) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { viewModel.setArchived(!item.wallet.archived) }) {
                        Icon(
                            if (item.wallet.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            stringResource(if (item.wallet.archived) R.string.action_unarchive else R.string.action_archive),
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                }
            }
        },
        floatingActionButton = {
            if (item != null && !item.wallet.archived) {
                GlassFab(onClick = { onAddTransaction(item.wallet.id) }) { Icon(Icons.Outlined.Add, stringResource(R.string.transaction_new)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (item == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                PressableCard(
                    onClick = null,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(16.dp).fillMaxWidth().sharedContainer("wallet-${item.wallet.id}"),
                ) {
                    Text(stringResource(R.string.wallet_balance), style = MaterialTheme.typography.labelLarge)
                    AnimatedAmount(item.balanceMinor, item.wallet.currency, style = AmountStyle)
                    Text(walletTypeLabel(item.wallet.type) + " · " + item.wallet.currency, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.rows.isEmpty()) {
                item { EmptyState(CategoryIcons.walletGeneric, stringResource(R.string.transactions_empty_title), stringResource(R.string.wallet_no_transactions)) }
            }
            items(state.rows, key = { it.transaction.id }) { row -> TransactionItem(row, onClick = { onOpenTransaction(row.transaction.id) }) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.wallet_delete_title),
            stringResource(R.string.wallet_delete_body),
            stringResource(R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

data class WalletForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val type: WalletType = WalletType.CASH,
    val currency: String = Money.DEFAULT_CURRENCY,
    val opening: String = "",
    val colorIndex: Int = 0,
)

@HiltViewModel
class WalletEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val wallets: WalletRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<WalletEditorRoute>().id
    private var original: Wallet? = null
    private val _form = MutableStateFlow(WalletForm())
    val form: StateFlow<WalletForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    init {
        viewModelScope.launch {
            val existing = id?.let { wallets.get(it) }
            original = existing
            val primary = settings.current().primaryCurrency
            _form.value = if (existing != null) {
                WalletForm(true, false, existing.name, existing.type, existing.currency, Money.toPlainString(existing.initialBalanceMinor, existing.currency), existing.colorIndex)
            } else {
                WalletForm(loaded = true, currency = primary)
            }
        }
    }

    fun update(block: (WalletForm) -> WalletForm) = _form.update(block)

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val now = clock.millis()
                val base = original
                wallets.save(
                    Wallet(
                        id = base?.id ?: newId(),
                        name = f.name,
                        type = f.type,
                        currency = base?.currency ?: f.currency,
                        initialBalanceMinor = Money.parse(f.opening, f.currency) ?: 0,
                        colorIndex = f.colorIndex,
                        archived = base?.archived ?: false,
                        sortOrder = base?.sortOrder ?: 0,
                        createdAt = base?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}

@Composable
fun WalletEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: WalletEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(if (form.isNew) R.string.wallet_add else R.string.wallet_edit), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_name))
            Text(stringResource(R.string.field_type), style = MaterialTheme.typography.titleSmall)
            ChipChoice(WalletType.entries.toList(), form.type, { t -> viewModel.update { it.copy(type = t) } }, { walletTypeLabel(it) })
            // Currency is fixed once a wallet exists: its history was recorded in that currency.
            CurrencyField(form.currency, { c -> viewModel.update { it.copy(currency = c) } }, enabled = form.isNew)
            AmountField(form.opening, { v -> viewModel.update { it.copy(opening = v) } }, form.currency, label = stringResource(R.string.field_opening_balance))
            Text(stringResource(R.string.field_color), style = MaterialTheme.typography.titleSmall)
            ColorPicker(form.colorIndex) { i -> viewModel.update { it.copy(colorIndex = i) } }
            GlassButton(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}

