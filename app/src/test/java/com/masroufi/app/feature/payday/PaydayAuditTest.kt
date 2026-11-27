package com.masroufi.app.feature.payday

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.DebtEntity
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.repo.DebtWithPerson
import com.masroufi.app.data.repo.JarShare
import com.masroufi.app.data.repo.RitualFixed
import com.masroufi.app.data.repo.RitualView
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The payday ritual at 200% font, right-to-left, animations off (Task 61). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaydayAuditTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 28)

    private val view = RitualView(
        jarShare = 700_000, debts = 0, fixed = 1_100_000, forTickets = 5_200_000, ticket = 17_300, lastCycleTicket = 16_000,
        fixedItems = listOf(RitualFixed("إيجار الشقة", 1, 800_000), RitualFixed("النت", 5, 40_000), RitualFixed("مصروف الولاد", 0, 40_000, 4)),
        days = 30, nextPayday = LocalDate.of(2026, 12, 28),
    )

    private val ui = RitualUi(
        loading = false, salary = 7_000_000, pocketName = "فودافون كاش", share = JarShare.Percent(10), jarTotal = 12_000_000, view = view, pocketId = 2,
    )

    private val debts = listOf(
        DebtWithPerson(DebtEntity(1, 1, DebtDirection.I_OWE, 150_000, date = today), PersonEntity(1, "أحمد صاحبي")),
        DebtWithPerson(DebtEntity(2, 2, DebtDirection.I_OWE, 80_000, date = today), PersonEntity(2, "ماما")),
    )

    private fun audit(s: RitualUi) {
        rule.audit { PaydayRitualContent(s, today, {}, PaydayActions.None) }.assertNone()
    }

    @Test fun ritual() = audit(ui)
    @Test fun ritualWithDebtsAndAmountShare() = audit(
        ui.copy(debts = debts, repay = setOf(1L), share = JarShare.Amount(500_000), view = view.copy(debts = 150_000, lastCycleTicket = 20_000)),
    )
    @Test fun ritualNoFixedNoNextPayday() = audit(ui.copy(view = view.copy(fixedItems = emptyList(), fixed = 0, nextPayday = null, lastCycleTicket = null)))
    @Test fun ritualLoading() = audit(RitualUi())
    @Test fun ritualAmountSheet() = audit(ui.copy(amountSheet = true, share = JarShare.Amount(500_000)))
}
