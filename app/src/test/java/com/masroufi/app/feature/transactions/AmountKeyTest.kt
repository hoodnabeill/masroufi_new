package com.masroufi.app.feature.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountKeyTest {
    private fun type(keys: String, currency: String = "EGP") = keys.fold("") { acc, k -> applyAmountKey(acc, k, currency) }

    @Test fun buildsAmountsWithinCurrencyPrecision() {
        assertEquals("125.5", type("125.5"))
        assertEquals("0.75", type(".75"))
        assertEquals("12.34", type("12.345"))
        assertEquals("1.2", type("1..2"))
        assertEquals("7", type("07"))
    }

    @Test fun backspaceAndZeroDecimalCurrencies() {
        assertEquals("12", type("123<"))
        assertEquals("", type("1<<"))
        assertEquals("150", type("15.0", currency = "JPY"))
    }

    @Test fun capsIntegerDigits() {
        assertEquals(12, type("9".repeat(20)).length)
    }

    @Test fun quickNotesAddWholePounds() {
        assertEquals("50", addWholeAmount("", 50))
        assertEquals("62.5", addWholeAmount("12.5", 50))
        assertEquals("300", addWholeAmount("100", 200))
        assertEquals("9".repeat(12), addWholeAmount("9".repeat(12), 5))
    }

    @Test fun sheetGroupsTypedThousandsAndKeepsTheFraction() {
        assertEquals("0", groupTyped(""))
        assertEquals("85", groupTyped("85"))
        assertEquals("1,105", groupTyped("1105"))
        assertEquals("1,234,567.5", groupTyped("1234567.5"))
        assertEquals("1,000.", groupTyped("1000."))
    }

    @Test fun sheetThrowProjectsAndEdgesResist() {
        // A 1,000 px/s flick comes to rest 1 * 0.998 / 0.002 = 499 px further on.
        assertEquals(499f, projectMomentum(1_000f), 0.5f)
        assertEquals(0f, projectMomentum(0f), 0f)
        // Pulling 400 px past the edge of an 800 px sheet moves it far less, and never more than the pull.
        val pulled = rubberBand(-400f, 800f)
        assertTrue(pulled < 0f && pulled > -400f * 0.55f)
    }

    @Test fun todayBudgetRoundsDownToWholePounds() {
        val today = com.masroufi.app.data.repo.TodayBudget("EGP", safeMinor = 15_600, spentMinor = 5_000, balanceMinor = 400_000, days = 29)
        assertEquals(10_600, today.remainingMinor)
        // (4,000 + 50 + 500) / 29 = 156.89 → 156
        assertEquals(15_600, today.afterIncome(50_000))
    }
}
