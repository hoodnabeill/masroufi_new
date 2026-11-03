package com.masroufi.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** One budget the Plan tab proposes: a category, a monthly limit, and what was already spent this month. */
data class BudgetSuggestion(val categoryId: String, val limitMinor: Long, val spentMinor: Long)

object BudgetPlan {
    /** Rounds up to a figure people actually budget with: 50s under 1,000, 100s above. [unit] is minor units per pound. */
    fun niceLimit(minor: Long, unit: Long): Long {
        if (minor <= 0) return 0
        val whole = (minor + unit - 1) / unit
        val step = if (whole < 1_000) 50 else 100
        return (whole + step - 1) / step * step * unit
    }

    /**
     * "مصروفي جهّزلك خطة": the categories the user spends most on, each capped at its last 30 days (or this month, if
     * that is already higher), rounded up. Categories that already have a budget are skipped.
     */
    fun suggest(recent: Map<String, Long>, thisMonth: Map<String, Long>, budgeted: Set<String>, unit: Long, count: Int = 3): List<BudgetSuggestion> =
        (recent.keys + thisMonth.keys)
            .filter { it !in budgeted }
            .map { id -> BudgetSuggestion(id, niceLimit(maxOf(recent[id] ?: 0, thisMonth[id] ?: 0), unit), thisMonth[id] ?: 0) }
            .filter { it.limitMinor > 0 }
            .sortedByDescending { it.limitMinor }
            .take(count)
}

object GoalBoost {
    private const val DAYS_PER_MONTH = 30.44

    /** README: months left = remaining ÷ (monthly + extraPerDay × 30). Null when nothing is being saved. */
    fun finish(remainingMinor: Long, monthlyMinor: Long, extraPerDayMinor: Long, today: LocalDate): LocalDate? {
        if (remainingMinor <= 0) return today
        val rate = monthlyMinor + extraPerDayMinor * 30
        if (rate <= 0) return null
        return today.plusDays(ceil(remainingMinor.toDouble() / rate * DAYS_PER_MONTH).toLong())
    }

    /** What has to be put aside each month to reach [remainingMinor] by [deadline]; null once the deadline has passed. */
    fun monthlyFor(remainingMinor: Long, today: LocalDate, deadline: LocalDate): Long? {
        val days = ChronoUnit.DAYS.between(today, deadline)
        if (days <= 0) return null
        if (remainingMinor <= 0) return 0
        return ceil(remainingMinor / (days / DAYS_PER_MONTH)).toLong()
    }

    /**
     * Ahead of plan when the saved share is at least the share of time gone between [start] and [deadline]; null
     * without a deadline, since there is no plan to compare with.
     */
    fun onTrack(savedMinor: Long, targetMinor: Long, start: LocalDate, deadline: LocalDate?, today: LocalDate): Boolean? {
        if (deadline == null || targetMinor <= 0) return null
        val total = ChronoUnit.DAYS.between(start, deadline).coerceAtLeast(1)
        val gone = ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
        return savedMinor * total >= targetMinor * gone
    }
}
