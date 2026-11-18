package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MatchRulesTest {
    private val hand = TicketStatus.CLOSED_BY_HAND
    private fun d(s: String) = LocalDate.parse(s)
    private fun under(date: String, spent: Long = 5_000) = DayResult(d(date), hand, 10_000, spent)
    private fun over(date: String) = DayResult(d(date), hand, 10_000, 12_000)
    private fun open(date: String, spent: Long = 3_000) = DayResult(d(date), TicketStatus.OPEN, 10_000, spent)

    /** [goals] under-days from each Saturday in [starts]. */
    private fun weeks(starts: List<String>, goals: List<Int>) = starts.indices.flatMap { i ->
        (0 until goals[i]).map { DayResult(d(starts[i]).plusDays(it.toLong()), hand, 10_000, 5_000) }
    }

    @Test fun weekCrossingMonthsRunsSaturdayToFriday() {
        val m = buildMatch(listOf(under("2026-10-31"), over("2026-11-01"), open("2026-11-02")), d("2026-11-02"), false)
        assertEquals(d("2026-10-31"), m.days.first().date)
        assertEquals(d("2026-11-06"), m.days.last().date)
        assertEquals(45, m.weekNumber)
        assertEquals(5, m.daysLeft)
        assertEquals(1, m.goalsFor)
        assertEquals(1, m.goalsAgainst)
        assertEquals(7_000L, m.todayLeft)
        assertEquals(d("2026-10-31"), m.bestDay)
        assertEquals(listOf(GoalKind.FOR, GoalKind.AGAINST, GoalKind.NONE), m.days.take(3).map { it.goal })
        assertEquals(2_000L, m.days[1].amount)
    }

    @Test fun fridayIsTheLastDayAndSaturdayStartsANewWeek() {
        assertEquals(1, buildMatch(emptyList(), d("2026-11-06"), false).daysLeft)
        val sat = buildMatch(listOf(under("2026-11-06")), d("2026-11-07"), false)
        assertEquals(0, sat.goalsFor)
        assertEquals(1, sat.lastWeekGoalsFor)
        assertEquals(7, sat.daysLeft)
    }

    @Test fun notClosedDayScoresNothing() {
        val m = buildMatch(listOf(open("2026-11-03", spent = 99_999)), d("2026-11-03"), false)
        assertEquals(0, m.goalsFor + m.goalsAgainst)
        assertEquals(GoalKind.NONE, m.days.single { it.date == d("2026-11-03") }.goal)
    }

    @Test fun autoClosedDayCountsAsClosed() {
        val m = buildMatch(listOf(DayResult(d("2026-11-03"), TicketStatus.CLOSED_AUTO, 10_000, 0)), d("2026-11-04"), false)
        assertEquals(1, m.goalsFor)
    }

    @Test fun savedIsLeftoversMinusOverspendsNotBelowZero() {
        val a = buildMatch(listOf(under("2026-10-31"), over("2026-11-01")), d("2026-11-02"), false)
        assertEquals(3_000L, a.league.first().saved)
        val b = buildMatch(listOf(DayResult(d("2026-11-01"), hand, 10_000, 15_000)), d("2026-11-02"), false)
        assertEquals(0L, b.league.first().saved)
    }

    @Test fun hatTrickNudgeThenDone() {
        val two = listOf(under("2026-11-02"), under("2026-11-03"))
        assertEquals(HatTrick.ONE_AWAY, buildMatch(two + open("2026-11-04"), d("2026-11-04"), false).hatTrick)
        assertEquals(HatTrick.DONE, buildMatch(two + under("2026-11-04"), d("2026-11-04"), false).hatTrick)
        assertEquals(HatTrick.NONE, buildMatch(listOf(under("2026-11-03")), d("2026-11-04"), false).hatTrick)
        assertEquals(HatTrick.NONE, buildMatch(listOf(under("2026-11-02"), over("2026-11-03")), d("2026-11-04"), false).hatTrick)
    }

    @Test fun leagueResultsAgainstTheWeekBefore() {
        val starts = listOf("2026-10-24", "2026-10-31", "2026-11-07")
        val m = buildMatch(weeks(starts, listOf(2, 3, 1)), d("2026-11-10"), false)
        assertEquals(listOf(null, WeekResult.WIN, null), m.league.reversed().map { it.result })
        assertTrue(m.league.first().current)
        assertEquals(3, m.league[1].goalsFor)
        assertEquals(7, m.league[1].days.size)
        assertEquals(1, m.wonOf)
        assertEquals(1, m.ofLast)
        assertFalse(m.firstWeek)
    }

    @Test fun wonOfLastAndLongestRun() {
        val starts = (0..5).map { d("2026-10-03").plusDays(7L * it).toString() }
        val m = buildMatch(weeks(starts, listOf(1, 2, 3, 1, 2, 3)), d("2026-11-10"), true)
        assertEquals(3, m.wonOf)
        assertEquals(4, m.ofLast)
        assertEquals(2, m.longestWinRun)
        assertEquals(WeekResult.LOSS, m.league[2].result)
    }

    @Test fun historyBeyondFourWeeksIsLockedWithoutPlus() {
        val starts = (0..5).map { d("2026-10-03").plusDays(7L * it).toString() }
        val rows = weeks(starts, listOf(1, 1, 1, 1, 1, 1))
        val free = buildMatch(rows, d("2026-11-10"), false)
        assertEquals(4, free.league.size)
        assertEquals(2, free.lockedWeeks)
        val plus = buildMatch(rows, d("2026-11-10"), true)
        assertEquals(6, plus.league.size)
        assertEquals(0, plus.lockedWeeks)
    }

    @Test fun firstWeekFlag() {
        assertTrue(buildMatch(emptyList(), d("2026-11-10"), false).firstWeek)
        assertTrue(buildMatch(listOf(under("2026-11-09")), d("2026-11-10"), false).firstWeek)
        assertFalse(buildMatch(listOf(under("2026-11-02")), d("2026-11-10"), false).firstWeek)
        assertNull(buildMatch(emptyList(), d("2026-11-10"), false).bestDay)
    }

    @Test fun longestStreakAndStampCounts() {
        val days = listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-06").map { under(it) }
        assertEquals(4, longestStreak(days))
        assertEquals(5, daysUnder(days))
        assertEquals(1, stampCount(StampKind.HAT_TRICK, days))
        assertEquals(1, stampCount(StampKind.FIRST_GRAM, days))
        val weekDays = (0L..6L).map { DayResult(d("2026-11-07").plusDays(it), hand, 10_000, 0) }
        assertEquals(1, stampCount(StampKind.FULL_WEEK, weekDays))
    }
}
