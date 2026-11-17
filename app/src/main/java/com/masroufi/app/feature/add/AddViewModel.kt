package com.masroufi.app.feature.add

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.EntryRepository
import com.masroufi.app.data.repo.NOTE_MAX_CHARS
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.punchesTicket
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.roundUp
import com.masroufi.app.entitlements.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What the user has chosen in the sheet, plus the pockets and categories it offers. */
data class AddForm(
    val type: EntryKind = EntryKind.SPENT,
    val amount: AmountInput = AmountInput(),
    val categoryId: Long? = null,
    val pocketId: Long? = null,
    val toPocketId: Long? = null,
    val note: String = "",
    /** Transfers only. */
    val fee: AmountInput = AmountInput(),
    /** The keypad types into [fee] (transfers only), else into [amount]. */
    val feeFocused: Boolean = false,
    val roundUpOn: Boolean = true,
    /** Last used first, then seed order. */
    val categories: List<CategoryEntity> = emptyList(),
    /** Not archived. */
    val pockets: List<PocketEntity> = emptyList(),
)

/** The form with what it is measured against: today's stored row, the punches not stored yet, Plus and the step. */
data class AddUiState(
    val form: AddForm = AddForm(),
    val today: DayTicketEntity? = null,
    val pendingSpent: Long = 0,
    val pendingHoles: Int = 0,
    val plus: Boolean = false,
    val roundUpStep: Int = 0,
) {
    val amount: Long get() = form.amount.toPiastres()
    val pocket: PocketEntity? get() = form.pockets.firstOrNull { it.id == form.pocketId }
    val toPocket: PocketEntity? get() = form.pockets.firstOrNull { it.id == form.toPocketId }

    /** The round-up to the next step, or 0 when the line is hidden (not Plus, no step, not a spend, already round). */
    val roundUpOffer: Long get() {
        val p = pocket
        if (!plus || roundUpStep <= 0 || form.type != EntryKind.SPENT || p == null || !p.punchesTicket) return 0
        return roundUp(amount, roundUpStep)
    }

    /** What this entry takes from today's ticket (as Home's ticket counts it). */
    val punched: Long get() {
        val p = pocket ?: return 0
        if (!p.punchesTicket) return 0
        return when (form.type) {
            EntryKind.SPENT -> amount + if (form.roundUpOn) roundUpOffer else 0
            EntryKind.MOVED -> form.fee.toPiastres()
            EntryKind.RECEIVED -> 0
        }
    }

    /** Today's ticket left after this entry, may be negative; null without an open ticket. */
    val leftAfter: Long? get() = today?.takeIf { it.status == TicketStatus.OPEN }?.let { it.amount - it.spent - pendingSpent - punched }

    val transferValid: Boolean get() {
        val from = pocket
        val to = toPocket
        return from != null && to != null && from.id != to.id && from.currency == to.currency
    }

    val canSave: Boolean get() = amount > 0 && pocket != null && (form.type != EntryKind.MOVED || transferValid)
}

private const val TAG = "AddSheet"
private const val DOUBLE_TAP_MS = 400L
private const val PENDING_CATCH_UP_MS = 2_000L

