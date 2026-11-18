package com.masroufi.app.feature.plan

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The kinds a fixed cost can be, in chip order (the gameya comes with People). */
val FIXED_COST_KINDS = listOf(RecurringKind.BILL, RecurringKind.INSTALLMENT, RecurringKind.SUBSCRIPTION)

data class FixedCostForm(
    val loaded: Boolean = false,
    val name: String = "",
    val amount: AmountInput = AmountInput(),
    val day: Int = 1,
    val pocketId: Long? = null,
    val kind: RecurringKind = RecurringKind.BILL,
    val pockets: List<PocketEntity> = emptyList(),
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = loaded && name.isNotBlank() && amount.toPiastres() > 0 && pocketId != null && !saving
}

/** The next [day] of a month on or after [today], short months clamped (31 -> 30 in November). */
internal fun nextDueOn(day: Int, today: LocalDate): LocalDate {
    fun inMonth(m: YearMonth) = m.atDay(minOf(day, m.lengthOfMonth()))
    val thisMonth = inMonth(YearMonth.from(today))
    return if (thisMonth.isBefore(today)) inMonth(YearMonth.from(today).plusMonths(1)) else thisMonth
}

private const val TAG = "FixedCostForm"
private const val NAME_MAX = 30

/** New fixed cost (no "id" argument, or 0) and edit fixed cost (the id). */
@HiltViewModel
class FixedCostFormViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    pocketRepo: PocketRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private var stored: RecurringEntity? = null

    private val _form = MutableStateFlow(FixedCostForm())
    val form: StateFlow<FixedCostForm> = _form.asStateFlow()

    val isEdit: Boolean get() = id != 0L

    /** The cost was deleted: the screen goes back. */
    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    init {
        viewModelScope.launch {
            val pockets = pocketRepo.observePockets().first().map { it.pocket }.filterNot { it.archived }
            val item = if (id != 0L) recurring.get(id) else null
            stored = item
            _form.update {
                if (item == null) {
                    it.copy(loaded = true, pockets = pockets, pocketId = pockets.firstOrNull()?.id, day = LocalDate.now(clock).dayOfMonth)
                } else {
                    it.copy(
                        loaded = true, pockets = pockets, name = item.name, amount = AmountInput.of(item.amount), day = item.scheduleDay,
                        pocketId = item.pocketId, kind = item.kind,
                    )
                }
            }
        }
    }

    fun setName(text: String) = _form.update { it.copy(name = text.take(NAME_MAX), failed = false) }
    fun setDay(day: Int) = _form.update { it.copy(day = day.coerceIn(1, 31)) }
    fun setPocket(id: Long) = _form.update { it.copy(pocketId = id) }
    fun setKind(kind: RecurringKind) = _form.update { it.copy(kind = kind) }
    fun digit(d: Int) = _form.update { it.copy(amount = it.amount.digit(d)) }
    fun point() = _form.update { it.copy(amount = it.amount.point()) }
    fun backspace() = _form.update { it.copy(amount = it.amount.backspace()) }

    fun save() {
        val f = _form.value
        if (!f.canSave) return
        _form.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                val old = stored
                if (old == null) {
                    recurring.add(f.name.trim(), f.amount.toPiastres(), checkNotNull(f.pocketId), nextDueOn(f.day, LocalDate.now(clock)), f.kind)
                } else {
                    // The same period, on the new day.
                    val period = YearMonth.from(old.nextDue)
                    // The kids' handover is weekly: its scheduleDay is a day of the week, which this form must not read as a day of the month.
                    val weekly = old.scheduleType == ScheduleType.WEEKLY
                    val due = if (weekly || f.day == old.scheduleDay) old.nextDue else period.atDay(minOf(f.day, period.lengthOfMonth()))
                    recurring.update(
                        old.copy(
                            name = f.name.trim(), amount = f.amount.toPiastres(), pocketId = checkNotNull(f.pocketId),
                            scheduleDay = if (weekly) old.scheduleDay else f.day, nextDue = due, kind = f.kind,
                        ),
                    )
                }
                _form.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                _form.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    fun delete() {
        if (stored == null) return
        viewModelScope.launch {
            recurring.delete(id)
            _gone.value = true
        }
    }
}
