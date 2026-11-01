package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val today = LocalDate.of(2026, 5, 10)
    private fun days(vararg back: Long) = back.map { today.minusDays(it) }.toSet()

    @Test fun streakCountsBackFromToday() {
        val s = Progress.streak(days(0, 1, 2, 5), today)
        assertEquals(3, s.current)
        assertTrue(s.loggedToday)
    }

    @Test fun streakStaysAliveUntilTodayEnds() {
        val s = Progress.streak(days(1, 2), today)
        assertEquals(2, s.current)
        assertFalse(s.loggedToday)
    }

    @Test fun missedDayBreaksStreakButKeepsBest() {
        val s = Progress.streak(days(3, 4, 5, 6), today)
        assertEquals(0, s.current)
        assertEquals(4, s.best)
    }

    @Test fun levelsClimbByThreshold() {
        assertEquals(1, Progress.level(0).number)
        assertEquals(2, Progress.level(100).number)
        assertEquals(10, Progress.level(50_000).number)
        assertEquals(1f, Progress.level(50_000).progress)
        assertEquals(0.5f, Progress.level(200).progress)
    }
}
