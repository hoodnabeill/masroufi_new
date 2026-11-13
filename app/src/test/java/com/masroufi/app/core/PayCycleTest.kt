package com.masroufi.app.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCycleTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun payday25Mid() {
        val today = d("2026-11-12")
        val c = payCycleFor(today, Payday.Day(25), null)
        assertEquals(d("2026-10-25"), c.start)
        assertEquals(d("2026-11-25"), c.nextPayday)
        assertEquals(13, c.daysLeft(today))
    }

    @Test fun paydayToday() {
        val today = d("2026-11-25")
        val c = payCycleFor(today, Payday.Day(25), null)
        assertEquals(d("2026-11-25"), c.start)
        assertEquals(d("2026-12-25"), c.nextPayday)
        assertEquals(30, c.daysLeft(today))
    }

    @Test fun payday31InFebruary() {
        val c = payCycleFor(d("2027-02-10"), Payday.Day(31), null)
        assertEquals(d("2027-01-31"), c.start)
        assertEquals(d("2027-02-28"), c.nextPayday)
    }

    @Test fun lastDay() {
        val c = payCycleFor(d("2026-11-30"), Payday.LastDay, null)
        assertEquals(d("2026-11-30"), c.start)
        assertEquals(d("2026-12-31"), c.nextPayday)
    }

    @Test fun daysLeftAtLeastOne() {
        val c = payCycleFor(d("2026-11-12"), Payday.Day(25), null)
        assertEquals(1, c.daysLeft(c.nextPayday.minusDays(1)))
        assertEquals(1, c.daysLeft(c.nextPayday))
    }

    @Test fun notFixedRolling() {
        val c = payCycleFor(d("2026-11-12"), Payday.NotFixed, d("2026-10-01"))
        assertEquals(d("2026-10-31"), c.start)
        assertEquals(d("2026-11-30"), c.nextPayday)
    }

    @Test fun notFixedWithoutIncomeStartsToday() {
        val c = payCycleFor(d("2026-11-12"), Payday.NotFixed, null)
        assertEquals(d("2026-11-12"), c.start)
        assertEquals(d("2026-12-12"), c.nextPayday)
    }

    @Test fun containsIncludesStartExcludesNext() {
        val c = PayCycle(d("2026-11-25"), d("2026-12-25"))
        assertTrue(c.contains(c.start))
        assertTrue(c.contains(d("2026-12-24")))
        assertFalse(c.contains(c.nextPayday))
        assertFalse(c.contains(d("2026-11-24")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun dayOutOfRangeRejected() { Payday.Day(32) }

    @Test fun notFixedFutureIncome() {
        val today = d("2026-11-12")
        val c = payCycleFor(today, Payday.NotFixed, d("2026-11-20"))
        assertEquals(d("2026-10-21"), c.start)
        assertEquals(d("2026-11-20"), c.nextPayday)
        assertTrue(c.contains(today))
        assertEquals(8, c.daysLeft(today))
    }

    @Test fun day31In30DayMonth() {
        val c = payCycleFor(d("2026-11-30"), Payday.Day(31), null)
        assertEquals(d("2026-11-30"), c.start)
        assertEquals(d("2026-12-31"), c.nextPayday)
    }

    @Test fun leapFebruary() {
        val c = payCycleFor(d("2028-02-28"), Payday.Day(31), null)
        assertEquals(d("2028-01-31"), c.start)
        assertEquals(d("2028-02-29"), c.nextPayday)
    }

    @Test fun day30InFebruary() {
        val c = payCycleFor(d("2027-03-01"), Payday.Day(30), null)
        assertEquals(d("2027-02-28"), c.start)
        assertEquals(d("2027-03-30"), c.nextPayday)
    }

    @Test fun decemberToJanuary() {
        val c = payCycleFor(d("2026-12-26"), Payday.Day(25), null)
        assertEquals(d("2026-12-25"), c.start)
        assertEquals(d("2027-01-25"), c.nextPayday)
    }

    @Test(expected = IllegalArgumentException::class)
    fun day0Rejected() { Payday.Day(0) }
}
