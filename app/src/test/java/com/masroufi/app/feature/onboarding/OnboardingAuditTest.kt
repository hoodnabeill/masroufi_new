package com.masroufi.app.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The five onboarding steps and their edge states at 200% font, right-to-left, animations off (Task 60). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingAuditTest {
    @get:Rule val rule = createComposeRule()

    private val actions = object : OnboardingActions {}

    private fun audit(content: @Composable () -> Unit) {
        rule.audit { content() }.assertNone()
    }

    private fun ui(step: Int) = OnboardingUi(loaded = true, step = step, daysLeft = 13)

    @Test fun welcome() = audit { WelcomeStep(actions) }
    @Test fun payday() = audit { PaydayStep(ui(STEP_PAYDAY), actions) }
    @Test fun paydayCustomDay() = audit { PaydayStep(ui(STEP_PAYDAY).copy(payday = Payday.Day(12)), actions) }
    @Test fun paydayNotFixed() = audit { PaydayStep(ui(STEP_PAYDAY).copy(payday = Payday.NotFixed), actions) }
    @Test fun paydayLastDay() = audit { PaydayStep(ui(STEP_PAYDAY).copy(payday = Payday.LastDay), actions) }
    @Test fun moneyEmpty() = audit { MoneyStep(ui(STEP_MONEY), actions) }
    @Test fun moneyTyped() = audit { MoneyStep(ui(STEP_MONEY).copy(money = AmountInput("1250.5")), actions) }
    @Test fun fixedEmpty() = audit { FixedStep(ui(STEP_FIXED), actions) }
    @Test fun fixedWithAnOpenKeypad() = audit {
        FixedStep(ui(STEP_FIXED).copy(fixed = mapOf("الإيجار" to 240_000L, "النت" to 30_000L), openFixed = "الجمعية", fixedInput = AmountInput("500")), actions)
    }

    private val reveal = RevealUi(amount = 24_000, endOfMonth = false, shortfall = 0, days = 13, paydayDay = 25)
    @Test fun reveal() = audit { RevealStep(ui(STEP_REVEAL).copy(money = AmountInput("5000"), fixed = mapOf("الإيجار" to 240_000L), reveal = reveal), actions) }
    @Test fun revealMoneySkippedNoReminder() = audit {
        RevealStep(ui(STEP_REVEAL).copy(moneySkipped = true, reminderHour = null, reveal = reveal), actions)
    }
    @Test fun revealShortOfTheFixedCosts() = audit {
        RevealStep(ui(STEP_REVEAL).copy(money = AmountInput("2000"), fixed = mapOf("الإيجار" to 240_000L), reveal = RevealUi(0, true, 40_000, 13, 25)), actions)
    }
    @Test fun paydayIsNearQuestion() = audit { RevealStep(ui(STEP_REVEAL).copy(askNear = true), actions) }
}
