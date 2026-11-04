package com.masroufi.app.feature.wallets

import com.masroufi.app.ui.components.pressClick
import com.masroufi.app.ui.components.staggerIn
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import com.masroufi.app.ui.components.GlassSheet
import com.masroufi.app.ui.components.ServiceTile
import com.masroufi.app.ui.components.Services
import com.masroufi.app.ui.theme.LocalGlass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.data.model.TransactionType
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import com.masroufi.app.ui.components.FormSection
import com.masroufi.app.ui.components.EditorSaveBar
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.FitAmount
import com.masroufi.app.ui.components.MIcons
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
class WalletsViewModel @Inject constructor(wallets: WalletRepository, transactions: TransactionRepository, settings: SettingsRepository) : ViewModel() {
    val wallets: StateFlow<List<WalletWithBalance>?> = wallets.observeWallets()
        .map<List<WalletWithBalance>, List<WalletWithBalance>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val currency: StateFlow<String> = settings.settings.map { it.primaryCurrency }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Money.DEFAULT_CURRENCY)

    /** When money last moved in or out of each wallet, for the "آخر حركة" line. */
    val lastActivity: StateFlow<Map<String, Long>> = transactions.observeWalletActivity()
        .map { list -> list.associate { it.walletId to it.lastAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

/**
 * المحافظ: the total and how it is split, every wallet in one list with when it last moved, the kinds people usually
 * add next, the three everyday actions (move money, fix a balance, a cash withdrawal) and logging from a pasted SMS.
 */
@Composable
fun WalletsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onAddPreset: (name: String, type: WalletType) -> Unit,
    onOpen: (String) -> Unit,
    onTransfer: () -> Unit,
    onReconcile: (String) -> Unit,
    onCashOut: (fromId: String?, toId: String?, note: String) -> Unit,
    onLogSms: (type: TransactionType, walletId: String?, amount: String, note: String?) -> Unit,
    viewModel: WalletsViewModel = hiltViewModel(),
) {
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val activity by viewModel.lastActivity.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(R.string.wallets_title), onBack = onBack) { GlassIconButton(onClick = onAdd) { Icon(MIcons.Plus, stringResource(R.string.wallet_add)) } } },
    ) { padding ->
        val list = wallets ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(CategoryIcons.walletGeneric, stringResource(R.string.wallets_empty_title), stringResource(R.string.wallets_empty_body), Modifier.padding(padding), stringResource(R.string.wallet_add), onAdd)
            return@Scaffold
        }
        val (archived, active) = list.partition { it.wallet.archived }
        val cashNote = stringResource(R.string.wallets_cash_note)
        val instaName = stringResource(R.string.wallet_preset_instapay)
        val bankName = stringResource(R.string.wallet_preset_bank)
        val presets = buildList {
            if (active.none { Services.forWallet(it.wallet.name)?.key == "w_instapay" }) add(instaName to WalletType.DIGITAL)
            if (active.none { it.wallet.type == WalletType.BANK }) add(bankName to WalletType.BANK)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).edgeFade(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "total") { Box(Modifier.staggerIn(0)) { WalletsTotal(list, currency) } }
            item(key = "list") { Box(Modifier.staggerIn(1)) { WalletGroup(active, activity, onOpen, presets, onAddPreset) } }
            if (active.isNotEmpty()) {
                item(key = "actions") {
                    Box(Modifier.staggerIn(2)) { QuickActions(
                        onTransfer = onTransfer,
                        onReconcile = { if (active.size == 1) onReconcile(active.first().wallet.id) else picking = true },
                        onCashOut = {
                            val cash = active.firstOrNull { it.wallet.type == WalletType.CASH }
                            val from = active.firstOrNull { it.wallet.type == WalletType.BANK }
                                ?: active.firstOrNull { it.wallet.type != WalletType.CASH }
                            onCashOut(from?.wallet?.id, cash?.wallet?.id, cashNote)
                        },
                    ) }
                }
            }
            item(key = "sms") { Box(Modifier.staggerIn(3)) { SmsLogCard(active, onLogSms) } }
            if (archived.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.wallets_archived)) }
                item(key = "archived") { WalletGroup(archived, activity, onOpen) }
            }
        }
        if (picking) {
            GlassSheet(onDismissRequest = { picking = false }) {
                Text(
                    stringResource(R.string.wallets_reconcile_pick),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Column(Modifier.padding(16.dp, 4.dp, 16.dp, 28.dp)) {
                    WalletGroup(active, activity, onOpen = { picking = false; onReconcile(it) }, shared = false)
                }
            }
        }
    }
}

