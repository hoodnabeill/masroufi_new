package com.masroufi.app.feature.endofmonth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.repo.BillDay
import com.masroufi.app.data.repo.EndOfMonthState
import com.masroufi.app.data.repo.Habit
import com.masroufi.app.data.repo.OwedToMe
import com.masroufi.app.data.repo.RescueSources
import com.masroufi.app.data.repo.SmallTicketDay
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** End-of-month mode at 200% font, right-to-left, animations off (Task 61). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EndOfMonthAuditTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 20)

    private val strip = (0..8).map { i ->
        SmallTicketDay(today.plusDays(i.toLong()), 15_000, if (i == 4) listOf(BillDay(today.plusDays(4), "فاتورة الكهربا", 60_000)) else emptyList())
    }

    private val state = EndOfMonthState(
        on = true, inMode = true, payday = LocalDate.of(2026, 11, 29), pool = 135_000, daysLeft = 9, ticket = 150, strip = strip,
        habits = listOf(Habit(1, "قهوة", 45_000, false, "coffee"), Habit(2, "خروجات", 80_000, true, "grid")), noSpendDays = 1, boost = 17,
        rescue = RescueSources(freeJar = 600_000, owedToMe = OwedToMe(1, "أحمد", 120_000), emergency = 300_000), offer = false,
    )

    private fun audit(s: EndOfMonthUi) {
        rule.audit { EndOfMonthContent(s, {}, { _, _ -> }, {}, {}, {}) }.assertNone()
    }

    @Test fun endOfMonth() = audit(EndOfMonthUi(loading = false, state = state))
    @Test fun endOfMonthTwoBillsChallengeEarned() = audit(
        EndOfMonthUi(
            loading = false,
            state = state.copy(
                strip = strip.mapIndexed { i, d -> if (i == 6) d.copy(bills = listOf(BillDay(d.date, "النت", 40_000))) else d }, noSpendDays = 2, boost = 0,
                rescue = RescueSources(null, null, null),
            ),
        ),
    )
    @Test fun endOfMonthShortfall() = audit(EndOfMonthUi(loading = false, state = state.copy(pool = -50_000, ticket = 0, strip = emptyList(), on = false)))
    @Test fun endOfMonthLoading() = audit(EndOfMonthUi())
}
