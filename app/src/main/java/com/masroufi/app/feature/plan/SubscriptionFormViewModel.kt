package com.masroufi.app.feature.plan

import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.roll.PendingDelete
import com.masroufi.app.feature.roll.DeleteKind
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.data.repo.SubscriptionRepository
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
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class SubscriptionForm(
    val loaded: Boolean = false,
    val name: String = "",
    /** The price per month, or per year when [yearly]. */
    val amount: AmountInput = AmountInput(),
    val day: Int = 1,
    val yearly: Boolean = false,
    /** What a monthly one costs paid yearly (optional): feeds the yearly hint. */
    val yearlyPrice: AmountInput = AmountInput(),
    val sharedWith: Int = 1,
    /** Days from today to the trial's last day; null = no trial. */
    val trialDays: Int? = null,
    val pocketId: Long? = null,
    val pockets: List<PocketEntity> = emptyList(),
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = loaded && name.isNotBlank() && amount.toPiastres() > 0 && pocketId != null && !saving
}

private const val TAG = "SubscriptionForm"
private const val NAME_MAX = 30
private const val MAX_PEOPLE = 20
private const val MAX_TRIAL_DAYS = 60

/** New subscription ("id" 0, optional "name" from a chip) and edit subscription (the id). */
@HiltViewModel
class SubscriptionFormViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val recurring: RecurringRepository,
    pocketRepo: PocketRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
    private val deletes: PendingDeletes,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val chipName: String = savedState.get<String>("name").orEmpty()
    private var stored: RecurringEntity? = null

    private val _form = MutableStateFlow(SubscriptionForm())
    val form: StateFlow<SubscriptionForm> = _form.asStateFlow()

    val isEdit: Boolean get() = id != 0L

    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val pockets = pocketRepo.observePockets().first().map { it.pocket }.filterNot { it.archived }
            val item = if (id != 0L) recurring.get(id) else null
            stored = item
            _form.update {
                if (item == null) {
                    it.copy(loaded = true, pockets = pockets, pocketId = pockets.firstOrNull()?.id, day = today.dayOfMonth, name = chipName.take(NAME_MAX))
                } else {
                    it.copy(
                        loaded = true, pockets = pockets, name = item.name, amount = AmountInput.of(item.amount), day = item.scheduleDay,
                        yearly = item.scheduleType == ScheduleType.YEARLY,
                        yearlyPrice = if (item.scheduleType == ScheduleType.MONTHLY) item.yearlyPrice?.let(AmountInput::of) ?: AmountInput() else AmountInput(),
                        sharedWith = item.sharedWith, pocketId = item.pocketId,
                        trialDays = item.trialEnd?.takeIf { e -> !e.isBefore(today) }?.let { e -> ChronoUnit.DAYS.between(today, e).toInt() },
                    )
                }
            }
        }
    }

    fun setName(text: String) = _form.update { it.copy(name = text.take(NAME_MAX), failed = false) }
    fun setDay(day: Int) = _form.update { it.copy(day = day.coerceIn(1, 31)) }
    fun setPocket(id: Long) = _form.update { it.copy(pocketId = id) }
    fun setYearly(yearly: Boolean) = _form.update { it.copy(yearly = yearly) }
    fun stepPeople(delta: Int) = _form.update { it.copy(sharedWith = (it.sharedWith + delta).coerceIn(1, MAX_PEOPLE)) }
    fun setTrial(on: Boolean) = _form.update { it.copy(trialDays = if (on) (it.trialDays ?: 7) else null) }
    fun stepTrial(delta: Int) = _form.update { f -> f.trialDays?.let { f.copy(trialDays = (it + delta).coerceIn(0, MAX_TRIAL_DAYS)) } ?: f }
    fun digit(d: Int) = _form.update { it.copy(amount = it.amount.digit(d)) }
    fun point() = _form.update { it.copy(amount = it.amount.point()) }
    fun backspace() = _form.update { it.copy(amount = it.amount.backspace()) }
    fun yearlyDigit(d: Int) = _form.update { it.copy(yearlyPrice = it.yearlyPrice.digit(d)) }
    fun yearlyPoint() = _form.update { it.copy(yearlyPrice = it.yearlyPrice.point()) }
    fun yearlyBackspace() = _form.update { it.copy(yearlyPrice = it.yearlyPrice.backspace()) }

    fun save() {
        val f = _form.value
        if (!f.canSave) return
        _form.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                val today = LocalDate.now(clock)
                val price = f.amount.toPiastres()
                val type = if (f.yearly) ScheduleType.YEARLY else ScheduleType.MONTHLY
                // Yearly: the price is the year's; monthly: an optional yearly price for the hint.
                val yearlyPrice = if (f.yearly) price else f.yearlyPrice.toPiastres().takeIf { it > 0 }
                val trialEnd = f.trialDays?.let { today.plusDays(it.toLong()) }
                val old = stored
                if (old == null) {
                    subscriptions.add(f.name.trim(), price, checkNotNull(f.pocketId), nextDueOn(f.day, today), type, trialEnd, yearlyPrice, f.sharedWith)
                } else {
                    val period = YearMonth.from(old.nextDue)
                    val due = if (f.day == old.scheduleDay) old.nextDue else period.atDay(minOf(f.day, period.lengthOfMonth()))
                    recurring.update(
                        old.copy(
                            name = f.name.trim(), amount = price, pocketId = checkNotNull(f.pocketId), scheduleDay = f.day, nextDue = due,
                            scheduleType = type, yearlyPrice = yearlyPrice, sharedWith = f.sharedWith, trialEnd = trialEnd,
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
        val id = id
        deletes.schedule(PendingDelete(id, kind = DeleteKind.SUBSCRIPTION)) { recurring.delete(id) }
        _gone.value = true
    }
}
