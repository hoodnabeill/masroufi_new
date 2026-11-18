package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.PIASTRES
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** yyyymm as a Long, the month key the settings use. */
internal fun YearMonth.key(): Long = year * 100L + monthValue

/** One installment plan. [total], [left], [endMonth] and [progress] are null for an open-ended plan. */
data class InstallmentPlan(
    val item: RecurringEntity,
    val paid: Int,
    val total: Int?,
    val left: Int?,
    /** The month of the last payment: the month of the next due date plus (left - 1). Null once finished or open-ended. */
    val endMonth: YearMonth?,
    val progress: Float?,
    val finished: Boolean,
) {
    val monthly: Long get() = item.amount
    val remaining: Long get() = (left ?: 0) * item.amount
}

/** What a plan ending adds to the daily ticket: its monthly amount / 30, floored to a pound. */
data class TimelineStep(val planId: Long, val month: YearMonth, val growth: Long)

data class InstallmentsView(
    /** Every plan not deleted; finished ones last. */
    val plans: List<InstallmentPlan>,
    /** Sum of the active plans' monthly amounts (open-ended ones included). */
    val monthly: Long,
    /** Monthly / 30 rounded to the nearest pound (a tie goes down: 1,335 -> 44). */
    val perDay: Long,
    /** Sum of left x monthly over the plans that have a total. */
    val remaining: Long,
    /** The latest end month; null when no active plan has a total. */
    val freedom: YearMonth?,
    val timeline: List<TimelineStep>,
)

data class EarlyPayoff(val plan: InstallmentPlan, val amount: Long)

/** Ticket growth when a plan ends. */
fun ticketGrowth(monthly: Long): Long = monthly / (30 * PIASTRES) * PIASTRES

/** Monthly / 30 to the nearest pound; exactly half a pound rounds down. */
fun installmentPerDay(monthly: Long): Long = (monthly + 15 * PIASTRES - 1) / (30 * PIASTRES) * PIASTRES

