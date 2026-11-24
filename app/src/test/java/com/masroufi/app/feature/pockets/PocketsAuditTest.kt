package com.masroufi.app.feature.pockets

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.repo.FeesSummary
import com.masroufi.app.data.repo.PocketView
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.ParsedSms
import com.masroufi.app.domain.SmsDirection
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The pockets list, one pocket and the new-pocket form at 200% font, right-to-left, animations off (Task 60). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PocketsAuditTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)
    private val cash = PocketEntity(id = 1, name = "الكاش", kind = PocketKind.CASH, color = 0)
    private val wallet = PocketEntity(id = 2, name = "فودافون كاش", kind = PocketKind.MOBILE_WALLET, color = 0xFF3DD6C8.toInt())
    private val bank = PocketEntity(id = 3, name = "CIB", kind = PocketKind.BANK, color = 0xFFF2C94C.toInt())
    private val tile = PocketEntity(id = 4, name = "تحت البلاطة", kind = PocketKind.UNDER_TILE, color = 0xFFAAAAAA.toInt())

    private fun row(p: PocketEntity, balance: Long, last: LocalDate?) = PocketRowUi(PocketView(p, balance, null), last)

    private val list = PocketsUi(
        loading = false, today = today,
        rows = listOf(row(cash, 312_000, today), row(wallet, 85_050, today.minusDays(1)), row(bank, 2_450_000, null), row(tile, 500_000, today.minusDays(9))),
        total = 3_347_050, counter = CashCounter(n200 = 1, n100 = 1, n50 = 0),
    )

    private fun audit(s: PocketsUi) {
        rule.audit { PocketsContent(s, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun pockets() = audit(list.copy(reconciled = ReconcileResult.Done))
    @Test fun pocketsWithAPastedMessage() {
        val preview = SmsPreview(ParsedSms(15_000, SmsDirection.SPENT, "كارفور", 85_050), "أكل", 1, wallet, matches = true)
        audit(list.copy(sms = SmsState.Parsed(preview), reconciled = ReconcileResult.Already))
    }
    @Test fun pocketsUnreadableMessage() = audit(list.copy(sms = SmsState.Unreadable))
    @Test fun pocketsLoading() = audit(PocketsUi(loading = true, today = today))

    private val detail = PocketDetailUi(
        loading = false, today = today, id = 2, name = "فودافون كاش", kind = PocketKind.MOBILE_WALLET, color = 0xFF3DD6C8.toInt(),
        balance = 85_050, matchesSms = true, smsAt = 1_700_000_000_000, limit = 6_000_000, moved = 4_200_000, warningRoom = 300_000,
        upcoming = RecurringEntity(name = "النت", amount = 40_000, pocketId = 2, scheduleDay = 15, nextDue = today.plusDays(3), kind = RecurringKind.BILL),
        fees = FeesSummary(1_500, 2 to 1_000L, 1 to 500L), recent = listOf(entry(1), entry(2, EntryKind.MOVED)),
        categories = mapOf(1L to CategoryEntity(1, "أكل", "grid", 0)), cashPocketId = 1,
    )

    private fun entry(id: Long, kind: EntryKind = EntryKind.SPENT) =
        EntryRow(id, kind, 3_500, 0, 0, 1_700_000_000_000 + id, null, 1, "أكل", 2, "فودافون كاش", "EGP", true, if (kind == EntryKind.MOVED) 1 else null, if (kind == EntryKind.MOVED) "الكاش" else null, null)

    private fun auditDetail(s: PocketDetailUi) {
        rule.audit { PocketDetailContent(s, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun walletWithLimit() = auditDetail(detail)
    @Test fun walletWithoutLimitOrFees() = auditDetail(detail.copy(limit = null, warningRoom = null, upcoming = null, fees = FeesSummary(0, 0 to 0L, 0 to 0L), recent = emptyList(), matchesSms = false))
    @Test fun walletOverTheLimit() = auditDetail(detail.copy(warningRoom = -50_000, lowBalanceAlert = true))
    @Test fun cashPocket() = auditDetail(detail.copy(kind = PocketKind.CASH, name = "الكاش", id = 1, counter = CashCounter(1, 2, 1), reconciled = ReconcileResult.Done))
    @Test fun detailLoading() = auditDetail(PocketDetailUi(today = today))

    private fun auditNew(f: NewPocketForm) {
        rule.audit { NewPocketContent(f, {}, {}, {}, {}, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun newPocketEmpty() = auditNew(NewPocketForm())
    @Test fun newPocketFilledAndFailed() = auditNew(NewPocketForm(name = "محفظة الشغل", kind = PocketKind.BANK, opening = com.masroufi.app.feature.add.AmountInput("1500"), failed = true))
}
