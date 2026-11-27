package com.masroufi.app.feature.kids

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.ChildMode
import com.masroufi.app.core.PIN_MAX_DIGITS
import com.masroufi.app.core.PIN_MIN_DIGITS
import com.masroufi.app.core.PinStore
import com.masroufi.app.data.repo.ChildView
import com.masroufi.app.data.repo.ChoreView
import com.masroufi.app.data.repo.KidsRepository
import com.masroufi.app.data.repo.KidsView
import com.masroufi.app.data.repo.PlusRequired
import com.masroufi.app.data.repo.TickResult
import com.masroufi.app.ui.haptics.HapticLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** One child's ticket on the kids screen: the repository's numbers plus what the ticket draws from them. */
data class ChildTicketUi(
    val view: ChildView,
    /** The weekday of the spend that used the money up; only read when nothing is left. */
    val finishedOn: DayOfWeek,
    /** Circles in the punches row (the filled ones are [ChildView.punches]). */
    val slots: Int,
)

sealed interface KidsSheet {
    data object HandoverDay : KidsSheet
    data object AddChore : KidsSheet
    data object Plus : KidsSheet

    /** A chore for any child, with more than one child able to do it: who did it? */
    data class ChooseChild(val choreId: Long) : KidsSheet
}

enum class KidsNotice { CHORE_TAKEN }

enum class PinStage { ENTER, REPEAT }

/** The first time a child screen is opened: the PIN is typed, then typed again. [mismatch] after the two did not match. */
data class PinSetup(val childId: Long, val stage: PinStage = PinStage.ENTER, val digits: String = "", val first: String = "", val mismatch: Boolean = false)

data class KidsUi(
    val loading: Boolean = true,
    val view: KidsView? = null,
    val tickets: List<ChildTicketUi> = emptyList(),
    val sheet: KidsSheet? = null,
    val pinSetup: PinSetup? = null,
    val notice: KidsNotice? = null,
) {
    /** The button's sum; 0 = nothing to hand over. */
    val total: Long get() = view?.total ?: 0L
}

/** What the kids screen and its sheets call on the view model (a stateless screen takes this, so a test can pass a no-op). */
interface KidsActions {
    fun openHandoverDay() {}
    fun openAddChore() {}
    fun openPlus() {}
    fun dismissSheet() {}
    fun setHandoverDay(day: DayOfWeek) {}
    fun addChore(label: String, bonus: Long, childId: Long?) {}
    fun tapChore(row: ChoreView) {}
    fun tick(choreId: Long, childId: Long) {}
    fun answerAdvance(advanceId: Long, approve: Boolean) {}
    fun handOver() {}
    fun openChildScreen(childId: Long) {}
    fun cancelPinSetup() {}
    fun setupDigit(d: Int) {}
    fun setupBackspace() {}
    fun setupNext() {}
}

private const val TAG = "Kids"
private const val MIN_SLOTS = 5
private const val MAX_SLOTS = 10

