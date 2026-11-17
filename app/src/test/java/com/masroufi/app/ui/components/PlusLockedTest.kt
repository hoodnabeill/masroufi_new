package com.masroufi.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.R
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
class PlusLockedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun contentIsHiddenFromAccessibilityAndTheButtonCallsBack() {
        var taps = 0
        rule.setContent { MasroufiTheme { PlusLocked(R.string.jar_lock_gold, { taps++ }) { Text("secret content") } } }
        rule.onNodeWithText("secret content").assertDoesNotExist()
        rule.onNodeWithText("الدهب بالسعر اللي تكتبه، مع بلس.").assertExists()
        rule.onNodeWithText("جرّب بلس").performClick()
        assertEquals(1, taps)
    }
}
