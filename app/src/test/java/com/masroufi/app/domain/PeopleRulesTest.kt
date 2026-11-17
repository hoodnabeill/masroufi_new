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
}
