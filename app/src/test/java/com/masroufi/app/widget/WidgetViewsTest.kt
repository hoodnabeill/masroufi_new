package com.masroufi.app.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.repo.RepeatChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Robolectric only for org.json (the summary's JSON); the mapping itself is plain Kotlin. */
@RunWith(AndroidJUnit4::class)
class WidgetViewsTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val chips = listOf(
        RepeatChip(1, "مواصلات", 1_500, 7), RepeatChip(2, "قهوة", 2_500, 7), RepeatChip(3, "فطار", 3_000, 7),
    )
    private val open = DaySummary(
        day = today.toEpochDay(), printed = true, open = true, amount = 24_000, left = 15_600, punches = 3, streak = 5,
        matchYou = 4, matchLast = 1, hatTrickNear = true, chips = chips, lockOn = false, hideAmounts = false,
    )

    @Test fun anOpenTicketShowsTheNumberTheTicketAndTheHoles() {
        val v = ticketView(open, today)
        assertEquals(TicketMode.OPEN, v.mode)
        assertEquals(listOf("156", "240"), listOf(v.number, v.of))
        assertEquals(3, v.holes)
        assertEquals(3, v.filled)
    }

    @Test fun holesStopAtTheSlotsOnTheSmallTicket() {
        val v = ticketView(open.copy(punches = 9), today)
        assertEquals(9, v.holes)
        assertEquals(HOLE_SLOTS, v.filled)
    }

    @Test fun amountsAreDotsWithTheLockOnOrHideAmountsOn() {
        for (s in listOf(open.copy(lockOn = true), open.copy(hideAmounts = true))) {
            val v = ticketView(s, today)
            assertEquals(listOf("•••", "•••"), listOf(v.number, v.of))
            assertTrue(wideView(s, today).chips.all { it.amount == "•••" || s.lockOn })
        }
    }

    @Test fun theLockHidesTheRepeatButtonsButHideAmountsKeepsThemAsDots() {
        val locked = wideView(open.copy(lockOn = true), today)
        assertEquals(emptyList<ChipView>(), locked.chips)
        assertTrue(locked.locked)
        val hidden = wideView(open.copy(hideAmounts = true), today)
        assertEquals(3, hidden.chips.size)
        assertEquals(listOf("•••", "•••", "•••"), hidden.chips.map { it.amount })
        assertEquals(chips.map { it.categoryName }, hidden.chips.map { it.chip.categoryName })
    }

    @Test fun theWideWidgetShowsAtMostThreeChipsWithTheirAmounts() {
        val v = wideView(open.copy(chips = chips + RepeatChip(4, "تاكسي", 9_000, 7)), today)
        assertEquals(listOf("15", "25", "30"), v.chips.map { it.amount })
    }

    @Test fun aClosedDayHasNoButtonsAndAnOverspentDayShowsZero() {
        val closed = open.copy(open = false, left = 5_000)
        assertEquals(TicketMode.CLOSED, ticketView(closed, today).mode)
        assertEquals(emptyList<ChipView>(), wideView(closed, today).chips)
        val over = ticketView(open.copy(left = -3_000), today)
        assertEquals("0", over.number)
        assertTrue(over.over)
    }

    @Test fun noSummaryAnotherDayOrNothingPrintedIsNoTicket() {
        for (s in listOf(null, open.copy(day = today.toEpochDay() - 1), open.copy(printed = false))) {
            val v = ticketView(s, today)
            assertEquals(TicketMode.EMPTY, v.mode)
            assertEquals(emptyList<ChipView>(), wideView(s, today).chips)
        }
    }

    @Test fun theMatchShowsBothScoresEvenWithTheLockOnAndTheHatTrickNudge() {
        val v = matchView(open.copy(lockOn = true))
        assertEquals(listOf("4", "1"), listOf(v.you, v.last))
        assertTrue(v.hatNear)
        assertEquals(listOf("0", "0"), matchView(null).let { listOf(it.you, it.last) })
    }

    @Test fun theSummarySurvivesItsJson() {
        assertEquals(open, daySummaryFromJson(open.toJson()))
        assertNull(daySummaryFromJson(null))
        assertNull(daySummaryFromJson("not json"))
    }
}
