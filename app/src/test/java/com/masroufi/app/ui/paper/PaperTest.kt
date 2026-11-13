package com.masroufi.app.ui.paper

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class PaperTest {
    @get:Rule val rule = createComposeRule()

    private fun ticket(holes: Int, stub: @Composable () -> Unit = {}) {
        rule.setContent {
            MasroufiTheme {
                Ticket(
                    label = "معاك النهارده", leftPiastres = 15_600, subLine = "من 240", holes = holes,
                    caption = "كل تسجيلة = خُرم في التذكرة", stub = stub, showQersh = false,
                    contentDescription = "معاك النهارده 156 جنيه",
                )
            }
        }
    }

    @Test
    fun ticketShowsPlusN() {
        ticket(holes = 11)
        // The ticket body is a cleared node, so look in the unmerged tree.
        rule.onNodeWithText("+3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun ticketIsOneTalkBackNode() {
        ticket(holes = 3)
        rule.onNodeWithContentDescription("معاك النهارده 156 جنيه").assertExists()
    }

    @Test
    fun stubStaysFocusable() {
        var clicks = 0
        ticket(holes = 3) { Text("قفّل اليوم", Modifier.clickable { clicks++ }) }
        rule.onNodeWithText("قفّل اليوم").performClick()
        assertEquals(1, clicks)
    }
}
