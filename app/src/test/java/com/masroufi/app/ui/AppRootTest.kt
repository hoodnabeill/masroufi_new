package com.masroufi.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.components.TzTab
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
class AppRootTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tabsSwitchAndPlusCallsOnAdd() {
        val tabs = mutableListOf<TzTab>()
        var adds = 0
        rule.setContent {
            MasroufiTheme { AppScaffold(TzTab.Today, { tabs += it }, { adds++ }, showTabBar = true) {} }
        }
        rule.onNodeWithText("الخطة").performClick()
        rule.onNodeWithContentDescription("سجّل عملية جديدة").performClick()
        assertEquals(listOf(TzTab.Plan), tabs)
        assertEquals(1, adds)
    }

    @Test fun tabBarHiddenWhenNotShown() {
        rule.setContent { MasroufiTheme { AppScaffold(null, {}, {}, showTabBar = false) {} } }
        rule.onNodeWithText("الخطة").assertDoesNotExist()
    }
}
