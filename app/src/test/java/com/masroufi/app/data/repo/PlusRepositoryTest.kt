package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.entitlements.ActiveSubscription
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.PurchaseResult
import com.masroufi.app.entitlements.StubBilling
import com.masroufi.app.entitlements.TrialState
import com.masroufi.app.entitlements.plusOn
import com.masroufi.app.entitlements.trialInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** First open day is 2026-11-10 (day 1), so day 7 of use is 2026-11-16. */
@RunWith(AndroidJUnit4::class)
class PlusRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val day1 = LocalDate.of(2026, 11, 10)
    private val day7 = day1.plusDays(6)
    private val clock = TestClock(ZonedDateTime.of(day1, LocalTime.of(9, 0), zone).toInstant(), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private suspend fun TestScope.setUp(onboarded: Boolean = true) {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        f.settings.current() // the first read stamps the first open day (day 1)
        if (onboarded) f.settings.setOnboarded(true)
    }

    private fun on(date: LocalDate) {
        clock.instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()
    }

    private suspend fun startTrialOnDay7(): LocalDate {
        on(day7)
        assertTrue(f.plusRepo.startTrial(day7))
        return day7.plusDays(13)
    }

    private fun settings(vararg p: Pair<String, Long?>) = Settings(
        proverbSeed = 1, firstOpenDay = day1.toEpochDay(),
        trialStartDay = p.firstOrNull { it.first == "start" }?.second, trialEndDay = p.firstOrNull { it.first == "end" }?.second,
    )

    // --- trial state machine ---------------------------------------------------------------------------------

    @Test fun trialIsOfferedFromDaySevenOfUse() {
        val s = settings()
        assertEquals(TrialState.NOT_YET, trialInfo(s, day1).state)
        assertEquals(TrialState.NOT_YET, trialInfo(s, day7.minusDays(1)).state)
        assertEquals(TrialState.OFFERED, trialInfo(s, day7).state)
        assertEquals("the offer stays", TrialState.OFFERED, trialInfo(s, day7.plusDays(30)).state)
    }

    @Test fun trialLastsFourteenDaysThenEnds() {
        val end = day7.plusDays(13)
        val s = settings("start" to day7.toEpochDay(), "end" to end.toEpochDay())
        assertEquals(TrialState.ACTIVE, trialInfo(s, day7).state)
        assertEquals(13, trialInfo(s, day7).daysLeft)
        assertEquals(TrialState.ACTIVE, trialInfo(s, end).state)
        assertEquals(0, trialInfo(s, end).daysLeft)
        assertEquals(TrialState.ENDED, trialInfo(s, end.plusDays(1)).state)
    }

    @Test fun endsSoonLineStartsThreeDaysBeforeTheEnd() {
        val end = day7.plusDays(13)
        val s = settings("start" to day7.toEpochDay(), "end" to end.toEpochDay())
        assertFalse(trialInfo(s, end.minusDays(4)).endsSoon)
        assertTrue(trialInfo(s, end.minusDays(3)).endsSoon)
        assertTrue(trialInfo(s, end).endsSoon)
        assertFalse("ended is not ending soon", trialInfo(s, end.plusDays(1)).endsSoon)
    }

    @Test fun startingNeedsTheOfferAndHappensOnce() = runTest {
        setUp()
        on(day7.minusDays(1))
        assertFalse("day 6 is too early", f.plusRepo.startTrial())
        on(day7)
        assertTrue(f.plusRepo.startTrial())
        val info = f.plusRepo.trialInfoNow()
        assertEquals(listOf(TrialState.ACTIVE, day7, day7.plusDays(13)), listOf(info.state, info.startDay, info.endDay))
        on(day7.plusDays(2))
        assertFalse("one trial per phone", f.plusRepo.startTrial())
        assertEquals(day7, f.plusRepo.trialInfoNow().startDay)
    }

    // --- the paywall's own opening ---------------------------------------------------------------------------

    @Test fun paywallOpensOnlyTheDayBeforeAndOnTheEndDay() = runTest {
        setUp()
        val end = startTrialOnDay7()
        assertFalse(f.plusRepo.shouldAutoOpenPaywall(day7))
        assertFalse(f.plusRepo.shouldAutoOpenPaywall(end.minusDays(2)))
        assertTrue(f.plusRepo.shouldAutoOpenPaywall(end.minusDays(1)))
        assertTrue(f.plusRepo.shouldAutoOpenPaywall(end))
        assertFalse("after the end only Settings or a feature opens it", f.plusRepo.shouldAutoOpenPaywall(end.plusDays(1)))
    }

    @Test fun paywallOpensOncePerDay() = runTest {
        setUp()
        val end = startTrialOnDay7()
        on(end.minusDays(1))
        assertTrue(f.plusRepo.shouldAutoOpenPaywall())
        f.plusRepo.markPaywallShown()
        assertFalse(f.plusRepo.shouldAutoOpenPaywall())
        on(end)
        assertTrue("the end day is another day", f.plusRepo.shouldAutoOpenPaywall())
        f.plusRepo.markPaywallShown()
        assertFalse(f.plusRepo.shouldAutoOpenPaywall())
    }

    @Test fun paywallNeverOpensBeforeOnboardingEnds() = runTest {
        setUp(onboarded = false)
        val end = startTrialOnDay7()
        on(end)
        assertFalse(f.plusRepo.shouldAutoOpenPaywall())
        f.settings.setOnboarded(true)
        assertTrue(f.plusRepo.shouldAutoOpenPaywall())
    }

    @Test fun paywallNeverOpensInEndOfMonthMode() = runTest {
        setUp()
        val end = startTrialOnDay7()
        on(end)
        f.settings.eomSwitchOn(end.toEpochDay(), end.plusDays(10).toEpochDay())
        assertFalse(f.plusRepo.shouldAutoOpenPaywall())
        f.settings.eomSwitchOff()
        assertTrue(f.plusRepo.shouldAutoOpenPaywall())
        db.dayTicketDao().insert(DayTicketEntity(date = end, amount = 0, base = 0, endOfMonth = true))
        assertFalse("an end-of-month row is the mode too", f.plusRepo.shouldAutoOpenPaywall())
    }

    @Test fun paywallNeverOpensTheDayAfterAnOverspentDay() = runTest {
        setUp()
        val end = startTrialOnDay7()
        on(end)
        val over = db.dayTicketDao().insert(
            DayTicketEntity(date = end.minusDays(1), amount = 10_000, base = 10_000, spent = 12_000, status = TicketStatus.CLOSED_BY_HAND),
        )
        assertFalse(f.plusRepo.shouldAutoOpenPaywall())
        db.dayTicketDao().delete(over)
        db.dayTicketDao().insert(
            DayTicketEntity(date = end.minusDays(1), amount = 10_000, base = 10_000, spent = 10_000, status = TicketStatus.CLOSED_BY_HAND),
        )
        assertTrue("exactly on the ticket is not overspent", f.plusRepo.shouldAutoOpenPaywall())
    }

    @Test fun paywallNeverOpensWithASubscription() = runTest {
        setUp()
        val end = startTrialOnDay7()
        on(end)
        assertEquals(PurchaseResult.Success, f.billing.purchase(PlusPlan.MONTHLY))
        assertFalse(f.plusRepo.shouldAutoOpenPaywall())
    }

    // --- entitlement sources ---------------------------------------------------------------------------------

    @Test fun plusIsASubscriptionOrAnActiveTrialOrTheDebugOverride() {
        val none = settings()
        val trial = settings("start" to day7.toEpochDay(), "end" to day7.plusDays(13).toEpochDay())
        val sub = ActiveSubscription(PlusPlan.YEARLY, day7.plusYears(1))
        assertFalse(plusOn(none, null, false, day7))
        assertTrue("subscription", plusOn(none, sub, false, day7))
        assertTrue("trial", plusOn(trial, null, false, day7))
        assertFalse("trial over", plusOn(trial, null, false, day7.plusDays(14)))
        assertTrue("override", plusOn(none, null, true, day7))
        assertTrue("subscription outlives the trial", plusOn(trial, sub, false, day7.plusDays(20)))
    }

    @Test fun repositoryCombinesBillingTrialAndOverride() = runTest {
        setUp()
        val plus = f.plusRepo
        suspend fun until(want: Boolean) = withTimeout(10_000) { plus.isPlus.first { it == want } }

        until(false)
        plus.setDebugOverride(true)
        until(true)
        plus.setDebugOverride(false)
        until(false)

        on(day7)
        assertTrue(plus.startTrial())
        until(true)
        on(day7.plusDays(14))
        f.settings.setLastPaywallDay(0) // any settings write re-evaluates; the trial is over on this day
        until(false)

        assertEquals(PurchaseResult.Success, f.billing.purchase(PlusPlan.YEARLY))
        until(true)
        f.billing.clearSubscription()
        until(false)
    }

    @Test fun debugEntitlementsKeepsTheGalleryToggleOnTopOfThem() = runTest {
        setUp()
        val e = DebugEntitlements(plus = f.plusRepo)
        assertFalse(e.isPlus.first())
        e.setDebugOverride(true)
        withTimeout(10_000) { e.isPlus.first { it } }
        e.setDebugOverride(false)
        withTimeout(10_000) { e.isPlus.first { !it } }
    }

    @Test fun releaseStubHasNoProductsAndNeverSells() = runTest {
        val release = StubBilling(debug = false, clock = clock)
        assertTrue(release.products.value.isEmpty())
        assertEquals(PurchaseResult.Failed, release.purchase(PlusPlan.YEARLY))
        assertNull(release.subscription.value)
        assertNull(release.restore())
    }

    @Test fun debugStubSellsTheMocksTwoPricesAndTheOutcomeIsControllable() = runTest {
        val debug = StubBilling(debug = true, clock = clock)
        assertEquals(listOf(PlusPlan.YEARLY, PlusPlan.MONTHLY), debug.products.value.map { it.plan })
        assertTrue(debug.products.value.all { it.debug && it.priceText.contains("(debug)") })
        debug.nextOutcome = PurchaseResult.Cancelled
        assertEquals(PurchaseResult.Cancelled, debug.purchase(PlusPlan.YEARLY))
        assertNull(debug.subscription.value)
        debug.nextOutcome = PurchaseResult.Success
        assertEquals(PurchaseResult.Success, debug.purchase(PlusPlan.MONTHLY))
        assertEquals(ActiveSubscription(PlusPlan.MONTHLY, day1.plusMonths(1)), debug.subscription.value)
        assertEquals(debug.subscription.value, debug.restore())
    }

    // --- nothing is deleted when Plus ends -------------------------------------------------------------------

    @Test fun plusDataStaysReadableAfterPlusEnds() = runTest {
        val plus = DebugEntitlements(true)
        f = RepoFixture(db, backgroundScope, tmp.root, clock, plus)
        repeat(5) { f.envelopes.create("ظرف $it", 0, 1_000, com.masroufi.app.data.db.EnvelopeKind.SPENDING, emptySet()) }
        val person = f.people.addPerson("خالتي")
        val wedding = f.duty.addOccasion("فرحي", day7, OccasionOwner.MINE)
        f.duty.addGift(wedding, person, 50_000, com.masroufi.app.data.db.GiftDirection.RECEIVED)
        val seasonId = f.seasons.createPersonal("مصيف", day7.plusDays(40), 500_000)

        plus.setDebugOverride(false)

        assertEquals("a fifth envelope stays", 5, f.envelopes.observe().first().size)
        assertEquals(1, f.duty.observe().first().single().gifts.size)
        assertTrue(f.seasons.observe().first().any { it.season.id == seasonId })
        assertTrue("the sixth cannot be made on Free", runCatching {
            f.envelopes.create("ظرف جديد", 0, 1_000, com.masroufi.app.data.db.EnvelopeKind.SPENDING, emptySet())
        }.exceptionOrNull() is PlusRequired)
    }
}
