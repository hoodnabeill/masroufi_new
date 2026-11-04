package com.masroufi.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The four health factors, each 0..1 (README: health score = their average). */
data class HealthFactors(
    /** Days logged out of the last 14. */
    val logging: Float,
    /** 1 while spending stays at or under the safe pace, less the further above it. */
    val pace: Float,
    /** Emergency goal saved ÷ its target; 0 without one. */
    val emergency: Float,
    /** 1 once recurring bills are tracked. */
    val bills: Float,
) {
    val score: Int get() = (((logging + pace + emergency + bills) / 4f) * 100).toInt().coerceIn(0, 100)
}

/** End-of-month projection at the current pace. */
data class Forecast(
    /** Average daily spending this month, recurring bills left out. */
    val dailyPaceMinor: Long,
    /** The day the balance runs out at that pace; null when it lasts past the month (or nothing is spent). */
    val runOut: LocalDate?,
)

/** What the monthly recap story shows. */
data class RecapStats(
    val month: YearMonth,
    val spentMinor: Long,
    val perDayMinor: Long,
    /** The month's own daily number: its income spread over its days (or what was spent, without income). */
    val dailyNumberMinor: Long,
    val daysUnder: Int,
    val days: Int,
    val topCategoryId: String?,
    val topCategoryMinor: Long,
    val busiestWeekday: DayOfWeek?,
    /** (daily number × days) − spent, never below zero. */
    val savedMinor: Long,
)

object HealthMath {
    fun factors(logDays: Set<LocalDate>, today: LocalDate, dailyPaceMinor: Long, safeMinor: Long, emergencySaved: Long, emergencyTarget: Long, billsTracked: Boolean) = HealthFactors(
        logging = (0L until 14L).count { today.minusDays(it) in logDays } / 14f,
        pace = when {
            dailyPaceMinor <= 0 -> 1f
            safeMinor <= 0 -> 0f
            else -> (safeMinor.toFloat() / dailyPaceMinor).coerceIn(0f, 1f)
        },
        emergency = if (emergencyTarget > 0) (emergencySaved.toFloat() / emergencyTarget).coerceIn(0f, 1f) else 0f,
        bills = if (billsTracked) 1f else 0f,
    )

    /** README: pace = average daily non-bill spending; run-out = today + balance ÷ pace, if that falls this month. */
    fun forecast(balanceMinor: Long, nonBillSpentThisMonth: Long, today: LocalDate): Forecast {
        val pace = nonBillSpentThisMonth / today.dayOfMonth.coerceAtLeast(1)
        if (pace <= 0) return Forecast(0, null)
        val runOut = today.plusDays((balanceMinor.coerceAtLeast(0) / pace))
        return Forecast(pace, runOut.takeIf { !it.isAfter(today.withDayOfMonth(today.lengthOfMonth())) })
    }

    /**
     * shortcut: the month's daily number is its income ÷ days (what the user had to live on), since past balances
     * are not stored; upgrade once a daily balance history exists.
     */
    fun recap(month: YearMonth, dailyExpense: Map<LocalDate, Long>, byCategory: Map<String, Long>, incomeMinor: Long, throughDay: LocalDate): RecapStats {
        val last = minOf(month.atEndOfMonth(), throughDay)
        val days = last.dayOfMonth
        val spent = dailyExpense.filterKeys { YearMonth.from(it) == month }.values.sum()
        val daily = (if (incomeMinor > 0) incomeMinor else spent) / month.lengthOfMonth()
        val inMonth = (1..days).map { month.atDay(it) }
        val top = byCategory.maxByOrNull { it.value }
        val busiest = dailyExpense.filterKeys { YearMonth.from(it) == month }.entries
            .groupBy({ it.key.dayOfWeek }, { it.value })
            .maxByOrNull { (_, v) -> v.sum() }?.key
        return RecapStats(
            month = month,
            spentMinor = spent,
            perDayMinor = spent / days.coerceAtLeast(1),
            dailyNumberMinor = daily,
            daysUnder = inMonth.count { (dailyExpense[it] ?: 0) <= daily },
            days = days,
            topCategoryId = top?.key,
            topCategoryMinor = top?.value ?: 0,
            busiestWeekday = busiest,
            savedMinor = (daily * days - spent).coerceAtLeast(0),
        )
    }
}
