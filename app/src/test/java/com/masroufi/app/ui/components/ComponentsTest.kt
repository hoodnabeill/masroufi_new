package com.masroufi.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.Haptics
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.MasroufiTheme
import com.masroufi.app.ui.theme.TzType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class RecordingHaptics : Haptics {
    val levels = mutableListOf<HapticLevel>()
    override fun perform(level: HapticLevel) { levels += level }
}

@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    private fun show(haptics: Haptics = RecordingHaptics(), content: @Composable () -> Unit) {
        rule.setContent { MasroufiTheme { CompositionLocalProvider(LocalHaptics provides haptics) { content() } } }
    }

    @Test
    fun disabledPrimaryButtonFiresReject() {
        val haptics = RecordingHaptics()
        var clicks = 0
        show(haptics) { PrimaryButton("احفظ", onClick = { clicks++ }, enabled = false) }
        rule.onNodeWithText("احفظ").performClick()
        rule.waitForIdle()
        assertEquals("onClick must not run when disabled", 0, clicks)
        assertEquals("one Reject haptic per disabled tap", listOf(HapticLevel.Reject), haptics.levels)
    }

    @Test
    fun tabBarAddHasLabel() {
        show { TzTabBar(selected = TzTab.Today, onSelect = {}, onAdd = {}) }
        rule.onNodeWithContentDescription("سجّل عملية جديدة").assertExists()
    }

    @Test
    fun odometerShowsWholePounds() {
        show { Odometer(15600, TzType.hero) }
        rule.onNodeWithText("156").assertExists()
    }

    @Test
    fun odometerDigitsDoNotShift() {
        show {
            Column {
                Odometer(11100, TzType.hero, Modifier.testTag("narrow"))
                Odometer(88800, TzType.hero, Modifier.testTag("wide"))
            }
        }
        val a = rule.onNodeWithTag("narrow").getUnclippedBoundsInRoot().width
        val b = rule.onNodeWithTag("wide").getUnclippedBoundsInRoot().width
        assertEquals("111 and 888 must occupy the same width", a, b)
    }
}
