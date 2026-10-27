package com.masroufi.app.domain

import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

object Recurrence {
    private const val MAX_STEPS = 20_000

    /**
     * Occurrences are computed from the start date by index rather than by chaining, so a rule that
     * starts on the 31st lands on the last day of shorter months and returns to the 31st afterwards.
     */
    fun occurrence(start: LocalDate, frequency: Frequency, interval: Int, index: Int): LocalDate {
        val step = index.toLong() * interval.coerceAtLeast(1)
        return when (frequency) {
            Frequency.DAILY -> start.plusDays(step)
            Frequency.WEEKLY -> start.plusWeeks(step)
            Frequency.MONTHLY -> start.plusMonths(step)
            Frequency.YEARLY -> start.plusYears(step)
        }
    }

    /** Occurrences in (afterExclusive, upToInclusive], bounded by [end]. */
    fun due(
        start: LocalDate,
        frequency: Frequency,
        interval: Int,
        end: LocalDate?,
        afterExclusive: LocalDate?,
        upToInclusive: LocalDate,
    ): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        for (i in 0 until MAX_STEPS) {
            val date = occurrence(start, frequency, interval, i)
            if (date.isAfter(upToInclusive) || (end != null && date.isAfter(end))) break
            if (afterExclusive == null || date.isAfter(afterExclusive)) result += date
        }
        return result
    }

    fun nextAfter(start: LocalDate, frequency: Frequency, interval: Int, end: LocalDate?, after: LocalDate): LocalDate? {
        for (i in 0 until MAX_STEPS) {
            val date = occurrence(start, frequency, interval, i)
            if (end != null && date.isAfter(end)) return null
            if (date.isAfter(after)) return date
        }
        return null
    }
}
