package com.masroufi.app.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegalScreensArabicTest {
    @get:Rule val rule = createComposeRule()

    @Test fun privacyAndTermsRenderInArabic() {
        rule.setContent { MasroufiTheme { LegalScreen(onBack = {}) } }
        rule.onNodeWithText("الخصوصية والشروط").assertExists()
        rule.onNodeWithText("باختصار").assertExists()
        rule.onNodeWithText("التواصل").assertExists()
        rule.onNodeWithText("الشروط").performClick()
        rule.onNodeWithText("إيه هو مصروفي").assertExists()
    }

    @Test fun licencesListBothFontsWithTheirText() {
        rule.setContent { MasroufiTheme { LicencesScreen(onBack = {}) } }
        rule.onNodeWithText("Alexandria").assertExists()
        rule.onNodeWithText("Lalezar").assertExists()
        rule.onNodeWithText("The Alexandria Project Authors", substring = true).assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegalScreensEnglishTest {
    @get:Rule val rule = createComposeRule()

    @Test fun privacyAndTermsRenderInEnglish() {
        rule.setContent { MasroufiTheme { LegalScreen(onBack = {}) } }
        rule.onNodeWithText("In short").assertExists()
        rule.onNodeWithText("Terms").performClick()
        rule.onNodeWithText("What Masroufi is").assertExists()
    }

    @Test fun theContactLineUsesTheSupportAddressOnceSet() {
        rule.setContent { MasroufiTheme { LegalScreen(onBack = {}, supportEmail = "help@example.com") } }
        rule.onNodeWithText("For any question, write to help@example.com.").assertExists()
    }
}
