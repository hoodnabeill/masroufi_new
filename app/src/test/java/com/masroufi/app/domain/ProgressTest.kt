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
        assertEquals(5, Progress.level(50_000).number)
        assertEquals(1f, Progress.level(50_000).progress)
        assertEquals(0.5f, Progress.level(200).progress)
    }

    @Test fun weeklyFreezeBridgesOneMissedDay() {
        // Logged today and two days ago; yesterday is bridged by this week's freeze.
        val s = Progress.streak(days(0, 2, 3), today)
        assertEquals(3, s.current)
        assertEquals(setOf(today.minusDays(1)), s.frozenDays)
    }

    @Test fun pointsCountDaysGameyaSetupAndBadges() {
        val logged = days(0, 1, 2)
        // Spent (and ate out) every day, so the zero-day and home-cook badges stay locked.
        val input = ProgressInput(logDays = logged, entries = 5, expenseDays = logged, foodDays = logged, gameyas = 1, gameyaPaid = 2, onboardingDone = true)
        val snap = Progress.snapshot(input, today)
        // 3 days x 10 + 2 instalments x 5 + 20 setup + badges: first log 10, 3-day 20, gameya 20.
        assertEquals(30 + 10 + 20 + 10 + 20 + 20, snap.points)
        assertTrue(Badge.STARTED_RIGHT in snap.earned)
    }
}
