package com.masroufi.app.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

sealed interface Payday {
    data class Day(val day: Int) : Payday {
        init {
            require(day in 1..31) { "payday must be 1..31" }
        }
    }

    data object LastDay : Payday
    data object NotFixed : Payday
}

data class PayCycle(val start: LocalDate, val nextPayday: LocalDate) {
    /** Days from today through nextPayday - 1, minimum 1. */
    fun daysLeft(today: LocalDate): Int =
        maxOf(1, ChronoUnit.DAYS.between(today, nextPayday).toInt())

    /** start <= date < nextPayday. */
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(nextPayday)
}

fun payCycleFor(today: LocalDate, payday: Payday, lastIncome: LocalDate?): PayCycle {
    fun monthly(paydayIn: (YearMonth) -> LocalDate): PayCycle {
        val month = YearMonth.from(today)
        return if (!today.isBefore(paydayIn(month))) {
            PayCycle(paydayIn(month), paydayIn(month.plusMonths(1)))
        } else {
            PayCycle(paydayIn(month.minusMonths(1)), paydayIn(month))
        }
    }
    return when (payday) {
        is Payday.Day -> monthly { it.atDay(minOf(payday.day, it.lengthOfMonth())) }
        Payday.LastDay -> monthly { it.atEndOfMonth() }
        Payday.NotFixed -> {
            val anchor = lastIncome ?: today
            val start = anchor.plusDays(30L * Math.floorDiv(ChronoUnit.DAYS.between(anchor, today), 30L))
            PayCycle(start, start.plusDays(30))
        }
    }
}