/** One card holding every wallet as a row (divided by the 1 px stroke), with dashed "add" chips under them. */
@Composable
private fun WalletGroup(
    wallets: List<WalletWithBalance>,
    activity: Map<String, Long>,
    onOpen: (String) -> Unit,
    presets: List<Pair<String, WalletType>> = emptyList(),
    onAddPreset: (String, WalletType) -> Unit = { _, _ -> },
    shared: Boolean = true,
) {
    val glass = LocalGlass.current
    val shape = MaterialTheme.shapes.large
    Column(Modifier.fillMaxWidth().clip(shape).background(glass.fill).border(1.dp, glass.rim, shape)) {
        wallets.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = glass.strokeBottom)
            WalletRow(item, activity[item.wallet.id], onOpen, shared)
        }
        if (presets.isNotEmpty()) {
            if (wallets.isNotEmpty()) HorizontalDivider(color = glass.strokeBottom)
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (name, type) -> AddChip(name, Modifier.weight(1f)) { onAddPreset(name, type) } }
            }
        }
    }
}

@Composable
private fun WalletRow(item: WalletWithBalance, lastAt: Long?, onOpen: (String) -> Unit, shared: Boolean) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (shared) Modifier.sharedContainer("wallet-${item.wallet.id}") else Modifier)
            .pressClick(RectangleShape, scaleTo = 1f) { onOpen(item.wallet.id) }
            .padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val brand = Services.forWallet(item.wallet.name)
        if (brand != null) ServiceTile(brand, size = 44.dp)
        else IconBadge(CategoryIcons.wallet(item.wallet.type), categoryColor(item.wallet.colorIndex))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.wallet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(lastActivityLabel(lastAt), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary, maxLines = 1)
        }
        AnimatedAmount(item.balanceMinor, item.wallet.currency, style = MaterialTheme.typography.titleMedium)
    }
}

/** "آخر حركة النهارده / امبارح / 23 أكتوبر", or "مفيش حركة لسه" for a wallet nothing was logged in. */
@Composable
private fun lastActivityLabel(lastAt: Long?): String {
    if (lastAt == null) return stringResource(R.string.wallet_no_activity)
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(lastAt).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val format = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }
    val whenText = when (day) {
        today -> stringResource(R.string.day_today).lowercase(locale)
        today.minusDays(1) -> stringResource(R.string.day_yesterday).lowercase(locale)
        else -> day.format(format)
    }
    return stringResource(R.string.wallet_last_activity, whenText)
}

