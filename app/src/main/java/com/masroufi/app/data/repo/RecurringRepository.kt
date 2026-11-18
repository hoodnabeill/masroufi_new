package com.masroufi.app.data.repo

import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.RecurringRow
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.domain.EntryKind
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The due date [periods] schedule periods from the item's own (negative = back). A monthly item keeps its
 * scheduleDay, clamped to short months (31 -> 30 Nov -> 31 Dec); a weekly one moves 7 days a period.
 */
internal fun RecurringEntity.shiftedDue(periods: Long): LocalDate = when (scheduleType) {
    ScheduleType.MONTHLY -> YearMonth.from(nextDue).plusMonths(periods).let { it.atDay(minOf(scheduleDay, it.lengthOfMonth())) }
    ScheduleType.YEARLY -> nextDue.plusYears(periods)
    ScheduleType.WEEKLY -> nextDue.plusWeeks(periods)
}

@Singleton
class RecurringRepository @Inject constructor(
    private val recurringDao: RecurringDao,
    private val entryDao: EntryDao,
    // Provider: EntryRepository -> TicketRepository -> this would be a cycle.
    private val entries: Provider<EntryRepository>,
    private val clock: Clock,
) {
    /** Active items with their pocket's name, soonest due first. */
    fun observe(): Flow<List<RecurringRow>> = recurringDao.rows()

    suspend fun add(
        name: String,
        amount: Long,
        pocketId: Long,
        nextDue: LocalDate,
        kind: RecurringKind = kindOfFixed(name),
        scheduleType: ScheduleType = ScheduleType.MONTHLY,
        envelopeId: Long? = null,
    ): Long {
        require(amount > 0) { "amount must be > 0" }
        return recurringDao.insert(
            RecurringEntity(
                name = name, amount = amount, pocketId = pocketId, scheduleDay = nextDue.dayOfMonth, nextDue = nextDue,
                envelopeId = envelopeId, kind = kind, scheduleType = scheduleType, updatedAt = clock.millis(),
            ),
        )
    }

    suspend fun get(id: Long): RecurringEntity? = recurringDao.byId(id)

    suspend fun update(item: RecurringEntity) {
        require(item.amount > 0) { "amount must be > 0" }
        recurringDao.update(item.copy(updatedAt = clock.millis()))
    }

    /** Deletes the item, or archives it while entries still pay it (they keep their link). */
    suspend fun delete(id: Long) {
        val item = recurringDao.byId(id) ?: return
        if (entryDao.countAllForRecurring(id) == 0) {
            recurringDao.delete(id)
        } else {
            recurringDao.update(item.copy(archived = true, updatedAt = clock.millis()))
        }
    }

    /** Logs the payment (a SPENT entry paying the item: reserved, so it never punches) and so moves its due date on one period. */
    suspend fun markPaid(id: Long, pocketId: Long, amount: Long? = null): Long {
        val item = checkNotNull(recurringDao.byId(id)) { "no recurring item $id" }
        return entries.get().log(
            NewEntry(kind = EntryKind.SPENT, amount = amount ?: item.amount, pocketId = pocketId, happenedAt = clock.millis(), recurringId = id),
        )
    }
}
