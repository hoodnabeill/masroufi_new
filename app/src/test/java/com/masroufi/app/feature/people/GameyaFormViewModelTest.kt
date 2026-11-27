package com.masroufi.app.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.repo.NewGameyaMember
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** Today 2026-11-01. */
@RunWith(AndroidJUnit4::class)
class GameyaFormViewModelTest : PlanVmTestBase() {
    private var cash = 0L

    @Before fun open() {
        plus.setDebugOverride(true)
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 1_000_000, color = 0)) }
    }

    private fun form(id: Long = 0L): GameyaFormViewModel {
        val vm = make(GameyaFormViewModel::class, { it.form }) { GameyaFormViewModel(f.gameyas, f.pockets, plus, clock, SavedStateHandle(mapOf("id" to id)), deletes) }
        settle("loaded") { vm.form.value.loaded }
        return vm
    }

    private fun active() = readOnIo { f.gameyas.observe().first() }

    private fun GameyaFormViewModel.fill(name: String = "جمعية", digits: List<Int> = listOf(5, 0, 0), others: List<String> = listOf("أ", "ب")) {
        setName(name)
        digits.forEach { digit(it) }
        others.forEach { setNewMember(it); addMember() }
    }

    @Test fun validationNeedsNameMonthlyMembersAndASeat() {
        val vm = form()
        val f0 = vm.form.value
        assertFalse("empty", f0.canSave)
        vm.setName("جمعية")
        assertFalse("no amount, no members", vm.form.value.canSave)
        vm.digit(5)
        assertFalse("no members", vm.form.value.canSave)
        vm.setNewMember("   ")
        vm.addMember()
        assertTrue("a blank name adds nobody", vm.form.value.members.isEmpty())
        vm.setNewMember("منى")
        vm.addMember()
        assertTrue(vm.form.value.canSave)
        vm.backspace()
        assertFalse("monthly 0", vm.form.value.canSave)
        vm.digit(3)
        vm.setName("  ")
        assertFalse("blank name", vm.form.value.canSave)
    }

    @Test fun myTurnStaysWithinTheParticipants() {
        val vm = form()
        vm.fill(others = listOf("أ", "ب"))
        vm.setMyTurn(9)
        assertEquals(3, vm.form.value.myTurn)
        vm.setMyTurn(0)
        assertEquals(1, vm.form.value.myTurn)
        vm.setMyTurn(3)
        vm.removeSeat(1)
        assertEquals("one member left: two seats", 2, vm.form.value.myTurn)
        assertTrue(vm.form.value.myTurn in 1..vm.form.value.participants)
    }

    @Test fun seatsMoveAndTheUsersSeatIsNeverRemoved() {
        val vm = form()
        vm.fill(others = listOf("أ", "ب", "ج"))
        vm.moveSeat(1, 1) // me later: أ, me, ب, ج
        assertEquals(2, vm.form.value.myTurn)
        vm.moveSeat(4, -1) // ج before ب
        assertEquals(listOf("أ", "ج", "ب"), vm.form.value.members)
        vm.moveSeat(3, -1) // ج moves above me: me takes its place
        assertEquals(listOf(3, listOf("أ", "ج", "ب")), listOf(vm.form.value.myTurn, vm.form.value.members))
        vm.removeSeat(3)
        assertEquals("the user's seat stays", 4, vm.form.value.participants)
        vm.removeSeat(1)
        assertEquals(listOf("ج", "ب"), vm.form.value.members)
        assertEquals(3, vm.form.value.participants)
    }

    @Test fun savingCreatesTheGameyaAndItsInstallmentItem() {
        val vm = form()
        vm.fill(name = " جمعية الشغل ", others = listOf("منى", "طارق"))
        vm.shiftStart(1)
        vm.setMyTurn(2)
        vm.save()
        settle("saved") { vm.form.value.saved }
        val g = active().single()
        assertEquals(listOf<Any>("جمعية الشغل", 50_000L, 2, YearMonth.of(2026, 12)), listOf(g.gameya.name, g.gameya.monthly, g.gameya.myTurn, g.myTurnMonth.minusMonths(1)))
        assertEquals(listOf("منى", "طارق"), runBlocking { db.gameyaDao().membersOf(g.gameya.id) }.map { it.name })
        assertEquals(RecurringKind.GAMEYA, runBlocking { db.recurringDao().byId(checkNotNull(g.gameya.recurringId)) }?.kind)
    }

    @Test fun aSecondGameyaOnFreeShowsThePlusCardInsteadOfTheForm() {
        runBlocking { f.gameyas.create("الأولى", 100_000, YearMonth.of(2026, 11), 1, listOf(NewGameyaMember("أ")), cash) }
        plus.setDebugOverride(false)
        val vm = form()
        assertTrue(vm.form.value.blocked)
        vm.fill()
        assertFalse(vm.form.value.canSave)
    }

    @Test fun plusGetsTheFormForASecondGameya() {
        runBlocking { f.gameyas.create("الأولى", 100_000, YearMonth.of(2026, 11), 1, listOf(NewGameyaMember("أ")), cash) }
        assertFalse(form().form.value.blocked)
    }

    @Test fun editChangesNameAndMonthlyOnlyAndArchiveGoesThroughTheQuestion() {
        val id = runBlocking { f.gameyas.create("الأولى", 100_000, YearMonth.of(2026, 11), 1, listOf(NewGameyaMember("أ")), cash) }
        val vm = form(id)
        assertEquals(listOf<Any>(true, "الأولى", "1000"), listOf(vm.form.value.editing, vm.form.value.name, vm.form.value.amount.text))
        assertTrue("edit needs no members", vm.form.value.canSave)
        vm.setName("جمعية البيت")
        vm.backspace()
        vm.digit(5)
        vm.save()
        settle("saved") { vm.form.value.saved }
        assertEquals(listOf<Any>("جمعية البيت", 100_500L), active().single().let { listOf(it.gameya.name, it.gameya.monthly) })

        vm.askArchive()
        assertTrue(vm.form.value.askArchive)
        assertEquals("asked, nothing archived yet", 1, active().size)
        vm.archive()
        settle("saved, the screen goes back") { vm.form.value.saved }
        assertEquals("archived after the undo window, not before", 1, active().size)
        passFiveSeconds()
        settle("archived") { active().isEmpty() }
    }
}
