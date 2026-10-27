package com.masroufi.app.domain

import java.time.LocalDate

sealed interface Insight {
    /** Factual: compares this month so far against the same days of last month. */
    data class SpendingPace(val currentMinor: Long, val previousSameDaysMinor: Long, val currency: String) : Insight
    data class CategoryRising(val categoryId: String, val currentMinor: Long, val previousMinor: Long, val percent: Int, val currency: String) : Insight
    data class UpcomingRecurring(val note: String, val amountMinor: Long, val currency: String, val date: LocalDate) : Insight
    data class BudgetAlert(val budgetName: String, val status: BudgetStatus, val remainingMinor: Long, val currency: String) : Insight
    /** Estimate: a linear projection, only produced with enough history. */
    data class MonthEndProjection(val projectedMinor: Long, val currency: String) : Insight
}

data class InsightInput(
    val today: LocalDate,
    val currency: String,
    val currentByCategory: Map<String?, Long>,
    val previousSameDaysByCategory: Map<String?, Long>,
    val monthsWithHistory: Int,
    val upcoming: List<Insight.UpcomingRecurring>,
    val budgetAlerts: List<Insight.BudgetAlert>,
)

object InsightEngine {
    private const val RISE_THRESHOLD_PERCENT = 25
    private const val MIN_PREVIOUS_MINOR = 10_000L
    private const val MIN_DAYS_FOR_PROJECTION = 7
    private const val MIN_MONTHS_FOR_PROJECTION = 2

    fun generate(input: InsightInput): List<Insight> {
        val out = mutableListOf<Insight>()
        out += input.budgetAlerts.filter { it.status != BudgetStatus.ON_TRACK }
        out += input.upcoming.sortedBy { it.date }.take(3)

        val current = input.currentByCategory.values.sum()
        val previous = input.previousSameDaysByCategory.values.sum()
        if (previous > 0 || current > 0) {
            out += Insight.SpendingPace(current, previous, input.currency)
        }

        input.currentByCategory
            .mapNotNull { (categoryId, amount) ->
                val before = input.previousSameDaysByCategory[categoryId] ?: 0
                if (categoryId == null || before < MIN_PREVIOUS_MINOR) return@mapNotNull null
                val percent = ((amount - before) * 100 / before).toInt()
                if (percent >= RISE_THRESHOLD_PERCENT) Insight.CategoryRising(categoryId, amount, before, percent, input.currency) else null
            }
            .maxByOrNull { it.percent }
            ?.let { out += it }

        val day = input.today.dayOfMonth
        if (input.monthsWithHistory >= MIN_MONTHS_FOR_PROJECTION && day >= MIN_DAYS_FOR_PROJECTION && current > 0) {
            val length = input.today.lengthOfMonth()
            out += Insight.MonthEndProjection(current * length / day, input.currency)
        }
        return out
    }
}
