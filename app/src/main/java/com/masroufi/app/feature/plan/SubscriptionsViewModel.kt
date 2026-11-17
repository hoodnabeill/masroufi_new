package com.masroufi.app.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.COMMON_SUBSCRIPTIONS
import com.masroufi.app.data.repo.PeopleRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.data.repo.SubscriptionAnswer
import com.masroufi.app.data.repo.SubscriptionRepository
import com.masroufi.app.data.repo.SubscriptionView
import com.masroufi.app.domain.sharedGroupName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** A list row: [daysTo] is negative once the renewal day has passed. */
data class SubRowUi(val view: SubscriptionView, val daysTo: Long)

/** The trial ending within a week; [reminded] once the remind-me button was tapped. */
data class TrialCardUi(val view: SubscriptionView, val reminded: Boolean)

/** The question of the month; [grams] = what stopping saves a year in 21-karat gold, when a gold price exists. */
data class QuestionCardUi(val view: SubscriptionView, val grams: Long?)

data class SubscriptionsUi(
    val loading: Boolean = true,
    val rows: List<SubRowUi> = emptyList(),
    val monthlyTotal: Long = 0,
    val perYear: Long = 0,
    val tickets: Long = 0,
    val trial: TrialCardUi? = null,
    val question: QuestionCardUi? = null,
    /** One-tap add chips for names not already subscribed. */
    val chips: List<String> = emptyList(),
)

const val TRIAL_WARNING_DAYS = 7L

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repo: SubscriptionRepository,
    private val recurring: RecurringRepository,
    private val people: PeopleRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<SubscriptionsUi> = combine(repo.observe(), settings.settings) { v, s ->
        val today = LocalDate.now(clock)
        val trial = v.items.filter { (it.trialDaysLeft ?: Long.MAX_VALUE) <= TRIAL_WARNING_DAYS }
            .minWithOrNull(compareBy<SubscriptionView>({ it.trialDaysLeft }, { it.item.id }))
        val names = v.items.map { it.item.name }.toSet()
        SubscriptionsUi(
            loading = false,
            rows = v.items.map { SubRowUi(it, ChronoUnit.DAYS.between(today, it.item.nextDue)) },
            monthlyTotal = v.monthlyTotal,
            perYear = v.perYear,
            tickets = v.tickets,
            trial = trial?.let { TrialCardUi(it, s.trialReminders.containsKey(it.item.id)) },
            question = v.question?.let { q ->
                QuestionCardUi(q, q.stopSaving?.let { save -> s.goldPricePerGram?.let { g -> (save / g).takeIf { it >= 1 } } })
            },
            chips = COMMON_SUBSCRIPTIONS.filter { it !in names },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUi())

    /** The People entry of a shared subscription's group ("{name} · N صحاب"); null until a payment created its debt. */
    suspend fun sharedPersonId(view: SubscriptionView): Long? =
        people.personByName(sharedGroupName(view.item.name, view.item.sharedWith - 1))?.id

    fun answer(id: Long, answer: SubscriptionAnswer) {
        viewModelScope.launch { repo.answer(id, answer) }
    }

    /** Remind the day before the trial ends; the notification itself comes with Phase 11. */
    fun remindAboutTrial(view: SubscriptionView) {
        val end = view.item.trialEnd ?: return
        viewModelScope.launch { settings.setTrialReminder(view.item.id, end.minusDays(1).toEpochDay()) }
    }

    /** Keep it: a normal subscription from now on. */
    fun keepAfterTrial(view: SubscriptionView) {
        viewModelScope.launch { recurring.update(view.item.copy(trialEnd = null)) }
    }
}
