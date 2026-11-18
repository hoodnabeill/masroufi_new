package com.masroufi.app.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.RestoreOutcome
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlusSettingsRowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsPlanAndRenewalAndBothActionsWork() {
        var manage = 0
        var restore = 0
        rule.setContent {
            MasroufiTheme {
                PlusSettingsRow(PlusPlan.YEARLY, LocalDate.of(2027, 11, 18), null, RestoreOutcome.NONE, { manage++ }, { restore++ })
            }
        }
        rule.onNodeWithText("مصروفي بلس").assertExists()
        rule.onNodeWithText("اشتراك سنوي · بيتجدد يوم 18 نوفمبر 2027").assertExists()
        rule.onNodeWithText("ملقيناش اشتراك على حسابك في جوجل بلاي.").assertExists()
        rule.onNodeWithTag(PlusManageTag).performClick()
        rule.onNodeWithTag(PlusRestoreTag).performClick()
        assertEquals(listOf(1, 1), listOf(manage, restore))
    }

    @Test fun showsTheTrialEndOrNotSubscribed() {
        rule.setContent {
            MasroufiTheme { PlusSettingsRow(null, null, LocalDate.of(2026, 11, 25), null, {}, {}) }
        }
        rule.onNodeWithText("التجربة بتخلص يوم 25 نوفمبر 2026").assertExists()
    }
}
