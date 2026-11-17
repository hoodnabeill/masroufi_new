package com.masroufi.app.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.R
import com.masroufi.app.ui.paper.BlankSheet
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
class EmptyStateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyStateShowsCopy() {
        var taps = 0
        rule.setContent {
            MasroufiTheme {
                EmptyState({ BlankSheet() }, R.string.empty_roll_title, R.string.empty_roll_line, R.string.empty_roll_action, { taps++ }, primaryAction = true)
            }
        }
        rule.onNodeWithText("الدفتر لسه أبيض").assertExists()
        rule.onNodeWithText("أول خُرم هو اللي بيبدأ الحكاية. سجّل أي حاجة صرفتها النهارده، حتى لو كوباية شاي.").assertExists()
        rule.onNodeWithText("سجّل أول حاجة").performClick()
        assertEquals(1, taps)
    }

    @Test fun allCaughtUpRowShowsCopy() {
        rule.setContent { MasroufiTheme { AllCaughtUpRow() } }
        rule.onNodeWithText("خلّصت كل حاجة").assertExists()
        rule.onNodeWithText("مفيش فواتير جاية الأسبوع ده. استمتع.").assertExists()
    }
}
