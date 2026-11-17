package com.masroufi.app.feature.onboarding

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.core.Payday
import com.masroufi.app.core.payCycleFor
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.FixedCost
import com.masroufi.app.data.repo.ONBOARDING_END_STEP
import com.masroufi.app.data.repo.OnboardingRepository
import com.masroufi.app.data.repo.shortfall
import com.masroufi.app.domain.TicketCalc
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Screens: 0 welcome, 1 payday, 2 money, 3 fixed costs, 4 reveal. */
const val STEP_WELCOME = 0
const val STEP_PAYDAY = 1
const val STEP_MONEY = 2
const val STEP_FIXED = 3
const val STEP_REVEAL = 4

const val DEFAULT_REMINDER_HOUR = 21

/** A fixed-cost chip: [key] is what is stored (always Arabic, the repository matches kinds on it), [label] what shows. */
data class FixedChip(val key: String, @StringRes val label: Int)

val FixedChips = listOf(
    FixedChip("الإيجار", R.string.ob_fixed_rent),
    FixedChip("النت", R.string.ob_fixed_net),
    FixedChip("الجمعية", R.string.ob_fixed_gameya),
    FixedChip("قسط", R.string.ob_fixed_installment),
    FixedChip("الكهربا والغاز", R.string.ob_fixed_utilities),
    FixedChip("دروس العيال", R.string.ob_fixed_lessons),
    FixedChip("اشتراك الجيم", R.string.ob_fixed_gym),
    FixedChip("حاجة تانية", R.string.ob_fixed_other),
)

/** What the reveal prints; [amount] is 0 when [endOfMonth] (what you have is short of what is reserved by [shortfall]). */
data class RevealUi(val amount: Long, val endOfMonth: Boolean, val shortfall: Long, val days: Int, val paydayDay: Int)

data class OnboardingUi(
    val loaded: Boolean = false,
    val step: Int = STEP_WELCOME,
    val payday: Payday = Payday.Day(25),
    val daysLeft: Int = 0,
    val money: AmountInput = AmountInput(),
    val moneySkipped: Boolean = false,
    /** Chip key to piastres, in the order they were picked. */
    val fixed: Map<String, Long> = emptyMap(),
    /** The chip whose inline keypad is open; one at a time. */
    val openFixed: String? = null,
    val fixedInput: AmountInput = AmountInput(),
    /** "Payday is near" question showing instead of the ticket. */
    val askNear: Boolean = false,
    val reveal: RevealUi? = null,
    val reminderHour: Int? = DEFAULT_REMINDER_HOUR,
    val finishing: Boolean = false,
) {
    val fixedTotal: Long get() = fixed.values.sum()
    val moneyReady: Boolean get() = money.text.isNotEmpty()
}

sealed interface OnboardingEvent {
    /** [OnboardingRepository.finish] ran: AppRoot goes to Home and opens the add sheet. */
    data object Finished : OnboardingEvent
}

/** What the steps do; [OnboardingViewModel] implements it, tests pass their own. */
interface OnboardingActions {
    fun next() = Unit
    fun back() = Unit
    fun pickPayday(payday: Payday) = Unit
    fun digit(d: Int) = Unit
    fun point() = Unit
    fun backspace() = Unit
    fun addMoney(piastres: Long) = Unit
    fun skipMoney() = Unit
    fun tapFixed(key: String) = Unit
    fun confirmFixed() = Unit
    fun printTicket() = Unit
    fun skipFixed() = Unit
    fun answerNear(afterPayday: Boolean) = Unit
    fun pickReminder(hour: Int?) = Unit
    fun finish() = Unit
}

