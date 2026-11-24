package com.masroufi.app.notify

import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.DutyRepository
import com.masroufi.app.data.repo.GameyaRepository
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.data.repo.WishRepository
import com.masroufi.app.data.repo.toInt
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.NotifKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.streak
import com.masroufi.app.entitlements.PlusRepository
import com.masroufi.app.entitlements.TrialState
import com.masroufi.app.entitlements.trialInfo
import com.masroufi.app.feature.pockets.DEFAULT_LOW_BALANCE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Where a notice goes out; the Android one posts it, tests use a fake. */
interface NoticeSink {
    /** False while the app may not post (permission refused, or switched off in the system). */
    fun canPost(): Boolean
    fun post(notice: Notice): Boolean
}

/**
 * One check: reads the facts, asks [NotificationPlanner] what is due now, posts it and remembers what was posted.
 * Runs under one lock because the hourly job and a one-off can overlap. Nothing counts (or is remembered) unless it was posted.
 */
@Singleton
class NotificationService @Inject constructor(
    private val settings: SettingsStore,
    private val tickets: DayTicketDao,
    private val recurring: RecurringDao,
    private val gameyas: GameyaRepository,
    private val wishes: WishRepository,
    private val duty: DutyRepository,
    private val pockets: PocketRepository,
    private val plus: PlusRepository,
    private val sink: NoticeSink,
    private val clock: Clock,
) {
    private val lock = Mutex()

    /** Returns how many notifications were posted. */
    suspend fun run(): Int = lock.withLock {
        val s = settings.current()
        // A child using the phone sees nothing; before onboarding there is nothing to say.
        if (!s.onboarded || s.childModeId != null || !sink.canPost()) return 0
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val facts = facts(s, today)
        val state = NotifState(
            sent = s.notifSent,
            postedToday = if (s.notifDay == today.toEpochDay()) s.notifCount else 0,
            lowNotified = s.notifLow,
        )
        val cfg = NotifConfig(NotifKind.entries.filter(s::notifEnabled).toSet(), s.reminderHour, mask = s.lockOn || s.hideAmounts)
        val posted = NotificationPlanner.plan(now, facts, cfg, state).filter { sink.post(it) }
        settings.recordNotified(
            day = today.toEpochDay(),
            total = state.postedToday + posted.size,
            sent = NotificationPlanner.prune(s.notifSent + posted.map { it.key }, today.toEpochDay()),
            low = NotificationPlanner.lowAfter(state, facts, posted),
        )
        posted.size
    }

    private suspend fun facts(s: Settings, today: LocalDate): NotifFacts {
        val tomorrow = today.plusDays(1)
        val row = tickets.forDate(today)
        val open = row?.status == TicketStatus.OPEN
        val recent = tickets.range(today.minusDays(400), today).first()
        val items = recurring.allNow().filter { !it.archived }
        val trial = trialInfo(s, today)
        val pocketRows = pockets.observePockets().first()
        val thisMonth = YearMonth.from(today)
        return NotifFacts(
            ticketOpen = open,
            ticketLeft = row?.let { it.amount - it.spent } ?: 0,
            ticketAmount = row?.amount,
            streak = streak(recent.map { DayResult(it.date, it.status, it.amount, it.spent) }, today, plus.isPlus.value),
            billsTomorrow = items.filter { it.kind == RecurringKind.BILL && it.nextDue == tomorrow }.map { DueItem(it.id, it.name, it.amount) },
            gameyaDueTomorrow = items.filter { it.kind == RecurringKind.GAMEYA && it.nextDue == tomorrow }.map { DueItem(it.id, it.name, it.amount) },
            gameyaTurns = gameyas.observe().first().filter { !it.finished && it.myTurnMonth == thisMonth }
                .map { TurnItem(it.gameya.id, thisMonth.toInt(), it.payout) },
            wishesDue = wishes.dueForAnswer().first().map { DueItem(it.id, it.name, it.price) },
            occasions = duty.theirsOn(today.plusDays(7)).map { OccasionItem(it.occasion.id, it.occasion.name, it.gaveMe) },
            plusTrialEndsTomorrow = trial.state == TrialState.ACTIVE && trial.endDay == tomorrow && plus.subscription.value == null,
            subTrials = items.filter { it.kind == RecurringKind.SUBSCRIPTION && it.trialEnd != null && s.trialReminders[it.id] == today.toEpochDay() }
                .map { DueItem(it.id, it.name) },
            lowPockets = pocketRows.filter { it.pocket.lowBalanceAlert && it.balance < (it.pocket.lowBalanceThreshold ?: DEFAULT_LOW_BALANCE) }
                .map { LowPocket(it.pocket.id, it.pocket.name, it.balance) },
        )
    }
}