@HiltViewModel
class AddViewModel @Inject constructor(
    private val entryRepo: EntryRepository,
    private val entryDao: EntryDao,
    private val categoryDao: CategoryDao,
    private val pocketDao: PocketDao,
    ticketDao: DayTicketDao,
    settings: SettingsStore,
    entitlements: Entitlements,
    private val pendingPunches: PendingPunches,
    private val feedback: FeedbackBus,
    private val clock: Clock,
) : ViewModel(), AddSheetActions {
    private val form = MutableStateFlow(AddForm())
    private var lastSaveAt = Long.MIN_VALUE
    /** The last save failed: the next open keeps what was typed. */
    private var keepDraft = false

    // Today is re-read on each pending change, so a sheet left open over midnight follows the new day's row.
    private val today = pendingPunches.pending.map { LocalDate.now(clock) }.distinctUntilChanged().flatMapLatest { ticketDao.forDateFlow(it) }

    val state: StateFlow<AddUiState> = combine(
        form, today, pendingPunches.pending, entitlements.isPlus, settings.settings.map { it.roundUpStep },
    ) { f, row, pending, plus, step ->
        // Placeholders the stored row already holds are no longer extra (Home prunes them).
        val live = pending.filter { it.expectedHoles > (row?.entryCount ?: 0) }
        AddUiState(f, row, live.sumOf { it.punched }, live.size, plus, step)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddUiState())

    /**
     * Starts the sheet: a fresh form (with [prefill] if given), or the form of a save that failed when there is no
     * prefill. The reset is immediate so no old value flashes; pockets, categories and the default pocket load after.
     */
    fun open(prefill: AddPrefill?) {
        val keep = keepDraft && prefill == null
        keepDraft = false
        if (!keep) {
            val old = form.value
            form.value = AddForm(
                amount = prefill?.amount?.let(AmountInput::of) ?: AmountInput(),
                categoryId = prefill?.categoryId,
                note = prefill?.note.orEmpty().take(NOTE_MAX_CHARS),
                categories = old.categories,
                pockets = old.pockets,
            )
        }
        viewModelScope.launch {
            val pockets = pocketDao.all().first().filterNot { it.archived }.sortedBy { it.id }
            val all = categoryDao.all().first().sortedBy { it.id }
            val used = entryDao.categoriesByLastUse()
            val categories = used.mapNotNull { id -> all.firstOrNull { it.id == id } } + all.filterNot { it.id in used }
            val latest = entryDao.latestPocketId()
            val default = pockets.firstOrNull { it.id == latest } ?: pockets.firstOrNull { it.punchesTicket } ?: pockets.firstOrNull()
            form.update { f ->
                fun Long?.kept() = this?.takeIf { id -> pockets.any { it.id == id } }
                f.copy(
                    categories = categories,
                    pockets = pockets,
                    categoryId = f.categoryId?.takeIf { id -> categories.any { it.id == id } },
                    pocketId = f.pocketId.kept() ?: default?.id,
                    toPocketId = f.toPocketId.kept(),
                )
            }
        }
    }

    override fun setType(type: EntryKind) = form.update { f ->
        // A transfer starts with the first other pocket as its target.
        val to = f.toPocketId ?: f.pockets.firstOrNull { it.id != f.pocketId }?.id
        f.copy(type = type, toPocketId = if (type == EntryKind.MOVED) to else f.toPocketId)
    }

    override fun digit(d: Int) = edit { it.digit(d) }
    override fun point() = edit { it.point() }
    override fun backspace() = edit { it.backspace() }

    override fun focusFee(on: Boolean) = form.update { it.copy(feeFocused = on) }

    private fun edit(op: (AmountInput) -> AmountInput) = form.update { f ->
        if (f.feeFocused && f.type == EntryKind.MOVED) f.copy(fee = op(f.fee)) else f.copy(amount = op(f.amount))
    }

    override fun selectCategory(id: Long) = form.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    override fun selectPocket(id: Long) = form.update { it.copy(pocketId = id) }
    override fun selectToPocket(id: Long) = form.update { it.copy(toPocketId = id) }
    override fun setNote(text: String) = form.update { it.copy(note = text.take(NOTE_MAX_CHARS)) }
    override fun setRoundUp(on: Boolean) = form.update { it.copy(roundUpOn = on) }

    /**
     * Shows the punch at once and stores it in the background. False when the form cannot be saved or this is a second
     * tap within 400 ms. The form stays as typed until the next [open]; on failure the punch is rolled back, Home says
     * so, and that open keeps the typed values.
     */
    fun save(): Boolean {
        val s = state.value
        if (!s.canSave) return false
        val now = clock.millis()
        if (lastSaveAt != Long.MIN_VALUE && now - lastSaveAt < DOUBLE_TAP_MS) return false
        lastSaveAt = now
        val f = s.form
        val entry = NewEntry(
            kind = f.type,
            amount = s.amount,
            pocketId = checkNotNull(f.pocketId),
            toPocketId = if (f.type == EntryKind.MOVED) f.toPocketId else null,
            categoryId = if (f.type == EntryKind.SPENT) f.categoryId else null,
            note = f.note.trim().ifEmpty { null },
            happenedAt = now,
            fee = if (f.type == EntryKind.MOVED) f.fee.toPiastres() else 0,
            roundUp = f.roundUpOn,
        )
        val punched = s.punched
        val holesBefore = (s.today?.entryCount ?: 0) + s.pendingHoles
        val pendingId = if (punched > 0) pendingPunches.add(Pending(0, punched, expectedHoles = holesBefore + 1)) else null
        val left = s.leftAfter
        viewModelScope.launch {
            try {
                entryRepo.log(entry)
            } catch (e: CancellationException) {
                pendingId?.let(pendingPunches::remove)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "log failed", e)
                pendingId?.let(pendingPunches::remove)
                form.value = f
                keepDraft = true
                feedback.emit(PunchFeedback.Failed)
                return@launch
            }
            if (pendingId == null) return@launch
            if (left != null) feedback.emit(PunchFeedback.Saved(left, firstOfDay = holesBefore == 0))
            // Home drops the placeholder once its row has the hole; this only covers Home not looking (or a new day).
            try {
                delay(PENDING_CATCH_UP_MS)
            } finally {
                pendingPunches.remove(pendingId)
            }
        }
        return true
    }
}
