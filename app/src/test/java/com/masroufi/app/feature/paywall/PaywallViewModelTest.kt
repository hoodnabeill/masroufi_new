package com.masroufi.app.feature.paywall

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.repo.FeaturePitch
import com.masroufi.app.data.repo.LossRow
import com.masroufi.app.data.repo.LossRows
import com.masroufi.app.data.repo.PLAIN_FEATURES
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.entitlements.ActiveSubscription
import com.masroufi.app.entitlements.Billing
import com.masroufi.app.entitlements.BillingProduct
import com.masroufi.app.entitlements.MANAGE_SUBSCRIPTION_URL
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.PurchaseResult
import com.masroufi.app.entitlements.StubBilling
import com.masroufi.app.entitlements.TrialInfo
import com.masroufi.app.entitlements.TrialState
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

private class FakeBilling(var result: PurchaseResult = PurchaseResult.Success) : Billing {
    override val products = MutableStateFlow(
        listOf(
            BillingProduct(PlusPlan.YEARLY, "399 ج", 399_000_000, "EGP"),
            BillingProduct(PlusPlan.MONTHLY, "49 ج", 49_000_000, "EGP"),
        ),
    )
    override val subscription = MutableStateFlow<ActiveSubscription?>(null)
    val bought = mutableListOf<PlusPlan>()
    override suspend fun purchase(plan: PlusPlan): PurchaseResult {
        bought += plan
        return result
    }
    override suspend fun restore(): ActiveSubscription? = subscription.value
    override fun manageSubscriptionUrl() = MANAGE_SUBSCRIPTION_URL
}

/** Today is 2026-11-01 (PlanVmTestBase); with no trial the usage window is the last 14 days. */
@RunWith(AndroidJUnit4::class)
class PaywallViewModelTest : PlanVmTestBase() {
    private lateinit var vm: PaywallViewModel
    private val ui get() = vm.state.value
    private val today = LocalDate.of(2026, 11, 1)

    private fun open(feature: PlusFeature? = null, billing: Billing = f.billing): PaywallViewModel {
        vm = make(PaywallViewModel::class, { it.state }) {
            PaywallViewModel(billing, f.lossRows, f.plusRepo, clock, SavedStateHandle(mapOf("feature" to (feature?.name ?: ""))))
        }
        settle("loaded") { !ui.loading }
        return vm
    }

    /** A gold row and a round-up row: the user used the jar in the window. */
    private fun jarData() = runBlocking {
        f.jar.setGoldPrice(490_000, today.toEpochDay())
        f.jar.deposit(175_400)
        db.jarDao().insert(JarMovementEntity(pot = Pot.JAR, amount = 8_600, source = JarSource.ROUND_UP, at = clock.millis(), updatedAt = clock.millis()))
    }

    /** A running trial that ends [daysLeft] days from today. */
    private fun trial(daysLeft: Long) = runBlocking {
        f.settings.current() // stamps the first open day
        assertTrue(f.settings.startTrial(today.plusDays(daysLeft - 13).toEpochDay(), today.plusDays(daysLeft).toEpochDay()))
    }

    // --- rows ------------------------------------------------------------------------------------------------

    @Test fun rowsComeFromTheUsersOwnData() {
        jarData()
        open()
        assertFalse(ui.plain)
        assertEquals(listOf(PlusFeature.GOLD, PlusFeature.ROUND_UPS), ui.rows.map { it.feature })
        assertTrue(ui.rows.all { it is PaywallRow.Loss && it.pitch == null })
        assertEquals("no trial and nothing to lose: it says what Plus adds", Headline.Adds, ui.headline)
        assertFalse(ui.stops)
    }

    @Test fun withNothingUsedItIsThePlainVersionWithThreeFeatures() {
        open()
        assertTrue(ui.plain)
        assertEquals(Headline.Plain, ui.headline)
        assertEquals(PLAIN_FEATURES, ui.rows.map { it.feature })
        assertTrue(ui.rows.all { it is PaywallRow.Pitch })
    }

    @Test fun openedFromAFeatureItComesFirst() {
        jarData()
        open(PlusFeature.GOLD)
        val top = ui.rows.first()
        assertTrue("the gold row itself, not a copy", top is PaywallRow.Loss && top.row is LossRow.Gold)
        assertNotNull("with the pitch's line", (top as PaywallRow.Loss).pitch)
        assertEquals("nothing shown twice", 2, ui.rows.size)
    }

