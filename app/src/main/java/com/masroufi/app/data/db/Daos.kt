package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.masroufi.app.domain.EntryForDay
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.StampKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class PocketBalance(val pocketId: Long, val balance: Long)

// Opening + received + adjustments and debts (signed) - spent - every fee - moved out + moved in. Round-ups are not deducted: the
// jar is not a pocket.
private const val BALANCE_EXPR = """p.openingBalance
    + COALESCE((SELECT SUM(e.amount) FROM entry e WHERE e.pocketId = p.id AND e.kind IN ('RECEIVED', 'ADJUST', 'DEBT')), 0)
    - COALESCE((SELECT SUM(e.amount) FROM entry e WHERE e.pocketId = p.id AND e.kind = 'SPENT'), 0)
    - COALESCE((SELECT SUM(e.fee) FROM entry e WHERE e.pocketId = p.id), 0)
    - COALESCE((SELECT SUM(e.amount) FROM entry e WHERE e.pocketId = p.id AND e.kind = 'MOVED'), 0)
    + COALESCE((SELECT SUM(e.amount) FROM entry e WHERE e.toPocketId = p.id AND e.kind = 'MOVED'), 0)"""

// Primary currency only: the daily ticket is EGP (SQL cannot use PRIMARY_CURRENCY; keep 'EGP' in sync).
private const val COUNTING_TOTAL =
    "SELECT COALESCE(SUM($BALANCE_EXPR), 0) FROM pocket p WHERE p.countsInTicket = 1 AND p.archived = 0 AND p.currency = 'EGP'"

/** An entry with the names the roll shows; countsInTicket as in forDayWithPocket. */
data class EntryRow(
    val id: Long,
    val kind: EntryKind,
    val amount: Long,
    val fee: Long,
    val roundUp: Long,
    val happenedAt: Long,
    val note: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val pocketId: Long,
    val pocketName: String,
    val currency: String,
    val countsInTicket: Boolean,
    val toPocketId: Long?,
    val toPocketName: String?,
    val recurringId: Long?,
)

private const val ENTRY_ROW = """SELECT e.id, e.kind, e.amount, e.fee, e.roundUp, e.happenedAt, e.note, e.categoryId,
    c.name AS categoryName, e.pocketId, p.name AS pocketName, p.currency AS currency,
    (p.countsInTicket = 1 AND p.archived = 0 AND p.currency = 'EGP') AS countsInTicket,
    e.toPocketId, tp.name AS toPocketName, e.recurringId
    FROM entry e JOIN pocket p ON p.id = e.pocketId LEFT JOIN category c ON c.id = e.categoryId
    LEFT JOIN pocket tp ON tp.id = e.toPocketId"""

data class PocketActivity(val pocketId: Long, val at: Long?)

/** A move's fee from a pocket; cashOut = the move went into a CASH pocket. */
data class FeeRow(val fee: Long, val cashOut: Boolean)

/** SPENT amounts (no fees, no round-ups) in a category over a range. */
data class CategorySpend(val categoryId: Long, val total: Long)

/** A recurring item with its pocket's name, for the fixed-costs list. */
data class RecurringRow(
    val id: Long,
    val name: String,
    val amount: Long,
    val kind: RecurringKind,
    val pocketId: Long,
    val pocketName: String,
    val nextDue: LocalDate,
    val envelopeId: Long?,
)

/** A repeated spend in the last days: same category, amount and pocket. */
data class RepeatRow(val categoryId: Long, val amount: Long, val pocketId: Long, val count: Int, val lastAt: Long)

@Dao
interface PocketDao {
    @Insert suspend fun insert(pocket: PocketEntity): Long
    @Update suspend fun update(pocket: PocketEntity)
    @Query("SELECT * FROM pocket") fun all(): Flow<List<PocketEntity>>
    @Query("SELECT * FROM pocket WHERE id = :id") suspend fun byId(id: Long): PocketEntity?
    @Query("SELECT COUNT(*) FROM pocket") suspend fun count(): Int

    /** The cash pocket: the lowest-id CASH pocket. */
    @Query("SELECT * FROM pocket WHERE kind = 'CASH' ORDER BY id LIMIT 1") suspend fun firstCash(): PocketEntity?

    @Query("SELECT $BALANCE_EXPR FROM pocket p WHERE p.id = :id") suspend fun balanceOf(id: Long): Long

