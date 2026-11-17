package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProverbCatalogTest {
    @Test fun catalogHas50Unique() {
        assertEquals((1..50).toList(), ProverbCatalog.all.map { it.number }.sorted())
        assertEquals(50, PROVERB_SET_SIZE)
    }

    @Test fun textsAreCleanAndNotBlank() {
        ProverbCatalog.all.forEach {
            for (t in listOf(it.text, it.line)) {
                assertTrue("blank text in ${it.number}", t.isNotBlank())
                assertFalse("dash in ${it.number}", "—" in t)
                assertFalse("rare marker in ${it.number}", "(نادر)" in t)
            }
        }
    }

    @Test fun rareCards() {
        assertEquals(setOf(45, 49, 50), ProverbCatalog.rare)
        assertEquals(ProverbCatalog.rare, ProverbCatalog.all.filter { it.rare }.map { it.number }.toSet())
    }

    @Test fun setsFollowHeadings() {
        val sizes = ProverbCatalog.all.groupingBy { it.set }.eachCount()
        assertEquals(
            mapOf(
                ProverbSet.SAVING to 12, ProverbSet.SPENDING to 12, ProverbSet.DEBT to 5,
                ProverbSet.WORK to 8, ProverbSet.PEOPLE to 8, ProverbSet.PATIENCE to 5,
            ),
            sizes,
        )
    }
}
