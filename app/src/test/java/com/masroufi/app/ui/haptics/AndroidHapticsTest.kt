package com.masroufi.app.ui.haptics

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidHapticsTest {
    private class RecordingView(context: Context) : View(context) {
        var haptics = 0
        var sounds = 0
        override fun performHapticFeedback(feedbackConstant: Int): Boolean { haptics++; return true }
        override fun playSoundEffect(soundConstant: Int) { sounds++ }
    }

    private val view = RecordingView(ApplicationProvider.getApplicationContext())

    @Test fun vibrationOffSilencesEveryLevel() {
        val h = AndroidHaptics(view)
        h.enabled = false
        HapticLevel.values().forEach { h.perform(it) }
        assertEquals(0, view.haptics)
        h.enabled = true
        h.perform(HapticLevel.Tap)
        assertEquals(1, view.haptics)
    }

    @Test fun ticketSoundsAreOffByDefaultAndPlayTheSystemClickWhenOn() {
        val h = AndroidHaptics(view)
        h.sound()
        assertEquals(0, view.sounds)
        h.sounds = true
        h.sound()
        assertEquals(1, view.sounds)
    }
}
