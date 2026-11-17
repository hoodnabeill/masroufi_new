package com.masroufi.app.feature.plan

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.SeasonKind
import com.masroufi.app.data.repo.AboveFreeJar
import com.masroufi.app.data.repo.JarRepository
import com.masroufi.app.data.repo.SeasonRepository
import com.masroufi.app.data.repo.SeasonView
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.Year
import javax.inject.Inject

data class SeasonForm(
    val loaded: Boolean = false,
    val name: String = "",
    val month: Int = 1,
    val day: Int = 1,
    val target: AmountInput = AmountInput(),
    val saved: Boolean = false,
)

sealed interface SeasonSheet {
    data object Item : SeasonSheet
    data object Money : SeasonSheet
}

private const val TAG = "SeasonDetail"
private const val NAME_MAX = 30

/** The next [month]/[day] on or after [today]; a short month clamps the day (Feb 30 -> Feb 28 or 29). */
internal fun nextMonthDay(month: Int, day: Int, today: LocalDate): LocalDate {
    fun inYear(y: Int) = MonthDay.of(month, minOf(day, Month.of(month).length(Year.isLeap(y.toLong())))).atYear(y)
    return inYear(today.year).let { if (it.isBefore(today)) inYear(today.year + 1) else it }
}

/** A season's page (id = its id) or a new personal occasion (id 0). */
@HiltViewModel
class SeasonDetailViewModel @Inject constructor(
    private val seasons: SeasonRepository,
    private val jar: JarRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    val isNew: Boolean get() = id == 0L

    private val _form = MutableStateFlow(
        LocalDate.now(clock).let { SeasonForm(loaded = id == 0L, month = it.monthValue, day = it.dayOfMonth) },
    )
    val form: StateFlow<SeasonForm> = _form.asStateFlow()

    /** The live season (checklist, earmarked amount); null for a new occasion. */
    private val _view = MutableStateFlow<SeasonView?>(null)
    val view: StateFlow<SeasonView?> = _view.asStateFlow()

    private val _free = MutableStateFlow(0L)
    val free: StateFlow<Long> = _free.asStateFlow()

    private val _sheet = MutableStateFlow<SeasonSheet?>(null)
    val sheet: StateFlow<SeasonSheet?> = _sheet.asStateFlow()

    init {
        viewModelScope.launch {
            jar.observe().collect { _free.value = it.free }
        }
        if (id != 0L) viewModelScope.launch {
            seasons.observe().collect { all ->
                val v = all.firstOrNull { it.season.id == id }
                _view.value = v
                if (v != null && !_form.value.loaded) {
                    val d = v.season.gregorianDate ?: v.date
                    _form.value = SeasonForm(true, v.season.name, d.monthValue, d.dayOfMonth, AmountInput.of(v.season.target))
                }
            }
        }
    }

    fun setName(v: String) = _form.update { it.copy(name = v.take(NAME_MAX)) }
    fun setMonth(v: Int) = _form.update { it.copy(month = v) }
    fun stepDay(delta: Int) = _form.update { it.copy(day = (it.day + delta).coerceIn(1, Month.of(it.month).maxLength())) }
    fun digit(d: Int) = _form.update { it.copy(target = it.target.digit(d)) }
    fun point() = _form.update { it.copy(target = it.target.point()) }
    fun backspace() = _form.update { it.copy(target = it.target.backspace()) }
    fun open(s: SeasonSheet) { _sheet.value = s }
    fun dismiss() { _sheet.value = null }

    fun canSave(f: SeasonForm = _form.value) = f.loaded && (f.name.isNotBlank() || (_view.value?.season?.kind ?: SeasonKind.PERSONAL) != SeasonKind.PERSONAL)

    /** The checklist's sum becomes the target. */
    fun targetFromChecklist() {
        val sum = _view.value?.items?.sumOf { it.amount } ?: return
        _form.update { it.copy(target = AmountInput.of(sum)) }
    }

    fun save() {
        val f = _form.value
        if (!canSave(f)) return
        act {
            val today = LocalDate.now(clock)
            val season = _view.value?.season
            if (season == null) {
                seasons.createPersonal(f.name.trim(), nextMonthDay(f.month, f.day, today), f.target.toPiastres())
            } else {
                seasons.update(
                    season.copy(
                        name = if (season.kind == SeasonKind.PERSONAL) f.name.trim() else season.name,
                        gregorianDate = if (season.hijriMonth == null) nextMonthDay(f.month, f.day, today) else season.gregorianDate,
                        target = f.target.toPiastres(),
                    ),
                )
            }
            _form.update { it.copy(saved = true) }
        }
    }

    fun addItem(label: String, amount: Long) = act { if (id != 0L && label.isNotBlank()) seasons.addItem(id, label.trim(), amount) }
    fun removeItem(itemId: Long) = act { seasons.deleteItem(itemId) }

    /** Above the free jar nothing moves (the sheet already caps it). */
    fun earmark(amount: Long) = act { _sheet.value = null; jar.earmark(amount, seasonId = id) }
    fun release(amount: Long) = act { _sheet.value = null; jar.release(amount, seasonId = id) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AboveFreeJar) {
                Log.w(TAG, "above the free jar (${e.free})")
            } catch (e: Exception) {
                Log.e(TAG, "season action failed", e)
            }
        }
    }
}
