package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ProverbDeckTest {
    @Test fun proverbDeckStableForSeed() {
        val c = setOf(3, 9)
        assertEquals(nextProverb(c, 42L, 50), nextProverb(c, 42L, 50))
        assertNotEquals((1..50).shuffled(Random(1L)), (1..50).shuffled(Random(2L)))
    }

    @Test fun proverbDeckExhausted() = assertNull(nextProverb((1..5).toSet(), 7L, 5))

    @Test fun proverbDeckSizeZero() = assertNull(nextProverb(emptySet(), 7L, 0))

    @Test fun skipsCollected() {
        val first = nextProverb(emptySet(), 11L, 10)!!
        val second = nextProverb(setOf(first), 11L, 10)!!
        assertNotEquals(first, second)
    }
}
