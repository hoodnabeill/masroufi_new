package com.masroufi.app.data.repo

import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketCalc
import com.masroufi.app.domain.TicketStatus
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface JarShare {
    fun of(salary: Long): Long

    data class Percent(val percent: Int) : JarShare {
        init {
            require(percent in 0..100) { "percent must be 0..100" }
        }

        override fun of(salary: Long) = salary * percent / 100
    }

    data class Amount(val amount: Long) : JarShare {
        init {
            require(amount >= 0) { "amount must be >= 0" }
        }

        override fun of(salary: Long) = minOf(amount, salary)
    }
}

/**
 * The ritual in its fixed order: jar share, the debts the user ticked, fixed costs still unpaid this cycle; what
 * is left goes to the tickets. [ticket] is today's ticket with the share reserved (0 at end of month); [lastCycleTicket]
 * is the first ordinary ticket of the previous cycle, for "أكتر من الشهر اللي فات بـ N".
 */
data class RitualFixed(val name: String, val day: Int, val amount: Long)

data class RitualView(
    val jarShare: Long,
    val debts: Long = 0,
    val fixed: Long,
    val forTickets: Long,
    val ticket: Long,
    val lastCycleTicket: Long?,
    val fixedItems: List<RitualFixed> = emptyList(),
    /** The tickets to print: today up to the day before [nextPayday]. */
    val days: Int = 0,
    val nextPayday: LocalDate? = null,
)

@Singleton
class PaydayRitualRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val pocketDao: PocketDao,
    private val recurringDao: RecurringDao,
    private val ticketDao: DayTicketDao,
    private val tickets: TicketRepository,
    private val jar: JarRepository,
    private val envelopes: EnvelopeRepository,
    private val people: PeopleRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    /**
     * Decision 5: an income into a counting pocket of at least half the usual salary; before the first ritual, one
     * that covers the unpaid fixed costs of its cycle (which must be > 0). Offered once per income.
     */
    suspend fun shouldOffer(entryId: Long): Boolean {
        val entry = entryDao.byId(entryId) ?: return false
        if (entry.kind != EntryKind.RECEIVED) return false
        if (pocketDao.byId(entry.pocketId)?.punchesTicket != true) return false
        val s = settings.current()
        if (s.lastRitualForEntryId == entryId) return false
        s.usualSalary?.let { return entry.amount * 2 >= it }
        val cycle = entryDao.payCycleOn(clock.dayOf(entry.happenedAt), s.payday, clock)
        val fixed = recurringDao.unpaidDueBefore(cycle.nextPayday)
        return fixed > 0 && entry.amount >= fixed
    }

    /** The user closed the ritual without printing: it is not offered again for this income. */
    suspend fun markOffered(entryId: Long) = settings.setLastRitualForEntryId(entryId)

    /** Step 2: what the user owes people (loans, not the tab); [apply] repays the ticked ones. */
    suspend fun debtsIOwe(): List<DebtWithPerson> = people.debtsIOwe()

    /** [repay]: ids of the debts the user ticked, each paid in full at apply time; they come off before the fixed costs. */
    suspend fun preview(salary: Long, share: JarShare, repay: Set<Long> = emptySet()): RitualView {
        require(salary > 0) { "salary must be > 0" }
        val s = settings.current()
        val today = LocalDate.now(clock)
        val jarShare = share.of(salary)
        val debts = people.debtsIOwe().filter { it.debt.id in repay }.sumOf { it.debt.amount }
        val cycle = entryDao.payCycleOn(today, s.payday, clock)
        val fixed = recurringDao.unpaidDueBefore(cycle.nextPayday)
        val calc = tickets.basisToday(s, today, startAfterPayday = false, extraReserved = jarShare + debts).calc
        val previous = entryDao.payCycleOn(cycle.start.minusDays(1), s.payday, clock)
        val lastTicket = ticketDao.range(previous.start, cycle.start.minusDays(1)).first().firstOrNull { !it.endOfMonth }?.amount
        return RitualView(
            jarShare = jarShare,
            debts = debts,
            fixed = fixed,
            forTickets = (salary - jarShare - debts - fixed).coerceAtLeast(0),
            ticket = (calc as? TicketCalc.Printed)?.amount ?: 0L,
            lastCycleTicket = lastTicket,
            fixedItems = recurringDao.unpaidItemsDueBefore(cycle.nextPayday).map { RitualFixed(it.name, it.nextDue.dayOfMonth, it.amount) },
            days = ChronoUnit.DAYS.between(today, cycle.nextPayday).toInt(),
            nextPayday = cycle.nextPayday,
        )
    }

    /**
     * "اطبع تذاكر الشهر": the jar share as a MANUAL movement, the ticked debts repaid in full from [repayPocketId] (DEBT
     * entries), the salary and share remembered, the saved envelope split applied to the salary, and today's ticket
     * re-printed when its open row has no punches yet.
     */
    suspend fun apply(
        salary: Long,
        share: JarShare,
        entryId: Long? = null,
        repay: Set<Long> = emptySet(),
        repayPocketId: Long? = null,
    ): RitualView {
        require(repay.isEmpty() || repayPocketId != null) { "ticked debts need a pocket to pay from" }
        val view = preview(salary, share, repay)
        if (view.jarShare > 0) jar.deposit(view.jarShare)
        people.debtsIOwe().filter { it.debt.id in repay }.forEach { people.repay(it.debt.id, it.debt.amount, repayPocketId) }
        settings.setRitualDone(salary, (share as? JarShare.Percent)?.percent)
        entryId?.let { settings.setLastRitualForEntryId(it) }
        envelopes.applySplit(salary)
        val row = ticketDao.forDate(LocalDate.now(clock))
        if (row != null && row.status == TicketStatus.OPEN && row.entryCount == 0) {
            ticketDao.delete(row.id)
            tickets.printToday()
        }
        return view
    }
}
