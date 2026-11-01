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
}
