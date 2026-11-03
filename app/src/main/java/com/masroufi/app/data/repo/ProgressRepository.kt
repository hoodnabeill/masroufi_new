package com.masroufi.app.data.repo

import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.domain.Progress
import com.masroufi.app.domain.ProgressInput
import com.masroufi.app.domain.ProgressSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Streak, points, level and badges, recomputed from live data whenever it changes (see [Progress]). */
@Singleton
class ProgressRepository @Inject constructor(
    private val transactions: TransactionRepository,
    private val goals: GoalRepository,
    private val budgets: BudgetRepository,
    private val gameyas: GameyaRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private data class Activity(val logTimes: List<Long>, val expenseDays: Set<LocalDate>, val foodDays: Set<LocalDate>)

    private data class Plans(val budgets: Int, val emergencyGoal: Boolean, val gameyas: Int, val myPaid: Int)

    private val activity: Flow<Activity> = combine(
        transactions.observeLogTimes(),
        transactions.observeDailyExpense(0L),
        transactions.observeExpenseDays(FOOD_CATEGORY),
    ) { times, daily, food ->
        Activity(times, daily.mapTo(HashSet()) { LocalDate.parse(it.day) }, food.mapTo(HashSet()) { LocalDate.parse(it) })
    }

    private val plans: Flow<Plans> = combine(
        budgets.observeWithProgress(),
        goals.observeGoals(),
        gameyas.observeAll(),
        gameyas.observeAllMembers(),
        gameyas.observeAllPayments(),
    ) { b, g, list, members, payments ->
        val mine = members.filter { it.isMe }.mapTo(HashSet()) { it.id }
        Plans(
            budgets = b.size,
            // The emergency template names the goal "فلوس الطوارئ"; a user-made one usually says طوارئ or emergency.
            emergencyGoal = g.any { it.goal.name.contains("طوارئ") || it.goal.name.contains("emergency", ignoreCase = true) },
            gameyas = list.size,
            myPaid = payments.count { it.memberId in mine },
        )
    }

    fun observe(): Flow<ProgressSnapshot> = combine(
        activity,
        plans,
        transactions.observeMonthlyTotals(0L),
        settings.settings,
    ) { a, p, monthly, s ->
        val today = LocalDate.now(clock)
        val days = a.logTimes.mapTo(HashSet()) { it.toLocalDate(clock.zone) }
        val months = monthly.filter { it.currency == s.primaryCurrency }
            .groupBy { YearMonth.parse(it.month) }
            .mapValues { (_, rows) ->
                rows.filter { it.type == TransactionType.INCOME }.sumOf { it.total } to rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.total }
            }
        Progress.snapshot(
            ProgressInput(
                logDays = days,
                entries = a.logTimes.size,
                entriesToday = a.logTimes.count { it.toLocalDate(clock.zone) == today },
                expenseDays = a.expenseDays,
                foodDays = a.foodDays,
                activeBudgets = p.budgets,
                emergencyGoal = p.emergencyGoal,
                gameyas = p.gameyas,
                gameyaPaid = p.myPaid,
                greenMonth = Progress.hasGreenMonth(months, today),
                onboardingDone = s.onboardingDone,
            ),
            today,
        )
    }

    private companion object {
        const val FOOD_CATEGORY = "sys_food"
    }
}
