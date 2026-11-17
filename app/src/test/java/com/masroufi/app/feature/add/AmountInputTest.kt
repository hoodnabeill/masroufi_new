package com.masroufi.app.feature.add

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {
    private fun type(keys: String): AmountInput = keys.fold(AmountInput()) { a, k ->
        when (k) {
            '.' -> a.point()
            '<' -> a.backspace()
            else -> a.digit(k.digitToInt())
        }
    }

    @Test fun keypadEdges() {
        assertEquals(1250L, type("12.5").toPiastres())
        assertEquals("0 is not an amount", 0L, type("0").toPiastres())
        assertEquals("a point alone is 0", 0L, type(".").toPiastres())
        assertEquals(0L, AmountInput(".").toPiastres())
        assertEquals("9th whole digit ignored", "12345678", type("123456789").text)
        assertEquals(1_234_567_800L, type("123456789").toPiastres())
        assertEquals("third decimal ignored", "1.25", type("1.257").text)
        assertEquals(125L, type("1.257").toPiastres())
        assertEquals("leading zeros collapse", "7", type("007").text)
        assertEquals("one point only", "1.5", type("1..5").text)
        assertEquals("0.5", type(".5").text)
        assertEquals(50L, type(".5").toPiastres())
        assertEquals("backspace", "1", type("12<").text)
        assertEquals("", type("<").text)
        assertEquals("a decimal digit after 8 whole digits", 1_234_567_890L, type("12345678.9").toPiastres())
    }

    @Test fun prefillAmounts() {
        assertEquals("40", AmountInput.of(4_000).text)
        assertEquals("12.05", AmountInput.of(1_205).text)
        assertEquals("", AmountInput.of(0).text)
        assertEquals("too long for the keypad", "", AmountInput.of(100_000_000_00L).text)
    }
}
