package com.masroufi.app.feature.endofmonth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.repo.BillDay
import com.masroufi.app.data.repo.EndOfMonthRepository
import com.masroufi.app.data.repo.EndOfMonthState
import com.masroufi.app.data.repo.NO_SPEND_GOAL
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [state] is null while loading, and after a failed read (the screen then shows nothing). */
data class EndOfMonthUi(val loading: Boolean = true, val state: EndOfMonthState? = null) {
    /** Spendable - Reserved <= 0: the hero says what is short and the rescue rows come first. */
    val shortfall: Boolean get() = state != null && state.pool <= 0

    /** What the hero's big number shows: the pool, or how far short it is. */
    val heroAmount: Long get() = state?.pool?.let { if (it > 0) it else -it } ?: 0L

    /** Reserved bills due before payday, earliest first. The screen names the first one and counts the rest. */
    val bills: List<BillDay> get() = state?.strip.orEmpty().flatMap { it.bills }

    /** The challenge's count for the "x / 2" pill (never above the goal). */
    val challengeDone: Int get() = (state?.noSpendDays ?: 0).coerceAtMost(NO_SPEND_GOAL)

    val challengeEarned: Boolean get() = challengeDone >= NO_SPEND_GOAL

    /** The button switches the mode off when it was switched on by hand, else on. */
    val modeOn: Boolean get() = state?.on == true

    /** No rescue source has money: the "لو اتزنقت" card is left out. */
    val hasRescue: Boolean get() = state?.rescue?.let { it.freeJar != null || it.owedToMe != null || it.emergency != null } == true
}

private const val TAG = "EndOfMonth"

@HiltViewModel
class EndOfMonthViewModel @Inject constructor(private val eom: EndOfMonthRepository) : ViewModel() {
    val state: StateFlow<EndOfMonthUi> = eom.observe()
        .map { EndOfMonthUi(false, it) }
        .catch { Log.e(TAG, "end-of-month read failed", it); emit(EndOfMonthUi(false, null)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EndOfMonthUi())

    /** The two habit toggles ("وقّفها لحد المرتب"): a reminder after saving, never a block. */
    fun setPaused(categoryId: Long, paused: Boolean) = act { eom.setPaused(categoryId, paused) }

    /** The bottom button: on until payday, or off again when it was on. */
    fun toggleMode() = act { if (eom.state().on) eom.switchOff() else eom.switchOn() }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "end-of-month action failed", e)
            }
        }
    }
}
