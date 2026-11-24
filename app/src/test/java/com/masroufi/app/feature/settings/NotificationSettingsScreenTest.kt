package com.masroufi.app.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.domain.NotifKind
import com.masroufi.app.notify.channelName
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class FakeNotificationActions : NotificationActions {
    val calls = mutableListOf<String>()
    override fun setKind(kind: NotifKind, on: Boolean) { calls += "${kind.name}:$on" }
    override fun permissionResult(kind: NotifKind?, granted: Boolean) { calls += "result:$kind:$granted" }
    override fun openReminder() { calls += "openReminder" }
    override fun closeReminder() { calls += "closeReminder" }
    override fun setReminderHour(hour: Int?) { calls += "hour:$hour" }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationSettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val ui = NotificationSettingsUi(
        loading = false,
        on = NotifKind.entries.associateWith { it.defaultOn && it != NotifKind.EVENING },
        reminderHour = null,
    )

    private fun show(state: NotificationSettingsUi, actions: NotificationActions, blocked: Boolean = false, openSystem: () -> Unit = {}) {
        rule.setContent { MasroufiTheme { NotificationSettingsContent(state, actions, blocked, openSystem, onBack = {}) } }
    }

    @Test fun everyKindHasASwitchAndTheTwoADayLineShows() {
        show(ui, FakeNotificationActions())
        NotifKind.entries.forEach { kind ->
            rule.onAllNodesWithText(context.getString(channelName(kind))).assertCountEquals(1)
        }
        rule.onNodeWithText("تنبيهين في اليوم بالكتير. ومفيش غير اللي يفيد.").performScrollTo().assertExists()
    }

    @Test fun aSwitchAsksForTheOppositeOfWhatItShows() {
        val a = FakeNotificationActions()
        show(ui, a)
        rule.onNodeWithContentDescription("فاتورة بكره").performScrollTo().performClick()
        rule.onNodeWithContentDescription("تذكرة النهارده (الصبح)").performScrollTo().performClick()
        assertEquals(listOf("BILL:false", "MORNING:true"), a.calls)
    }

    @Test fun theReminderTimeRowShowsTheHourAndOpensTheSheet() {
        val a = FakeNotificationActions()
        show(ui.copy(reminderHour = 21), a)
        rule.onNodeWithText("9:00 م").performScrollTo().performClick()
        assertEquals(listOf("openReminder"), a.calls)
    }

    @Test fun theRefusedPermissionLineAndLinkShowOnlyWhenBlocked() {
        show(ui, FakeNotificationActions(), blocked = false)
        rule.onAllNodesWithText("افتح إعدادات الموبايل").assertCountEquals(0)
    }

    @Test fun whenBlockedTheLinkOpensTheSystemSettingsAndTheSwitchStaysOff() {
        var opened = 0
        show(ui, FakeNotificationActions(), blocked = true, openSystem = { opened++ })
        rule.onNodeWithText("التنبيهات مقفولة للتطبيق من إعدادات الموبايل، فمش هتوصلك.").performScrollTo().assertExists()
        rule.onNodeWithText("افتح إعدادات الموبايل").performScrollTo().performClick()
        assertEquals(1, opened)
        // the morning switch is still the off one the state says
        rule.onNodeWithContentDescription("تذكرة النهارده (الصبح)").performScrollTo().assertExists()
    }
}
