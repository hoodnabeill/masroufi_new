package com.masroufi.app.feature.pockets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.repo.FeesSummary
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.feature.add.AddPrefill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The low-balance alert threshold until the user types one: 200 pounds. */
const val DEFAULT_LOW_BALANCE: Long = 20_000

data class PocketDetailUi(
    val loading: Boolean = true,
    /** The pocket is gone (archived): the screen leaves. */
    val missing: Boolean = false,
    val today: LocalDate,
    val id: Long = 0,
    val name: String = "",
    val kind: PocketKind = PocketKind.CASH,
    val color: Int = 0,
    val balance: Long = 0,
    /** The last pasted message's balance equals the balance now; [smsAt] is when it was pasted. */
    val matchesSms: Boolean = false,
    val smsAt: Long? = null,
    val countsInTicket: Boolean = true,
    val lowBalanceAlert: Boolean = false,
    val lowBalanceThreshold: Long = DEFAULT_LOW_BALANCE,
    val limit: Long? = null,
    val moved: Long = 0,
    /** limit - moved when moved + unpaid payments due this month pass the limit; null = no warning. */
    val warningRoom: Long? = null,
    /** The soonest unpaid payment behind the warning, if there is one. */
    val upcoming: RecurringEntity? = null,
    val fees: FeesSummary = FeesSummary(0, 0 to 0L, 0 to 0L),
    val recent: List<EntryRow> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    /** The first CASH pocket, where "سحبت كاش" sends the money; null without one (or on a cash pocket). */
    val cashPocketId: Long? = null,
    val counter: CashCounter = CashCounter(),
    val reconciled: ReconcileResult? = null,
) {
    val isCash: Boolean get() = kind == PocketKind.CASH

    /** The mock's tip shows when cash-outs cost more than plain transfers; otherwise no advice. */
    val feeTip: Boolean get() = fees.cashOut.second > fees.transfer.second
}

@HiltViewModel
class PocketDetailViewModel @Inject constructor(
    private val repo: PocketRepository,
    entryDao: EntryDao,
    categoryDao: CategoryDao,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    /** The nav argument (PocketDetail.id). */
    val id: Long = checkNotNull(savedState.get<Long>("id")) { "PocketDetail needs an id" }

    private val today = LocalDate.now(clock)
    private val touched = MutableStateFlow<CashCounter?>(null)
    private val reconciled = MutableStateFlow<ReconcileResult?>(null)

    private val base = combine(repo.observePockets(), entryDao.rowsForPocket(id, RECENT), categoryDao.all()) { pockets, recent, cats ->
        Triple(pockets, recent, cats)
    }.map { (pockets, recent, cats) ->
        val view = pockets.firstOrNull { it.pocket.id == id }
        if (view == null) {
            PocketDetailUi(loading = false, missing = true, today = today)
        } else {
            val p = view.pocket
            val warning = repo.limitWarning(id)
            PocketDetailUi(
                loading = false,
                today = today,
                id = id,
                name = p.name,
                kind = p.kind,
                color = p.color,
                balance = view.balance,
                matchesSms = p.lastSmsBalance != null && p.lastSmsBalance == view.balance,
                smsAt = p.lastSmsAt,
                countsInTicket = p.countsInTicket,
                lowBalanceAlert = p.lowBalanceAlert,
                lowBalanceThreshold = p.lowBalanceThreshold ?: DEFAULT_LOW_BALANCE,
                limit = p.monthlyLimit,
                moved = repo.movedThisMonth(id),
                warningRoom = warning,
                upcoming = if (warning != null) repo.upcomingPayment(id) else null,
                fees = repo.feesThisMonth(id),
                recent = recent,
                categories = cats.associateBy { it.id },
                cashPocketId = pockets.firstOrNull { it.pocket.kind == PocketKind.CASH && it.pocket.id != id }?.pocket?.id,
            )
        }
    }

    val state: StateFlow<PocketDetailUi> = combine(base, touched, reconciled) { ui, t, r ->
        ui.copy(counter = t ?: CashCounter.of(ui.balance), reconciled = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PocketDetailUi(today = today))

    // --- the four actions ---

    fun receivePrefill() = AddPrefill(amount = null, categoryId = null, note = null, type = EntryKind.RECEIVED, pocketId = id)

    fun transferPrefill() = AddPrefill(amount = null, categoryId = null, note = null, type = EntryKind.MOVED, pocketId = id)

    /** سحبت كاش: out of this pocket into the first cash pocket; the sheet's fee field is there. */
    fun cashOutPrefill() = AddPrefill(
        amount = null, categoryId = null, note = null, type = EntryKind.MOVED, pocketId = id, toPocketId = state.value.cashPocketId,
    )

    /** سوّي الرصيد: an ADJUST for counted - balance (none when equal). */
    fun reconcile(counted: Long) {
        viewModelScope.launch {
            val written = repo.reconcile(id, counted)
            touched.value = null
            reconciled.value = if (written == null) ReconcileResult.Already else ReconcileResult.Done
        }
    }

    // --- the note counter (cash pockets) ---

    fun step(denomination: Int, delta: Int) {
        touched.value = (touched.value ?: state.value.counter).step(denomination, delta)
        reconciled.value = null
    }

    fun setChange(text: String) {
        touched.value = (touched.value ?: state.value.counter).withChange(text)
        reconciled.value = null
    }

    fun reconcileCounted() = reconcile((touched.value ?: state.value.counter).total)

    // --- limit, switches, archive ---

    private fun edit(change: (PocketEntity) -> PocketEntity) {
        viewModelScope.launch {
            val current = repo.observePockets().first().firstOrNull { it.pocket.id == id } ?: return@launch
            repo.update(change(current.pocket))
        }
    }

    /** The limit the user's provider gave them; 0 or null removes it. */
    fun setLimit(piastres: Long?) = edit { it.copy(monthlyLimit = piastres?.takeIf { v -> v > 0 }) }

    fun setCountsInTicket(on: Boolean) = edit { it.copy(countsInTicket = on) }

    fun setLowBalanceAlert(on: Boolean) = edit { it.copy(lowBalanceAlert = on) }

    fun setLowBalanceThreshold(piastres: Long) = edit { it.copy(lowBalanceThreshold = piastres.takeIf { v -> v > 0 }) }

    /** Closes the pocket (the screen asked first). */
    fun archive() {
        viewModelScope.launch { repo.archive(id) }
    }

    private companion object {
        const val RECENT = 3
    }
}
