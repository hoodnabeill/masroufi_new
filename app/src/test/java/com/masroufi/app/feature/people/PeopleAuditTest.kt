package com.masroufi.app.feature.people

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.DebtEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.GameyaEntity
import com.masroufi.app.data.db.GameyaMemberEntity
import com.masroufi.app.data.db.GameyaPlanEntity
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.GiftEntity
import com.masroufi.app.data.db.OccasionEntity
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TabItemEntity
import com.masroufi.app.data.repo.GameyaView
import com.masroufi.app.data.repo.GiftRow
import com.masroufi.app.data.repo.OccasionView
import com.masroufi.app.data.repo.PeopleFilter
import com.masroufi.app.data.repo.PeopleTotals
import com.masroufi.app.data.repo.PersonRow
import com.masroufi.app.data.repo.SlotState
import com.masroufi.app.data.repo.UpcomingDuty
import com.masroufi.app.data.repo.WheelSlot
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * People, person detail, the duty ledger, the gameya and its form, and every sheet of them at 200% font, right-to-left,
 * animations off (Task 62). No `allowOverflow`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PeopleAuditTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)
    private val none = object : PeopleActions {}
    private val remind: (String, Long) -> Unit = { _, _ -> }

    private val sara = PersonEntity(1, "سارة محمود", "0100", PersonKind.PERSON)
    private val shop = PersonEntity(2, "بقالة عم حسين", null, PersonKind.SHOP)
    private val omar = PersonEntity(3, "عمر", null, PersonKind.PERSON)
    private val pockets = listOf(
        PocketEntity(1, "الكاش", PocketKind.CASH, color = 0),
        PocketEntity(2, "فودافون كاش", PocketKind.MOBILE_WALLET, color = 0xFF3DD6C8.toInt()),
    )

    private fun row(p: PersonEntity, owed: Long, iOwe: Long, tab: Long, debts: Int, items: Int, since: LocalDate?, due: LocalDate?) =
        PersonRow(p, owed, iOwe, tab, debts, items, false, since, due)

    private val rows = listOf(
        PersonRowUi(row(sara, 250_000, 0, 0, 1, 0, today.minusDays(20), today.plusDays(8)), RowAction.REMIND, 20, false),
        PersonRowUi(row(shop, 0, 0, 18_500, 0, 4, today.minusDays(3), null), RowAction.SETTLE_TAB, 3, false),
        PersonRowUi(row(omar, 0, 120_000, 0, 1, 0, today.minusDays(70), today.minusDays(5)), RowAction.REPAY, 70, true),
    )

    private val gameyaEntity = GameyaEntity(1, "جمعية الشغل", 200_000, 202609, 4)
    private fun member(id: Long, name: String, turn: Int) = GameyaMemberEntity(id, 1, name, turn)
    private val members = listOf(member(1, "منى", 1), member(2, "أحمد", 2), member(3, "هالة", 3), member(4, "كريم", 5), member(5, "نورا", 6))
    private val wheel = listOf(
        WheelSlot(1, members[0], SlotState.PAID_OUT), WheelSlot(2, members[1], SlotState.PAID_OUT), WheelSlot(3, members[2], SlotState.THIS_MONTH),
        WheelSlot(4, null, SlotState.WAITING), WheelSlot(5, members[3], SlotState.WAITING), WheelSlot(6, members[4], SlotState.WAITING),
    )
    private val gameyaView = GameyaView(
        gameya = gameyaEntity, people = 6, payout = 1_200_000, myTurnMonth = YearMonth.of(2026, 12), monthNumber = 3, finished = false,
        thisMonth = YearMonth.of(2026, 11), monthsUntil = 1, myPaidSoFar = 600_000, myPaidThisMonth = false, wheel = wheel,
        paidMembers = listOf(members[0], members[1]), unpaidMembers = listOf(members[2], members[3], members[4]), paidCount = 2,
        plan = listOf(GameyaPlanEntity(1, 1, "دفعة مقدّم الشقة", 700_000), GameyaPlanEntity(2, 1, "فرش", 300_000)), unplanned = 200_000,
    )

    private val occasion = OccasionEntity(1, "فرح ابن خالي", today.plusDays(12), OccasionOwner.THEIRS, 1)
    private val mine = OccasionEntity(2, "فرحي", today.minusDays(300), OccasionOwner.MINE, null)
    private val upcoming = UpcomingDuty(occasion, sara, 12, 100_000)

    // ---- People

    private val list = PeopleUi(
        loading = false, filter = PeopleFilter.ALL, totals = PeopleTotals(250_000, 1, 138_500, 18_500), rows = rows, persons = listOf(sara, shop, omar),
        pockets = pockets, plus = true, duty = DutyCardUi(upcoming, 1_420_000, 38), gameya = gameyaView, kids = KidsCardUi(2, 1_450_000),
    )

    private fun people(ui: PeopleUi) {
        rule.audit { PeopleContent(ui, none, remind, {}, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun peopleList() = people(list.copy(notice = PeopleNotice(NoticeKind.REMINDED)))
    @Test fun peopleFree() = people(list.copy(plus = false, gameya = null, kids = KidsCardUi()))
    @Test fun peopleEmpty() = people(PeopleUi(loading = false))
    @Test fun peopleLoading() = people(PeopleUi())
    @Test fun addSheet() = people(list.copy(sheet = PeopleSheet.Add))
    @Test fun addSheetWithoutPeople() = people(list.copy(sheet = PeopleSheet.Add, persons = emptyList(), pockets = emptyList()))
    @Test fun splitSheet() = people(list.copy(sheet = PeopleSheet.Split, split = SplitDraft(AmountInput("900"), 1L, listOf(ME, 1L, 3L))))
    @Test fun settleTabSheet() = people(list.copy(sheet = PeopleSheet.SettleTab(2, "بقالة عم حسين", 18_500)))
    @Test fun repaySheet() = people(list.copy(sheet = PeopleSheet.Repay(3, "عمر", 120_000)))

    // ---- Person detail

    private val debtOwed = DebtEntity(1, 1, DebtDirection.THEY_OWE, 250_000, "سلفة العربية", today.minusDays(20), today.plusDays(8))
    private val debtMine = DebtEntity(2, 1, DebtDirection.I_OWE, 50_000, null, today.minusDays(40))
    private val debtDone = DebtEntity(3, 1, DebtDirection.THEY_OWE, 30_000, "غدا", today.minusDays(60), null, settled = true)
    private val repayEntry = EntryEntity(9, EntryKind.DEBT, 20_000, 1, happenedAt = 0, loggedAt = 0, debtId = 1)
    private val history = listOf(
        HistoryItem.Loan(debtOwed), HistoryItem.Loan(debtMine), HistoryItem.Repayment(repayEntry, true, today.minusDays(10), 20_000),
        HistoryItem.TabLine(TabItemEntity(1, 1, "عيش وجبنة", 4_500, today.minusDays(2))), HistoryItem.Loan(debtDone),
    )
    private val detailNone = object : PersonDetailActions {}
    private val detail = PersonDetailUi(
        loading = false, row = row(sara, 250_000, 50_000, 18_500, 2, 1, today.minusDays(40), today.plusDays(8)), history = history, pockets = pockets,
    )

    private fun detail(ui: PersonDetailUi) {
        rule.audit { PersonDetailContent(ui, detailNone, remind, {}) }.assertNone()
    }

    @Test fun personDetail() = detail(detail.copy(notice = PeopleNotice(NoticeKind.RECEIVED)))
    @Test fun personDetailNoHistory() = detail(detail.copy(history = emptyList(), row = row(sara, 0, 0, 0, 0, 0, null, null)))
    @Test fun personDetailLoading() = detail(PersonDetailUi())
    @Test fun settleSheet() = detail(detail.copy(sheet = DetailSheet.Settle))
    @Test fun receivedSheet() = detail(detail.copy(sheet = DetailSheet.Money(debtOwed)))
    @Test fun repaySheetOfDetail() = detail(detail.copy(sheet = DetailSheet.Money(debtMine)))
    @Test fun editSheet() = detail(detail.copy(sheet = DetailSheet.Edit))
    @Test fun deleteSheet() = detail(detail.copy(sheet = DetailSheet.Delete))

    // ---- Duty ledger

    private val dutyNone = object : DutyActions {}
    private val gifts = listOf(
        GiftRow(GiftEntity(1, 1, 1, 100_000, GiftDirection.GIVEN), sara), GiftRow(GiftEntity(2, 1, 3, 50_000, GiftDirection.RECEIVED), omar),
    )
    private val ledger = DutyUi(
        loading = false, plus = true, today = today,
        mine = listOf(OccasionView(mine, null, gifts, 1_420_000, 0, 38)),
        theirs = listOf(OccasionView(occasion, sara, gifts, 50_000, 100_000, 1)), upcoming = upcoming, persons = listOf(sara, shop, omar),
    )

    private fun duty(ui: DutyUi) {
        rule.audit { DutyLedgerContent(ui, dutyNone, {}) }.assertNone()
    }

    @Test fun dutyLedger() = duty(ledger)
    @Test fun dutyLocked() = duty(ledger.copy(plus = false))
    @Test fun dutyEmpty() = duty(DutyUi(loading = false, plus = true, today = today))
    @Test fun dutyLoading() = duty(DutyUi(plus = true))
    @Test fun occasionSheetNew() = duty(ledger.copy(sheet = DutySheet.Occasion(null)))
    @Test fun occasionSheetEdit() = duty(ledger.copy(sheet = DutySheet.Occasion(ledger.theirs[0])))
    @Test fun giftSheetNew() = duty(ledger.copy(sheet = DutySheet.Gift(1, null)))
    @Test fun giftSheetEdit() = duty(ledger.copy(sheet = DutySheet.Gift(1, gifts[0])))
    @Test fun deleteOccasionSheet() = duty(ledger.copy(sheet = DutySheet.ConfirmDelete(1, "فرح ابن خالي", 2)))

    // ---- Gameya

    private val gameyaNone = object : GameyaActions {}
    private val gameyaUi = GameyaUi(loading = false, gameyas = listOf(gameyaView, gameyaView.copy(gameya = gameyaEntity.copy(id = 2, name = "جمعية البيت"))), pockets = pockets, plus = true)

    private fun gameya(ui: GameyaUi) {
        rule.audit { GameyaContent(ui, gameyaNone, {}, {}, {}) }.assertNone()
    }

    @Test fun gameyaMain() = gameya(gameyaUi)
    @Test fun gameyaMyInstallmentPaid() = gameya(gameyaUi.copy(gameyas = listOf(gameyaView.copy(myPaidThisMonth = true, unpaidMembers = emptyList(), paidCount = 6, plan = emptyList(), unplanned = 0))))
    @Test fun gameyaEmpty() = gameya(GameyaUi(loading = false))
    @Test fun gameyaLoading() = gameya(GameyaUi())
    @Test fun gameyaPaySheet() = gameya(gameyaUi.copy(sheet = GameyaSheet.Pay))
    @Test fun gameyaSwapSheet() = gameya(gameyaUi.copy(sheet = GameyaSheet.Swap))
    @Test fun gameyaPlanSheet() = gameya(gameyaUi.copy(sheet = GameyaSheet.Plan(null)))
    @Test fun gameyaPlanSheetEdit() = gameya(gameyaUi.copy(sheet = GameyaSheet.Plan(gameyaView.plan[0])))

    // ---- Gameya form

    private val formNone = object : GameyaFormActions {}
    private val form = GameyaFormState(
        loaded = true, name = "جمعية الشغل", amount = AmountInput("2000"), members = listOf("منى", "أحمد", "هالة"), start = YearMonth.of(2026, 12),
        myTurn = 2, pocketId = 1, pockets = pockets,
    )

    private fun form(f: GameyaFormState, edit: Boolean = false) {
        rule.audit { GameyaFormContent(f, if (edit) object : GameyaFormActions { override val isEdit = true } else formNone, {}) }.assertNone()
    }

    @Test fun gameyaFormNew() = form(form)
    @Test fun gameyaFormFailed() = form(form.copy(failed = true))
    @Test fun gameyaFormEdit() = form(form.copy(editing = true), edit = true)
    @Test fun gameyaFormArchive() = form(form.copy(editing = true, askArchive = true), edit = true)
    @Test fun gameyaFormBlocked() = form(form.copy(blocked = true))
}
