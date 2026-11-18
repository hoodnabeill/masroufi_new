package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface StoryDao {
    /** Returns -1 when a story for that cycle exists already (the unique index): a build is idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(story: MonthStoryEntity): Long
    @Query("SELECT * FROM month_story WHERE cycleStart = :start") suspend fun byCycleStart(start: LocalDate): MonthStoryEntity?
    @Query("SELECT * FROM month_story WHERE id = :id") suspend fun byId(id: Long): MonthStoryEntity?
    @Query("SELECT * FROM month_story ORDER BY cycleStart DESC") fun all(): Flow<List<MonthStoryEntity>>

    /** The newest story never opened. */
    @Query("SELECT * FROM month_story WHERE shownAt IS NULL ORDER BY cycleStart DESC LIMIT 1") fun unseen(): Flow<MonthStoryEntity?>
    @Query("UPDATE month_story SET shownAt = :at WHERE id = :id AND shownAt IS NULL") suspend fun markShown(id: Long, at: Long)
}

@Dao
interface KidsDao {
    @Insert suspend fun insertChild(child: ChildEntity): Long
    @Update suspend fun updateChild(child: ChildEntity)
    @Query("SELECT * FROM child WHERE archived = 0 ORDER BY id") fun children(): Flow<List<ChildEntity>>
    @Query("SELECT * FROM child WHERE archived = 0 ORDER BY id") suspend fun childrenNow(): List<ChildEntity>
    @Query("SELECT * FROM child WHERE id = :id") suspend fun childById(id: Long): ChildEntity?

    @Insert suspend fun insertChore(chore: ChoreEntity): Long
    @Update suspend fun updateChore(chore: ChoreEntity)
    @Query("DELETE FROM chore WHERE id = :id") suspend fun deleteChore(id: Long)
    @Query("DELETE FROM chore_tick WHERE choreId = :choreId") suspend fun deleteTicksOfChore(choreId: Long)
    @Query("SELECT * FROM chore ORDER BY id") fun chores(): Flow<List<ChoreEntity>>
    @Query("SELECT * FROM chore ORDER BY id") suspend fun choresNow(): List<ChoreEntity>
    @Query("SELECT * FROM chore WHERE id = :id") suspend fun choreById(id: Long): ChoreEntity?

    @Insert suspend fun insertTick(tick: ChoreTickEntity): Long
    @Query("DELETE FROM chore_tick WHERE id = :id") suspend fun deleteTick(id: Long)
    @Query("SELECT * FROM chore_tick") fun ticks(): Flow<List<ChoreTickEntity>>
    @Query("SELECT * FROM chore_tick WHERE weekStart = :week") suspend fun ticksIn(week: LocalDate): List<ChoreTickEntity>

    @Insert suspend fun insertAdvance(advance: ChildAdvanceEntity): Long
    @Update suspend fun updateAdvance(advance: ChildAdvanceEntity)
    @Query("SELECT * FROM child_advance WHERE id = :id") suspend fun advanceById(id: Long): ChildAdvanceEntity?
    @Query("SELECT * FROM child_advance") fun advances(): Flow<List<ChildAdvanceEntity>>
    @Query("SELECT * FROM child_advance WHERE weekStart = :week") suspend fun advancesIn(week: LocalDate): List<ChildAdvanceEntity>

    /** A change of handover day or a handover moves the week key: the open week's rows follow it. */
    @Query("UPDATE chore_tick SET weekStart = :to WHERE weekStart = :from") suspend fun rekeyTicks(from: LocalDate, to: LocalDate)
    @Query("UPDATE child_advance SET weekStart = :to WHERE weekStart = :from AND answer = 'WAITING'") suspend fun rekeyWaiting(from: LocalDate, to: LocalDate)
    @Query("UPDATE child_advance SET weekStart = :to WHERE weekStart = :from") suspend fun rekeyAdvances(from: LocalDate, to: LocalDate)

    @Insert suspend fun insertJarMove(move: ChildJarMoveEntity): Long
    @Query("SELECT * FROM child_jar_move ORDER BY at, id") fun jarMoves(): Flow<List<ChildJarMoveEntity>>
    @Query("SELECT * FROM child_jar_move WHERE childId = :childId ORDER BY at, id") suspend fun jarMovesOf(childId: Long): List<ChildJarMoveEntity>

    @Insert suspend fun insertSpend(spend: ChildSpendEntity): Long
    @Query("SELECT * FROM child_spend ORDER BY at, id") fun spends(): Flow<List<ChildSpendEntity>>
    @Query("SELECT * FROM child_spend WHERE childId = :childId AND at >= :since") suspend fun spendsSince(childId: Long, since: Long): List<ChildSpendEntity>
}
