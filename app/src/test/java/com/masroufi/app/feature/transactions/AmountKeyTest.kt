package com.masroufi.app.feature.transactions

import org.junit.Assert.assertEquals
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
}
