package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.masroufi.app.domain.EntryForDay
import com.masroufi.app.domain.StampKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class PocketBalance(val pocketId: Long, val balance: Long)

// Opening + received - spent - every fee - moved out + moved in. Round-ups are not deducted: the jar is not a pocket.
private const val BALANCE_EXPR = """p.openingBalance
    + COALESCE((SELECT SUM(e.amount) FROM entry e WHERE e.pocketId = p.id AND e.kind = 'RECEIVED'), 0)
    - COALESCE((SELECT SUM(e.amount) FROM entry e WHERE e.pocketId = p.id AND e.kind = 'SPENT'), 0)
    - COALESCE((SELECT SUM(e.fee) FROM entry e WHERE e.pocketId = p.id), 0)
    - COALESCE((SELECT SUM(e.amount) FROM entry e WHERE e.pocketId = p.id AND e.kind = 'MOVED'), 0)
    + COALESCE((SELECT SUM(e.amount) FROM entry e WHERE e.toPocketId = p.id AND e.kind = 'MOVED'), 0)"""

@Dao
interface PocketDao {
    @Insert suspend fun insert(pocket: PocketEntity): Long
    @Update suspend fun update(pocket: PocketEntity)
    @Query("SELECT * FROM pocket") fun all(): Flow<List<PocketEntity>>
    @Query("SELECT * FROM pocket WHERE id = :id") suspend fun byId(id: Long): PocketEntity?

    @Query("SELECT p.id AS pocketId, $BALANCE_EXPR AS balance FROM pocket p")
    fun balances(): Flow<List<PocketBalance>>

    // Primary currency only: the daily ticket is EGP.
    @Query("SELECT COALESCE(SUM($BALANCE_EXPR), 0) FROM pocket p WHERE p.countsInTicket = 1 AND p.archived = 0 AND p.currency = 'EGP'")
    suspend fun countingBalanceTotal(): Long
}

@Dao
interface EntryDao {
    @Insert suspend fun insert(entry: EntryEntity): Long
    @Update suspend fun update(entry: EntryEntity)
    @Query("DELETE FROM entry WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM entry WHERE id = :id") suspend fun byId(id: Long): EntryEntity?

    /** Epoch-millis bounds, [start, end). */
    @Query("SELECT * FROM entry WHERE happenedAt >= :start AND happenedAt < :end ORDER BY happenedAt")
    suspend fun forDay(start: Long, end: Long): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE happenedAt >= :start AND happenedAt < :end ORDER BY happenedAt")
    fun forDayFlow(start: Long, end: Long): Flow<List<EntryEntity>>

    /** A day's entries, countsInTicket = the pocket counts in the EGP ticket, for spentToday; [start, end) epoch millis. */
    @Query(
        """SELECT e.kind, e.amount, e.roundUp, (p.countsInTicket = 1 AND p.archived = 0 AND p.currency = 'EGP') AS countsInTicket,
        e.recurringId FROM entry e
        JOIN pocket p ON p.id = e.pocketId WHERE e.happenedAt >= :start AND e.happenedAt < :end"""
    )
    suspend fun forDayWithPocket(start: Long, end: Long): List<EntryForDay>

    /** Latest income into a counting pocket (anchors a not-fixed pay cycle). */
    @Query(
        """SELECT MAX(e.happenedAt) FROM entry e JOIN pocket p ON p.id = e.pocketId
        WHERE e.kind = 'RECEIVED' AND p.countsInTicket = 1 AND p.archived = 0 AND p.currency = 'EGP'"""
    )
    suspend fun latestReceivedAt(): Long?
}

@Dao
interface CategoryDao {
    @Insert suspend fun insert(category: CategoryEntity): Long
    @Update suspend fun update(category: CategoryEntity)
    @Query("SELECT * FROM category") fun all(): Flow<List<CategoryEntity>>
}

@Dao
interface DayTicketDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(ticket: DayTicketEntity): Long
    @Update suspend fun update(ticket: DayTicketEntity)
    @Query("SELECT * FROM day_ticket WHERE date = :date") suspend fun forDate(date: LocalDate): DayTicketEntity?

    @Query("SELECT * FROM day_ticket WHERE status = 'OPEN' AND date < :date ORDER BY date DESC LIMIT 1")
    suspend fun latestOpenBefore(date: LocalDate): DayTicketEntity?

    @Query("SELECT * FROM day_ticket WHERE status = 'OPEN' AND date < :date ORDER BY date")
    suspend fun openBefore(date: LocalDate): List<DayTicketEntity>

    @Query("SELECT * FROM day_ticket ORDER BY date") suspend fun allNow(): List<DayTicketEntity>

    @Query("SELECT * FROM day_ticket WHERE date >= :from AND date <= :to ORDER BY date")
    fun range(from: LocalDate, to: LocalDate): Flow<List<DayTicketEntity>>
}

@Dao
interface EnvelopeDao {
    @Insert suspend fun insert(envelope: EnvelopeEntity): Long
    @Update suspend fun update(envelope: EnvelopeEntity)
    @Query("SELECT * FROM envelope") fun all(): Flow<List<EnvelopeEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun link(link: EnvelopeCategoryEntity)
    @Query("SELECT * FROM envelope_category") fun links(): Flow<List<EnvelopeCategoryEntity>>
}

@Dao
interface RecurringDao {
    @Insert suspend fun insert(recurring: RecurringEntity): Long
    @Update suspend fun update(recurring: RecurringEntity)
    @Query("SELECT * FROM recurring") fun all(): Flow<List<RecurringEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM recurring WHERE paidThisCycle = 0 AND nextDue < :date")
    suspend fun unpaidDueBefore(date: LocalDate): Long
}

@Dao
interface JarDao {
    @Insert suspend fun insert(movement: JarMovementEntity): Long
    @Update suspend fun update(movement: JarMovementEntity)
    @Query("DELETE FROM jar_movement WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT COALESCE(SUM(amount), 0) FROM jar_movement WHERE pot = :pot") fun total(pot: Pot): Flow<Long>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM jar_movement WHERE pot = :pot") suspend fun totalNow(pot: Pot): Long
    @Query("SELECT * FROM jar_movement WHERE dayDate = :date") suspend fun forDay(date: LocalDate): List<JarMovementEntity>
    @Query("SELECT * FROM jar_movement WHERE entryId = :entryId") suspend fun forEntry(entryId: Long): List<JarMovementEntity>
}

@Dao
interface GoalDao {
    @Insert suspend fun insert(goal: GoalEntity): Long
    @Update suspend fun update(goal: GoalEntity)
    @Query("SELECT * FROM goal") fun all(): Flow<List<GoalEntity>>
}

@Dao
interface ProverbDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(collected: ProverbCollectedEntity)
    @Query("SELECT * FROM proverb_collected") fun all(): Flow<List<ProverbCollectedEntity>>
    @Query("SELECT number FROM proverb_collected") suspend fun numbers(): List<Int>
}

@Dao
interface StampDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(stamp: StampEntity)
    @Query("SELECT * FROM stamp") fun all(): Flow<List<StampEntity>>
    @Query("SELECT kind FROM stamp") suspend fun kinds(): List<StampKind>
}
