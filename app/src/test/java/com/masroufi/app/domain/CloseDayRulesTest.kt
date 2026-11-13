package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CloseDayRulesTest {
    @Test fun leftoverNeverNegative() {
        assertEquals(0L, leftover(1000, 1500))
        assertEquals(500L, leftover(1000, 500))
        assertEquals(0L, leftover(1000, 1000))
    }
}
