package com.masroufi.app.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.RecurringRow
import com.masroufi.app.data.repo.EnvelopeRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.feature.add.AddPrefill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Where a fixed cost stands this pay cycle (decision 4: paid once its due date has reached the next payday). */
sealed interface DueState {
    data object Paid : DueState
    data class Due(val date: LocalDate) : DueState
    data object Overdue : DueState
}

data class FixedCostUi(val row: RecurringRow, val due: DueState)

data class FixedCostsUi(val loading: Boolean = true, val items: List<FixedCostUi> = emptyList())

internal fun dueState(nextDue: LocalDate, today: LocalDate, nextPayday: LocalDate): DueState = when {
    !nextDue.isBefore(nextPayday) -> DueState.Paid
    nextDue.isBefore(today) -> DueState.Overdue
    else -> DueState.Due(nextDue)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FixedCostsViewModel @Inject constructor(
    recurring: RecurringRepository,
    private val envelopes: EnvelopeRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<FixedCostsUi> = recurring.observe().mapLatest { rows ->
        val today = LocalDate.now(clock)
        val payday = envelopes.cycle().nextPayday
        FixedCostsUi(false, rows.map { FixedCostUi(it, dueState(it.nextDue, today, payday)) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FixedCostsUi())

    /** "ادفعها": the add sheet for the payment; saving it marks the cost paid and moves its due date on. */
    fun payPrefill(row: RecurringRow) = AddPrefill(
        amount = row.amount, categoryId = null, note = row.name, type = EntryKind.SPENT, pocketId = row.pocketId, recurringId = row.id,
    )
}
