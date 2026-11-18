package com.masroufi.app.feature.lock

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun secure() = rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test fun theWindowIsSecureOnlyWhileTheLockIsOn() {
        var on by mutableStateOf(true)
        rule.setContent { SecureWindow(on) }
        rule.waitForIdle()
        assertTrue("recents show nothing while the lock is on", secure())
        on = false
        rule.waitForIdle()
        assertFalse(secure())
    }

    @Test fun theKeysSendTheirDigitsAndTheFingerprintKeyIsThere() {
        val digits = mutableListOf<Int>()
        var fingerprint = 0
        rule.setContent { MasroufiTheme { LockContent(LockUi(loaded = true), true, { digits += it }, {}, { fingerprint++ }, {}) } }
        rule.onNodeWithText("7").performClick()
        rule.onNodeWithText("0").performClick()
        rule.onNodeWithContentDescription("افتح بالبصمة").performClick()
        assertEquals(listOf(7, 0), digits)
        assertEquals(1, fingerprint)
        rule.onNodeWithText("وريني إنك إنت").assertExists()
    }

    @Test fun noFingerprintKeyWhenItIsNotOffered() {
        rule.setContent { MasroufiTheme { LockContent(LockUi(loaded = true), false, {}, {}, {}, {}) } }
        rule.onNodeWithContentDescription("افتح بالبصمة").assertDoesNotExist()
    }

    @Test fun aWrongPinSaysSo() {
        rule.setContent { MasroufiTheme { LockContent(LockUi(loaded = true, wrong = true, shake = 1), true, {}, {}, {}, {}) } }
        rule.onNodeWithText("مش هو. جرّب تاني.").assertExists()
    }

    @Test fun aWaitCountsDownInSecondsAndQuietsTheKeys() {
        var digits = 0
        rule.setContent { MasroufiTheme { LockContent(LockUi(loaded = true, waitMillis = 30_000), true, { digits++ }, {}, {}, {}) } }
        rule.onNodeWithText("استنى 30 ثانية").assertExists()
        rule.onNodeWithText("5").performClick()
        assertEquals("quiet keys do nothing", 0, digits)
    }

    @Test fun theForgottenPinLinkIsOnTheScreen() {
        var forgot = 0
        rule.setContent { MasroufiTheme { LockContent(LockUi(loaded = true), true, {}, {}, {}, { forgot++ }) } }
        rule.onNodeWithText("نسيت الرقم؟ رجّع من نسختك الاحتياطية").performClick()
        assertEquals(1, forgot)
    }
}
