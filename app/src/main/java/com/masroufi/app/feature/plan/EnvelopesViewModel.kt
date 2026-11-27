package com.masroufi.app.feature.plan

import com.masroufi.app.feature.roll.withoutDeleted
import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.roll.PendingDelete
import com.masroufi.app.feature.roll.DeleteKind
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.PIASTRES
import com.masroufi.app.data.db.RecurringRow
import com.masroufi.app.data.repo.CATEGORY_COLORS
import com.masroufi.app.data.repo.EnvelopeLine
import com.masroufi.app.data.repo.EnvelopeRepository
import com.masroufi.app.data.repo.EnvelopeView
import com.masroufi.app.data.repo.FREE_ENVELOPES
import com.masroufi.app.data.repo.PlusRequired
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.entitlements.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The honest line an envelope card shows (11-DATA "Envelopes"); the card text comes from this. */
sealed interface CardLine {
    data class RunsOut(val day: Int) : CardLine
    data object OnPace : CardLine
    data object BetterThanLast : CardLine

    /** A paid fixed-bill envelope: the bill's amount and its day of the month. */
    data class Sealed(val amount: Long, val day: Int) : CardLine
}

data class EnvelopeCardUi(val view: EnvelopeView, val line: CardLine?)

/** The last applied split; [cycleStart] names the month ("مرتب نوفمبر"). */
data class SplitSummary(val income: Long, val unassigned: Long, val cycleStart: LocalDate)

/** One envelope running out, another with room: the suggested move. */
data class MoveTip(val fromId: Long, val toId: Long, val fromName: String, val toName: String, val amount: Long)

sealed interface EnvelopeSheet {
    data object Plus : EnvelopeSheet

    /** The move sheet; [fromId] / [toId] are preselected (null = the user picks). */
    data class Move(val fromId: Long?, val toId: Long?, val initial: Long) : EnvelopeSheet
}

data class EnvelopesUi(
    val loading: Boolean = true,
    val cards: List<EnvelopeCardUi> = emptyList(),
    val split: SplitSummary? = null,
    val tip: MoveTip? = null,
    val sheet: EnvelopeSheet? = null,
)

/** Sealed wins; a runs-out date beats "better than last month", which beats "on pace". */
internal fun cardLine(v: EnvelopeView, bills: List<RecurringRow>): CardLine? {
    val line = v.line
    return when {
        v.sealed -> bills.minByOrNull { it.nextDue }?.let { CardLine.Sealed(bills.sumOf { b -> b.amount }, it.nextDue.dayOfMonth) }
        line is EnvelopeLine.RunsOut -> CardLine.RunsOut(line.date.dayOfMonth)
        v.betterThanLast -> CardLine.BetterThanLast
        line is EnvelopeLine.OnPace -> CardLine.OnPace
        else -> null
    }
}

/** Half of the roomiest envelope's left, in whole pounds, towards the first one running out; null when there is no pair. */
internal fun moveTip(cards: List<EnvelopeCardUi>): MoveTip? {
    val needy = cards.firstOrNull { it.line is CardLine.RunsOut } ?: return null
    val donor = cards.filter { it !== needy && it.line !is CardLine.RunsOut && it.line !is CardLine.Sealed && it.view.left > 0 }
        .maxByOrNull { it.view.left } ?: return null
    val amount = donor.view.left / 2 / PIASTRES * PIASTRES
    if (amount <= 0) return null
    return MoveTip(donor.view.id, needy.view.id, donor.view.name, needy.view.name, amount)
}

private data class Inputs(
    val views: List<EnvelopeView>,
    val bills: List<RecurringRow>,
    val split: Pair<Long, Long>?,
    val sheet: EnvelopeSheet?,
)

private const val TAG = "Envelopes"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EnvelopesViewModel @Inject constructor(
    private val envelopes: EnvelopeRepository,
    recurring: RecurringRepository,
    private val entitlements: Entitlements,
    deletes: PendingDeletes,
) : ViewModel() {
    private val sheet = MutableStateFlow<EnvelopeSheet?>(null)

    /** The envelope to edit after "قسّملي" made the starters (its monthly amount is still 0). */
    private val _openEdit = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openEdit: SharedFlow<Long> = _openEdit

    val state: StateFlow<EnvelopesUi> = combine(envelopes.observe().withoutDeleted(deletes, DeleteKind.ENVELOPE) { it.id }, recurring.observe(), envelopes.observeSplit(), sheet) { v, b, s, sh ->
        Inputs(v, b, s, sh)
    }
        .mapLatest { (views, bills, split, sheet) ->
            val cards = views.map { v -> EnvelopeCardUi(v, cardLine(v, bills.filter { it.envelopeId == v.id })) }
            EnvelopesUi(
                loading = false,
                cards = cards,
                split = split?.let { (income, unassigned) -> SplitSummary(income, unassigned, envelopes.cycle().start) },
                tip = moveTip(cards),
                sheet = sheet,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnvelopesUi())

    /** True when the new-envelope screen may open; on Free with 4 envelopes it shows the Plus card instead. */
    fun requestNew(): Boolean {
        if (!entitlements.isPlus.value && state.value.cards.size >= FREE_ENVELOPES) {
            sheet.value = EnvelopeSheet.Plus
            return false
        }
        return true
    }

    /** A drop of [fromId] on [toId]: the sheet opens with what the first has left. */
    fun requestMove(fromId: Long, toId: Long) {
        val from = state.value.cards.firstOrNull { it.view.id == fromId }?.view ?: return
        if (fromId == toId || from.left <= 0) return
        sheet.value = EnvelopeSheet.Move(fromId, toId, from.left)
    }

    /** The tip card (and the accessible way): the same sheet, with the suggested pair and amount. */
    fun openMove() {
        val tip = state.value.tip
        sheet.value = if (tip != null) EnvelopeSheet.Move(tip.fromId, tip.toId, tip.amount) else EnvelopeSheet.Move(null, null, 0)
    }

    /** The screen-reader way to move from one envelope: the sheet with only the source chosen. */
    fun openMoveFrom(fromId: Long) {
        val left = state.value.cards.firstOrNull { it.view.id == fromId }?.view?.left ?: return
        sheet.value = EnvelopeSheet.Move(fromId, null, left)
    }

    fun dismissSheet() {
        sheet.value = null
    }

    /** Moves [amount] (capped at what [fromId] has left) for this cycle. */
    fun confirmMove(fromId: Long, toId: Long, amount: Long) {
        val left = state.value.cards.firstOrNull { it.view.id == fromId }?.view?.left ?: return
        val capped = minOf(amount, left)
        sheet.value = null
        if (capped <= 0 || fromId == toId) return
        viewModelScope.launch {
            try {
                envelopes.move(fromId, toId, capped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "move failed", e)
            }
        }
    }

    /** "قسّملي": the four starter envelopes (monthly 0), then the first one's edit screen. */
    fun suggest() {
        viewModelScope.launch {
            try {
                var first: Long? = null
                envelopes.suggestDefaults(null).forEachIndexed { i, s ->
                    val id = envelopes.create(s.name, CATEGORY_COLORS[i % CATEGORY_COLORS.size], s.monthly, s.kind, s.categoryIds)
                    if (first == null) first = id
                }
                first?.let { _openEdit.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlusRequired) {
                sheet.value = EnvelopeSheet.Plus
            } catch (e: Exception) {
                Log.e(TAG, "suggest failed", e)
            }
        }
    }
}
