package com.masroufi.app.ui.motion

import kotlin.math.PI
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringsTest {
    @Test fun pressStiffness() {
        assertEquals((2 * PI / 0.15).pow(2).toFloat(), TzSpring.Press.stiffness, 0.5f)
    }

    @Test fun rewardOvershoots() {
        assertTrue(TzSpring.Reward.damping < 1f)
    }
}