    @Query("SELECT p.id AS pocketId, $BALANCE_EXPR AS balance FROM pocket p")
    fun balances(): Flow<List<PocketBalance>>

    @Query(COUNTING_TOTAL) suspend fun countingBalanceTotal(): Long
    @Query(COUNTING_TOTAL) fun countingBalanceTotalFlow(): Flow<Long>

    /** Latest entry from or into each pocket (null = none). */
    @Query(
        """SELECT p.id AS pocketId,
        (SELECT MAX(e.happenedAt) FROM entry e WHERE e.pocketId = p.id OR e.toPocketId = p.id) AS at FROM pocket p"""
    )
    fun lastActivity(): Flow<List<PocketActivity>>

    @Query("UPDATE pocket SET lastSmsBalance = :balance, lastSmsAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun recordSms(id: Long, balance: Long, at: Long)
}

@Dao
interface EntryDao {
    @Insert suspend fun insert(entry: EntryEntity): Long
    @Update suspend fun update(entry: EntryEntity)
    @Query("DELETE FROM entry WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM entry WHERE id = :id") suspend fun byId(id: Long): EntryEntity?

    /** Categories of SPENT entries, the most recently used first. */
    @Query(
        """SELECT categoryId FROM entry WHERE kind = 'SPENT' AND categoryId IS NOT NULL
        GROUP BY categoryId ORDER BY MAX(happenedAt) DESC"""
    )
    suspend fun categoriesByLastUse(): List<Long>

    /** The pocket of the most recent entry, if any. */
    @Query("SELECT pocketId FROM entry ORDER BY happenedAt DESC, id DESC LIMIT 1") suspend fun latestPocketId(): Long?

    /** Emits on every entry change; Home uses it to re-read today. */
    @Query("SELECT COUNT(*) FROM entry") fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM entry WHERE recurringId = :recurringId") suspend fun countAllForRecurring(recurringId: Long): Int

    /** Envelopes: SPENT amounts per category from EGP pockets (counting or not), [start, end) epoch millis. */
    @Query(
        """SELECT e.categoryId AS categoryId, SUM(e.amount) AS total FROM entry e JOIN pocket p ON p.id = e.pocketId
        WHERE e.kind = 'SPENT' AND e.isFee = 0 AND e.categoryId IS NOT NULL AND p.currency = 'EGP'
        AND e.happenedAt >= :start AND e.happenedAt < :end GROUP BY e.categoryId"""
    )
    suspend fun spentByCategory(start: Long, end: Long): List<CategorySpend>

    /** Epoch-millis bounds, [start, end). */
    @Query("SELECT * FROM entry WHERE happenedAt >= :start AND happenedAt < :end ORDER BY happenedAt")
    suspend fun forDay(start: Long, end: Long): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE happenedAt >= :start AND happenedAt < :end ORDER BY happenedAt")
    fun forDayFlow(start: Long, end: Long): Flow<List<EntryEntity>>

