package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PayCycleTest {
    @Test
    fun cycleStartsOnLastPaydayAndEndsOnNext() {
        val (start, next) = PayCycle.current(LocalDate.of(2026, 3, 10), 25)
        assertEquals(LocalDate.of(2026, 2, 25), start)
        assertEquals(LocalDate.of(2026, 3, 25), next)
    }

    @Test
    fun paydayItselfStartsANewCycle() {
        val today = LocalDate.of(2026, 3, 25)
        assertEquals(today, PayCycle.current(today, 25).first)
        assertEquals(31, PayCycle.daysUntilPayday(today, 25))
    }

    @Test
    fun shortMonthsPayOnTheirLastDay() {
        assertEquals(LocalDate.of(2026, 2, 28), PayCycle.paydayIn(java.time.YearMonth.of(2026, 2), 31))
        assertEquals(LocalDate.of(2026, 2, 28), PayCycle.current(LocalDate.of(2026, 2, 10), 31).second)
    }

    @Test
    fun safePerDaySplitsBalanceAndNeverGoesNegative() {
        val today = LocalDate.of(2026, 3, 20)
        assertEquals(200_00L, PayCycle.safePerDay(1_000_00L, today, 25))
        assertEquals(0L, PayCycle.safePerDay(-50_00L, today, 25))
    }

    @Test
    fun safeTodayUsesMorningBalanceAndMonthEndFallback() {
        val today = LocalDate.of(2026, 11, 2)
        // No payday: 29 days left in November including today; 4,527 now + 0 spent = 156 a day.
        assertEquals(29, PayCycle.daysUntilNextIncome(today, 0))
        assertEquals(156_10L, PayCycle.safeToday(4_527_00L, 0, today, 0))
        // Spending today does not move today's limit.
        assertEquals(156_10L, PayCycle.safeToday(4_427_00L, 100_00L, today, 0))
        assertEquals(0L, PayCycle.safeToday(-10_00L, 0, today, 0))
    }
}
