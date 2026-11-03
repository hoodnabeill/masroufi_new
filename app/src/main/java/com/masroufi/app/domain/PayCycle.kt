package com.masroufi.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Salary-to-salary month: most Egyptian salaries land on a fixed day, so "this month" runs from one payday to the
 * next instead of the 1st to the 31st. [day] is 1–31; a month shorter than that pays on its last day.
 */
object PayCycle {
    fun isSet(day: Int): Boolean = day in 1..31

    fun paydayIn(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** The cycle containing [today]: its first day (the last payday, inclusive) and the next payday (exclusive). */
    fun current(today: LocalDate, day: Int): Pair<LocalDate, LocalDate> {
        val month = YearMonth.from(today)
        val thisMonths = paydayIn(month, day)
        val start = if (today.isBefore(thisMonths)) paydayIn(month.minusMonths(1), day) else thisMonths
        return start to paydayIn(YearMonth.from(start).plusMonths(1), day)
    }

    /** Whole days from today until the next salary; on payday itself the new cycle has just begun. */
    fun daysUntilPayday(today: LocalDate, day: Int): Int = ChronoUnit.DAYS.between(today, current(today, day).second).toInt()

    /** Days until the next income, today included: the payday when one is set, otherwise the end of the month. */
    fun daysUntilNextIncome(today: LocalDate, day: Int): Int =
        if (isSet(day)) daysUntilPayday(today, day) else today.lengthOfMonth() - today.dayOfMonth + 1

    /**
     * "تقدر تصرف النهارده": the balance as it stood this morning (today's spending added back) spread over the days
     * until the next income. Using the morning balance keeps today's limit fixed while the user logs against it.
     */
    fun safeToday(balanceMinor: Long, spentTodayMinor: Long, today: LocalDate, day: Int): Long =
        ((balanceMinor + spentTodayMinor) / daysUntilNextIncome(today, day)).coerceAtLeast(0)

    /** What can be spent per day, today included, so the balance lasts until payday. Never negative. */
    fun safePerDay(balanceMinor: Long, today: LocalDate, day: Int): Long =
        (balanceMinor / daysUntilPayday(today, day).coerceAtLeast(1)).coerceAtLeast(0)
}