private const val TAG = "Onboarding"

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: OnboardingRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel(), OnboardingActions {
    private val _state = MutableStateFlow(OnboardingUi())
    val state: StateFlow<OnboardingUi> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingEvent> = _events.receiveAsFlow()

    /** The answer to the "payday is near" question; null = not asked yet. */
    private var startAfterPayday: Boolean? = null

    init {
        viewModelScope.launch {
            val s = settings.current()
            val step = s.onboardingStep.coerceIn(STEP_WELCOME, ONBOARDING_END_STEP - 1)
            _state.value = OnboardingUi(
                loaded = true,
                step = step,
                payday = s.payday,
                daysLeft = daysLeft(s.payday),
                money = repo.savedMoney()?.let { AmountInput.of(it) } ?: AmountInput(),
                moneySkipped = s.moneyUnknown,
                fixed = repo.savedFixedCosts().associate { it.label to it.amount },
            )
            if (step == STEP_REVEAL) prepareReveal()
        }
    }

    private fun daysLeft(payday: Payday): Int {
        val today = LocalDate.now(clock)
        return payCycleFor(today, payday, null).daysLeft(today)
    }

    override fun pickPayday(payday: Payday) = _state.update { it.copy(payday = payday, daysLeft = daysLeft(payday)) }

    /** Saves the current step's answer and moves on. */
    override fun next() {
        val s = _state.value
        if (s.step >= STEP_FIXED) return
        viewModelScope.launch {
            when (s.step) {
                STEP_PAYDAY -> repo.setPayday(s.payday)
                STEP_MONEY -> {
                    if (!s.moneyReady) return@launch
                    repo.setMoney(s.money.toPiastres())
                    _state.update { it.copy(moneySkipped = false) }
                }
            }
            go(s.step + 1)
        }
    }

    override fun back() {
        val s = _state.value
        if (s.step > STEP_WELCOME) viewModelScope.launch { go(s.step - 1) }
    }

    override fun skipMoney() {
        viewModelScope.launch {
            repo.setMoney(null)
            _state.update { it.copy(money = AmountInput(), moneySkipped = true) }
            go(STEP_FIXED)
        }
    }

    override fun printTicket() = saveFixed(_state.value.fixed.map { FixedCost(it.key, it.value) })

    override fun skipFixed() {
        _state.update { it.copy(fixed = emptyMap()) }
        saveFixed(emptyList())
    }

    private fun saveFixed(costs: List<FixedCost>) {
        viewModelScope.launch {
            repo.setFixedCosts(costs)
            go(STEP_REVEAL)
        }
    }

    private suspend fun go(step: Int) {
        repo.setStep(step)
        if (step < STEP_REVEAL) startAfterPayday = null
        _state.update { it.copy(step = step, openFixed = null, fixedInput = AmountInput(), reveal = null, askNear = false) }
        if (step == STEP_REVEAL) prepareReveal()
    }

    private suspend fun prepareReveal() {
        if (startAfterPayday == null && repo.paydayIsNear()) {
            _state.update { it.copy(askNear = true, reveal = null) }
            return
        }
        val after = startAfterPayday == true
        val calc = repo.previewTicket(after)
        val today = LocalDate.now(clock)
        val payday = _state.value.payday
        val now = payCycleFor(today, payday, null)
        // Starting after payday means the cycle that begins tomorrow.
        val cycle = if (after && payday != Payday.NotFixed && now.nextPayday == today.plusDays(1)) payCycleFor(now.nextPayday, payday, null) else now
        _state.update {
            it.copy(
                askNear = false,
                reveal = RevealUi(
                    amount = (calc as? TicketCalc.Printed)?.amount ?: 0L,
                    endOfMonth = calc is TicketCalc.EndOfMonth,
                    shortfall = calc.shortfall,
                    days = cycle.daysLeft(maxOf(today, cycle.start)),
                    paydayDay = cycle.nextPayday.dayOfMonth,
                ),
            )
        }
    }

    override fun answerNear(afterPayday: Boolean) {
        startAfterPayday = afterPayday
        viewModelScope.launch { prepareReveal() }
    }

    // The keypad types into the money on its step and into the open chip on the fixed-costs step.
    private fun edit(op: (AmountInput) -> AmountInput) = _state.update {
        when {
            it.step == STEP_MONEY -> it.copy(money = op(it.money))
            it.step == STEP_FIXED && it.openFixed != null -> it.copy(fixedInput = op(it.fixedInput))
            else -> it
        }
    }

    override fun digit(d: Int) = edit { it.digit(d) }
    override fun point() = edit { it.point() }
    override fun backspace() = edit { it.backspace() }

    /** The shortcut chips add to what is typed; an amount that would not fit the keypad is ignored. */
    override fun addMoney(piastres: Long) = _state.update {
        val sum = AmountInput.of(it.money.toPiastres() + piastres)
        if (sum.text.isEmpty()) it else it.copy(money = sum)
    }

    /** Tap = open (one at a time); tap the open one = close; tap a filled one = remove. */
    override fun tapFixed(key: String) = _state.update {
        when {
            key in it.fixed -> it.copy(fixed = it.fixed - key, openFixed = it.openFixed.takeIf { open -> open != key })
            it.openFixed == key -> it.copy(openFixed = null)
            else -> it.copy(openFixed = key, fixedInput = AmountInput())
        }
    }

    override fun confirmFixed() = _state.update {
        val key = it.openFixed
        val amount = it.fixedInput.toPiastres()
        if (key == null || amount <= 0) it else it.copy(fixed = it.fixed + (key to amount), openFixed = null, fixedInput = AmountInput())
    }

    override fun pickReminder(hour: Int?) = _state.update { it.copy(reminderHour = hour) }

    override fun finish() {
        val s = _state.value
        if (s.finishing || s.reveal == null) return
        _state.update { it.copy(finishing = true) }
        viewModelScope.launch {
            try {
                repo.finish(s.reminderHour, startAfterPayday == true)
                _events.send(OnboardingEvent.Finished)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "finish failed", e)
                _state.update { it.copy(finishing = false) }
            }
        }
    }
}
