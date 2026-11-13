package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundUpTest {
    @Test fun roundsToNextFive() = assertEquals(200L, roundUp(2300, 5))
    @Test fun alreadyMultiple() = assertEquals(0L, roundUp(2500, 5))
    @Test fun roundsToFifty() = assertEquals(2700L, roundUp(2300, 50))
    @Test fun roundUpOff() = assertEquals(0L, roundUp(2300, 0))
    @Test fun roundUpNegativeAmount() = assertEquals(0L, roundUp(-500, 5))
}
