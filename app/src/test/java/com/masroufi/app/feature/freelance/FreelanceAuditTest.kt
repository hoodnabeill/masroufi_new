package com.masroufi.app.feature.freelance

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.ClientJobEntity
import com.masroufi.app.data.repo.ClientJobView
import com.masroufi.app.data.repo.SalarySuggestion
import com.masroufi.app.data.repo.SuggestionKind
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** The freelance screen (turn-on block, vault, chart, jobs and their sheets) at 200% font, right-to-left, animations off (Task 61). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FreelanceAuditTest {
    @get:Rule val rule = createComposeRule()

    private val now = YearMonth.of(2026, 11)

    private fun job(id: Long, client: String, label: String, amount: Long, overdue: Boolean) =
        ClientJobView(ClientJobEntity(id, client, label, amount, LocalDate.of(2026, 11, 20)), overdue, if (overdue) 5 else 0)

    private val ui = FreelanceUi(
        loading = false, on = true, vault = 4_500_000, bufferMonths = 2, bufferHalf = true, tank = 0.6f, safeUntil = LocalDate.of(2027, 2, 16),
        salary = 1_800_000, selfPayday = 28, daysToPayday = 16, dailyTicket = 22_500,
        bars = listOf(
            IncomeBarUi(now.minusMonths(5), 1_500_000, 0.7f, BarTone.UNDER), IncomeBarUi(now.minusMonths(4), 2_600_000, 1f, BarTone.OVER),
            IncomeBarUi(now.minusMonths(3), 0, 0f, BarTone.EMPTY), IncomeBarUi(now.minusMonths(2), 1_900_000, 0.8f, BarTone.OVER),
            IncomeBarUi(now.minusMonths(1), 1_700_000, 0.7f, BarTone.UNDER), IncomeBarUi(now, 2_000_000, 0.9f, BarTone.OVER),
        ),
        line = 0.75f, average = 1_950_000, emptyMonth = now.minusMonths(3), suggestion = SalarySuggestion(SuggestionKind.RAISE, 2_000_000),
        jobs = listOf(job(1, "شركة النور", "تصميم لوجو", 600_000, true), job(2, "أستاذ كريم", "", 250_000, false)), owed = 850_000,
    )

    private fun audit(s: FreelanceUi, sheet: Sheet? = null) {
        rule.audit { FreelanceContent(s, {}, FreelanceActions.None, sheet) }.assertNone()
    }

    @Test fun turnOnBlock() = audit(FreelanceUi(loading = false))
    @Test fun freelance() = audit(ui)
    @Test fun freelanceNothingDueNoSuggestion() = audit(ui.copy(suggestion = null, jobs = emptyList(), owed = 0, bufferMonths = 0, bufferHalf = false, safeUntil = null, daysToPayday = 0))
    @Test fun freelanceLowerSuggestion() = audit(ui.copy(suggestion = SalarySuggestion(SuggestionKind.LOWER, 1_500_000), daysToPayday = 1))
    @Test fun freelanceEmpty() = audit(ui.copy(bars = ui.bars.map { it.copy(amount = 0, fraction = 0f, tone = BarTone.EMPTY) }, jobs = emptyList(), owed = 0, suggestion = null))
    @Test fun freelanceLoading() = audit(FreelanceUi())
    @Test fun salarySheet() = audit(ui, Sheet.Salary)
    @Test fun workSheet() = audit(ui, Sheet.Work)
    @Test fun newJobSheet() = audit(ui, Sheet.Job(null))
    @Test fun editJobSheet() = audit(ui, Sheet.Job(1))
    @Test fun jobActionsSheet() = audit(ui, Sheet.Actions(1))
}