    @Test fun openedFromAFeatureWithoutARowItGetsOneOfItsOwnOnTop() {
        jarData()
        open(PlusFeature.GAMEYA)
        assertEquals(listOf(PlusFeature.GAMEYA, PlusFeature.GOLD, PlusFeature.ROUND_UPS), ui.rows.map { it.feature })
        assertTrue(ui.rows.first() is PaywallRow.Pitch)
    }

    @Test fun openedFromAFeatureWithNothingToShowItIsPlainAndItComesFirst() {
        open(PlusFeature.KIDS)
        assertTrue(ui.plain)
        assertEquals(listOf(PlusFeature.KIDS, PlusFeature.GOLD, PlusFeature.SEASONS), ui.rows.map { it.feature })
    }

    @Test fun neverMoreThanFiveRowsEvenWithTheFeatureOnTop() {
        val rows = listOf(
            LossRow.Gold(37, 490_000, 5), LossRow.RoundUps(8_600, 4), LossRow.Seasons(5, "رمضان", 120_000, 88, 3),
            LossRow.Duty(38, null, null, 2), LossRow.SafetyTicket(13, 1),
        )
        val shown = paywallRows(LossRows(rows, FeaturePitch(PlusFeature.KIDS), PLAIN_FEATURES))
        assertEquals(5, shown.size)
        assertEquals(PlusFeature.KIDS, shown.first().feature)
        assertEquals("the least used row made room", rows.dropLast(1).map { it.feature }, shown.drop(1).map { it.feature })
    }

    // --- headline and trial tag ---------------------------------------------------------------------------------

    @Test fun theHeadlineIsOnlyTrueForTheTrialsState() {
        fun info(state: TrialState, left: Int) = TrialInfo(state, today, today.plusDays(left.toLong()), left, left <= 3)
        assertEquals(Headline.Tomorrow, headlineFor(info(TrialState.ACTIVE, 1), plain = false))
        assertEquals("on the last day it is from tomorrow", Headline.Today, headlineFor(info(TrialState.ACTIVE, 0), plain = false))
        assertEquals(Headline.Later, headlineFor(info(TrialState.ACTIVE, 5), plain = false))
        assertEquals(Headline.Ended, headlineFor(info(TrialState.ENDED, 0), plain = false))
        assertEquals("never a claim that something stops", Headline.Adds, headlineFor(info(TrialState.OFFERED, 0), plain = false))
        assertEquals(Headline.Plain, headlineFor(info(TrialState.ACTIVE, 1), plain = true))
    }

    @Test fun aTrialEndingTomorrowGivesTheMocksHeadlineAndDashedRows() {
        jarData()
        trial(daysLeft = 1)
        open()
        assertEquals(TrialState.ACTIVE, ui.trialState)
        assertEquals(listOf(Headline.Tomorrow, 1), listOf(ui.headline, ui.daysLeft))
        assertEquals(today.plusDays(1), ui.endDay)
        assertTrue(ui.stops)
    }

    // --- plans ---------------------------------------------------------------------------------------------------

    @Test fun savingsAndThePerMonthLineComeFromTheStoresMicros() {
        open()
        val (year, month) = ui.plans
        assertEquals(listOf(PlusPlan.YEARLY, PlusPlan.MONTHLY), listOf(year.plan, month.plan))
        assertEquals("the store's own text", "399 جنيه (debug)", year.priceText)
        assertEquals("1 - 399 / (12 x 49), rounded down", 32, year.savePercent)
        assertEquals("399 / 12 = 33.25", 33L, year.perMonth)
        assertEquals(listOf(null, null), listOf(month.savePercent, month.perMonth))
        assertEquals(PlusPlan.YEARLY, ui.selected)
    }

