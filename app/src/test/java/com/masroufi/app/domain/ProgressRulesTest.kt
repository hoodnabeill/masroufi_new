package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressRulesTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val hand = TicketStatus.CLOSED_BY_HAND
    private val auto = TicketStatus.CLOSED_AUTO

    private fun day(offset: Long, s: TicketStatus, ticket: Long = 1000, spent: Long = 500) =
        DayResult(today.minusDays(offset), s, ticket, spent)

    @Test fun streakCountsHandClosesOnly() =
        // oldest to newest: hand, hand, auto, hand, hand (ending yesterday)
        assertEquals(2, streak(listOf(day(5, hand), day(4, hand), day(3, auto), day(2, hand), day(1, hand)), today))

    @Test fun streakBreaksOnGap() =
        assertEquals(1, streak(listOf(day(4, hand), day(3, hand), day(1, hand)), today))

    @Test fun streakIncludesNoSpendDay() =
        assertEquals(2, streak(listOf(day(2, hand, spent = 0), day(1, hand)), today))

    @Test fun openTodayKeepsYesterdayStreak() =
        assertEquals(2, streak(listOf(day(2, hand), day(1, hand), day(0, TicketStatus.OPEN)), today))

    @Test fun streakIncludesHandClosedToday() =
        assertEquals(2, streak(listOf(day(1, hand), day(0, hand)), today))

    @Test fun streakBreaksOnAutoClose() =
        assertEquals(0, streak(listOf(day(2, hand), day(1, auto)), today))

    @Test fun levelThresholds() {
        assertEquals(1, level(0))
        assertEquals(1, level(6))
        assertEquals(2, level(7))
        assertEquals(8, level(365))
    }

    @Test fun nextLevelAtValues() {
        assertEquals(7, nextLevelAt(0))
        assertEquals(20, nextLevelAt(7))
        assertEquals(365, nextLevelAt(180))
    }

    @Test fun nextLevelAtTop() {
        assertNull(nextLevelAt(365))
        assertNull(nextLevelAt(1000))
    }

    @Test fun fullWeekNeedsSatToFri() {
        val sat = LocalDate.of(2026, 11, 7) // Saturday; week ends Fri 2026-11-13
        val week = (0L..6L).map { DayResult(sat.plusDays(it), hand, 1000, 500) }
        assertTrue(StampKind.FULL_WEEK in earnedStamps(week))
        // Sun..Sat spans two weeks: not a full week
        val shifted = (1L..7L).map { DayResult(sat.plusDays(it), hand, 1000, 500) }
        assertFalse(StampKind.FULL_WEEK in earnedStamps(shifted))
        assertFalse(StampKind.FULL_WEEK in earnedStamps(week.drop(1)))
    }

    @Test fun hatTrickNeedsUnder() {
        val d0 = LocalDate.of(2026, 11, 1)
        fun run(spent3: Long) = listOf(
            DayResult(d0, hand, 1000, 1000),
            DayResult(d0.plusDays(1), hand, 1000, 0),
            DayResult(d0.plusDays(2), hand, 1000, spent3),
        )
        assertTrue(StampKind.HAT_TRICK in earnedStamps(run(999)))
        assertFalse(StampKind.HAT_TRICK in earnedStamps(run(1001)))
    }

    @Test fun firstTicketOnly() {
        assertEquals(emptySet<StampKind>(), earnedStamps(listOf(day(1, auto))))
        assertEquals(setOf(StampKind.FIRST_TICKET), earnedStamps(listOf(day(1, hand))))
    }

    @Test fun weekStartsSaturday() {
        assertEquals(LocalDate.of(2026, 11, 7), weekStart(LocalDate.of(2026, 11, 12)))
        assertEquals(LocalDate.of(2026, 11, 7), weekStart(LocalDate.of(2026, 11, 7)))
    }

    // today is Thursday 2026-11-12; its Saturday-Friday week is Nov 7..13, the one before Oct 31..Nov 6.

    @Test fun safetyTicketKeepsTheStreakThroughOneMissedDayAWeek() {
        // hand on Nov 11, 9, 8, 7; Nov 10 missed; Nov 6 and 5 missing (two misses in the older week).
        val days = listOf(day(1, hand), day(3, hand), day(4, hand), day(5, hand))
        val r = streakResult(days, today, plusSafety = true)
        assertEquals("the missed day is bridged and not counted", 4, r.days)
        assertEquals(listOf(LocalDate.of(2026, 11, 10)), r.bridged)
        assertEquals(1, streak(days, today))
        assertEquals(3, protectedDays(days, today))
    }

    @Test fun aSecondMissedDayInTheSameWeekBreaksIt() {
        // Nov 10 and Nov 9 both missed (the week Nov 7..13); hand on Nov 11, 8, 7.
        val days = listOf(day(1, hand), day(4, hand), day(5, hand))
        assertEquals(1, streak(days, today, plusSafety = true))
        assertEquals(0, protectedDays(days, today))
    }

    @Test fun eachWeekHasItsOwnTicket() {
        // Nov 10 and Nov 5 missed, one in each week.
        val days = listOf(day(1, hand), day(3, hand), day(4, hand), day(5, hand), day(6, hand), day(8, hand), day(9, hand))
        assertEquals(7, streak(days, today, plusSafety = true))
        assertEquals(1, streak(days, today))
        assertEquals(6, protectedDays(days, today))
    }

    @Test fun anAutoClosedDayIsAMissedDayForTheTicket() {
        val days = listOf(day(1, hand), day(2, auto), day(3, hand))
        assertEquals(2, streak(days, today, plusSafety = true))
        assertEquals(1, streak(days, today))
    }

    @Test fun aMissedYesterdayWithAnOpenTodayIsBridged() {
        val days = listOf(day(2, hand), day(3, hand), day(0, TicketStatus.OPEN))
        assertEquals(2, streak(days, today, plusSafety = true))
        assertEquals(0, streak(days, today))
    }

    @Test fun aBridgeWithNoStreakDayBeforeItProtectsNothing() {
        val days = listOf(day(1, hand))
        val r = streakResult(days, today, plusSafety = true)
        assertEquals(1, r.days)
        assertTrue(r.bridged.isEmpty())
        assertEquals(0, protectedDays(days, today))
        assertEquals(0, streak(emptyList(), today, plusSafety = true))
    }
}
