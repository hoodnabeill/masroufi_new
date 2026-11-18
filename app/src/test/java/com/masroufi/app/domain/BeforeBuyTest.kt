package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeforeBuyTest {
    private val ticket = 24_000L // 240 EGP

    @Test fun mockPriceIsSevenAndAHalfTickets() {
        val t = checkNotNull(ticketsFor(180_000, ticket))
        assertEquals("7.5", t.plain())
        assertEquals("7 ونص", t.spoken())
        assertEquals("takes 8 days of tickets", 8, cashDays(180_000, ticket))
    }

    @Test fun spokenFormWholeHalfAndDecimal() {
        assertEquals("8", ticketsFor(192_000, ticket)!!.spoken())
        assertEquals("7.3", ticketsFor(175_200, ticket)!!.spoken())
        assertEquals("نص", ticketsFor(12_000, ticket)!!.spoken())
        assertEquals("1 ونص", Tenths(15).spoken())
    }

    @Test fun ticketsAreHiddenWithoutATicket() {
        assertNull(ticketsFor(180_000, null))
        assertNull(ticketsFor(180_000, 0))
        assertNull(cashDays(180_000, null))
    }

    @Test fun workDaysFromMonthlyIncome() {
        assertEquals("6", workDaysFor(180_000, 900_000)!!.plain())
        assertEquals("1.5", workDaysFor(45_000, 900_000)!!.plain())
        assertNull("hidden when income is unknown", workDaysFor(180_000, null))
    }

    @Test fun averageIncomeOfThreeMonthsElseUsualSalary() {
        assertEquals(900_000L, averageMonthlyIncome(listOf(900_000, 600_000, 1_200_000), null))
        assertEquals(500_000L, averageMonthlyIncome(listOf(0, 0, 0), 500_000))
        assertNull(averageMonthlyIncome(listOf(0, 0, 0), null))
    }

    @Test fun goldGramsNeedATypedPrice() {
        assertEquals(36L, goldGramsHundredths(180_000, 500_000))
        assertEquals("0.36", gramsText(36))
        assertEquals("12.05", gramsText(1205))
        assertNull(goldGramsHundredths(180_000, null))
    }

    @Test fun seasonShareIsHiddenWhenNothingIsSaved() {
        assertEquals(36, seasonSharePercent(180_000, 500_000))
        assertNull(seasonSharePercent(180_000, 0))
        assertNull(seasonSharePercent(180_000, null))
    }

    @Test fun installmentMockNumbers() {
        val i = checkNotNull(installment(180_000, 18_500, 12))
        assertEquals(222_000L, i.total)
        assertEquals("420 extra", 42_000L, i.extra)
        assertEquals("the ticket shrinks about 6 a day", 617L, i.perDay)
        assertEquals(12, i.months)
        assertNull(installment(180_000, 0, 12))
        assertNull(installment(180_000, 18_500, 0))
    }

    @Test fun beforeBuyKeepsUnknownsNull() {
        val b = beforeBuy(180_000, BuyContext(ticket, null, null, null, null))
        assertEquals(listOf("7 ونص", null, null, null), listOf(b.tickets?.spoken(), b.workDays, b.goldGramsHundredths, b.seasonSharePercent))
        val full = beforeBuy(180_000, BuyContext(ticket, 900_000, 500_000, 500_000, "رمضان"))
        assertEquals(listOf("6", 36L, 36, "رمضان"), listOf(full.workDays?.plain(), full.goldGramsHundredths, full.seasonSharePercent, full.seasonName))
    }
}
