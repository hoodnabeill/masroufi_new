package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.DateWindow
import com.masroufi.app.core.newId
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.model.Allocation
import com.masroufi.app.data.model.Budget
import com.masroufi.app.data.model.BudgetPeriod
import com.masroufi.app.data.model.Goal
import com.masroufi.app.data.model.GoalEntry
import com.masroufi.app.data.model.GoalWithSaved
import com.masroufi.app.data.model.SplitItem
import com.masroufi.app.data.model.SplitRule
import com.masroufi.app.domain.BudgetProgress
import com.masroufi.app.domain.SplitResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class BudgetWithProgress(val budget: Budget, val progress: BudgetProgress, val window: DateWindow, val previousSpentMinor: Long?)

@Singleton
class BudgetRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.budgetDao()
    private val txDao = db.transactionDao()

    fun windowFor(budget: Budget, today: LocalDate): DateWindow = when (budget.period) {
        BudgetPeriod.MONTHLY -> DateWindow.month(YearMonth.from(today), clock.zone)
        BudgetPeriod.CUSTOM -> DateWindow.days(
            LocalDate.ofEpochDay(budget.startEpochDay),
            LocalDate.ofEpochDay(budget.endEpochDay ?: budget.startEpochDay),
            clock.zone,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWithProgress(): Flow<List<BudgetWithProgress>> = dao.observeAll().flatMapLatest { budgets ->
        if (budgets.isEmpty()) return@flatMapLatest flowOf(emptyList())
        val today = LocalDate.now(clock)
        combine(budgets.map { budget -> observeOne(budget, today) }) { it.toList() }
    }

    private fun observeOne(budget: Budget, today: LocalDate): Flow<BudgetWithProgress> {
        val window = windowFor(budget, today)
        val current = txDao.observeSpent(budget.categoryId, budget.currency, window.startMillis, window.endMillis)
        return if (budget.period == BudgetPeriod.MONTHLY) {
            val previousMonth = YearMonth.from(today).minusMonths(1)
            val prev = DateWindow.month(previousMonth, clock.zone)
            val previous = txDao.observeSpent(budget.categoryId, budget.currency, prev.startMillis, prev.endMillis)
            // Only roll over from a month the budget actually existed in.
            val existedLastMonth = budget.startEpochDay <= previousMonth.atEndOfMonth().toEpochDay()
            combine(current, previous) { spent, prevSpent ->
                val carriedFrom = if (existedLastMonth) prevSpent else null
                BudgetWithProgress(budget, BudgetProgress.of(budget.limitMinor, spent, carriedFrom, budget.rollover), window, carriedFrom)
            }
        } else {
            current.map { spent ->
                BudgetWithProgress(budget, BudgetProgress.of(budget.limitMinor, spent, null, false), window, null)
            }
        }
    }

    suspend fun get(id: String) = dao.get(id)

    suspend fun save(budget: Budget) {
        ensure(budget.limitMinor > 0, FinanceError.AMOUNT_REQUIRED)
        ensure(budget.name.isNotBlank(), FinanceError.NAME_REQUIRED)
        dao.upsert(budget.copy(name = budget.name.trim(), updatedAt = clock.millis()))
    }

    suspend fun delete(id: String) {
        val budget = dao.get(id) ?: return
        dao.upsert(budget.copy(deleted = true, updatedAt = clock.millis()))
    }
}

@Singleton
class GoalRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.goalDao()

    fun observeGoals(): Flow<List<GoalWithSaved>> = dao.observeWithSaved()
    fun observeGoal(id: String): Flow<GoalWithSaved?> = dao.observeOne(id)
    fun observeEntries(goalId: String): Flow<List<GoalEntry>> = dao.observeEntries(goalId)
    suspend fun get(id: String) = dao.get(id)

    suspend fun save(goal: Goal) {
        ensure(goal.name.isNotBlank(), FinanceError.NAME_REQUIRED)
        ensure(goal.targetMinor > 0, FinanceError.AMOUNT_REQUIRED)
        dao.upsert(goal.copy(name = goal.name.trim(), updatedAt = clock.millis()))
    }

    /** [amountMinor] positive contributes, negative withdraws; a withdrawal can never take the goal below zero. */
    suspend fun addEntry(goalId: String, amountMinor: Long, note: String) = db.withTransaction {
        ensure(amountMinor != 0L, FinanceError.AMOUNT_REQUIRED)
        if (amountMinor < 0) ensure(dao.saved(goalId) + amountMinor >= 0, FinanceError.WITHDRAW_EXCEEDS_SAVED)
        val now = clock.millis()
        dao.upsertEntry(GoalEntry(id = newId(), goalId = goalId, amountMinor = amountMinor, occurredAt = now, note = note.trim(), updatedAt = now))
    }

    suspend fun deleteEntry(entry: GoalEntry) = db.withTransaction {
        // Removing a withdrawal raises the balance, removing a contribution must not make it negative.
        if (entry.amountMinor > 0) ensure(dao.saved(entry.goalId) - entry.amountMinor >= 0, FinanceError.WITHDRAW_EXCEEDS_SAVED)
        dao.upsertEntry(entry.copy(deleted = true, updatedAt = clock.millis()))
    }

    suspend fun delete(id: String) {
        val goal = dao.get(id) ?: return
        dao.upsert(goal.copy(deleted = true, updatedAt = clock.millis()))
    }
}

@Singleton
class SplitRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val json: Json,
) {
    private val dao = db.splitDao()
    private val itemsSerializer = ListSerializer(SplitItem.serializer())

    fun observeRules(): Flow<List<SplitRule>> = dao.observeRules()
    fun observeAllocations(): Flow<List<Allocation>> = dao.observeAllocations()
    suspend fun get(id: String) = dao.get(id)

    fun items(rule: SplitRule): List<SplitItem> = runCatching { json.decodeFromString(itemsSerializer, rule.itemsJson) }.getOrDefault(emptyList())

    suspend fun save(id: String, name: String, currency: String, items: List<SplitItem>) {
        ensure(name.isNotBlank(), FinanceError.NAME_REQUIRED)
        ensure(items.isNotEmpty(), FinanceError.AMOUNT_REQUIRED)
        dao.upsert(SplitRule(id, name.trim(), currency, json.encodeToString(itemsSerializer, items), clock.millis()))
    }

    suspend fun delete(id: String) {
        val rule = dao.get(id) ?: return
        dao.upsert(rule.copy(deleted = true, updatedAt = clock.millis()))
    }

    /**
     * Applies a previewed split. [allocationId] is created with the preview, so a repeated tap rewrites the same
     * rows instead of allocating the income twice. Goal-linked lines become goal contributions.
     */
    suspend fun apply(allocationId: String, rule: SplitRule, incomeMinor: Long, result: SplitResult.Ok) = db.withTransaction {
        val now = clock.millis()
        dao.upsertAllocation(
            Allocation(
                id = allocationId,
                ruleId = rule.id,
                ruleName = rule.name,
                incomeMinor = incomeMinor,
                currency = rule.currency,
                detailJson = json.encodeToString(itemsSerializer, result.lines.map { it.item.copy(kind = it.item.kind, value = it.amountMinor) }),
                createdAt = now,
                updatedAt = now,
            ),
        )
        result.lines.forEachIndexed { index, line ->
            val goalId = line.item.goalId ?: return@forEachIndexed
            if (line.amountMinor <= 0) return@forEachIndexed
            db.goalDao().upsertEntry(
                GoalEntry(id = "$allocationId:$index", goalId = goalId, amountMinor = line.amountMinor, occurredAt = now, note = rule.name, updatedAt = now),
            )
        }
    }
}
