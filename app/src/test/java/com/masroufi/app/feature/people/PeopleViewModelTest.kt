package com.masroufi.app.feature.people

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.repo.PeopleFilter
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Today 2026-11-01; cash holds 1,000 EGP. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
class PeopleViewModelTest : PlanVmTestBase() {
    private lateinit var vm: PeopleViewModel
    private var cash = 0L
    private val state get() = vm.state.value

    @Before fun open() {
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 100_000, color = 0)) }
        vm = make(PeopleViewModel::class, { it.state }) { PeopleViewModel(f.people, f.duty, f.gameyas, f.pockets, plus, clock) }
        settle("pockets") { state.pockets.isNotEmpty() }
    }

    private fun person(name: String, kind: PersonKind = PersonKind.PERSON) = runBlocking { f.people.addPerson(name, kind = kind) }
    private fun balance() = runBlocking { db.pocketDao().balanceOf(cash) }

    /** Ahmed owes 500, Mama is owed 500, the kiosk is owed 120. */
    private fun scenario(): Triple<Long, Long, Long> {
        val ahmed = person("أحمد")
        val mama = person("ماما")
        val kiosk = person("كشك عم حسن", PersonKind.SHOP)
        runBlocking {
            f.people.lend(ahmed, 50_000, null)
            f.people.borrow(mama, 50_000, null)
            f.people.addTabItem(kiosk, "سجائر", 12_000)
        }
        return Triple(ahmed, mama, kiosk)
    }

    @Test fun totalsAndTheOneActionPerRow() {
        val (ahmed, mama, kiosk) = scenario()
        settle("rows") { state.rows.size == 3 }
        assertEquals(50_000L, state.totals.theyOwe)
        assertEquals(1, state.totals.theyOweCount)
        assertEquals(62_000L, state.totals.iOwe)
        assertEquals(12_000L, state.totals.onTab)
        val actions = state.rows.associate { it.row.person.id to it.action }
        assertEquals(RowAction.REMIND, actions[ahmed])
        assertEquals(RowAction.REPAY, actions[mama])
        assertEquals(RowAction.SETTLE_TAB, actions[kiosk])
    }

    @Test fun filtersNarrowTheList() {
        val (ahmed, mama, kiosk) = scenario()
        val gift = person("خالتي")
        runBlocking {
            val wedding = f.duty.addOccasion("فرحي", LocalDate.of(2026, 12, 1), OccasionOwner.MINE)
            f.duty.addGift(wedding, gift, 100_000, GiftDirection.RECEIVED)
        }
        settle("all") { state.rows.size == 3 }
        vm.setFilter(PeopleFilter.LOANS)
        settle("loans") { state.filter == PeopleFilter.LOANS && state.rows.map { it.row.person.id }.toSet() == setOf(ahmed, mama) }
        vm.setFilter(PeopleFilter.TAB)
        settle("tab") { state.filter == PeopleFilter.TAB && state.rows.map { it.row.person.id } == listOf(kiosk) }
        vm.setFilter(PeopleFilter.DUTY)
        settle("duty") { state.filter == PeopleFilter.DUTY && state.rows.map { it.row.person.id } == listOf(gift) }
        vm.setFilter(PeopleFilter.ALL)
        settle("back to all") { state.rows.size == 3 }
    }

    @Test fun emptyListWhenNobodyOwesAnything() {
        settle("loaded") { !state.loading }
        assertTrue(state.rows.isEmpty())
        assertEquals(0L, state.totals.theyOwe)
    }

    @Test fun rowActionPicksTheBalanceToActOn() {
        scenario()
        settle("rows") { state.rows.size == 3 }
        val both = person("سمير")
        runBlocking {
            f.people.lend(both, 90_000, null)
            f.people.borrow(both, 10_000, null)
        }
        settle("mixed") { state.rows.any { it.row.person.id == both } }
        assertEquals("they owe more than I do", RowAction.REMIND, state.rows.first { it.row.person.id == both }.action)
    }

    // -- quick split

    @Test fun splitSharesAreEqualThenEditedAndAlwaysAddUp() {
        val p1 = person("كريم")
        val p2 = person("منى")
        settle("persons") { state.persons.size == 2 }
        vm.openSplit()
        settle("split sheet") { state.sheet is PeopleSheet.Split }
        assertEquals(cash, state.split.pocketId)
        listOf(3, 0, 0).forEach(vm::splitDigit)
        vm.toggleParticipant(p1)
        vm.toggleParticipant(p2)
        settle("draft") { state.split.participants.size == 3 && state.split.total == 30_000L }
        assertEquals(mapOf(ME to 10_000L, p1 to 10_000L, p2 to 10_000L), state.split.shares)
        vm.editShare(p1, "150")
        settle("edited") { state.split.edited.isNotEmpty() }
        val edited = state.split.shares!!
        assertEquals(mapOf(ME to 7_500L, p1 to 15_000L, p2 to 7_500L), edited)
        assertEquals(30_000L, edited.values.sum())
        vm.editShare(p1, "400")
        settle("too much") { state.split.edited[p1] == "400" }
        assertNull("cannot add up", state.split.shares)
        assertFalse(state.split.canSave)
        vm.editShare(p1, "150")
        settle("fixed again") { state.split.canSave }
        vm.confirmSplit()
        settle("saved") { state.sheet == null && state.notice?.kind == NoticeKind.SPLIT }
        val debts = runBlocking { db.peopleDao().openDebts() }
        assertEquals(setOf(p1 to 15_000L, p2 to 7_500L), debts.map { it.personId to it.amount }.toSet())
        assertTrue(debts.all { it.direction == DebtDirection.THEY_OWE })
        assertEquals("the whole bill left the pocket", 70_000L, balance())
    }

    @Test fun splitWithoutMeLeavesTheWholeBillOnTheOthers() {
        val p1 = person("كريم")
        settle("persons") { state.persons.size == 1 }
        vm.openSplit()
        listOf(1, 0, 0).forEach(vm::splitDigit)
        vm.toggleParticipant(ME)
        vm.toggleParticipant(p1)
        settle("draft") { state.split.participants == listOf(p1) && state.split.total == 10_000L }
        assertEquals(mapOf(p1 to 10_000L), state.split.shares)
        vm.confirmSplit()
        settle("saved") { state.sheet == null }
        assertEquals(10_000L, runBlocking { db.peopleDao().openDebts() }.single().amount)
        assertEquals(90_000L, balance())
    }

    @Test fun splitNeedsSomeoneElse() {
        settle("loaded") { !state.loading }
        vm.openSplit()
        vm.splitDigit(5)
        settle("typed") { state.split.total == 500L }
        assertFalse("only me", state.split.canSave)
    }

    // -- settle, repay, add

    @Test fun settleTabPaysOnceFromThePocketAndSaysSo() {
        val (_, _, kiosk) = scenario()
        settle("rows") { state.rows.size == 3 }
        vm.onAction(state.rows.first { it.row.person.id == kiosk }.row)
        settle("sheet") { state.sheet is PeopleSheet.SettleTab }
        val sheet = state.sheet as PeopleSheet.SettleTab
        assertEquals(12_000L, sheet.amount)
        vm.settleTab(kiosk, sheet.name, cash)
        settle("settled") { state.notice?.kind == NoticeKind.TAB_SETTLED }
        assertEquals("كشك عم حسن", state.notice?.name)
        assertNull(state.sheet)
        assertEquals(88_000L, balance())
        assertTrue(runBlocking { db.peopleDao().openTabOf(kiosk) }.isEmpty())
        settle("gone from the list") { state.rows.none { it.row.person.id == kiosk } }
        vm.clearNotice()
        settle("cleared") { state.notice == null }
    }

    @Test fun repayAllSettlesTheLoansFromThePocket() {
        val mama = person("ماما")
        runBlocking { f.people.borrow(mama, 50_000, cash) }
        assertEquals(150_000L, balance())
        settle("rows") { state.rows.size == 1 }
        vm.onAction(state.rows.single().row)
        settle("sheet") { state.sheet is PeopleSheet.Repay }
        vm.repayAll(mama, cash)
        settle("repaid") { state.notice?.kind == NoticeKind.REPAID }
        assertEquals(100_000L, balance())
        assertTrue(runBlocking { db.peopleDao().openDebts() }.isEmpty())
        settle("empty") { state.rows.isEmpty() }
    }

    @Test fun addALoanWithADueDateAndANewPerson() {
        vm.add(AddMode.THEY_OWE, null, "سمير", 20_000, " غدا ", cash, 7)
        settle("added") { state.rows.size == 1 }
        val debt = runBlocking { db.peopleDao().openDebts() }.single()
        assertEquals(DebtDirection.THEY_OWE, debt.direction)
        assertEquals("غدا", debt.reason)
        assertEquals(LocalDate.of(2026, 11, 8), debt.dueDate)
        assertEquals(80_000L, balance())
    }

    @Test fun addATabItemMakesAShop() {
        vm.add(AddMode.TAB, null, "كشك", 1_500, "شاي", null, null)
        settle("added") { state.rows.size == 1 }
        assertEquals(PersonKind.SHOP, state.rows.single().row.person.kind)
        assertEquals(1_500L, state.totals.onTab)
        assertEquals(100_000L, balance())
    }

    // -- reminders

    @Test fun reminderTextIsPoliteAndNamesTheDebt() {
        val text = reminderText(ApplicationProvider.getApplicationContext<Context>(), "أحمد", 50_000)
        assertTrue(text, "أحمد" in text && "500" in text)
        vm.reminded()
        settle("reminded") { state.notice?.kind == NoticeKind.REMINDED }
    }
}
