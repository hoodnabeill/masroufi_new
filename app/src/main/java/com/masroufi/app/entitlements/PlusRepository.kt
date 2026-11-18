package com.masroufi.app.entitlements

import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.EndOfMonthMode
import com.masroufi.app.di.AppScope
import com.masroufi.app.domain.TicketStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Offered from day 7 of use (the first open day is day 1). */
const val TRIAL_OFFER_AFTER_DAYS = 6L

/** Plus lasts 14 days: the start day and the 13 after it. */
const val TRIAL_DAYS = 14L

/** The quiet Home line shows this many days before the end (and on the end day). */
const val TRIAL_ENDS_SOON_DAYS = 3L

enum class RestoreOutcome { FOUND, NONE, FAILED }

enum class TrialState { NOT_YET, OFFERED, ACTIVE, ENDED }

/** [endDay] is the last day of Plus (inclusive); [daysLeft] counts to it, 0 on that day. */
data class TrialInfo(val state: TrialState, val startDay: LocalDate?, val endDay: LocalDate?, val daysLeft: Int, val endsSoon: Boolean)

fun trialInfo(s: Settings, today: LocalDate): TrialInfo {
    val start = s.trialStartDay?.let(LocalDate::ofEpochDay)
    val end = s.trialEndDay?.let(LocalDate::ofEpochDay)
    if (start != null && end != null) {
        if (today.isAfter(end)) return TrialInfo(TrialState.ENDED, start, end, 0, false)
        val left = (end.toEpochDay() - today.toEpochDay()).toInt()
        return TrialInfo(TrialState.ACTIVE, start, end, left, left <= TRIAL_ENDS_SOON_DAYS)
    }
    val eligibleFrom = (s.firstOpenDay ?: return TrialInfo(TrialState.NOT_YET, null, null, 0, false)) + TRIAL_OFFER_AFTER_DAYS
    val state = if (today.toEpochDay() >= eligibleFrom) TrialState.OFFERED else TrialState.NOT_YET
    return TrialInfo(state, null, null, 0, false)
}

/** The one entitlement rule: a subscription, an active trial, or the debug override. */
fun plusOn(s: Settings, subscription: ActiveSubscription?, debugOverride: Boolean, today: LocalDate): Boolean =
    debugOverride || subscription != null || trialInfo(s, today).state == TrialState.ACTIVE

/**
 * Plus state: billing, the trial and the debug override. Nothing here deletes data: Plus-locked screens only stop
 * showing it. Must not depend on anything that depends on [Entitlements] (a Hilt cycle).
 * Limit: [isPlus] re-evaluates when billing or settings change, not at midnight by itself (the screens re-read on resume).
 */
@Singleton
class PlusRepository @Inject constructor(
    private val billing: Billing,
    private val settings: SettingsStore,
    private val ticketDao: DayTicketDao,
    private val eomMode: EndOfMonthMode,
    private val clock: Clock,
    @AppScope scope: CoroutineScope,
) {
    private val debugOverride = MutableStateFlow(false)

    val isPlus: StateFlow<Boolean> = combine(billing.subscription, settings.settings, debugOverride) { sub, s, o ->
        plusOn(s, sub, o, LocalDate.now(clock))
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Restore on start: a reinstall finds its subscription again.
        scope.launch { runCatching { billing.restore() } }
    }

    fun setDebugOverride(on: Boolean) { debugOverride.value = on }

    fun trial(): Flow<TrialInfo> = settings.settings.map { trialInfo(it, LocalDate.now(clock)) }

    suspend fun trialInfoNow(today: LocalDate = LocalDate.now(clock)): TrialInfo = trialInfo(settings.current(), today)

    /** Starts the one trial today, if it is offered and nothing is paid yet; true when it started. */
    suspend fun startTrial(today: LocalDate = LocalDate.now(clock)): Boolean {
        if (billing.subscription.value != null || trialInfo(settings.current(), today).state != TrialState.OFFERED) return false
        return settings.startTrial(today.toEpochDay(), today.plusDays(TRIAL_DAYS - 1).toEpochDay())
    }

    suspend fun restore(): ActiveSubscription? = billing.restore()

    /** The settings row's "استرجاع الاشتراك": a plain result, never an exception. */
    suspend fun restoreOutcome(): RestoreOutcome = try {
        if (billing.restore() != null) RestoreOutcome.FOUND else RestoreOutcome.NONE
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RestoreOutcome.FAILED
    }

    /** What is paid for now (null = nothing): the Home trial card and line stay away when it is set. */
    val subscription: StateFlow<ActiveSubscription?> get() = billing.subscription

    /**
     * The paywall opens by itself the day before the trial ends and on the end day, once a day; never with a
     * subscription, before onboarding is finished, during end-of-month mode, or on the day after an overspent day.
     */
    suspend fun shouldAutoOpenPaywall(today: LocalDate = LocalDate.now(clock)): Boolean {
        val s = settings.current()
        val trial = trialInfo(s, today)
        if (trial.state != TrialState.ACTIVE || trial.daysLeft > 1) return false
        if (billing.subscription.value != null || !s.onboarded) return false
        if (s.lastPaywallDay == today.toEpochDay()) return false
        if (eomMode.isOn.first()) return false
        val yesterday = ticketDao.forDate(today.minusDays(1))
        return !(yesterday != null && yesterday.status != TicketStatus.OPEN && yesterday.spent > yesterday.amount)
    }

    suspend fun markPaywallShown(today: LocalDate = LocalDate.now(clock)) = settings.setLastPaywallDay(today.toEpochDay())
}
