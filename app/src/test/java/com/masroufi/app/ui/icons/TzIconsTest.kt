package com.masroufi.app.ui.icons

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TzIconsTest {
    @Test fun allIconsAre24Grid() {
        val vectors = TzIcons.all.flatMap { it.allVectors() }
        assertTrue(vectors.isNotEmpty())
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

    @Test fun filledTodayHasBodyAndInk() {
        // Body: the ticket outline; ink: the perforation, drawn on its own layer.
        assertEquals(1, TzIcons.today.vector(1.9f, filled = true).root.size)
        assertEquals(2, TzIcons.today.vector(1.9f).root.size)
        assertEquals(1, TzIcons.today.ink(1.9f)!!.root.size)
        assertNotNull(TzIcons.today.ink(2.2f))
        assertEquals(null, TzIcons.flame.ink(1.9f))
    }

    @Test fun directionalIconsAutoMirror() {
        TzIcons.back.allVectors().forEach { assertTrue(it.autoMirror) }
        TzIcons.chevron.allVectors().forEach { assertTrue(it.autoMirror) }
        TzIcons.delete.allVectors().forEach { assertFalse(it.autoMirror) }
    }

    @Test fun strokeBySize() {
        assertEquals(2.2f, strokeFor(14.dp))
        assertEquals(2.2f, strokeFor(18.dp))
        assertEquals(1.9f, strokeFor(18.5.dp))
        assertEquals(1.9f, strokeFor(24.dp))
    }
}
