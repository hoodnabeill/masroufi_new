package com.masroufi.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun floorToPound() = assertEquals(15600L, 15699L.floorToPound())
    @Test fun floorToPoundNegative() = assertEquals(-200L, (-150L).floorToPound())
    @Test fun ceilToPound() = assertEquals(15700L, 15601L.ceilToPound())
    @Test fun ceilToPoundExact() = assertEquals(15600L, 15600L.ceilToPound())
    @Test fun ceilToPoundNegative() = assertEquals(-100L, (-150L).ceilToPound())
    @Test fun ceilDiv() = assertEquals(4L, 10L.ceilDiv(3L))
    @Test fun ceilDivNegative() = assertEquals(-3L, (-10L).ceilDiv(3L))
    @Test(expected = IllegalArgumentException::class)
    fun ceilDivRejectsZero() { 10L.ceilDiv(0L) }
}
