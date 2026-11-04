package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HealthMathTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test fun scoreAveragesTheFourFactors() {
        val f = HealthMath.factors(
            logDays = (0L until 7L).map { today.minusDays(it) }.toSet(),
            today = today, dailyPaceMinor = 20_000, safeMinor = 10_000,
            emergencySaved = 0, emergencyTarget = 0, billsTracked = true,
        )
        assertEquals(0.5f, f.logging, 0.001f)
        assertEquals(0.5f, f.pace, 0.001f)
        // (0.5 + 0.5 + 0 + 1) / 4 = 50
        assertEquals(50, f.score)
    }

    @Test fun forecastRunsOutThisMonthOrLasts() {
        // 2,000 spent in 10 days = 200 a day; 1,800 left lasts 9 more days.
        val f = HealthMath.forecast(180_000, 200_000, today)
        assertEquals(20_000L, f.dailyPaceMinor)
        assertEquals(LocalDate.of(2026, 11, 19), f.runOut)
        assertNull(HealthMath.forecast(1_000_000, 200_000, today).runOut)
        assertNull(HealthMath.forecast(1_000, 0, today).runOut)
    }

    @Test fun recapCountsDaysUnderAndSavings() {
        val month = YearMonth.of(2026, 10)
        val daily = mapOf(month.atDay(1) to 50_000L, month.atDay(2) to 5_000L, month.atDay(8) to 10_000L)
        val r = HealthMath.recap(month, daily, mapOf("food" to 40_000L, "fun" to 25_000L), incomeMinor = 310_000, throughDay = today)
        assertEquals(65_000L, r.spentMinor)
        assertEquals(10_000L, r.dailyNumberMinor)
        assertEquals(30, r.daysUnder)
        assertEquals("food", r.topCategoryId)
        assertEquals(DayOfWeek.THURSDAY, r.busiestWeekday)
        assertEquals(310_000L - 65_000L, r.savedMinor)
    }
}
