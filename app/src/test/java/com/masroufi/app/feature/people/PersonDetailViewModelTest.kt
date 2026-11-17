package com.masroufi.app.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Today 2026-11-01; cash holds 1,000 EGP. */
@RunWith(AndroidJUnit4::class)
class PersonDetailViewModelTest : PlanVmTestBase() {
    private var cash = 0L

    @Before fun open() {
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 100_000, color = 0)) }
    }

    private fun detail(id: Long): PersonDetailViewModel {
        val vm = make(PersonDetailViewModel::class, { it.state }) { PersonDetailViewModel(f.people, f.pockets, clock, SavedStateHandle(mapOf("id" to id))) }
        settle("loaded") { !vm.state.value.loading }
        return vm
    }

    private fun balance() = runBlocking { db.pocketDao().balanceOf(cash) }

    @Test fun partialRepaymentLowersWhatIsOpenAndShowsInTheHistory() {
        val ahmed = runBlocking { f.people.addPerson("أحمد") }
        val debt = runBlocking { f.people.lend(ahmed, 50_000, cash) }
        val vm = detail(ahmed)
        assertEquals(50_000L, vm.state.value.row?.owedToMe)
        vm.settleDebt(runBlocking { db.peopleDao().debtById(debt)!! }, 20_000, cash)
        settle("received") { vm.state.value.notice?.kind == NoticeKind.RECEIVED }
        val ui = vm.state.value
        assertEquals(30_000L, ui.row?.owedToMe)
        assertEquals(100_000L - 50_000 + 20_000, balance())
        val repayment = ui.history.first() as HistoryItem.Repayment
        assertTrue(repayment.toMe)
        assertEquals(20_000L, repayment.amount)
        assertEquals("the opening move is the loan row, not a repayment", 2, ui.history.size)
        assertTrue(ui.history.last() is HistoryItem.Loan)
    }

    @Test fun payingWhatIOweIsARepaymentByMe() {
        val mama = runBlocking { f.people.addPerson("ماما") }
        val debt = runBlocking { f.people.borrow(mama, 50_000, cash) }
        val vm = detail(mama)
        vm.settleDebt(runBlocking { db.peopleDao().debtById(debt)!! }, 50_000, cash)
        settle("repaid") { vm.state.value.notice?.kind == NoticeKind.REPAID }
        assertEquals(100_000L, balance())
        assertEquals(0L, vm.state.value.row?.iOweLoans)
        assertEquals(false, (vm.state.value.history.first() as HistoryItem.Repayment).toMe)
    }

    @Test fun settleAllTabItemsFromAPocket() {
        val kiosk = runBlocking { f.people.addPerson("كشك", kind = PersonKind.SHOP) }
        runBlocking {
            f.people.addTabItem(kiosk, "شاي", 1_000)
            f.people.addTabItem(kiosk, "سجائر", 5_000)
        }
        val vm = detail(kiosk)
        assertEquals(6_000L, vm.state.value.row?.onTab)
        vm.openSettle()
        assertEquals(DetailSheet.Settle, vm.state.value.sheet)
        vm.settleTab(cash)
        settle("settled") { vm.state.value.notice?.kind == NoticeKind.TAB_SETTLED }
        assertEquals("كشك", vm.state.value.notice?.name)
        assertEquals(94_000L, balance())
        assertEquals(0L, vm.state.value.row?.onTab)
        assertTrue("settled items stay as history", vm.state.value.history.filterIsInstance<HistoryItem.TabLine>().all { it.item.settled })
    }

    @Test fun editThenDeleteTakesTheDebtsWithThePerson() {
        val ahmed = runBlocking { f.people.addPerson("أحمد") }
        runBlocking { f.people.lend(ahmed, 50_000, null) }
        val vm = detail(ahmed)
        vm.saveEdit("أحمد سمير", "0100", PersonKind.SHOP)
        settle("edited") { vm.state.value.person?.name == "أحمد سمير" }
        assertEquals("0100", vm.state.value.person?.phone)
        assertEquals(PersonKind.SHOP, vm.state.value.person?.kind)
        vm.openDelete()
        assertEquals(DetailSheet.Delete, vm.state.value.sheet)
        vm.delete()
        settle("gone") { vm.state.value.missing }
        assertNull(runBlocking { db.peopleDao().personById(ahmed) })
        assertTrue(runBlocking { db.peopleDao().openDebts() }.isEmpty())
    }
}
