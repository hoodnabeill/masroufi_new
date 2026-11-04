package com.masroufi.app.domain

import com.masroufi.app.data.model.Frequency
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Instalment arithmetic for recurring rules: an instalment plan is simply a rule with an end date. */
object Schedule {
    private fun unit(frequency: Frequency): ChronoUnit = when (frequency) {
        Frequency.DAILY -> ChronoUnit.DAYS
        Frequency.WEEKLY -> ChronoUnit.WEEKS
        Frequency.MONTHLY -> ChronoUnit.MONTHS
        Frequency.YEARLY -> ChronoUnit.YEARS
    }

    /** The date of the [count]-th payment, the first being on [start]. */
    fun endFor(start: LocalDate, count: Int, frequency: Frequency, interval: Int): LocalDate =
        start.plus(((count.coerceAtLeast(1) - 1) * interval.coerceAtLeast(1)).toLong(), unit(frequency))

    /** Payments from [start] to [day] inclusive (0 before the start). */
    fun countThrough(start: LocalDate, day: LocalDate, frequency: Frequency, interval: Int): Int {
        if (day.isBefore(start)) return 0
        return (unit(frequency).between(start, day) / interval.coerceAtLeast(1)).toInt() + 1
    }

    /** What a rule costs per month, so daily, weekly and yearly items add up in one monthly total. */
    fun monthlyMinor(amountMinor: Long, frequency: Frequency, interval: Int): Long {
        val every = interval.coerceAtLeast(1)
        return when (frequency) {
            Frequency.DAILY -> amountMinor * 30 / every
            Frequency.WEEKLY -> amountMinor * 52 / 12 / every
            Frequency.MONTHLY -> amountMinor / every
            Frequency.YEARLY -> amountMinor / 12 / every
        }
    }
}
