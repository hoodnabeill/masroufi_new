package com.masroufi.app.feature.gallery

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryTest {
    @get:Rule val rule = createComposeRule()

    private val sections = listOf(
        "icons" to "Icons", "buttons" to "Buttons", "chips" to "Chips", "segmented" to "Segmented",
        "cards" to "Cards & rows", "toggle" to "Toggle", "progress" to "Progress bar", "odometer" to "Odometer",
        "tabbar" to "Tab bar", "ticket" to "Ticket", "stub" to "Stub", "envelope" to "Envelope",
        "stamp" to "Stamp", "proverb" to "Proverb card", "qersh" to "Qersh", "bubble" to "QershBubble",
    )

    @Test
    fun galleryComposes() {
        rule.setContent { MasroufiTheme { GalleryScreen(DebugEntitlements()) } }
        for ((key, title) in sections) {
            try {
                rule.onNode(hasScrollToKeyAction()).performScrollToKey(key)
                rule.onNodeWithText(title).assertExists("section '$title' missing after scrolling to key '$key'")
            } catch (e: AssertionError) {
                throw AssertionError("section '$title' (key '$key') failed: ${e.message}", e)
            }
        }
    }

    @Test
    fun odometerButtonChangesAmount() {
        rule.setContent { MasroufiTheme { GalleryScreen(DebugEntitlements()) } }
        rule.onNode(hasScrollToKeyAction()).performScrollToKey("odometer")
        rule.onNodeWithText("240").assertExists("start amount 240 missing")
        rule.onNodeWithText("+72").performClick()
        rule.onNodeWithText("312").assertExists("amount did not change to 312")
        rule.onNodeWithText("240").assertDoesNotExist()
    }
}
