package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.Payday
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.TicketCalc
import com.masroufi.app.domain.TicketStatus
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A fixed cost picked in onboarding: the chip label, what it costs this cycle, and how it is paid. */
data class FixedCost(val label: String, val amount: Long, val kind: RecurringKind = kindOfFixed(label))

/** Decision 2: the gameya and the installment have their own kinds; every other chip is a bill. */
fun kindOfFixed(label: String): RecurringKind = when (label) {
    "الجمعية" -> RecurringKind.GAMEYA
    "قسط" -> RecurringKind.INSTALLMENT
    else -> RecurringKind.BILL
}

/** Onboarding screens 0 welcome, 1 payday, 2 money, 3 fixed costs, 4 reveal; this is "done". */
const val ONBOARDING_END_STEP = 5

private const val ONBOARDING_SOURCE = "onboarding"

/** How much short the pool is when the ticket is end-of-month because money < reserved; 0 for a printed ticket. */
val TicketCalc.shortfall: Long get() = (this as? TicketCalc.EndOfMonth)?.let { (-it.pool).coerceAtLeast(0) } ?: 0L

@Singleton
class OnboardingRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val pocketDao: PocketDao,
    private val entryDao: EntryDao,
    private val ticketDao: DayTicketDao,
    private val recurringDao: RecurringDao,
    private val tickets: TicketRepository,
    private val seed: SeedData,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun setPayday(payday: Payday) = settings.setPayday(payday)

    suspend fun setStep(step: Int) = settings.setOnboardingStep(step)

    /** Null = "not now": the starter ticket prints and the balance stays untouched. */
    suspend fun setMoney(piastres: Long?) {
        if (piastres == null) {
            settings.setMoneyUnknown(true)
            return
        }
        require(piastres >= 0) { "money must be >= 0" }
        seed.ensureSeeded()
        db.withTransaction {
            val cash = checkNotNull(pocketDao.firstCash()) { "no cash pocket after seeding" }
            // Opening absorbs everything else the balance holds, so the balance is exactly `piastres` now.
            val others = pocketDao.balanceOf(cash.id) - cash.openingBalance
            pocketDao.update(cash.copy(openingBalance = piastres - others, updatedAt = clock.millis()))
        }
        settings.setMoneyUnknown(false)
    }

    /** Replaces the recurring items onboarding made earlier; each is due the day before the next payday, from الكاش. */
    suspend fun setFixedCosts(costs: List<FixedCost>) {
        seed.ensureSeeded()
        val s = settings.current()
        val today = LocalDate.now(clock)
        db.withTransaction {
            val cash = checkNotNull(pocketDao.firstCash()) { "no cash pocket after seeding" }
            val due = entryDao.payCycleOn(today, s.payday, clock).nextPayday.minusDays(1)
            val now = clock.millis()
            recurringDao.deleteOnboarding()
            costs.forEach {
                recurringDao.insert(
                    RecurringEntity(
                        name = it.label, amount = it.amount, pocketId = cash.id, scheduleDay = due.dayOfMonth, nextDue = due,
                        paidThisCycle = false, kind = it.kind, source = ONBOARDING_SOURCE, updatedAt = now,
                    ),
                )
            }
        }
    }

    /** The amount saved earlier, for resuming mid-flow; null when skipped or never answered. */
    suspend fun savedMoney(): Long? {
        if (settings.current().moneyUnknown) return null
        val cash = pocketDao.firstCash() ?: return null
        return pocketDao.balanceOf(cash.id).takeIf { it > 0 }
    }

    suspend fun savedFixedCosts(): List<FixedCost> =
        recurringDao.all().first().filter { it.source == ONBOARDING_SOURCE }.map { FixedCost(it.name, it.amount, it.kind) }

    /** What printToday would print now; see [startAfterPayday] on [TicketRepository.basisToday]. Writes nothing. */
    suspend fun previewTicket(startAfterPayday: Boolean = false): TicketCalc =
        tickets.basisToday(settings.current(), LocalDate.now(clock), startAfterPayday).calc

    suspend fun paydayIsNear(): Boolean = tickets.paydayIsNear()

    /** Ends onboarding and prints the first real ticket (today's untouched OPEN row, if any, is replaced). */
    suspend fun finish(reminderHour: Int?, startAfterPayday: Boolean = false) {
        settings.setReminderHour(reminderHour)
        settings.setOnboarded(true)
        settings.setOnboardingStep(ONBOARDING_END_STEP)
        val today = LocalDate.now(clock)
        val start = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        db.withTransaction {
            ticketDao.forDate(today)
                ?.takeIf { it.status == TicketStatus.OPEN && entryDao.forDay(start, end).isEmpty() }
                ?.let { ticketDao.delete(it.id) }
        }
        tickets.printToday(startAfterPayday)
    }
}