@Singleton
class InstallmentRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val recurringDao: RecurringDao,
    private val entryDao: EntryDao,
    private val pocketDao: PocketDao,
    private val jarDao: JarDao,
    private val recurring: RecurringRepository,
    private val jar: JarRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun view(): InstallmentsView {
        val plans = recurringDao.allNow().filter { it.kind == RecurringKind.INSTALLMENT && !it.archived }.map { plan(it) }
            .sortedWith(compareBy({ it.finished }, { it.item.nextDue }, { it.item.id }))
        val active = plans.filter { !it.finished }
        val monthly = active.sumOf { it.monthly }
        val ending = active.filter { it.endMonth != null }
        return InstallmentsView(
            plans = plans,
            monthly = monthly,
            perDay = installmentPerDay(monthly),
            remaining = ending.sumOf { it.remaining },
            freedom = ending.maxOfOrNull { it.endMonth!! },
            timeline = ending.sortedBy { it.endMonth }.map { TimelineStep(it.item.id, it.endMonth!!, ticketGrowth(it.monthly)) },
        )
    }

    /** Re-reads on every plan and entry change. */
    fun observe(): Flow<InstallmentsView> = combine(recurringDao.all(), entryDao.countFlow()) { _, _ -> view() }

    private suspend fun plan(item: RecurringEntity): InstallmentPlan {
        val paid = item.paidBefore + entryDao.countAllForRecurring(item.id)
        val total = item.totalCount ?: return InstallmentPlan(item, paid, null, null, null, null, finished = false)
        val left = (total - paid).coerceAtLeast(0)
        return InstallmentPlan(
            item = item, paid = paid, total = total, left = left,
            endMonth = if (left > 0) YearMonth.from(item.nextDue).plusMonths(left - 1L) else null,
            progress = (paid.toFloat() / total).coerceIn(0f, 1f),
            finished = left == 0,
        )
    }

    /** The day of the month is the plan's due day; the first due date is that day this month if it is not past, else next month. */
    suspend fun add(
        name: String,
        monthly: Long,
        day: Int,
        pocketId: Long,
        totalCount: Int? = null,
        paidBefore: Int = 0,
        nextDue: LocalDate? = null,
    ): Long {
        require(name.isNotBlank()) { "a plan needs a name" }
        require(monthly > 0) { "monthly amount must be > 0" }
        require(day in 1..31) { "day must be 1..31" }
        checkCounts(totalCount, paidBefore)
        val today = LocalDate.now(clock)
        val due = nextDue ?: dueOn(YearMonth.from(today), day).let { if (it.isBefore(today)) dueOn(YearMonth.from(today).plusMonths(1), day) else it }
        return recurringDao.insert(
            RecurringEntity(
                name = name.trim(), amount = monthly, pocketId = pocketId, scheduleDay = day, nextDue = due,
                kind = RecurringKind.INSTALLMENT, totalCount = totalCount, paidBefore = paidBefore, updatedAt = clock.millis(),
            ),
        )
    }

    /** Changing the day moves the next due date to that day of its month. */
    suspend fun edit(id: Long, name: String, monthly: Long, day: Int, pocketId: Long, totalCount: Int?, paidBefore: Int) {
        require(name.isNotBlank()) { "a plan needs a name" }
        require(monthly > 0) { "monthly amount must be > 0" }
        require(day in 1..31) { "day must be 1..31" }
        checkCounts(totalCount, paidBefore)
        val item = checkNotNull(recurringDao.byId(id)) { "no plan $id" }
        recurring.update(
            item.copy(
                name = name.trim(), amount = monthly, scheduleDay = day, pocketId = pocketId, totalCount = totalCount,
                paidBefore = paidBefore, nextDue = dueOn(YearMonth.from(item.nextDue), day),
            ),
        )
    }

    /** Deletes the plan, or archives it while entries still pay it. */
    suspend fun delete(id: Long) = recurring.delete(id)

    private fun checkCounts(totalCount: Int?, paidBefore: Int) {
        require(totalCount == null || totalCount >= 1) { "total must be >= 1" }
        require(paidBefore >= 0 && (totalCount == null || paidBefore <= totalCount)) { "paid before must be 0..total" }
    }

    private fun dueOn(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))

    /**
     * Day-of logging, called by printToday: every active plan whose due date is today or earlier logs one payment from its
     * pocket through the ordinary recurring path (so the roll's undo, the count and the next due date follow); a plan
     * behind by several months catches up one payment per open, so a backlog is never logged in one go. A due date is
     * logged by itself once: undoing it brings the same due date back and it stays undone. A plan whose pocket is gone
     * is left alone.
     */
    suspend fun logDue(today: LocalDate) {
        val items = recurringDao.allNow().filter { it.kind == RecurringKind.INSTALLMENT && !it.archived && !it.nextDue.isAfter(today) }
        for (item in items) {
            if (plan(item).finished) continue
            if (settings.current().installmentLogged[item.id] == item.nextDue.toEpochDay()) continue
            if (pocketDao.byId(item.pocketId)?.archived != false) continue
            recurring.markPaid(item.id, item.pocketId)
            settings.setInstallmentLogged(item.id, item.nextDue.toEpochDay())
        }
    }

    /**
     * The plan with the least remaining (open-ended plans have none), when the free jar (not earmarked for a goal or a
     * season) covers it and its suggestion was not dismissed this month. Never changes anything.
     */
    suspend fun earlyPayoff(): EarlyPayoff? {
        val plan = view().plans.filter { !it.finished && it.total != null }.minByOrNull { it.remaining } ?: return null
        val month = YearMonth.now(clock).key()
        if (settings.current().earlyPayoffMonths[plan.item.id] == month) return null
        if (jarDao.rowsNow(Pot.JAR).free() < plan.remaining) return null
        return EarlyPayoff(plan, plan.remaining)
    }

    /** "كمّل عادي": hidden for the rest of the month. */
    suspend fun dismissEarlyPayoff(planId: Long) = settings.setEarlyPayoffMonth(planId, YearMonth.now(clock).key())

    /**
     * "سدّد": one entry against the plan for the confirmed [amount] (typed or confirmed by the user) from the plan's pocket and a
     * MANUAL withdrawal of the same amount from the free jar ([AboveFreeJar] when it is not there); the plan is finished.
     */
    suspend fun payOff(planId: Long, amount: Long) {
        require(amount > 0) { "amount must be > 0" }
        db.withTransaction {
            val item = checkNotNull(recurringDao.byId(planId)) { "no plan $planId" }
            val total = checkNotNull(item.totalCount) { "an open-ended plan cannot be paid off" }
            jar.withdraw(amount)
            recurring.markPaid(planId, item.pocketId, amount)
            val paid = item.paidBefore + entryDao.countAllForRecurring(planId)
            if (paid < total) recurringDao.update(checkNotNull(recurringDao.byId(planId)).copy(paidBefore = item.paidBefore + total - paid, updatedAt = clock.millis()))
        }
    }
}
