package com.masroufi.app.feature.proverb

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.ui.theme.MasroufiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private fun card(n: Int) = ProverbCatalog.all.first { it.number == n }

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProverbRewardTest {
    @get:Rule val rule = createComposeRule()

    private val jar = ProverbRewardUi.Ready(
        ticketNumber = 47, proverb = card(18), allCollected = false, leftover = 7_200,
        destination = LeftoverDestination.JAR, streak = 13, weekDays = 5,
    )

    private fun show(s: ProverbRewardUi.Ready) {
        rule.setContent { MasroufiTheme { ProverbRewardContent(s, {}, {}) } }
        rule.waitForIdle()
    }

    @Test fun showsCardNumberAndSetSize() {
        show(jar)
        rule.onNodeWithText("التذكرة رقم 47 اتقطعت").assertExists()
        rule.onNodeWithText("كسبت مثل جديد").assertExists()
        rule.onNodeWithText("18 / ${ProverbCatalog.all.size}").assertExists()
        rule.onNodeWithText("أمثال الفلوس").assertExists()
        rule.onNodeWithText("13 يوم").assertExists()
        rule.onNodeWithText("5 / 7").assertExists()
        rule.onNodeWithText("للحصّالة").assertExists()
        rule.onNodeWithText("شارك المثل").assertExists()
        rule.onNodeWithText("تمام، لبكره").assertExists()
    }

    @Test fun lineAddsSavingsOnlyForJar() {
        show(jar)
        rule.onNodeWithText(card(18).line + " والنهارده حوّشت 72.").assertExists()
    }

    @Test fun lineWithoutSavingsForTomorrow() {
        show(jar.copy(destination = LeftoverDestination.TOMORROW))
        rule.onNodeWithText(card(18).line).assertExists()
        rule.onNodeWithText("حوّشت", substring = true).assertDoesNotExist()
        rule.onNodeWithText("لبكره").assertExists()
    }

    @Test fun lineWithoutSavingsWhenNothingLeft() {
        show(jar.copy(leftover = 0))
        rule.onNodeWithText("حوّشت", substring = true).assertDoesNotExist()
    }

    @Test fun allCollectedShowsFavourite() {
        show(jar.copy(allCollected = true))
        rule.onNodeWithText("معاك الألبوم كله").assertExists()
        rule.onNodeWithText("كسبت مثل جديد").assertDoesNotExist()
        rule.onNodeWithText(card(18).text).assertExists()
    }

    @Test fun rareShowsStamp() {
        show(jar.copy(proverb = card(45)))
        rule.onNodeWithText("نادر").assertExists()
    }

    @Test fun ordinaryCardHasNoStamp() {
        show(jar)
        rule.onNodeWithText("نادر").assertDoesNotExist()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProverbShareTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun shareIntentHasPngUri() {
        runBlocking { ProverbShare.share(app, card(18)) }
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(send)
        assertEquals("image/png", send!!.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)
        assertNotNull(uri)
        assertEquals("content", uri!!.scheme)
        assertTrue(File(app.cacheDir, "share/proverb_18.png").length() > 0)
    }

    @Test fun bitmapHasNoAmounts() {
        val lines = ProverbShare.lines(app, card(18))
        assertEquals(listOf(card(18).text, "18 / ${ProverbCatalog.all.size}", "مصروفي"), lines)
        // The mock's day: 72 saved, a 13-day streak.
        assertFalse(lines.any { it.contains("72") || it.contains("13 ") })
        assertEquals(null, shadowOf(app).nextStartedActivity)
    }
}