    /** A day's entries, countsInTicket = the pocket counts in the EGP ticket, for spentToday; [start, end) epoch millis. */
    @Query(
        """SELECT e.kind, e.amount, e.roundUp, e.fee, (p.countsInTicket = 1 AND p.archived = 0 AND p.currency = 'EGP') AS countsInTicket,
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

    /**
     * SPENT entries since `sinceMillis` repeated at least twice, most repeated first, then most recent; at most 3.
     * Only an existing category (so never null) and a pocket that exists and is not archived: a group that cannot
     * become a chip never takes one of the 3 places.
     */
    @Query(
        """SELECT categoryId, amount, pocketId, COUNT(*) AS count, MAX(happenedAt) AS lastAt FROM entry
        WHERE kind = 'SPENT' AND happenedAt >= :sinceMillis
        AND categoryId IN (SELECT id FROM category) AND pocketId IN (SELECT id FROM pocket WHERE archived = 0)
        GROUP BY categoryId, amount, pocketId HAVING COUNT(*) >= 2
        ORDER BY COUNT(*) DESC, MAX(happenedAt) DESC LIMIT 3"""
    )
    suspend fun repeatCandidates(sinceMillis: Long): List<RepeatRow>

    /** [start, end) epoch millis, newest first. */
    @Query("$ENTRY_ROW WHERE e.happenedAt >= :start AND e.happenedAt < :end ORDER BY e.happenedAt DESC, e.id DESC")
    fun rows(start: Long, end: Long): Flow<List<EntryRow>>

    @Query("$ENTRY_ROW ORDER BY e.happenedAt DESC, e.id DESC") fun allRows(): Flow<List<EntryRow>>

    /** The newest entries from or into a pocket. */
    @Query("$ENTRY_ROW WHERE e.pocketId = :pocketId OR e.toPocketId = :pocketId ORDER BY e.happenedAt DESC, e.id DESC LIMIT :limit")
    fun rowsForPocket(pocketId: Long, limit: Int): Flow<List<EntryRow>>

    @Query("SELECT happenedAt FROM entry") fun allHappenedAt(): Flow<List<Long>>

    /**
     * Everything leaving or entering the pocket in [start, end) epoch millis, plus the fees it paid. Adjustments are
     * not money moving.
     */
    @Query(
        """SELECT COALESCE(SUM(ABS(amount) + CASE WHEN pocketId = :pocketId THEN fee ELSE 0 END), 0) FROM entry
        WHERE (pocketId = :pocketId OR toPocketId = :pocketId) AND kind != 'ADJUST' AND happenedAt >= :start AND happenedAt < :end"""
    )
    suspend fun movedBetween(pocketId: Long, start: Long, end: Long): Long

    @Query(
        """SELECT e.fee, (tp.kind = 'CASH') AS cashOut FROM entry e JOIN pocket tp ON tp.id = e.toPocketId
        WHERE e.pocketId = :pocketId AND e.kind = 'MOVED' AND e.fee > 0 AND e.happenedAt >= :start AND e.happenedAt < :end"""
    )
    suspend fun moveFees(pocketId: Long, start: Long, end: Long): List<FeeRow>
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
    @Query("SELECT * FROM day_ticket WHERE date = :date") fun forDateFlow(date: LocalDate): Flow<DayTicketEntity?>

    /** Emits on every day_ticket change; Home uses it to re-read today. */
    @Query("SELECT COUNT(*) FROM day_ticket") fun countFlow(): Flow<Int>

    @Query("SELECT * FROM day_ticket WHERE status = 'OPEN' AND date < :date ORDER BY date")
    suspend fun openBefore(date: LocalDate): List<DayTicketEntity>

    @Query("SELECT * FROM day_ticket ORDER BY date") suspend fun allNow(): List<DayTicketEntity>
    @Query("SELECT * FROM day_ticket ORDER BY date") fun all(): Flow<List<DayTicketEntity>>
    @Query("SELECT COUNT(*) FROM day_ticket WHERE status = 'CLOSED_BY_HAND'") fun handClosedCount(): Flow<Int>

    @Query("SELECT * FROM day_ticket WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun latestBefore(date: LocalDate): DayTicketEntity?

    @Query("SELECT date FROM day_ticket") fun dates(): Flow<List<LocalDate>>

    /** Debug sample data only (re-printing today). */
    @Query("DELETE FROM day_ticket WHERE id = :id") suspend fun delete(id: Long)

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
    @Query("SELECT * FROM envelope WHERE id = :id") suspend fun byId(id: Long): EnvelopeEntity?
    @Query("SELECT COUNT(*) FROM envelope WHERE archived = 0") suspend fun activeCount(): Int
    @Query("DELETE FROM envelope_category WHERE envelopeId = :envelopeId") suspend fun unlinkAll(envelopeId: Long)

    @Insert suspend fun insertMove(move: EnvelopeMoveEntity): Long
    @Query("SELECT * FROM envelope_move") fun moves(): Flow<List<EnvelopeMoveEntity>>

    @Query("DELETE FROM split_rule") suspend fun clearSplit()
    @Insert suspend fun insertSplit(rules: List<SplitRuleEntity>)
    @Query("SELECT * FROM split_rule ORDER BY sortOrder") suspend fun splitRules(): List<SplitRuleEntity>
}

@Dao
interface RecurringDao {
    @Insert suspend fun insert(recurring: RecurringEntity): Long
    @Update suspend fun update(recurring: RecurringEntity)
    @Query("SELECT * FROM recurring") fun all(): Flow<List<RecurringEntity>>
    @Query("DELETE FROM recurring WHERE source = 'onboarding'") suspend fun deleteOnboarding()

    @Query("SELECT * FROM recurring") suspend fun allNow(): List<RecurringEntity>
    @Query("SELECT * FROM recurring WHERE id = :id") suspend fun byId(id: Long): RecurringEntity?
    @Query("DELETE FROM recurring WHERE id = :id") suspend fun delete(id: Long)

    @Query(
        """SELECT r.id, r.name, r.amount, r.kind, r.pocketId, p.name AS pocketName, r.nextDue, r.envelopeId
        FROM recurring r JOIN pocket p ON p.id = r.pocketId WHERE r.archived = 0 ORDER BY r.nextDue, r.id"""
    )
    fun rows(): Flow<List<RecurringRow>>

    /** Reserved bills (still due before [date], overdue included): only items paid from a pocket that punches the EGP ticket (same predicate as COUNTING_TOTAL). */
    @Query(
        """SELECT COALESCE(SUM(r.amount), 0) FROM recurring r JOIN pocket p ON p.id = r.pocketId
        WHERE r.archived = 0 AND r.nextDue < :date
        AND p.countsInTicket = 1 AND p.archived = 0 AND p.currency = 'EGP'"""
    )
    suspend fun unpaidDueBefore(date: LocalDate): Long

    /** The items behind [unpaidDueBefore], soonest first. */
    @Query(
        """SELECT r.* FROM recurring r JOIN pocket p ON p.id = r.pocketId
        WHERE r.archived = 0 AND r.nextDue < :date
        AND p.countsInTicket = 1 AND p.archived = 0 AND p.currency = 'EGP' ORDER BY r.nextDue, r.id"""
    )
    suspend fun unpaidItemsDueBefore(date: LocalDate): List<RecurringEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM recurring WHERE pocketId = :pocketId AND archived = 0 AND nextDue < :date")
    suspend fun unpaidFromPocketDueBefore(pocketId: Long, date: LocalDate): Long

    /** The soonest unpaid payment from the pocket due before [date]. */
    @Query("SELECT * FROM recurring WHERE pocketId = :pocketId AND archived = 0 AND nextDue < :date ORDER BY nextDue, id LIMIT 1")
    suspend fun nextUnpaidFromPocket(pocketId: Long, date: LocalDate): RecurringEntity?
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
    @Query("SELECT * FROM jar_movement WHERE pot = :pot ORDER BY at, id") fun rows(pot: Pot): Flow<List<JarMovementEntity>>
    @Query("SELECT * FROM jar_movement WHERE pot = :pot ORDER BY at, id") suspend fun rowsNow(pot: Pot): List<JarMovementEntity>
}

@Dao
interface GoalDao {
    @Insert suspend fun insert(goal: GoalEntity): Long
    @Update suspend fun update(goal: GoalEntity)
    @Query("SELECT * FROM goal") fun all(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goal WHERE id = :id") suspend fun byId(id: Long): GoalEntity?
    @Query("DELETE FROM goal WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface SeasonDao {
    @Insert suspend fun insert(season: SeasonEntity): Long
    @Update suspend fun update(season: SeasonEntity)
    @Query("SELECT * FROM season ORDER BY id") fun all(): Flow<List<SeasonEntity>>
    @Query("SELECT * FROM season ORDER BY id") suspend fun allNow(): List<SeasonEntity>
    @Query("SELECT * FROM season WHERE id = :id") suspend fun byId(id: Long): SeasonEntity?
    @Query("SELECT COUNT(*) FROM season") suspend fun count(): Int

    @Insert suspend fun insertItem(item: SeasonItemEntity): Long
    @Update suspend fun updateItem(item: SeasonItemEntity)
    @Query("DELETE FROM season_item WHERE id = :id") suspend fun deleteItem(id: Long)
    @Query("SELECT * FROM season_item ORDER BY id") fun items(): Flow<List<SeasonItemEntity>>
}

@Dao
interface ProverbDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(collected: ProverbCollectedEntity)
    @Query("SELECT * FROM proverb_collected") fun all(): Flow<List<ProverbCollectedEntity>>
    @Query("SELECT number FROM proverb_collected") suspend fun numbers(): List<Int>
    @Query("SELECT COUNT(*) FROM proverb_collected") fun countFlow(): Flow<Int>
}

@Dao
interface StampDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(stamp: StampEntity)
    @Query("SELECT * FROM stamp") fun all(): Flow<List<StampEntity>>
    @Query("SELECT kind FROM stamp") suspend fun kinds(): List<StampKind>
}
