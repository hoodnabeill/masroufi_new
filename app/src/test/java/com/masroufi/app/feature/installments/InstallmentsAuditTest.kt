package com.masroufi.app.feature.installments

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.repo.InstallmentPlan
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.ui.audit.AuditConfig
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** Installments and the plan form at 200% font, right-to-left, animations off (Task 61). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InstallmentsAuditTest {
    @get:Rule val rule = createComposeRule()

    private val now = YearMonth.of(2026, 11)

    private fun plan(id: Long, name: String, monthly: Long, total: Int?, paid: Int) = InstallmentPlan(
        item = RecurringEntity(id, name, monthly, 1, 12, LocalDate.of(2026, 12, 12), kind = RecurringKind.INSTALLMENT),
        paid = paid, total = total, left = total?.minus(paid), endMonth = total?.let { now.plusMonths((it - paid).toLong()) }, progress = total?.let { paid.toFloat() / it },
        finished = false,
    )

    private val rows = listOf(
        PlanRowUi(plan(1, "موبايل سامسونج", 120_000, 12, 4), PlanLine.Left(8, 12, 12, now.plusMonths(8)), ending = false),
        PlanRowUi(plan(2, "تلاجة", 90_000, 24, 20), PlanLine.TwoLeft(now.plusMonths(2)), ending = false),
        PlanRowUi(plan(3, "نضارة", 30_000, 6, 5), PlanLine.OneLeft(now.plusMonths(1)), ending = false),
        PlanRowUi(plan(4, "جمعية العيلة", 50_000, null, 0), PlanLine.Open(12), ending = false),
    )

    private val ui = InstallmentsUi(
        loading = false, rows = rows, monthly = 290_000, perDay = 97, remaining = 2_100_000, freedom = now.plusMonths(8),
        bars = listOf(
            TimelineBar(BarKind.NOW, null, 1, 1f), TimelineBar(BarKind.MONTH, now.plusMonths(1), 1, 0.8f), TimelineBar(BarKind.MONTH, now.plusMonths(2), 2, 0.6f),
            TimelineBar(BarKind.MONTH, now.plusMonths(4), 3, 0.3f), TimelineBar(BarKind.FREE, null, 1, 0.1f),
        ),
        growth = listOf(GrowthStep(10, now.plusMonths(1), false), GrowthStep(30, now.plusMonths(2), false), GrowthStep(40, now.plusMonths(14), true)),
        early = EarlyUi(1, "موبايل سامسونج", 900_000, 40), today = now,
    )

    private fun audit(s: InstallmentsUi, paying: Boolean = false) {
        // allowOverflow: the month labels under the freedom chart stay on one line at their normal size (fixed-size chart art, bars a few dp wide).
        rule.audit(AuditConfig(allowOverflow = listOf("دلوقتي", "ديسمبر", "خلصت", "يناير", "فبراير", "مارس", "أبريل", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر"))) { InstallmentsContent(s, {}, {}, {}, {}, {}, { _, _ -> }, {}, payingOpen = paying) }.assertNone()
    }

    @Test fun installments() = audit(ui)
    @Test fun installmentsFailedPayoff() = audit(ui.copy(payOffFailed = true))
    @Test fun installmentsNoEarlyNoTimeline() = audit(ui.copy(early = null, freedom = null, bars = emptyList()))
    @Test fun installmentsPayoffSheet() = audit(ui, paying = true)
    @Test fun installmentsEmpty() = audit(InstallmentsUi(loading = false))
    @Test fun installmentsLoading() = audit(InstallmentsUi())

    private val pockets = listOf(PocketEntity(1, "الكاش", PocketKind.CASH, color = 0), PocketEntity(2, "فودافون كاش", PocketKind.MOBILE_WALLET, color = 0xFF3DD6C8.toInt()))

    @Test fun formNew() {
        rule.audit { InstallmentFormContent(InstallmentForm(loaded = true, pockets = pockets), false, {}, InstallmentFormActions.None) }.assertNone()
    }
    private val filled = InstallmentForm(loaded = true, name = "موبايل سامسونج", amount = AmountInput("1200"), day = 12, total = 12, paidBefore = 4, pocketId = 2, pockets = pockets, failed = true)

    @Test fun formFilled() {
        rule.audit { InstallmentFormContent(filled, true, {}, InstallmentFormActions.None) }.assertNone()
    }
    @Test fun formOpenEnded() {
        rule.audit { InstallmentFormContent(filled.copy(total = null, paidBefore = 0), true, {}, InstallmentFormActions.None) }.assertNone()
    }
}