@Composable
private fun AddChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dash = LocalGlass.current.track
    Row(
        modifier
            .pressClick(MaterialTheme.shapes.extraSmall, onClick = onClick)
            .height(44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .drawBehind {
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** حوّل بينهم · سوّي الرصيد · سحبت كاش, three equal tiles. */
@Composable
private fun QuickActions(onTransfer: () -> Unit, onReconcile: () -> Unit, onCashOut: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile(MIcons.Transfer, categoryColor(1), stringResource(R.string.wallets_qa_transfer), onTransfer, Modifier.weight(1f))
        ActionTile(MIcons.Repeat, categoryColor(8), stringResource(R.string.wallets_qa_reconcile), onReconcile, Modifier.weight(1f))
        ActionTile(MIcons.Cash, categoryColor(2), stringResource(R.string.wallets_qa_cash), onCashOut, Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit, modifier: Modifier) {
    val glass = LocalGlass.current
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .pressClick(shape, onClick = onClick)
            .height(76.dp)
            .clip(shape)
            .background(glass.fill)
            .border(1.dp, glass.rim, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

data class WalletDetailState(val wallet: WalletWithBalance? = null, val rows: List<TransactionRow> = emptyList())

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val wallets: WalletRepository,
    private val transactions: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedState.toRoute<WalletDetailRoute>()
    val id = route.id
    val startReconcile = route.reconcile
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

    val reconcileEffects = EffectChannel<EditorEffect>()

    /**
     * سوّي الرصيد: the user types what the wallet really holds; the difference is logged as one adjustment entry
     * (uncategorised, noted "تسوية الرصيد"), keeping history intact instead of rewriting the opening balance.
     */
    fun reconcile(actualMinor: Long, note: String) {
        val w = state.value.wallet ?: return
        val diff = actualMinor - w.balanceMinor
        viewModelScope.launch {
            reconcileEffects.attempt {
                if (diff != 0L) {
                    val now = clock.millis()
                    transactions.save(
                        Transaction(
                            id = newId(), type = if (diff > 0) TransactionType.INCOME else TransactionType.EXPENSE,
                            amountMinor = kotlin.math.abs(diff), currency = w.wallet.currency, walletId = w.wallet.id,
                            note = note, occurredAt = now, createdAt = now, updatedAt = now,
                        ),
                    )
                }
            }
        }
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
    var reconciling by rememberSaveable { mutableStateOf(viewModel.startReconcile) }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone = onBack)
    HandleEditorEffects(viewModel.reconcileEffects.flow, snackbar, onDone = { reconciling = false })
    val reconcileNote = stringResource(R.string.wallet_reconcile_note)
    val item = state.wallet
    if (reconciling && item != null) {
        com.masroufi.app.feature.goals.AmountSheet(
            title = stringResource(R.string.wallet_reconcile_title),
            currency = item.wallet.currency,
            onDismiss = { reconciling = false },
            onConfirm = { amount, _ -> viewModel.reconcile(amount, reconcileNote) },
            initial = Money.toPlainString(item.balanceMinor.coerceAtLeast(0), item.wallet.currency),
        )
    }
    Scaffold(
        topBar = {
            MasroufiTopBar(item?.wallet?.name.orEmpty(), onBack = onBack) {
                if (item != null) {
                    if (!item.wallet.archived) {
                        GlassIconButton(onClick = { onAddTransaction(item.wallet.id) }) { Icon(MIcons.Plus, stringResource(R.string.transaction_new)) }
                    }
                    if (!item.wallet.archived) {
                        GlassIconButton(onClick = { reconciling = true }) { Icon(MIcons.Check, stringResource(R.string.wallet_reconcile)) }
                    }
                    GlassIconButton(onClick = { onEdit(item.wallet.id) }) { Icon(MIcons.Pencil, stringResource(R.string.action_edit)) }
                    GlassIconButton(onClick = { viewModel.setArchived(!item.wallet.archived) }) {
                        Icon(
                            if (item.wallet.archived) MIcons.Unarchive else MIcons.Archive,
                            stringResource(if (item.wallet.archived) R.string.action_unarchive else R.string.action_archive),
                        )
                    }
                    GlassIconButton(onClick = { confirmDelete = true }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (item == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
            item {
                PressableCard(
                    onClick = null,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(16.dp).fillMaxWidth().sharedContainer("wallet-${item.wallet.id}"),
                ) {
                    Text(stringResource(R.string.wallet_balance), style = MaterialTheme.typography.labelLarge)
                    FitAmount(item.balanceMinor, item.wallet.currency, style = AmountStyle)
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
    private val route = savedState.toRoute<WalletEditorRoute>()
    private val id = route.id
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
                WalletForm(
                    loaded = true,
                    currency = primary,
                    name = route.name.orEmpty().take(40),
                    type = route.type?.let { t -> WalletType.entries.firstOrNull { it.name == t } } ?: WalletType.CASH,
                )
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
        snackbarHost = { GlassSnackbarHost(snackbar) },
        bottomBar = { if (form.loaded) EditorSaveBar(viewModel::save) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Live preview: the wallet card as it will appear, updating with every field.
            val color = categoryColor(form.colorIndex)
            PressableCard(onClick = null, modifier = Modifier.fillMaxWidth(), color = color.copy(alpha = 0.22f)) {
                IconBadge(CategoryIcons.wallet(form.type), color, size = 48.dp)
                Spacer(Modifier.height(20.dp))
                Text(form.name.ifBlank { stringResource(R.string.wallet_preview_name) }, style = MaterialTheme.typography.titleMedium)
                FitAmount(Money.parse(form.opening, form.currency) ?: 0L, form.currency, style = AmountStyle)
            }
            FormSection(stringResource(R.string.section_details)) {
                TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_name))
                ChipChoice(WalletType.entries.toList(), form.type, { t -> viewModel.update { it.copy(type = t) } }, { walletTypeLabel(it) })
                // Currency is fixed once a wallet exists: its history was recorded in that currency.
                CurrencyField(form.currency, { c -> viewModel.update { it.copy(currency = c) } }, enabled = form.isNew)
                AmountField(form.opening, { v -> viewModel.update { it.copy(opening = v) } }, form.currency, label = stringResource(R.string.field_opening_balance))
            }
            FormSection(stringResource(R.string.section_look)) {
                ColorPicker(form.colorIndex) { i -> viewModel.update { it.copy(colorIndex = i) } }
            }
        }
    }
}

