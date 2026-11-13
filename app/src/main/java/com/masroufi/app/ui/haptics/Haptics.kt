package com.masroufi.app.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

enum class HapticLevel { Tick, Tap, Confirm, Heavy, Milestone, Reject }

interface Haptics {
    fun perform(level: HapticLevel)
}

object NoHaptics : Haptics {
    override fun perform(level: HapticLevel) = Unit
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { NoHaptics }

/** performHapticFeedback respects the system touch-feedback setting; [enabled] is the in-app switch (Phase 11). */
class AndroidHaptics(private val view: View) : Haptics {
    var enabled = true

    private val vibrator: Vibrator? by lazy {
        val c = view.context
        if (Build.VERSION.SDK_INT >= 31) c.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else c.getSystemService(Vibrator::class.java)
    }

    override fun perform(level: HapticLevel) {
        if (!enabled) return
        val api30 = Build.VERSION.SDK_INT >= 30
        when (level) {
            HapticLevel.Tick -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            HapticLevel.Tap -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            HapticLevel.Confirm -> view.performHapticFeedback(
                if (api30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
            )
            HapticLevel.Heavy -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            HapticLevel.Reject -> view.performHapticFeedback(
                if (api30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
            )
            HapticLevel.Milestone -> vibrator?.takeIf { it.hasAmplitudeControl() }?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 20, 60, 20, 60, 45), intArrayOf(0, 120, 0, 120, 0, 255), -1),
            )
        }
    }
}
