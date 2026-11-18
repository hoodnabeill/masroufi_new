package com.masroufi.app.feature.home

import com.masroufi.app.core.PayCycle
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.WishEntity
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.repo.HomeData
import com.masroufi.app.data.repo.homeFreelance
import com.masroufi.app.data.repo.selfPayCardKey
import com.masroufi.app.domain.TomorrowBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Home after the freelancer's self-payday: the card says what moved, in the first-week card's slot. Today 2026-11-02. */
class HomeSelfPayTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val key = selfPayCardKey(YearMonth.of(2026, 11))

    private fun settings(
        paid: Long? = 700_000,
        entryId: Long? = 5,
        short: Boolean = false,
        on: Boolean = true,
        ritualFor: Long? = null,
        dismissed: Set<String> = emptySet(),
        seen: Set<String> = emptySet(),
        month: Long = 202611,
    ) = Settings(
        freelanceOn = on, selfSalary = 700_000, selfPayday = 1, selfPaidMonth = month, selfPayEntryId = entryId, selfPaid = paid,
        selfPayShort = short, lastRitualForEntryId = ritualFor, firstWeekCardsDismissed = dismissed, milestonesSeen = seen,
    )

    private fun card(s: Settings) = selfPayCard(homeFreelance(s, today), today)

    private fun data(s: Settings, entryCount: Int = 0) = HomeData(
        today = DayTicketEntity(date = today, amount = 23_300, base = 23_300),
        week = emptyList(), jarTotal = 0, emergencyTotal = 0, streak = 0, collected = 0, spendable = 700_000,
        cycle = PayCycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), unpaidBills = 0, repeats = emptyList(),
        tomorrow = TomorrowBasis(29, 0), lastTicketShown = today.toEpochDay(), entryCount = entryCount, moneyUnknown = false,
        dismissedCards = emptySet(), firstOpenDay = null, freelance = homeFreelance(s, today),
    )

    @Test fun fullPayday() {
        val c = checkNotNull(card(settings()))
        assertEquals(listOf(key, 700_000L, 700_000L, 5L, false), listOf(c.key, c.paid, c.salary, c.entryId, c.short))
        assertEquals("its haptic has not fired yet", false, c.landedSeen)
    }

    @Test fun shortVaultSaysWhatMovedAndStillOffersTheRitual() {
        val c = checkNotNull(card(settings(paid = 300_000, short = true)))
        assertEquals(listOf(300_000L, 700_000L, 5L, true), listOf(c.paid, c.salary, c.entryId, c.short))
    }

    @Test fun emptyVaultMovesNothingAndHasNoRitual() {
        val c = checkNotNull(card(settings(paid = 0, entryId = null, short = true)))
        assertEquals(listOf(0L, null, true), listOf(c.paid, c.entryId, c.short))
    }

    @Test fun hapticIsRememberedOnceSeen() {
        assertTrue(checkNotNull(card(settings(seen = setOf(key)))).landedSeen)
    }

    @Test fun cardGoesWhenTheRitualWasDoneOrSkippedOrTheCardClosed() {
        assertNull("ritual done or skipped for this entry", card(settings(ritualFor = 5)))
        assertNotNull("another entry's ritual does not count", card(settings(ritualFor = 4)))
        assertNull("closed", card(settings(dismissed = setOf(key))))
        assertNull("an empty vault's card is closed the same way", card(settings(paid = 0, entryId = null, short = true, dismissed = setOf(key))))
    }

    @Test fun noCardWhenTheModeIsOffOrItIsLastMonthsPayday() {
        assertNull(card(settings(on = false)))
        assertNull(card(settings(month = 202610)))
        assertNull("nothing moved and the vault was not short", card(settings(paid = 0, entryId = null, short = false)))
    }

    @Test fun homeShowsTheCardAndTheSourceLine() {
        val ready = homeUi(data(settings()), emptyList(), 10)
        assertEquals(5L, ready.selfPayCard?.entryId)
        assertTrue(ready.freelanceOn)
        assertEquals(false, homeUi(data(settings(on = false)), emptyList(), 10).freelanceOn)
    }

    @Test fun neverTwoCardsAtOnce() {
        val firstEntry = homeUi(data(settings(), entryCount = 1), emptyList(), 10)
        assertNotNull("a first-week card goes first", firstEntry.firstWeekCard)
        assertNull(firstEntry.selfPayCard)

        val wish = WishEntity(name = "x", price = 100, parkedAt = 0)
        val withOffer = homeUi(data(settings()), emptyList(), 10, dueWishes = listOf(wish), offer = true)
        assertNotNull(withOffer.selfPayCard)
        assertEquals("the end-of-month offer waits", false, withOffer.endOfMonthOffer)
        assertNull("so does the 48-hour question", withOffer.dueWish)

        val none = homeUi(data(settings(ritualFor = 5)), emptyList(), 10, dueWishes = listOf(wish), offer = true)
        assertNull(none.selfPayCard)
        assertTrue(none.endOfMonthOffer)
    }
}
