package com.masroufi.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvelopeRulesTest {
    private val a = 1L
    private val b = 2L
    private val today = LocalDate.of(2026, 11, 12)
    private val payday = LocalDate.of(2026, 11, 25)

    @Test fun runsOutBeforePayday() =
        assertEquals(LocalDate.of(2026, 11, 18), runsOutDay(30000, 50000, 10, today, payday))

    @Test fun hiddenUnderFiveDays() = assertNull(runsOutDay(30000, 50000, 4, today, payday))

    @Test fun hiddenWhenAfterPayday() = assertNull(runsOutDay(900000, 50000, 10, today, payday))

    @Test fun runsOutHiddenWhenLeftZero() = assertNull(runsOutDay(0, 50000, 10, today, payday))

    @Test fun envelopeLeftCanBeNegative() = assertEquals(-500L, envelopeLeft(1000, 1500))

    @Test fun splitFixedThenPercent() {
        val r = splitIncome(1000000, listOf(SplitRule.Fixed(a, 300000), SplitRule.Percent(b, 50)))
        assertEquals(mapOf(a to 300000L, b to 350000L), r.shares)
        assertEquals(350000L, r.unassigned)
    }

    @Test fun splitFixedExceedsIncome() {
        val r = splitIncome(100000, listOf(SplitRule.Fixed(a, 300000)))
        assertEquals(mapOf(a to 100000L), r.shares)
        assertEquals(0L, r.unassigned)
    }

    @Test fun percentsShareOneBase() {
        val r = splitIncome(1000000, listOf(SplitRule.Percent(a, 50), SplitRule.Percent(b, 50)))
        assertEquals(mapOf(a to 500000L, b to 500000L), r.shares)
        assertEquals(0L, r.unassigned)
    }

    @Test(expected = IllegalArgumentException::class)
    fun percentOver100Rejected() {
        splitIncome(1000, listOf(SplitRule.Percent(a, 60), SplitRule.Percent(b, 50)))
    }

    @Test fun sameEnvelopeTwiceAdds() {
        val r = splitIncome(1000000, listOf(SplitRule.Fixed(a, 100000), SplitRule.Fixed(a, 200000)))
        assertEquals(mapOf(a to 300000L), r.shares)
        assertEquals(700000L, r.unassigned)
    }

    @Test fun percentFloorsToPound() {
        val r = splitIncome(9999, listOf(SplitRule.Percent(a, 50)))
        assertEquals(mapOf(a to 4900L), r.shares)
        assertEquals(5099L, r.unassigned)
    }

    @Test fun sharesKeepRuleOrder() {
        val r = splitIncome(1000000, listOf(SplitRule.Percent(1, 20), SplitRule.Fixed(2, 100000)))
        assertEquals(listOf(1L, 2L), r.shares.keys.toList())
    }

    @Test fun runsOutHiddenWhenSpentZero() = assertNull(runsOutDay(30000, 0, 10, today, payday))

    @Test fun runsOutOnPaydayIsHidden() = assertNull(runsOutDay(65000, 50000, 10, today, payday))

    @Test fun fiveDaysShowsDate() =
        assertEquals(LocalDate.of(2026, 11, 18), runsOutDay(30000, 25000, 5, today, payday))

    @Test fun realSalarySplit() {
        val r = splitIncome(999999, listOf(SplitRule.Percent(a, 50), SplitRule.Percent(b, 30), SplitRule.Percent(3, 20)))
        assertEquals(mapOf(a to 499900L, b to 299900L, 3L to 199900L), r.shares)
        assertEquals(299L, r.unassigned)
    }

    @Test fun zeroIncome() {
        val r = splitIncome(0, listOf(SplitRule.Fixed(a, 500), SplitRule.Percent(b, 50)))
        assertEquals(mapOf(a to 0L, b to 0L), r.shares)
        assertEquals(0L, r.unassigned)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeIncomeRejected() {
        splitIncome(-1, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeFixedRejected() {
        splitIncome(1000, listOf(SplitRule.Fixed(a, -1)))
    }
}
