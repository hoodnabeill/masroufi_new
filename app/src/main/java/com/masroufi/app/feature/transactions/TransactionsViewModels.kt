package com.masroufi.app.feature.transactions

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.core.startMillis
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.CategoryKind
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.WalletWithBalance
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.CategoryRepository
import com.masroufi.app.data.repo.TransactionFilter
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.receipts.ReceiptStore
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.navigation.TransactionDetailRoute
import com.masroufi.app.ui.navigation.TransactionEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TransactionsUiState(
    val loading: Boolean = true,
    val rows: List<TransactionRow> = emptyList(),
    val filter: TransactionFilter = TransactionFilter(),
    val query: String = "",
    val wallets: List<WalletWithBalance> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(TransactionFilter())
    private val query = MutableStateFlow("")

    val state: StateFlow<TransactionsUiState> = combine(
        filter.flatMapLatest { f -> transactions.observeRows(f) },
        filter,
        query,
        wallets.observeWallets(),
        categories.observeAll(),
    ) { rows, f, q, w, c -> TransactionsUiState(false, rows, f, q, w, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: TransactionFilter) {
        filter.value = value
    }
}

data class EditorUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val toAmount: String = "",
    val walletId: String? = null,
    val toWalletId: String? = null,
    val categoryId: String? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val tags: String = "",
    val receipt: String? = null,
    val wallets: List<WalletWithBalance> = emptyList(),
    val categories: List<Category> = emptyList(),
    val saving: Boolean = false,
) {
    val wallet: WalletWithBalance? get() = wallets.firstOrNull { it.wallet.id == walletId }
    val toWallet: WalletWithBalance? get() = wallets.firstOrNull { it.wallet.id == toWalletId }
    val currency: String get() = wallet?.wallet?.currency ?: Money.DEFAULT_CURRENCY
    val needsTargetAmount: Boolean
        get() = type == TransactionType.TRANSFER && toWallet != null && toWallet?.wallet?.currency != currency
    val visibleCategories: List<Category>
        get() = categories.filter { it.kind == if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }
    val canSave: Boolean get() = !saving && (Money.parse(amount, currency) ?: 0) > 0 && walletId != null
}

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val walletsRepo: WalletRepository,
    private val categoriesRepo: CategoryRepository,
    private val settings: SettingsRepository,
    private val receipts: ReceiptStore,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedState.toRoute<TransactionEditorRoute>()
    private val _state = MutableStateFlow(EditorUiState(date = LocalDate.now(clock)))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    val effects = EffectChannel<EditorEffect>()
    private var original: Transaction? = null
    private var saved = false

    init {
        viewModelScope.launch {
            val wallets = walletsRepo.observeWallets().first().filter { !it.wallet.archived }
            val existing = route.id?.let { transactions.get(it) }
            original = existing
            val defaultWallet = route.walletId
                ?: settings.lastWalletId()?.takeIf { id -> wallets.any { it.wallet.id == id } }
                ?: wallets.firstOrNull()?.wallet?.id
            _state.update { s ->
                if (existing != null) {
                    s.copy(
                        isNew = false,
                        type = existing.type,
                        amount = Money.toPlainString(existing.amountMinor, existing.currency),
                        toAmount = existing.toAmountMinor?.let { Money.toPlainString(it, existing.currency) }.orEmpty(),
                        walletId = existing.walletId,
                        toWalletId = existing.toWalletId,
                        categoryId = existing.categoryId,
                        date = existing.occurredAt.toLocalDate(clock.zone),
                        note = existing.note,
                        tags = existing.tags,
                        receipt = existing.receiptPath,
                        wallets = wallets,
                    )
                } else {
                    val type = runCatching { TransactionType.valueOf(route.type) }.getOrDefault(TransactionType.EXPENSE)
                    s.copy(
                        type = type,
                        walletId = defaultWallet,
                        toWalletId = if (type == TransactionType.TRANSFER) wallets.firstOrNull { it.wallet.id != defaultWallet }?.wallet?.id else null,
                        wallets = wallets,
                    )
                }
            }
            categoriesRepo.observeByRecentUse().collect { cats -> _state.update { it.copy(categories = cats, loaded = true) } }
        }
    }

    fun setType(type: TransactionType) = _state.update { s ->
        s.copy(
            type = type,
            categoryId = if (s.type == type) s.categoryId else null,
            toWalletId = if (type == TransactionType.TRANSFER) s.toWalletId ?: s.wallets.firstOrNull { it.wallet.id != s.walletId }?.wallet?.id else s.toWalletId,
        )
    }

    fun setAmount(value: String) = _state.update { it.copy(amount = value) }
    fun setToAmount(value: String) = _state.update { it.copy(toAmount = value) }
    fun setWallet(id: String) = _state.update { it.copy(walletId = id) }
    fun setToWallet(id: String) = _state.update { it.copy(toWalletId = id) }
    fun setCategory(id: String) = _state.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setNote(value: String) = _state.update { it.copy(note = value) }
    fun setTags(value: String) = _state.update { it.copy(tags = value) }

    fun attachReceipt(uri: Uri) {
        viewModelScope.launch {
            try {
                val name = receipts.import(uri)
                discardUnsavedReceipt()
                _state.update { it.copy(receipt = name) }
            } catch (e: Exception) {
                effects.send(EditorEffect.UnexpectedError)
            }
        }
    }

    fun removeReceipt() {
        viewModelScope.launch {
            discardUnsavedReceipt()
            _state.update { it.copy(receipt = null) }
        }
    }

    fun receiptFile(name: String): File? = receipts.file(name)

    private suspend fun discardUnsavedReceipt() {
        val current = _state.value.receipt
        if (current != null && current != original?.receiptPath) receipts.delete(current)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            effects.attempt {
                val now = clock.millis()
                // Keep the original time of day when only the date changes; new entries use the current time.
                val time = original?.occurredAt?.let { java.time.Instant.ofEpochMilli(it).atZone(clock.zone).toLocalTime() } ?: LocalTime.now(clock)
                val occurredAt = s.date.atTime(time).atZone(clock.zone).toInstant().toEpochMilli()
                transactions.save(
                    Transaction(
                        id = original?.id ?: newId(),
                        type = s.type,
                        amountMinor = Money.parse(s.amount, s.currency) ?: 0,
                        currency = s.currency,
                        walletId = s.walletId.orEmpty(),
                        toWalletId = s.toWalletId,
                        toAmountMinor = if (s.needsTargetAmount) Money.parse(s.toAmount, s.toWallet!!.wallet.currency) else null,
                        categoryId = s.categoryId,
                        note = s.note,
                        tags = s.tags,
                        occurredAt = occurredAt,
                        receiptPath = s.receipt,
                        recurringId = original?.recurringId,
                        occurrenceKey = original?.occurrenceKey,
                        createdAt = original?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
                saved = true
                s.walletId?.let { settings.setLastWalletId(it) }
            }
            _state.update { it.copy(saving = false) }
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCleared() {
        if (!saved) {
            val current = _state.value.receipt
            if (current != null && current != original?.receiptPath) {
                // viewModelScope is already cancelled here; the cleanup must still run.
                kotlinx.coroutines.GlobalScope.launch(NonCancellable) { receipts.delete(current) }
            }
        }
    }
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val receipts: ReceiptStore,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<TransactionDetailRoute>().id
    val row: StateFlow<TransactionRow?> = transactions.observeRow(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val effects = EffectChannel<EditorEffect>()

    fun receiptFile(name: String): File? = receipts.file(name)

    fun delete() {
        viewModelScope.launch { effects.attempt { transactions.delete(id) } }
    }

    fun removeReceipt() {
        viewModelScope.launch { withContext(NonCancellable) { transactions.removeReceipt(id) } }
    }

    fun duplicateNow() {
        val tx = row.value?.transaction ?: return
        viewModelScope.launch {
            effects.attempt {
                val now = clock.millis()
                transactions.save(
                    tx.copy(
                        id = newId(),
                        occurredAt = now,
                        receiptPath = null,
                        recurringId = null,
                        occurrenceKey = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}
