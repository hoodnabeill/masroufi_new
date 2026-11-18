package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumRulesTest {
    @Test fun progressRunsThroughTheCurrentLevelAndIsFullAtTheTop() {
        assertEquals(0f, levelProgress(0), 0f)
        assertEquals(0f, levelProgress(7), 0f)
        assertEquals(0.5f, levelProgress(30), 1e-6f) // level 3 runs 20 to 40
        assertEquals(1f, levelProgress(365), 0f)
        assertNull(nextLevelAt(365))
    }

    @Test fun hintsFollowTheMockOrderAndDropWhatIsOwned() {
        val all = ProverbCatalog.all.map { it.number }.toSet()
        assertEquals(listOf(LockHint.TOMORROW, LockHint.FULL_WEEK, LockHint.RARE), lockHints(emptySet()))
        assertEquals(listOf(LockHint.TOMORROW, LockHint.RARE), lockHints(setOf(45)))
        assertEquals(listOf(LockHint.TOMORROW, LockHint.FULL_WEEK), lockHints(setOf(49, 50)))
        assertEquals(emptyList<LockHint>(), lockHints(all))
        assertEquals(listOf(LockHint.TOMORROW), lockHints(setOf(45, 49, 50)))
    }
}
