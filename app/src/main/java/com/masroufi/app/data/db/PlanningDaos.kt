package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.masroufi.app.data.model.Allocation
import com.masroufi.app.data.model.Budget
import com.masroufi.app.data.model.Goal
import com.masroufi.app.data.model.GoalEntry
import com.masroufi.app.data.model.GoalWithSaved
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.SplitRule
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE deleted = 0")
    suspend fun active(): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun get(id: String): Budget?

    @Upsert
    suspend fun upsert(budget: Budget)

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<Budget>

    @Upsert
    suspend fun upsertAll(items: List<Budget>)

    @Query("DELETE FROM budgets")
    suspend fun clear()
}

@Dao
interface GoalDao {
    @Query(
        """
        SELECT g.*, COALESCE((SELECT SUM(e.amountMinor) FROM goal_entries e WHERE e.goalId = g.id AND e.deleted = 0), 0) AS savedMinor
        FROM goals g WHERE g.deleted = 0 ORDER BY g.archived, g.createdAt
        """,
    )
    fun observeWithSaved(): Flow<List<GoalWithSaved>>

    @Query(
        """
        SELECT g.*, COALESCE((SELECT SUM(e.amountMinor) FROM goal_entries e WHERE e.goalId = g.id AND e.deleted = 0), 0) AS savedMinor
        FROM goals g WHERE g.id = :id
        """,
    )
    fun observeOne(id: String): Flow<GoalWithSaved?>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM goal_entries WHERE goalId = :goalId AND deleted = 0")
    suspend fun saved(goalId: String): Long

    @Query("SELECT * FROM goal_entries WHERE goalId = :goalId AND deleted = 0 ORDER BY occurredAt DESC")
    fun observeEntries(goalId: String): Flow<List<GoalEntry>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: String): Goal?

    @Upsert
    suspend fun upsert(goal: Goal)

    @Upsert
    suspend fun upsertEntry(entry: GoalEntry)

    @Query("SELECT * FROM goals")
    suspend fun all(): List<Goal>

    @Query("SELECT * FROM goal_entries")
    suspend fun allEntries(): List<GoalEntry>

    @Upsert
    suspend fun upsertAll(items: List<Goal>)

    @Upsert
    suspend fun upsertAllEntries(items: List<GoalEntry>)

    @Query("DELETE FROM goals")
    suspend fun clear()

    @Query("DELETE FROM goal_entries")
    suspend fun clearEntries()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring WHERE deleted = 0 ORDER BY active DESC, nextEpochDay")
    fun observeAll(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring WHERE deleted = 0 AND active = 1")
    suspend fun active(): List<RecurringRule>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: String): RecurringRule?

    @Upsert
    suspend fun upsert(rule: RecurringRule)

    @Query("SELECT * FROM recurring")
    suspend fun all(): List<RecurringRule>

    @Upsert
    suspend fun upsertAll(items: List<RecurringRule>)

    @Query("DELETE FROM recurring")
    suspend fun clear()
}

@Dao
interface SplitDao {
    @Query("SELECT * FROM split_rules WHERE deleted = 0 ORDER BY name")
    fun observeRules(): Flow<List<SplitRule>>

    @Query("SELECT * FROM split_rules WHERE id = :id")
    suspend fun get(id: String): SplitRule?

    @Query("SELECT * FROM allocations WHERE deleted = 0 ORDER BY createdAt DESC LIMIT 50")
    fun observeAllocations(): Flow<List<Allocation>>

    @Upsert
    suspend fun upsert(rule: SplitRule)

    @Upsert
    suspend fun upsertAllocation(allocation: Allocation)

    @Query("SELECT * FROM split_rules")
    suspend fun all(): List<SplitRule>

    @Query("SELECT * FROM allocations")
    suspend fun allAllocations(): List<Allocation>

    @Upsert
    suspend fun upsertAll(items: List<SplitRule>)

    @Upsert
    suspend fun upsertAllAllocations(items: List<Allocation>)

    @Query("DELETE FROM split_rules")
    suspend fun clear()

    @Query("DELETE FROM allocations")
    suspend fun clearAllocations()
}
