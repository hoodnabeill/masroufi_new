package com.masroufi.app.feature.pockets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.PRIMARY_CURRENCY
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.data.repo.PocketView
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.ParsedSms
import com.masroufi.app.domain.SmsDirection
import com.masroufi.app.domain.normalize
import com.masroufi.app.domain.parseSms
import com.masroufi.app.feature.add.AddPrefill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** A pocket with the day of its last entry in the user's zone (null = none). */
data class PocketRowUi(val view: PocketView, val lastDate: LocalDate?)

/** What a pasted message was understood as; [pocket] is the one the entry would go to. */
data class SmsPreview(
    val parsed: ParsedSms,
    val categoryName: String?,
    val categoryId: Long?,
    val pocket: PocketEntity?,
    val matches: Boolean,
)

sealed interface SmsState {
    data object Idle : SmsState

    /** Nothing readable was pasted. */
    data object Unreadable : SmsState

    data class Parsed(val preview: SmsPreview) : SmsState
}

data class PocketsUi(
    val loading: Boolean = true,
    val today: LocalDate,
    val rows: List<PocketRowUi> = emptyList(),
    /** Every EGP pocket's balance, under-the-tile included (piastres). */
    val total: Long = 0,
    val counter: CashCounter = CashCounter(),
    val reconciled: ReconcileResult? = null,
    val sms: SmsState = SmsState.Idle,
) {
    val cash: PocketRowUi? get() = rows.firstOrNull { it.view.pocket.kind == PocketKind.CASH }
    val others: List<PocketRowUi> get() = rows.filter { it.view.pocket.id != cash?.view?.pocket?.id }
}

private data class Extras(val touched: CashCounter?, val reconciled: ReconcileResult?, val sms: SmsState)

@HiltViewModel
class PocketsViewModel @Inject constructor(
    private val repo: PocketRepository,
    private val categoryDao: CategoryDao,
    private val clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val touched = MutableStateFlow<CashCounter?>(null)
    private val reconciled = MutableStateFlow<ReconcileResult?>(null)
    private val sms = MutableStateFlow<SmsState>(SmsState.Idle)

    val state: StateFlow<PocketsUi> = combine(
        repo.observePockets(), combine(touched, reconciled, sms, ::Extras),
    ) { views, x ->
        val rows = views.map { v ->
            PocketRowUi(v, v.lastActivityAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() })
        }
        val cash = rows.firstOrNull { it.view.pocket.kind == PocketKind.CASH }
        PocketsUi(
            loading = false,
            today = today,
            rows = rows,
            total = views.filter { it.pocket.currency == PRIMARY_CURRENCY }.sumOf { it.balance },
            counter = x.touched ?: CashCounter.of(cash?.view?.balance ?: 0),
            reconciled = x.reconciled,
            sms = x.sms,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PocketsUi(today = today))

    private fun counter(): CashCounter = state.value.counter

    fun step(denomination: Int, delta: Int) {
        touched.value = (touched.value ?: counter()).step(denomination, delta)
        reconciled.value = null
    }

    fun setChange(text: String) {
        touched.value = (touched.value ?: counter()).withChange(text)
        reconciled.value = null
    }

    /** Reconciles the cash pocket to the counted total; the counter then follows the balance again. */
    fun reconcile() {
        val cash = state.value.cash ?: return
        val counted = (touched.value ?: counter()).total
        viewModelScope.launch {
            val written = repo.reconcile(cash.view.pocket.id, counted)
            touched.value = null
            reconciled.value = if (written == null) ReconcileResult.Already else ReconcileResult.Done
        }
    }

    // --- quick actions: the add sheet as a transfer ---

    private fun firstOf(kind: PocketKind): Long? = state.value.rows.firstOrNull { it.view.pocket.kind == kind }?.view?.pocket?.id

    /** حوّل بين الجيوب: pick both pockets in the sheet. */
    fun transferPrefill() = AddPrefill(amount = null, categoryId = null, note = null, type = EntryKind.MOVED)

    /** سحبت من الماكينة: bank to cash. */
    fun atmPrefill() = AddPrefill(
        amount = null, categoryId = null, note = null, type = EntryKind.MOVED,
        pocketId = firstOf(PocketKind.BANK), toPocketId = firstOf(PocketKind.CASH),
    )

    /** شحنت المحفظة: bank to mobile wallet. */
    fun topUpPrefill() = AddPrefill(
        amount = null, categoryId = null, note = null, type = EntryKind.MOVED,
        pocketId = firstOf(PocketKind.BANK), toPocketId = firstOf(PocketKind.MOBILE_WALLET),
    )

    // --- SMS paste ---

    /**
     * [text] is the clipboard's text (null or blank = nothing there). Nothing is saved or recorded here: the entry saves
     * from the add sheet, which then records the parsed balance.
     */
    fun pasteSms(text: String?) {
        viewModelScope.launch {
            val parsed = text?.takeIf { it.isNotBlank() }?.let { parseSms(it) }
            if (parsed == null) {
                sms.value = SmsState.Unreadable
                return@launch
            }
            val pockets = repo.observePockets().first()
            val matched = parsed.balance?.let { b -> pockets.firstOrNull { it.balance == b } }
            val pocket = matched
                ?: pockets.firstOrNull { it.pocket.kind == PocketKind.MOBILE_WALLET }
                ?: pockets.firstOrNull { it.pocket.kind == PocketKind.BANK }
                ?: pockets.firstOrNull()
            val category = if (parsed.direction == SmsDirection.SPENT && parsed.party != null) {
                val p = normalize(parsed.party)
                categoryDao.all().first().firstOrNull { c -> p.contains(normalize(c.name)) }
            } else {
                null
            }
            sms.value = SmsState.Parsed(SmsPreview(parsed, category?.name, category?.id, pocket?.pocket, matched != null))
        }
    }

    fun clearSms() {
        sms.value = SmsState.Idle
    }

    /** The add sheet for the understood message, or null when there is none. */
    fun smsPrefill(): AddPrefill? {
        val p = (state.value.sms as? SmsState.Parsed)?.preview ?: return null
        return AddPrefill(
            amount = p.parsed.amount,
            categoryId = p.categoryId,
            note = p.parsed.party,
            type = when (p.parsed.direction) {
                SmsDirection.SPENT -> EntryKind.SPENT
                SmsDirection.RECEIVED -> EntryKind.RECEIVED
                SmsDirection.MOVED_OUT -> EntryKind.MOVED
            },
            pocketId = p.pocket?.id,
            smsBalance = p.parsed.balance,
        )
    }
}
