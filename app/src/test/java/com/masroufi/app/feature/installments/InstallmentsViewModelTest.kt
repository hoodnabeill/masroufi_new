package com.masroufi.app.feature.installments

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** Today 2026-11-01; the mock's three plans: 185 x 2 left, 650 x 7 left, 500 x 15 left = 1,335 a month, 12,420 still owed. */
@RunWith(AndroidJUnit4::class)
class InstallmentsViewModelTest : PlanVmTestBase() {
    private lateinit var vm: InstallmentsViewModel
    private var cash = 0L
    private val state get() = vm.state.value

    @Before fun open() {
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 5_000_000, color = 0)) }
        vm = make(InstallmentsViewModel::class, { it.state }) { InstallmentsViewModel(f.installments, f.settings, clock) }
    }

    private fun plan(name: String, monthly: Long, day: Int, total: Int?, paid: Int) =
        runBlocking { f.installments.add(name, monthly, day, cash, total, paid) }

    private fun mock(): Triple<Long, Long, Long> =
        Triple(plan("نضارة", 18_500, 8, 6, 4), plan("الموبايل", 65_000, 5, 12, 5), plan("التلاجة", 50_000, 12, 24, 9))

    @Test fun heroNumbersAndTheFreedomDate() {
        mock()
        settle("three rows") { state.rows.size == 3 }
        assertEquals(133_500L, state.monthly)
        assertEquals("1,335 / 30 = 44.5 goes down", 4_400L, state.perDay)
        assertEquals(1_242_000L, state.remaining)
        assertEquals(YearMonth.of(2028, 1), state.freedom)
    }

    @Test fun timelineBarsFollowTheMock() {
        mock()
        settle("bars") { state.bars.size == 4 }
        assertEquals(listOf(BarKind.NOW, BarKind.MONTH, BarKind.MONTH, BarKind.FREE), state.bars.map { it.kind })
        assertEquals("the label of a bar is the month a plan ends", listOf(null, YearMonth.of(2026, 12), YearMonth.of(2027, 5), null), state.bars.map { it.month })
        assertEquals("months between the points; the last is fixed", listOf(1, 5, 8, 3), state.bars.map { it.weight })
        assertEquals(1f, state.bars[0].fraction, 0.001f)
        assertEquals(115_000f / 133_500f, state.bars[1].fraction, 0.001f)
        assertEquals(50_000f / 133_500f, state.bars[2].fraction, 0.001f)
        assertEquals("the freedom bar is a stub", 0.06f, state.bars[3].fraction, 0.001f)
    }

    @Test fun growthLineGivesTheTicketGrowthPerEnd() {
        mock()
        settle("growth") { state.growth.size == 3 }
        assertEquals(listOf(600L, 2_100L, 1_600L), state.growth.map { it.growth })
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 5), YearMonth.of(2028, 1)), state.growth.map { it.month })
        assertEquals("the year shows for a month a year or more away", listOf(false, false, true), state.growth.map { it.withYear })
    }

    @Test fun rowWordingShortensForTheLastTwo() {
        val (glasses, phone, fridge) = mock()
        val one = plan("قسط", 10_000, 20, 4, 3)
        val open = plan("مفتوح", 10_000, 21, null, 0)
        settle("five rows") { state.rows.size == 5 }
        fun line(id: Long) = state.rows.first { it.plan.item.id == id }.line
        assertEquals(PlanLine.TwoLeft(YearMonth.of(2026, 12)), line(glasses))
        assertEquals(PlanLine.Left(7, 12, 5, YearMonth.of(2027, 5)), line(phone))
        assertEquals(PlanLine.Left(15, 24, 12, YearMonth.of(2028, 1)), line(fridge))
        assertEquals(PlanLine.OneLeft(YearMonth.of(2026, 11)), line(one))
        assertEquals(PlanLine.Open(21), line(open))
        assertEquals("an open-ended plan is not part of the freedom date", YearMonth.of(2028, 1), state.freedom)
    }

    @Test fun emptyWhenThereAreNoPlans() {
        settle("loaded") { !state.loading }
        assertTrue(state.empty)
        assertNull(state.freedom)
        assertTrue(state.bars.isEmpty())
    }

    @Test fun earlyPayoffIsTheSmallestRemainingWhenTheJarCoversIt() {
        val (glasses, _, _) = mock()
        settle("rows") { state.rows.size == 3 }
        assertNull("the jar is empty", state.early)
        runBlocking { f.jar.deposit(40_000) }
        settle("offer") { state.early != null }
        val e = checkNotNull(state.early)
        assertEquals(listOf(glasses, "نضارة", 37_000L, 600L), listOf(e.planId, e.name, e.amount, e.growth))
    }

    @Test fun keepGoingHidesTheOfferForTheMonth() {
        val (glasses, _, _) = mock()
        runBlocking { f.jar.deposit(40_000) }
        settle("offer") { state.early != null }
        vm.keepGoing(glasses)
        settle("hidden") { state.early == null }
    }

    @Test fun payOffWithTheConfirmedAmountFinishesThePlan() {
        val (glasses, _, _) = mock()
        runBlocking { f.jar.deposit(40_000) }
        settle("offer") { state.early != null }
        vm.payOff(glasses, 36_500) // the seller said a different amount
        settle("finished") { state.rows.first { it.plan.item.id == glasses }.ending }
        assertNull("nothing left to offer", state.early)
        assertFalse(state.payOffFailed)
        val entries = runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }.filter { it.recurringId == glasses }
        assertEquals(listOf(36_500L), entries.map { it.amount })
        assertEquals(0, state.rows.first { it.plan.item.id == glasses }.plan.left)
    }

    @Test fun payOffAboveTheFreeJarMovesNothingAndSaysSo() {
        val (glasses, _, _) = mock()
        runBlocking { f.jar.deposit(10_000) }
        settle("rows") { state.rows.size == 3 }
        vm.payOff(glasses, 37_000)
        settle("failed") { state.payOffFailed }
        assertEquals(0, runBlocking { db.entryDao().countAllForRecurring(glasses) })
        assertEquals(2, state.rows.first { it.plan.item.id == glasses }.plan.left)
    }

    @Test fun aFinishedPlanKeepsItsEndMotionUntilSeen() {
        val id = plan("قسط", 30_000, 5, 3, 2)
        val other = plan("تاني", 6_000, 9, 12, 0)
        settle("two rows") { state.rows.size == 2 }
        assertEquals(36_000L, state.monthly)
        runBlocking { f.recurring.markPaid(id, cash) } // the last payment
        settle("ending") { state.rows.first { it.plan.item.id == id }.ending }
        assertEquals("the hero holds the plan until its motion played, so the number rolls down after it", 36_000L, state.monthly)
        assertEquals("360 / 30 = 12", 1_200L, state.perDay)

        vm.finishedSeen(id)
        settle("folded away") { state.rows.none { it.plan.item.id == id } }
        assertEquals(6_000L, state.monthly)
        assertEquals(200L, state.perDay)
        assertEquals(listOf(other), state.rows.map { it.plan.item.id })
    }

    @Test fun finishedPlanThatWasSeenIsNeverShownAgain() {
        val id = plan("قسط", 30_000, 5, 3, 3)
        settle("ending") { state.rows.any { it.ending } }
        vm.finishedSeen(id)
        settle("gone") { state.rows.isEmpty() }
        assertTrue(state.empty)
    }

    // ---- the add / edit form ----

    private fun form(id: Long = 0): InstallmentFormViewModel {
        val form = make(InstallmentFormViewModel::class, { it.form }) {
            InstallmentFormViewModel(f.installments, f.recurring, f.pockets, clock, SavedStateHandle(mapOf("id" to id)))
        }
        settle("form loaded") { form.form.value.loaded }
        return form
    }

    private fun InstallmentFormViewModel.type(text: String) = text.forEach { c -> if (c == '.') point() else digit(c - '0') }

    @Test fun newPlanNeedsANameAnAmountAndAPocket() {
        val form = form()
        assertFalse(form.form.value.canSave)
        form.setName("الموبايل")
        assertFalse("no amount", form.form.value.canSave)
        form.type("650")
        assertTrue(form.form.value.canSave)
        form.setName("   ")
        assertFalse("a blank name", form.form.value.canSave)
        assertEquals("the first pocket is chosen", cash, form.form.value.pocketId)
    }

    @Test fun vaultPocketIsNotOffered() {
        runBlocking { db.pocketDao().insert(PocketEntity(name = "الخزنة", kind = PocketKind.VAULT, color = 0, countsInTicket = false)) }
        assertEquals(listOf(cash), form().form.value.pockets.map { it.id })
    }

    @Test fun totalNeverGoesUnderWhatWasPaid() {
        val form = form()
        repeat(12) { form.stepPaid(1) }
        assertEquals("paid stops at the total", listOf(12, 12), listOf(form.form.value.paidBefore, form.form.value.total))
        form.stepTotal(-5)
        assertEquals("the total stops at the paid count", 12, form.form.value.total)
        repeat(130) { form.stepTotal(1) }
        assertEquals(MAX_INSTALLMENTS, form.form.value.total)
        form.setOpen(true)
        assertEquals(listOf(null, 0), listOf(form.form.value.total, form.form.value.paidBefore))
        form.setOpen(false)
        assertEquals(12, form.form.value.total)
    }

    @Test fun savingAddsThePlanWithItsCounts() {
        val form = form()
        form.setName("التلاجة")
        form.type("500")
        form.setDay(12)
        repeat(12) { form.stepTotal(1) } // 24
        repeat(9) { form.stepPaid(1) }
        form.save()
        settle("saved") { form.form.value.saved }
        val plan = runBlocking { f.installments.view() }.plans.single()
        assertEquals(listOf("التلاجة", 12, 24, 9, 15), listOf(plan.item.name, plan.item.scheduleDay, plan.total, plan.item.paidBefore, plan.left))
        assertEquals(50_000L, plan.monthly)
        assertEquals(cash, plan.item.pocketId)
    }

    @Test fun editingLoadsThePlanAndSavesChanges() {
        val id = plan("نضارة", 18_500, 8, 6, 4)
        val form = form(id)
        assertEquals(listOf("نضارة", "185", 8, 6, 4), form.form.value.let { listOf(it.name, it.amount.text, it.day, it.total, it.paidBefore) })
        assertTrue(form.isEdit)
        form.setName("نضارة جديدة")
        form.save()
        settle("saved") { form.form.value.saved }
        assertEquals("نضارة جديدة", runBlocking { f.recurring.get(id) }?.name)
    }

    @Test fun deletingGoesBack() {
        val id = plan("نضارة", 18_500, 8, 6, 4)
        val form = form(id)
        form.delete()
        settle("gone") { form.gone.value }
        assertTrue(runBlocking { f.installments.view() }.plans.isEmpty())
    }
}
