package com.masroufi.app.feature.roll

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.repo.DayKind
import com.masroufi.app.data.repo.RollDay
import com.masroufi.app.data.repo.RollFilter
import com.masroufi.app.data.repo.RollSummary
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** The roll (day stubs, search, undo) at 200% font, right-to-left, animations off (Task 60). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RollAuditTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)
    private val noon = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L
    private val cats = mapOf(1L to CategoryEntity(1, "أكل", "grid", 0))

    private fun entry(id: Long, kind: EntryKind = EntryKind.SPENT, amount: Long = 3_500, note: String? = null, recurringId: Long? = null) = EntryRow(
        id, kind, amount, 0, 0, noon + id * 60_000, note, 1, "أكل", 1, "الكاش", "EGP", true, null, null, recurringId,
    )

    private fun ticket(date: LocalDate, spent: Long, status: TicketStatus, dest: LeftoverDestination? = null) =
        DayTicketEntity(date = date, amount = 24_000, base = 24_000, spent = spent, entryCount = 3, status = status, destination = dest)

    private val days = listOf(
        RollDay(today, ticket(today, 8_400, TicketStatus.OPEN), listOf(entry(1), entry(2, note = "قهوة الصبح ومعاها حاجة تانية طويلة")), DayKind.TICKET),
        RollDay(today.minusDays(1), ticket(today.minusDays(1), 23_500, TicketStatus.CLOSED_BY_HAND, LeftoverDestination.JAR), listOf(entry(3)), DayKind.TICKET),
        RollDay(today.minusDays(2), ticket(today.minusDays(2), 27_500, TicketStatus.CLOSED_AUTO), listOf(entry(4)), DayKind.TICKET),
        RollDay(today.minusDays(3), null, listOf(entry(5, EntryKind.RECEIVED, 700_000)), DayKind.NON_TICKET),
        RollDay(today.minusDays(4), null, listOf(entry(6, recurringId = 9)), DayKind.NON_TICKET),
    )

    private fun ui(
        days: List<RollDay> = this.days,
        filter: RollFilter = RollFilter.ALL,
        searching: Boolean = false,
        query: String = "",
        results: List<EntryRow>? = null,
        undo: Boolean = false,
    ) = RollUi(
        today = today, month = YearMonth.from(today), filter = filter, searching = searching, query = query,
        months = listOf(YearMonth.from(today)), loading = false, summary = RollSummary(1_234_500, 700_000, 3, 4),
        days = days, results = results, categories = cats, undoVisible = undo,
    )

    // Long notes wrap on a stub; nothing is allowed to overflow.
    private fun audit(s: RollUi) {
        rule.audit { RollContent(s, {}, {}, {}, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun days() = audit(ui(undo = true))
    @Test fun filterEmpty() = audit(ui(days = emptyList(), filter = RollFilter.INCOME))
    @Test fun emptyRoll() = audit(ui(days = emptyList()))
    @Test fun searchResults() = audit(ui(searching = true, query = "قهوة", results = listOf(entry(1, note = "قهوة"), entry(2, EntryKind.MOVED))))
    @Test fun searchNothingFound() = audit(ui(searching = true, query = "xyz", results = emptyList()))

    @Test fun anExpandedClosedDay() {
        rule.audit { DayStub(days[1], today, true, cats, {}, {}) }.assertNone()
    }

    @Test fun salaryDayOffersTheRitual() {
        rule.audit { DayStub(days[3], today, false, cats, {}, {}, onPayday = {}) }.assertNone()
    }
}
