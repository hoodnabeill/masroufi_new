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

    @Test fun allSpringValues() {
        val expected = mapOf(
            TzSpring.Press to (1f to .15f), TzSpring.Snappy to (1f to .24f), TzSpring.Move to (1f to .38f),
            TzSpring.Sheet to (.86f to .32f), TzSpring.Reward to (.62f to .45f),
        )
        assertEquals(TzSpring.values().size, expected.size)
        expected.forEach { (s, v) -> assertEquals(v.first, s.damping, 0f); assertEquals(v.second, s.response, 0f) }
    }
}
