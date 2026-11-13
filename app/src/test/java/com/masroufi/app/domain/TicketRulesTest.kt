package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TicketRulesTest {
    private fun e(
        amount: Long,
        kind: EntryKind = EntryKind.SPENT,
        roundUp: Long = 0,
        counts: Boolean = true,
        recurringId: Long? = null,
    ) = EntryForDay(kind, amount, roundUp, counts, recurringId)

    @Test fun basic() =
        assertEquals(TicketCalc.Printed(24000, 24000, 0), computeTicket(587000, 275000, 0, 13))

    @Test fun roundsDownToPound() =
        assertEquals(TicketCalc.Printed(100000, 100000, 0), computeTicket(100050, 0, 0, 1))

    @Test fun carriedInNotDoubleCounted() =
        assertEquals(TicketCalc.Printed(16400, 9200, 7200), computeTicket(100000, 0, 7200, 10))

    @Test fun zeroPoolIsEndOfMonth() =
        assertEquals(TicketCalc.EndOfMonth(0, 5), computeTicket(100000, 100000, 0, 5))

    @Test fun negativePoolIsEndOfMonth() =
        assertEquals(TicketCalc.EndOfMonth(-40000, 5), computeTicket(100000, 140000, 0, 5))

    @Test fun subPoundPoolIsEndOfMonth() =
        assertEquals(TicketCalc.EndOfMonth(50, 1), computeTicket(50, 0, 0, 1))

    @Test fun fixedBillEntriesDoNotPunch() = assertEquals(0L, spentToday(listOf(e(5000, recurringId = 1))))

    @Test fun roundUpCountsAsSpent() = assertEquals(3000L, spentToday(listOf(e(2500, roundUp = 500))))

    @Test fun underTilePocketExcluded() = assertEquals(0L, spentToday(listOf(e(4000, counts = false))))

    @Test fun receivedAndMovedDoNotCount() =
        assertEquals(0L, spentToday(listOf(e(9000, EntryKind.RECEIVED), e(7000, EntryKind.MOVED))))

    @Test fun overspendSpread() = assertEquals(300L, overspendPerDay(3500, 12))

    @Test(expected = IllegalArgumentException::class)
    fun daysLeftZeroRejected() {
        computeTicket(100000, 0, 0, 0)
    }

    @Test fun carryKeptWhenBaseFloorsToZero() =
        assertEquals(TicketCalc.Printed(5000, 0, 5000), computeTicket(5800, 0, 5000, 10))

    @Test fun carryCappedAtAvailable() =
        assertEquals(TicketCalc.Printed(3000, 0, 3000), computeTicket(3000, 0, 5000, 4))

    @Test(expected = IllegalArgumentException::class)
    fun carriedInNegativeRejected() {
        computeTicket(100000, 0, -1, 10)
    }

    @Test fun overspendNonPositiveIsZero() {
        assertEquals(0L, overspendPerDay(0, 5))
        assertEquals(0L, overspendPerDay(-100, 5))
    }
}
