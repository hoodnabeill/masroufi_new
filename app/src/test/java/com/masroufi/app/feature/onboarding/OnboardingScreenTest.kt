package com.masroufi.app.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun paydayEchoShowsDaysAndTapPicks() {
        val picked = mutableListOf<Payday>()
        val actions = object : OnboardingActions {
            override fun pickPayday(payday: Payday) { picked += payday }
        }
        rule.setContent { MasroufiTheme { PaydayStep(OnboardingUi(loaded = true, step = STEP_PAYDAY, daysLeft = 13), actions) } }
        rule.onNodeWithText("يعني فاضل 13 يوم على القبض الجاي.").assertExists()
        rule.onNodeWithText("5").performClick()
        assertEquals(listOf<Payday>(Payday.Day(5)), picked)
    }

    @Test fun shortfallShowsCopy() {
        val reveal = RevealUi(amount = 0, endOfMonth = true, shortfall = 40_000, days = 13, paydayDay = 25)
        val state = OnboardingUi(
            loaded = true, step = STEP_REVEAL, money = AmountInput("2000"), fixed = mapOf("الإيجار" to 240_000L), reveal = reveal,
        )
        rule.setContent { MasroufiTheme { RevealStep(state, object : OnboardingActions {}) } }
        // The ticket's upper part is one TalkBack node, so the line lives in the unmerged tree.
        rule.onNodeWithText("اللي معاك أقل من اللي لازم يتدفع بـ 400. ولا يهمك، نرتّبها سوا.", useUnmergedTree = true).assertExists()
    }
}
