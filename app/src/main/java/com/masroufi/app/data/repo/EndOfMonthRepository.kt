package com.masroufi.app.data.repo

import com.masroufi.app.core.floorToPound
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.PeopleDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketCalc
import com.masroufi.app.domain.computeTicket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A reserved bill that falls due before payday: it stays outside the pool. */
data class BillDay(val date: LocalDate, val name: String, val amount: Long)

/** One small ticket of the strip; [bills] are the reserved bills due that day. */
data class SmallTicketDay(val date: LocalDate, val amount: Long, val bills: List<BillDay>)

/** An optional (non-essential) category and what it took this cycle. */
data class Habit(val categoryId: Long, val name: String, val spent: Long, val paused: Boolean, val icon: String = "")

data class OwedToMe(val personId: Long, val name: String, val amount: Long)

/** Where emergency money could come from; each is null at 0. */
data class RescueSources(val freeJar: Long?, val owedToMe: OwedToMe?, val emergency: Long?)

data class EndOfMonthState(
    /** Switched on by hand and still running. */
    val on: Boolean,
    /** Today's row is an end-of-month row (Spendable - Reserved <= 0): always "in the mode". */
    val inMode: Boolean,
    val payday: LocalDate,
    /** Spendable - Reserved, piastres (negative = short by that much). */
    val pool: Long,
    /** Days left including today. */
    val daysLeft: Int,
    /** The small ticket: floor(pool / daysLeft) to the pound; 0 when the pool is not positive. */
    val ticket: Long,
    val strip: List<SmallTicketDay>,
    val habits: List<Habit>,
    /** No-spend days closed by hand in the mode; the challenge is 2. */
    val noSpendDays: Int,
    /** What every remaining ticket gains from one more no-spend day: today's ticket / days after today. */
    val boost: Long,
    val rescue: RescueSources,
    /** Home shows the offer card. */
    val offer: Boolean,
)

/** The challenge is two no-spend days. */
const val NO_SPEND_GOAL = 2

/** Offered when today's ticket falls under this share of the cycle's average (percent). */
private const val OFFER_PERCENT = 60

/** ... with at least this many printed days to average. */
private const val OFFER_MIN_DAYS = 3

