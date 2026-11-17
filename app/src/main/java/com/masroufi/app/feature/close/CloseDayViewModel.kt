package com.masroufi.app.feature.close

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.repo.BiggestPunch
import com.masroufi.app.data.repo.HomeRepository
import com.masroufi.app.data.repo.TicketRepository
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.leftover
import com.masroufi.app.domain.overspendPerDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface CloseDayUi {
    data object Loading : CloseDayUi
    data object Error : CloseDayUi

    /** All money in piastres. [leftover] and [over] are never both above 0. */
    data class Ready(
        val date: LocalDate,
        val ticket: Long,
        val spent: Long,
        val holes: Int,
        val biggest: BiggestPunch?,
        val leftover: Long,
        val over: Long,
        /** Days after [date] left in its pay cycle; 0 on the cycle's last day. */
        val spreadDays: Int,
        val spreadPerDay: Long,
        val jarAfter: Long,
        val emergency: Long,
        /** Tomorrow's ticket with today's leftover carried, and without (the leftover went to a pot). */
        val tomorrowWith: Long,
        val tomorrowWithout: Long,
        val defaultDestination: LeftoverDestination,
    )
}

enum class TearState { Idle, Saving, Done, Failed }

private const val TAG = "CloseDay"

@HiltViewModel
class CloseDayViewModel @Inject constructor(
    private val homeRepo: HomeRepository,
    private val ticketRepo: TicketRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    /** The nav argument (CloseDay.date, an epoch day). */
    val date: LocalDate = LocalDate.ofEpochDay(checkNotNull(savedState.get<Long>("date")) { "CloseDay needs a date" })

    private val _state = MutableStateFlow<CloseDayUi>(CloseDayUi.Loading)
    val state: StateFlow<CloseDayUi> = _state.asStateFlow()

    private val _tear = MutableStateFlow(TearState.Idle)
    val tear: StateFlow<TearState> = _tear.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try<CloseDayUi> {
                ready()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "close day failed to load", e)
                CloseDayUi.Error
            }
        }
    }

    private suspend fun ready(): CloseDayUi.Ready {
        val d = homeRepo.closeDayData(date)
        val left = leftover(d.row.amount, d.row.spent)
        val over = (d.row.spent - d.row.amount).coerceAtLeast(0)
        val spreadDays = d.cycle.daysLeft(date) - 1
        // A leftover sent to a pot is reserved tomorrow; carried, it is added to the ticket instead.
        val without = d.tomorrow.preview(d.spendable, d.jarTotal + left, d.emergencyTotal, 0)
        val with = d.tomorrow.preview(d.spendable, d.jarTotal, d.emergencyTotal, left)
        return CloseDayUi.Ready(
            date = date,
            ticket = d.row.amount,
            spent = d.row.spent,
            holes = d.row.entryCount,
            biggest = d.biggest,
            leftover = left,
            over = over,
            spreadDays = spreadDays,
            spreadPerDay = if (spreadDays >= 1) overspendPerDay(over, spreadDays) else 0,
            jarAfter = d.jarTotal + left,
            emergency = d.emergencyTotal,
            tomorrowWith = with,
            tomorrowWithout = without,
            defaultDestination = d.leftoverDefault,
        )
    }

    /** Closes the day once: a second call while saving or after success is ignored; after a failure it retries. */
    fun tear(destination: LeftoverDestination) {
        if (_tear.value == TearState.Saving || _tear.value == TearState.Done) return
        _tear.value = TearState.Saving
        viewModelScope.launch {
            _tear.value = try {
                ticketRepo.closeDay(date, destination)
                TearState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "close day failed", e)
                TearState.Failed
            }
        }
    }
}
