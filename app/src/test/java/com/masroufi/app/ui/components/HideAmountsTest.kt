package com.masroufi.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.paper.Ticket
import com.masroufi.app.ui.theme.MasroufiTheme
import com.masroufi.app.ui.theme.TzType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HideAmountsTest {
    @get:Rule val rule = createComposeRule()

    private fun show(hidden: Boolean, content: @Composable () -> Unit) {
        rule.setContent { MasroufiTheme { CompositionLocalProvider(LocalHideAmounts provides hidden) { content() } } }
    }

    @Test fun anOdometerShowsItsNumberWhileAmountsAreShown() {
        show(false) { Odometer(123_400, TzType.hero) }
        rule.onNodeWithText("1,234").assertExists()
    }

    @Test fun anOdometerShowsDotsWhileAmountsAreHidden() {
        show(true) { Odometer(123_400, TzType.hero) }
        rule.onNodeWithText(HIDDEN_AMOUNT).assertExists()
        rule.onNodeWithText("1,234").assertDoesNotExist()
    }

    @Test fun anOdometerThatIsAnInputIsNeverMasked() {
        show(true) { Odometer(123_400, TzType.hero, maskable = false) }
        rule.onNodeWithText("1,234").assertExists()
        rule.onNodeWithText(HIDDEN_AMOUNT).assertDoesNotExist()
    }

    @Test fun theTicketsBigNumberBecomesDots() {
        show(true) {
            Ticket(
                label = "تذكرة اليوم", amountLabel = "معاك النهارده", leftPiastres = 15_600, subLine = "من 240", holes = 3,
                caption = null, stub = {}, showQersh = false, contentDescription = "معاك النهارده",
            )
        }
        rule.onNodeWithText(HIDDEN_AMOUNT, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("156", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun theTextHelperMasksAndOtherwiseFormats() {
        show(true) { Text(poundsText(123_400)) }
        rule.onNodeWithText(HIDDEN_AMOUNT).assertExists()
    }

    @Test fun theTextHelperFormatsWhileAmountsAreShown() {
        show(false) { Text(poundsText(123_400)) }
        rule.onNodeWithText("1,234").assertExists()
    }
}
