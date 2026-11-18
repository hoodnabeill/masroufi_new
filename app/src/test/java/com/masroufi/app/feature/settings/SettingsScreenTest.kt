package com.masroufi.app.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.ui.components.LocalOpenPaywall
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class FakeActions : SettingsActions {
    val calls = mutableListOf<String>()
    override fun open(sheet: SettingsSheet) { calls += "open:$sheet" }
    override fun closeSheet() { calls += "close" }
    override fun setPayday(v: Payday) { calls += "payday:$v" }
    override fun setReminderHour(v: Int?) { calls += "hour:$v" }
    override fun setLeftover(v: LeftoverDestination) { calls += "leftover:$v" }
    override fun setRoundUpStep(v: Int) { calls += "round:$v" }
    override fun setVibration(v: Boolean) { calls += "vibration:$v" }
    override fun setTicketSounds(v: Boolean) { calls += "sounds:$v" }
    override fun setHideAmounts(v: Boolean) { calls += "hide:$v" }
    override fun setLanguage(tag: String) { calls += "lang:$tag" }
    override fun manageUrl() = ""
    override fun restore() { calls += "restore" }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val ui = SettingsUi(
        loading = false, name = "يوسف", level = 4, levelName = "مدبّر",
        payday = Payday.Day(25), reminderHour = 21, leftover = LeftoverDestination.JAR, roundUpStep = 5,
    )

    private fun show(state: SettingsUi, actions: SettingsActions, email: String = "", paywall: (PlusFeature) -> Unit = {}) {
        rule.setContent {
            MasroufiTheme {
                CompositionLocalProvider(LocalOpenPaywall provides paywall) {
                    SettingsContent(state, actions, SettingsNav(), email, "9.9")
                }
            }
        }
    }

    @Test fun rowsShowTheirCurrentValues() {
        show(ui, FakeActions())
        rule.onNodeWithText("25 من كل شهر").assertExists()
        rule.onNodeWithText("9:00 م").assertExists()
        rule.onNodeWithText("الحصّالة").assertExists()
        rule.onNodeWithText("لأقرب 5").assertExists()
        rule.onNodeWithText("لسه مفيش نسخة").assertExists()
        rule.onNodeWithText("قريب").assertExists()
        rule.onNodeWithText("9.9").assertExists()
        rule.onNodeWithText("مستوى 4 · مدبّر · ملفك").assertExists()
    }

    @Test fun aValueOpensItsSheet() {
        val a = FakeActions()
        show(ui.copy(isPlus = true), a)
        rule.onNodeWithText("يوم القبض").performScrollTo().performClick()
        rule.onNodeWithText("حوّش الفكة").performScrollTo().performClick()
        assertEquals(listOf("open:Payday", "open:RoundUp"), a.calls)
    }

    @Test fun switchesActAtOnce() {
        val a = FakeActions()
        show(ui, a)
        rule.onNodeWithContentDescription("الاهتزاز").performScrollTo().performClick()
        rule.onNodeWithContentDescription("صوت التذكرة").performScrollTo().performClick()
        rule.onNodeWithContentDescription("خبّي المبالغ").performScrollTo().performClick()
        assertEquals(listOf("vibration:false", "sounds:true", "hide:true"), a.calls)
    }

    @Test fun theLanguageSwitchAsksForEnglish() {
        val a = FakeActions()
        show(ui, a)
        rule.onNodeWithText("English").performScrollTo().performClick()
        assertEquals(listOf("lang:en"), a.calls)
    }

    @Test fun feedbackIsHiddenWhileTheSupportAddressIsEmpty() {
        assertEquals("", SUPPORT_EMAIL)
        show(ui, FakeActions(), email = "")
        rule.onAllNodesWithText("قول رأيك لقرش").assertCountEquals(0)
    }

    @Test fun feedbackShowsOnceThereIsAnAddress() {
        show(ui, FakeActions(), email = "help@example.com")
        rule.onNodeWithText("قول رأيك لقرش").assertExists()
    }

    @Test fun roundUpsGoToThePaywallWhenNotPlus() {
        val opened = mutableListOf<PlusFeature>()
        val a = FakeActions()
        show(ui.copy(isPlus = false), a, paywall = { opened += it })
        rule.onNodeWithText("حوّش الفكة").performScrollTo().performClick()
        assertEquals(listOf(PlusFeature.ROUND_UPS), opened)
        assertEquals(emptyList<String>(), a.calls)
    }
}
