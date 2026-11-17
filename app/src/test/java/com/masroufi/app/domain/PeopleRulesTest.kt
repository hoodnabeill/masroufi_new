package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PeopleRulesTest {
    @Test fun equalSharesAddUpAndSpreadTheExtraPiastres() {
        assertEquals(listOf(3_334L, 3_333L, 3_333L), splitEqually(10_000, 3))
        assertEquals(listOf(5_000L, 5_000L), splitEqually(10_000, 2))
        assertEquals(listOf(1L, 0L, 0L), splitEqually(1, 3))
        assertThrows(IllegalArgumentException::class.java) { splitEqually(100, 0) }
    }

    @Test fun sharedSubscriptionGroupName() = assertEquals("نتفليكس · 2 صحاب", sharedGroupName("نتفليكس", 2))

    @Test fun debtEntriesNeverPunchOrCountAsSpent() {
        val debt = EntryForDay(EntryKind.DEBT, -50_000, 0, 0, true, null)
        assertEquals(false, debt.punches)
        assertEquals(0L, spentToday(listOf(debt, debt.copy(amount = 50_000))))
    }

    @Test fun sharesAreEqualUntilTypedOverAndAlwaysAddUp() {
        val equal = computeShares(10_000, listOf(0, 1, 2), emptyMap())!!
        assertEquals(10_000L, equal.values.sum())
        assertEquals(listOf(3_334L, 3_333L, 3_333L), equal.values.toList())
        // 1 is fixed at 40: the rest (60) is shared by 0 and 2.
        val edited = computeShares(10_000, listOf(0, 1, 2), mapOf(1L to 4_000L))!!
        assertEquals(mapOf(0L to 3_000L, 1L to 4_000L, 2L to 3_000L), edited)
        assertEquals(null, computeShares(10_000, listOf(0, 1), mapOf(1L to 10_001L)))
        assertEquals("all fixed and short", null, computeShares(10_000, listOf(0, 1), mapOf(0L to 1_000L, 1L to 1_000L)))
        assertEquals("all fixed and exact", mapOf(0L to 6_000L, 1L to 4_000L), computeShares(10_000, listOf(0, 1), mapOf(0L to 6_000L, 1L to 4_000L)))
        assertEquals(null, computeShares(0, listOf(0), emptyMap()))
    }
}
