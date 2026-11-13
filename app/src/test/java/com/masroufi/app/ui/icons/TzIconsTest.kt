package com.masroufi.app.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TzIconsTest {
    @Test fun allIconsAre24Grid() {
        val vectors = TzIcons.all.flatMap { it.allVectors() }
        assertTrue(vectors.size >= TzIcons.all.size * 2)
        vectors.forEach {
            assertEquals(it.name, 24f, it.viewportWidth)
            assertEquals(it.name, 24f, it.viewportHeight)
        }
    }

    @Test fun tabIconsHaveFilledPairs() {
        listOf(TzIcons.today, TzIcons.roll, TzIcons.plan, TzIcons.people, TzIcons.add)
            .forEach { assertTrue(it.name, it.filled != null) }
    }

    @Test fun namesAreUnique() {
        assertEquals(TzIcons.all.size, TzIcons.all.map { it.name }.toSet().size)
    }
}
