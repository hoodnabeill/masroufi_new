package com.masroufi.app.feature.settings

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main Settings reminder sheet asks for POST_NOTIFICATIONS on Android 13+, like the notifications screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsReminderPermissionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Actions : SettingsActions {
        val hours = mutableListOf<Int?>()
        override fun setReminderHour(v: Int?) { hours += v }
        override fun open(sheet: SettingsSheet) {}
        override fun closeSheet() {}
        override fun setPayday(v: Payday) {}
        override fun setLeftover(v: LeftoverDestination) {}
        override fun setRoundUpStep(v: Int) {}
        override fun setVibration(v: Boolean) {}
        override fun setTicketSounds(v: Boolean) {}
        override fun setHideAmounts(v: Boolean) {}
        override fun setLanguage(tag: String) {}
        override fun manageUrl() = ""
        override fun restore() {}
    }

    private fun show(actions: Actions) {
        val ui = SettingsUi(loading = false, name = "يوسف", payday = Payday.Day(25), reminderHour = 21, leftover = LeftoverDestination.JAR, sheet = SettingsSheet.Reminder)
        rule.setContent { MasroufiTheme { SettingsContent(ui, actions, SettingsNav(), "", "9.9") } }
    }

    private fun asked() = shadowOf(rule.activity).lastRequestedPermission?.requestedPermissions?.toList().orEmpty()

    @Test fun pickingAnHourAsksForTheNotificationPermission() {
        val a = Actions()
        show(a)
        rule.onNodeWithText("10 م").performClick()
        assertEquals(listOf<Int?>(22), a.hours)
        assertTrue("asked: ${asked()}", Manifest.permission.POST_NOTIFICATIONS in asked())
    }

    @Test fun pickingNoReminderAsksForNothing() {
        val a = Actions()
        show(a)
        rule.onNodeWithText("من غير").performClick()
        assertEquals(listOf<Int?>(null), a.hours)
        assertTrue(asked().isEmpty())
    }
}