@Singleton
class EndOfMonthRepository @Inject constructor(
    private val settings: SettingsStore,
    private val tickets: TicketRepository,
    private val ticketDao: DayTicketDao,
    private val entryDao: EntryDao,
    private val categoryDao: CategoryDao,
    private val recurringDao: RecurringDao,
    private val pocketDao: PocketDao,
    private val jarDao: JarDao,
    private val peopleDao: PeopleDao,
    private val stamps: StampAwarder,
    private val clock: Clock,
) {
    private fun LocalDate.startMillis() = atStartOfDay(clock.zone).toInstant().toEpochMilli()

    /** Everything the screen and the Home offer need; re-emits when any source changes. */
    fun observe(): Flow<EndOfMonthState> = merge<Any>(
        settings.settings, ticketDao.countFlow(), entryDao.countFlow(), pocketDao.countingBalanceTotalFlow(),
        jarDao.total(Pot.JAR), jarDao.total(Pot.EMERGENCY), recurringDao.all(), peopleDao.debts(), categoryDao.all(),
    ).map { state() }.distinctUntilChanged()

    suspend fun state(): EndOfMonthState {
        val s = settings.current()
        val today = LocalDate.now(clock)
        val pool = tickets.poolToday(s, today)
        val cycle = pool.cycle
        val sr = pool.spendable - pool.reserved
        val daysLeft = pool.daysLeft
        val calc = computeTicket(pool.spendable, pool.reserved, 0, daysLeft)
        val ticket = (calc as? TicketCalc.Printed)?.amount ?: 0L
        val on = s.eomActiveOn(today, cycle.nextPayday)
        val todayRow = ticketDao.forDate(today)
        val bills = recurringDao.unpaidItemsDueBefore(cycle.nextPayday)
            .map { BillDay(it.nextDue.coerceAtLeast(today), it.name, it.amount) }
        val strip = generateSequence(today) { it.plusDays(1) }.takeWhile { it.isBefore(cycle.nextPayday) }
            .map { d -> SmallTicketDay(d, ticket, bills.filter { it.date == d }) }.toList()

        val dayTicket = if (on && todayRow != null && !todayRow.endOfMonth) todayRow.amount else ticket
        val after = daysLeft - 1
        val boost = if (after > 0) Math.floorDiv(dayTicket, after.toLong()).floorToPound() else 0L

        return EndOfMonthState(
            on = on,
            inMode = on || todayRow?.endOfMonth == true,
            payday = cycle.nextPayday,
            pool = sr,
            daysLeft = daysLeft,
            ticket = ticket,
            strip = strip,
            habits = habits(s, cycle.start, today),
            noSpendDays = if (on) s.eomNoSpend.size else 0,
            boost = boost,
            rescue = rescue(),
            offer = !on && !s.moneyUnknown && s.eomOfferDismissedFor != cycle.nextPayday.toEpochDay() &&
                shouldOffer(todayRow, ticket, cycle.start, today),
        )
    }

    /** The offer rule (decision 5): an end-of-month row, or a ticket under 60% of the cycle's average printed ticket (3+ days). */
    private suspend fun shouldOffer(todayRow: DayTicketEntity?, computed: Long, start: LocalDate, today: LocalDate): Boolean {
        if (todayRow?.endOfMonth == true) return true
        val printed = ticketDao.allNow().filter { !it.date.isBefore(start) && it.date.isBefore(today) && !it.endOfMonth }
        if (printed.size < OFFER_MIN_DAYS) return false
        val ticket = todayRow?.amount ?: computed
        // ticket < 60% of sum / count, without dividing.
        return ticket * 100 * printed.size < printed.sumOf { it.amount } * OFFER_PERCENT
    }

    /** The two non-essential categories with the highest spend this cycle (spend above 0). */
    private suspend fun habits(s: Settings, start: LocalDate, today: LocalDate): List<Habit> {
        val spend = entryDao.spentByCategory(start.startMillis(), today.plusDays(1).startMillis()).associate { it.categoryId to it.total }
        return categoryDao.all().first().filter { !it.essential && (spend[it.id] ?: 0L) > 0 }
            .sortedWith(compareByDescending<CategoryEntity> { spend[it.id] }.thenBy { it.id })
            .take(2).map { Habit(it.id, it.name, spend.getValue(it.id), it.id in s.eomPaused, it.icon) }
    }

    private suspend fun rescue(): RescueSources {
        val free = jarDao.rowsNow(Pot.JAR).free()
        val owed = peopleDao.openDebts().filter { it.direction == DebtDirection.THEY_OWE }
            .groupBy { it.personId }.mapValues { (_, d) -> d.sumOf { it.amount } }
            .filterValues { it > 0 }.entries.sortedWith(compareByDescending<Map.Entry<Long, Long>> { it.value }.thenBy { it.key }).firstOrNull()
        val person = owed?.let { peopleDao.personById(it.key) }
        return RescueSources(
            freeJar = free.takeIf { it > 0 },
            owedToMe = if (owed != null && person != null) OwedToMe(person.id, person.name, owed.value) else null,
            emergency = jarDao.totalNow(Pot.EMERGENCY).takeIf { it > 0 },
        )
    }

    /** Switches the mode on until the next payday; today's ticket is re-printed if it is still open. */
    suspend fun switchOn() {
        val s = settings.current()
        val today = LocalDate.now(clock)
        val cycle = tickets.poolToday(s, today).cycle
        settings.eomSwitchOn(today.toEpochDay(), cycle.nextPayday.toEpochDay())
        tickets.reprintToday()
    }

    suspend fun switchOff() {
        settings.eomSwitchOff()
        tickets.reprintToday()
    }

    suspend fun setPaused(categoryId: Long, paused: Boolean) = settings.eomSetPaused(categoryId, paused)

    /** Hides the offer card until the next payday. */
    suspend fun dismissOffer() {
        val s = settings.current()
        settings.eomDismissOffer(tickets.poolToday(s, LocalDate.now(clock)).cycle.nextPayday.toEpochDay())
    }

    /**
     * The mode ends by itself when its payday has come or the pay cycle changed (a not-fixed income). Returns the
     * settings as they are after that, so the caller does not read them twice.
     */
    internal suspend fun endIfCycleChanged(s: Settings): Settings {
        if (!s.eomOn) return s
        val today = LocalDate.now(clock)
        if (s.eomActiveOn(today, tickets.poolToday(s, today).cycle.nextPayday)) return s
        settings.eomSwitchOff()
        return settings.current()
    }

    /**
     * A day was just closed by hand while the mode was on: with no SPENT entry that day it is a no-spend day, and the
     * second earns tight fist. True only when that stamp is earned now (idempotent).
     */
    internal suspend fun onClosedByHand(date: LocalDate): Boolean {
        val spent = entryDao.forDay(date.startMillis(), date.plusDays(1).startMillis()).any { it.kind == EntryKind.SPENT }
        if (spent) return false
        settings.eomAddNoSpend(date.toEpochDay())
        return stamps.checkTightFist(settings.current().eomNoSpend.size)
    }
}

/** Plus rules (phase 10) read this: the mode is on by hand, or today's ticket is an end-of-month row. */
@Singleton
class EndOfMonthMode @Inject constructor(
    private val settings: SettingsStore,
    private val ticketDao: DayTicketDao,
    private val clock: Clock,
) {
    val isOn: Flow<Boolean> = combine(settings.settings, ticketDao.countFlow()) { s, _ -> s }
        .map { s ->
            val today = LocalDate.now(clock)
            s.eomActiveOn(today) || ticketDao.forDate(today)?.endOfMonth == true
        }
        .distinctUntilChanged()
}
