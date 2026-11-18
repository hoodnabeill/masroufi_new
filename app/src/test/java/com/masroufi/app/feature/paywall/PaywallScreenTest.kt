package com.masroufi.app.feature.paywall

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.repo.LossRow
import com.masroufi.app.entitlements.BillingProduct
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.TrialState
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.Haptics
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

private class Felt : Haptics {
    val levels = mutableListOf<HapticLevel>()
    override fun perform(level: HapticLevel) { levels += level }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaywallScreenTest {
    @get:Rule val rule = createComposeRule()

    private val plans = plansUi(
        listOf(BillingProduct(PlusPlan.YEARLY, "399 ج", 399_000_000, "EGP"), BillingProduct(PlusPlan.MONTHLY, "49 ج", 49_000_000, "EGP")),
    )

    private fun ui(
        plans: List<PlanUi> = this.plans,
        purchase: PurchaseState = PurchaseState.Idle,
        close: Boolean = false,
        trial: Boolean = true,
    ) = PaywallUi(
        loading = false,
        trialState = if (trial) TrialState.ACTIVE else TrialState.NOT_YET,
        daysLeft = 1, endDay = LocalDate.of(2026, 11, 2),
        headline = if (trial) Headline.Tomorrow else Headline.Adds,
        rows = listOf(PaywallRow.Loss(LossRow.RoundUps(8_600, 1), null), PaywallRow.Loss(LossRow.Gold(37, 490_000, 2), null)),
        plans = plans, purchase = purchase, close = close,
    )

    private fun show(
        state: PaywallUi, felt: Felt = Felt(), onSelect: (PlusPlan) -> Unit = {}, onBuy: () -> Unit = {}, onClose: () -> Unit = {},
    ) = rule.setContent {
        MasroufiTheme { CompositionLocalProvider(LocalHaptics provides felt) { PaywallContent(state, onSelect, onBuy, onClose) } }
    }

    @Test fun theTrialTagTheHeadlineAndTheUsersNumbersShow() {
        show(ui())
        rule.onNodeWithText("التجربة بتخلص بكره").assertExists()
        rule.onNodeWithText("بعد بكره، دول هيقفوا").assertExists()
        rule.onNodeWithText("86 ج").assertExists()
        rule.onNodeWithText("0.37 جم").assertExists()
        rule.onNodeWithText("الفكة اللي بتتحوّش لوحدها").assertExists()
    }

    @Test fun withoutATrialThereIsNoTagAndNoClaimThatAnythingStops() {
        show(ui(trial = false))
        rule.onNodeWithText("التجربة بتخلص بكره").assertDoesNotExist()
        rule.onNodeWithText("بعد بكره، دول هيقفوا").assertDoesNotExist()
        rule.onNodeWithText("ده اللي بلس بيضيفه على أرقامك").assertExists()
    }

    @Test fun theDataLineIsAlwaysThereAndSoIsTheGooglePlayLine() {
        val line = "بياناتك كلها هتفضل محفوظة ومش هتتمسح. اللي هيقف هو المزايا دي بس."
        show(ui())
        rule.onNodeWithText(line).assertExists()
        rule.onNodeWithText("بتتجدد لوحدها كل سنة. تلغي في أي وقت من جوجل بلاي.").assertExists()
    }

    @Test fun theDataLineStaysWhenPricesAreUnavailable() {
        show(ui(plans = emptyList()))
        rule.onNodeWithText("بياناتك كلها هتفضل محفوظة ومش هتتمسح. اللي هيقف هو المزايا دي بس.").assertExists()
    }

    @Test fun bothWaysOutAreBigEnoughAndCloseWithoutAsking() {
        var closed = 0
        show(ui(), onClose = { closed++ })
        rule.onNodeWithTag(PaywallCloseTag).assertHeightIsAtLeast(44.dp).performClick()
        assertEquals(1, closed)
        rule.onNodeWithTag(PaywallNoThanksTag).performScrollTo().assertHeightIsAtLeast(44.dp).performClick()
        assertEquals(2, closed)
        rule.onNodeWithText("لا شكرًا، كمّل بالمجاني").assertExists()
    }

    @Test fun theStorePricesTheSavingAndThePerMonthLineAreShown() {
        show(ui())
        rule.onNodeWithText("وفّر 32%").assertExists()
        rule.onNodeWithText("يعني 33 جنيه في الشهر").assertExists()
        rule.onNodeWithText("تتجدد كل شهر").assertExists()
        rule.onNodeWithText("399 ج").assertExists()
        rule.onNodeWithText("49 ج").assertExists()
    }

    @Test fun unavailablePricesSayTheirPlainLineAndTheButtonIsOff() {
        var bought = 0
        show(ui(plans = emptyList()), onBuy = { bought++ })
        rule.onNodeWithText("الأسعار لسه مش متاحة. جرّب تاني بعد شوية.").assertExists()
        rule.onNodeWithText("وفّر 32%").assertDoesNotExist()
        rule.onNodeWithTag(PaywallButtonTag).performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(0, bought)
    }

    @Test fun selectingAPlanTicksAndTheButtonConfirms() {
        val felt = Felt()
        val picked = mutableListOf<PlusPlan>()
        var bought = 0
        show(ui(), felt, onSelect = { picked += it }, onBuy = { bought++ })
        rule.onNodeWithTag("paywall_plan_MONTHLY").performScrollTo().performClick()
        assertEquals(listOf(PlusPlan.MONTHLY), picked)
        assertEquals(listOf(HapticLevel.Tick), felt.levels)
        rule.onNodeWithTag(PaywallButtonTag).performScrollTo().performClick()
        assertEquals(1, bought)
        assertEquals(listOf(HapticLevel.Tick, HapticLevel.Confirm), felt.levels)
    }

    @Test fun aCancelledOrFailedPurchaseSaysNothingWasCharged() {
        show(ui(purchase = PurchaseState.Failed))
        rule.onNodeWithText("الدفع ماكملش ومتخصمش منك حاجة.").performScrollTo().assertExists()
        rule.onNodeWithTag(PaywallButtonTag).performScrollTo().performClick() // the retry is allowed
    }

    @Test fun aSuccessPlaysTheMilestoneOnceAndTheScreenClosesWhenTold() {
        val felt = Felt()
        var closed = 0
        var state by mutableStateOf(ui(purchase = PurchaseState.Done))
        rule.setContent {
            MasroufiTheme { CompositionLocalProvider(LocalHaptics provides felt) { PaywallContent(state, {}, {}, { closed++ }) } }
        }
        rule.waitForIdle()
        assertEquals(listOf(HapticLevel.Milestone), felt.levels)
        assertEquals("not closed until the ViewModel says so", 0, closed)
        state = ui(purchase = PurchaseState.Done, close = true)
        rule.waitForIdle()
        assertEquals(1, closed)
        assertEquals("still one Milestone", listOf(HapticLevel.Milestone), felt.levels)
    }

    @Test fun rowsArriveSixtyMillisecondsApart() {
        assertEquals(60L, ROW_STAGGER_MS)
        assertTrue(CLOSE_AFTER_PURCHASE_MS == 900L)
    }
}
