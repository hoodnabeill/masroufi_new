package com.masroufi.app.domain

import com.masroufi.app.data.model.Frequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {
    private val start = LocalDate.of(2026, 1, 31)

    @Test fun twelveMonthlyInstalmentsEndElevenMonthsLater() {
        assertEquals(LocalDate.of(2026, 12, 31), Schedule.endFor(start, 12, Frequency.MONTHLY, 1))
        assertEquals(start, Schedule.endFor(start, 1, Frequency.MONTHLY, 1))
    }

    @Test fun countsPaymentsSoFar() {
        val end = Schedule.endFor(start, 12, Frequency.MONTHLY, 1)
        assertEquals(12, Schedule.countThrough(start, end, Frequency.MONTHLY, 1))
        assertEquals(3, Schedule.countThrough(start, LocalDate.of(2026, 4, 15), Frequency.MONTHLY, 1))
        assertEquals(0, Schedule.countThrough(start, start.minusDays(1), Frequency.MONTHLY, 1))
    }

    @Test fun monthlyEquivalents() {
        assertEquals(1_200L, Schedule.monthlyMinor(14_400, Frequency.YEARLY, 1))
        assertEquals(4_333L, Schedule.monthlyMinor(1_000, Frequency.WEEKLY, 1))
        assertEquals(500L, Schedule.monthlyMinor(1_000, Frequency.MONTHLY, 2))
    }
}
