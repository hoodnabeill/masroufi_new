package com.masroufi.app.feature.proverb

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.ProverbDao
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.Proverb
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.streak
import com.masroufi.app.domain.weekStart
import com.masroufi.app.entitlements.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

sealed interface ProverbRewardUi {
    data object Loading : ProverbRewardUi
    data object Error : ProverbRewardUi

    /** Nothing to reward (the day closed by itself, or no card was drawn and none is owned): the screen goes straight back to Home. */
    data object NoCard : ProverbRewardUi

    /** [allCollected]: the day drew no card because the album is full, so [proverb] is a favourite from it. */
    data class Ready(
        val ticketNumber: Int,
        val proverb: Proverb,
        val allCollected: Boolean,
        val leftover: Long,
        val destination: LeftoverDestination?,
        val streak: Int,
        val weekDays: Int,
    ) : ProverbRewardUi {
        val rare: Boolean get() = !allCollected && proverb.number in ProverbCatalog.rare
    }
}

private const val TAG = "ProverbReward"

@HiltViewModel
class ProverbRewardViewModel @Inject constructor(
    private val ticketDao: DayTicketDao,
    private val proverbDao: ProverbDao,
    private val plus: Entitlements,
    savedState: SavedStateHandle,
) : ViewModel() {
    /** The nav argument (ProverbReward.date, an epoch day). */
    val date: LocalDate = LocalDate.ofEpochDay(checkNotNull(savedState.get<Long>("date")) { "ProverbReward needs a date" })

    private val _state = MutableStateFlow<ProverbRewardUi>(ProverbRewardUi.Loading)
    val state: StateFlow<ProverbRewardUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            var next: ProverbRewardUi = ProverbRewardUi.Error
            try {
                next = ready() ?: ProverbRewardUi.NoCard
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "proverb reward failed to load", e)
            }
            _state.value = next
        }
    }

    private suspend fun ready(): ProverbRewardUi.Ready? {
        val row = checkNotNull(ticketDao.forDate(date)) { "no ticket for $date" }
        val days = ticketDao.allNow()
        val hand = days.filter { it.status == TicketStatus.CLOSED_BY_HAND }
        val weekFrom = weekStart(date)
        val number = row.proverbNumber
        // Only a day closed by hand rewards a card (or, with the album full, a favourite).
        if (row.status != TicketStatus.CLOSED_BY_HAND) return null
        val proverb = if (number != null) {
            checkNotNull(ProverbCatalog.all.firstOrNull { it.number == number }) { "no card $number" }
        } else {
            // Album full: a favourite from it, stable for the day.
            val owned = proverbDao.numbers().sorted()
            if (owned.isEmpty()) return null
            val pick = owned[Random(date.toEpochDay()).nextInt(owned.size)]
            ProverbCatalog.all.first { it.number == pick }
        }
        return ProverbRewardUi.Ready(
            ticketNumber = hand.count { it.date <= date },
            proverb = proverb,
            allCollected = number == null,
            leftover = row.leftover,
            destination = row.destination,
            streak = streak(days.map { DayResult(it.date, it.status, it.amount, it.spent) }, date, plus.isPlus.value),
            weekDays = hand.count { it.date >= weekFrom && it.date <= weekFrom.plusDays(6) },
        )
    }
}
