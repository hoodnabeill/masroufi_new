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

    @Test fun ordinaryDeckSkipsRare() {
        val rare = ProverbCatalog.rare
        val drawn = mutableSetOf<Int>()
        repeat(47) { drawn += nextProverb(drawn, 5L, 50, rare)!! }
        assertEquals((1..50).toSet() - rare, drawn)
    }

    @Test fun allCollected() = assertNull(nextProverb((1..50).toSet() - ProverbCatalog.rare, 9L, 50, ProverbCatalog.rare))

    @Test fun excludeKeepsShuffleOrder() {
        val order = (1..50).shuffled(Random(42L))
        assertEquals(order.first { it !in setOf(45, 49, 50) }, nextProverb(emptySet(), 42L, 50, setOf(45, 49, 50)))
    }
}
