package com.masroufi.app.feature.close

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.repo.BiggestPunch
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Close day at 200% font, right-to-left, animations off (Task 60). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloseDayAuditTest {
    @get:Rule val rule = createComposeRule()

    private val under = CloseDayUi.Ready(
        date = LocalDate.of(2026, 11, 12), ticket = 24_000, spent = 16_800, holes = 6, biggest = BiggestPunch("بقالة", 9_000),
        leftover = 7_200, over = 0, spreadDays = 12, spreadPerDay = 0, jarAfter = 191_200, emergency = 60_000,
        tomorrowWith = 31_200, tomorrowWithout = 24_000, defaultDestination = LeftoverDestination.JAR,
    )

    private fun audit(s: CloseDayUi, tear: TearState = TearState.Idle) {
        rule.audit { CloseDayContent(s, tear, {}, {}, {}) }.assertNone()
    }

    @Test fun leftover() = audit(under)
    @Test fun leftoverSaveFailed() = audit(under.copy(holes = 12), TearState.Failed)
    @Test fun zero() = audit(under.copy(spent = 24_000, leftover = 0))
    @Test fun over() = audit(under.copy(spent = 27_500, leftover = 0, over = 3_500, spreadPerDay = 300))
    @Test fun overOnLastDay() = audit(under.copy(spent = 27_500, leftover = 0, over = 3_500, spreadDays = 0))
}
