package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ZakatTest {
    @Test fun zakatMalIsTwoAndAHalfPercentAboveNisab() {
        assertEquals(250_000L, zakatMal(10_000_000, 5_000_000))
        assertEquals("floored to piastres", 3L, zakatMal(155, 100))
        assertEquals("exactly at the nisab counts", 125_000L, zakatMal(5_000_000, 5_000_000))
    }

    @Test fun zakatMalZeroBelowNisab() {
        assertEquals(0L, zakatMal(4_999_999, 5_000_000))
    }

    @Test fun nisabIs85GramsOfGold() {
        assertEquals(34_000_000L, nisabFromGold(400_000))
    }

    @Test fun zakatFitrIsPeopleTimesAmount() {
        assertEquals(17_500L, zakatFitr(5, 3_500))
        assertEquals(0L, zakatFitr(0, 3_500))
    }
}
