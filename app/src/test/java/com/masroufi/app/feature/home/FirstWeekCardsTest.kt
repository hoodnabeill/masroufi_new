package com.masroufi.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FirstWeekCardsTest {
    private val first = LocalDate.of(2026, 11, 10)

    private fun card(
        entries: Int = 0,
        moneyUnknown: Boolean = false,
        dismissed: Set<String> = emptySet(),
        daysAfter: Long = 0,
        hour: Int = 12,
        open: Boolean = true,
        eom: Boolean = false,
    ) = firstWeekCard(entries, moneyUnknown, dismissed, first.toEpochDay(), first.plusDays(daysAfter), hour, open, eom)

    @Test fun cardOrderAndOneAtATime() {
        // Everything applies at once: money first, then each in priority order as the earlier ones are closed.
        val closed = mutableSetOf<String>()
        val seen = mutableListOf<FirstWeekCard>()
        while (true) {
            val c = card(entries = 5, moneyUnknown = true, dismissed = closed, daysAfter = 6, hour = 20) ?: break
            seen += c
            closed += c.id
        }
        assertEquals(
            listOf(FirstWeekCard.Money, FirstWeekCard.FirstEntry, FirstWeekCard.Day2, FirstWeekCard.Day3, FirstWeekCard.Day5),
            seen,
        )
        assertEquals(FirstWeekCard.FirstEvening, card(entries = 0, hour = 19))
        assertNull(card())
    }

    @Test fun dismissedNeverReturns() {
        assertEquals(FirstWeekCard.FirstEntry, card(entries = 1))
        assertNull(card(entries = 1, dismissed = setOf("first_entry")))
        assertNull(card(entries = 9, dismissed = setOf("first_entry"), daysAfter = 0, hour = 12))
    }

    @Test fun noCardsDuringEndOfMonth() {
        assertNull(card(entries = 5, moneyUnknown = true, daysAfter = 6, hour = 20, eom = true))
    }

    @Test fun moneyCardAfterThreeEntries() {
        assertNull(card(entries = 2, moneyUnknown = true, dismissed = setOf("first_entry")))
        assertEquals(FirstWeekCard.Money, card(entries = 3, moneyUnknown = true))
        assertEquals(FirstWeekCard.FirstEntry, card(entries = 3, moneyUnknown = false))
    }
}