    @Test fun otherPricesGiveOtherNumbers() {
        fun p(plan: PlusPlan, micros: Long) = BillingProduct(plan, "x", micros, "USD")
        val half = plansUi(listOf(p(PlusPlan.MONTHLY, 100_000_000), p(PlusPlan.YEARLY, 600_000_000))).first()
        assertEquals(listOf(50, 50L, "USD"), listOf(half.savePercent, half.perMonth, half.currency))
        val noSaving = plansUi(listOf(p(PlusPlan.MONTHLY, 100_000_000), p(PlusPlan.YEARLY, 1_200_000_000))).first()
        assertNull("no saving, no tag", noSaving.savePercent)
        val yearlyOnly = plansUi(listOf(p(PlusPlan.YEARLY, 399_000_000))).single()
        assertNull("nothing to compare with", yearlyOnly.savePercent)
        assertEquals(33L, yearlyOnly.perMonth)
        assertEquals(listOf(PlusPlan.MONTHLY), plansUi(listOf(p(PlusPlan.MONTHLY, 49_000_000))).map { it.plan })
    }

    @Test fun aTapSelectsAPlanAndTheButtonBuysThatOne() {
        val billing = FakeBilling()
        open(billing = billing)
        vm.select(PlusPlan.MONTHLY)
        settle("monthly selected") { ui.selected == PlusPlan.MONTHLY }
        vm.buy()
        settle("bought") { billing.bought.isNotEmpty() }
        assertEquals(listOf(PlusPlan.MONTHLY), billing.bought)
    }

    @Test fun withoutStorePricesThereAreNoPlansAndTheButtonStaysOff() {
        val release = StubBilling(debug = false, clock = clock)
        open(billing = release)
        assertFalse(ui.pricesAvailable)
        assertTrue(ui.plans.isEmpty())
        assertFalse(ui.canBuy)
        vm.buy()
        settle("nothing started") { true }
        main.scheduler.advanceTimeBy(5_000)
        main.scheduler.runCurrent()
        assertEquals(PurchaseState.Idle, ui.purchase)
        assertNull(release.subscription.value)
    }

    // --- purchase --------------------------------------------------------------------------------------------------

    @Test fun aSuccessfulPurchaseClosesTheScreenNineHundredMillisecondsLater() {
        val billing = f.billing.also { it.nextOutcome = PurchaseResult.Success }
        open(billing = billing)
        vm.buy()
        settle("done") { ui.purchase == PurchaseState.Done }
        assertFalse(ui.close)
        assertEquals(PlusPlan.YEARLY, billing.subscription.value?.plan)
        main.scheduler.advanceTimeBy(899)
        main.scheduler.runCurrent()
        assertFalse("not yet at 899 ms", ui.close)
        assertEquals(PurchaseState.Done, ui.purchase)
        main.scheduler.advanceTimeBy(1)
        main.scheduler.runCurrent()
        settle("closed") { ui.close }
    }

    @Test fun cancelAndFailureStayOnTheScreenAndCanBeRetried() {
        val billing = f.billing
        open(billing = billing)
        billing.nextOutcome = PurchaseResult.Cancelled
        vm.buy()
        settle("cancelled") { ui.purchase == PurchaseState.Failed }
        main.scheduler.advanceTimeBy(5_000)
        main.scheduler.runCurrent()
        assertFalse("it never closes by itself", ui.close)
        assertNull("nothing was charged", billing.subscription.value)
        assertTrue("the button works again", ui.canBuy)

        billing.nextOutcome = PurchaseResult.Failed
        vm.buy()
        settle("buying") { ui.purchase == PurchaseState.Buying || ui.purchase == PurchaseState.Failed }
        settle("failed") { ui.purchase == PurchaseState.Failed }
        assertFalse(ui.close)

        billing.nextOutcome = PurchaseResult.Success
        vm.buy()
        settle("done") { ui.purchase == PurchaseState.Done }
        assertNotNull(billing.subscription.value)
    }

    @Test fun aSecondTapWhileBuyingBuysOnce() {
        val billing = FakeBilling()
        open(billing = billing)
        vm.buy()
        vm.buy()
        settle("done") { ui.purchase == PurchaseState.Done }
        main.scheduler.advanceTimeBy(1_000)
        main.scheduler.runCurrent()
        vm.buy()
        vm.select(PlusPlan.MONTHLY)
        main.scheduler.runCurrent()
        assertEquals("one purchase", listOf(PlusPlan.YEARLY), billing.bought)
        assertEquals("a plan cannot change after buying", PlusPlan.YEARLY, ui.selected)
    }
}