/** مصروف العيال: the tickets, chores, handover and the way into a child's screen (which needs a PIN first). */
@HiltViewModel
class KidsViewModel @Inject constructor(
    private val kids: KidsRepository,
    private val childMode: ChildMode,
    private val pin: PinStore,
    private val clock: Clock,
) : ViewModel(), KidsActions {
    private val sheet = MutableStateFlow<KidsSheet?>(null)
    private val pinSetup = MutableStateFlow<PinSetup?>(null)
    private val notice = MutableStateFlow<KidsNotice?>(null)
    private val haptic = Channel<HapticLevel>(Channel.BUFFERED)

    /** Outcomes that feel like something (handover Heavy; advance and chore Confirm); taps are the screen's own. */
    val haptics: Flow<HapticLevel> = haptic.receiveAsFlow()

    val state: StateFlow<KidsUi> = combine(kids.observe(), sheet, pinSetup, notice) { v, s, p, n ->
        KidsUi(
            loading = false, view = v, sheet = s, pinSetup = p, notice = n,
            tickets = v.children.map { c ->
                val day = c.lastSpendAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone).dayOfWeek } ?: LocalDate.now(clock).dayOfWeek
                ChildTicketUi(c, day, c.punches.coerceIn(MIN_SLOTS, MAX_SLOTS))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KidsUi())

    override fun openHandoverDay() { sheet.value = KidsSheet.HandoverDay }
    override fun openAddChore() { sheet.value = KidsSheet.AddChore }
    override fun openPlus() { sheet.value = KidsSheet.Plus }
    override fun dismissSheet() { sheet.value = null }
    fun clearNotice() { notice.value = null }

    override fun setHandoverDay(day: DayOfWeek) = launchSafe("handover day") {
        kids.setHandoverDay(day)
        sheet.value = null
    }

    override fun addChore(label: String, bonus: Long, childId: Long?) = launchSafe("add chore") {
        if (label.isBlank() || bonus <= 0) return@launchSafe
        kids.addChore(label, bonus, childId)
        sheet.value = null
    }

    /**
     * A tap on a chore row. A chore for one child is theirs; one already ticked is unticked for whoever ticked it; one for
     * any child goes to the only child who can do it, or asks which of them did it.
     */
    override fun tapChore(row: ChoreView) {
        val view = state.value.view ?: return
        val doers = view.children.filter { it.editable && (row.chore.childId == null || row.chore.childId == it.child.id) }
        val who = row.tickedBy ?: row.chore.childId ?: doers.singleOrNull()?.child?.id
        if (who == null) {
            if (doers.isNotEmpty()) sheet.value = KidsSheet.ChooseChild(row.chore.id)
        } else {
            tick(row.chore.id, who)
        }
    }

    /** Ticks or unticks a chore for a child; a chore another child already did gives [KidsNotice.CHORE_TAKEN] and changes nothing. */
    override fun tick(choreId: Long, childId: Long) = launchSafe("tick chore") {
        when (kids.toggleChore(choreId, childId)) {
            TickResult.TICKED -> haptic.trySend(HapticLevel.Confirm)
            TickResult.TAKEN -> notice.value = KidsNotice.CHORE_TAKEN
            TickResult.UNTICKED -> Unit
        }
        sheet.value = null
    }

    override fun answerAdvance(advanceId: Long, approve: Boolean) = launchSafe("answer advance") {
        kids.answerAdvance(advanceId, approve)
        haptic.trySend(HapticLevel.Confirm)
    }

    /** "سلّم مصروف السبت": one entry for all the children; the repository resets the week. */
    override fun handOver() = launchSafe("hand over") {
        if (state.value.total <= 0) return@launchSafe
        kids.handOver()
        haptic.trySend(HapticLevel.Heavy)
    }

    // -- the child screen

    /** Opens the child's screen at once when a PIN exists; the first time the parent sets one before handing the phone over. */
    override fun openChildScreen(childId: Long) = launchSafe("open child screen") {
        if (pin.isSet()) {
            childMode.enter(childId)
        } else {
            pinSetup.value = PinSetup(childId)
        }
    }

    override fun cancelPinSetup() { pinSetup.value = null }

    override fun setupDigit(d: Int) {
        haptic.trySend(HapticLevel.Tick)
        pinSetup.update { if (it != null && it.digits.length < PIN_MAX_DIGITS) it.copy(digits = it.digits + d, mismatch = false) else it }
    }

    override fun setupBackspace() = pinSetup.update { it?.copy(digits = it.digits.dropLast(1)) }

    /** The check mark: the first typing moves on to the repeat; the repeat must match, then the PIN is kept and the child screen opens. */
    override fun setupNext() {
        val s = pinSetup.value ?: return
        if (s.digits.length < PIN_MIN_DIGITS) return
        when (s.stage) {
            PinStage.ENTER -> pinSetup.value = s.copy(stage = PinStage.REPEAT, first = s.digits, digits = "")
            PinStage.REPEAT ->
                if (s.digits != s.first) {
                    pinSetup.value = PinSetup(s.childId, mismatch = true)
                } else {
                    launchSafe("set pin") {
                        pin.set(s.digits)
                        childMode.enter(s.childId)
                        pinSetup.value = null
                    }
                }
        }
    }

    private fun launchSafe(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlusRequired) {
                sheet.value = KidsSheet.Plus
            } catch (e: Exception) {
                Log.e(TAG, "$what failed", e)
            }
        }
    }
}
