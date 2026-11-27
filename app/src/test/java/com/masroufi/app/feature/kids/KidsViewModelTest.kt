package com.masroufi.app.feature.kids

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.repo.ChoreView
import com.masroufi.app.data.repo.PlusRequired
import com.masroufi.app.ui.haptics.HapticLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class KidsViewModelTest : KidsVmTestBase() {
    private lateinit var vm: KidsViewModel
    private lateinit var haptics: MutableList<HapticLevel>

    private fun screen() {
        vm = make(KidsViewModel::class, { it.state }) { KidsViewModel(f.kids, f.childMode, f.pin, clock, deletes) }
        haptics = vm.record(vm.haptics)
        settle("loaded") { !vm.state.value.loading }
    }

    private val ui get() = vm.state.value
    private fun ticket(id: Long) = ui.tickets.single { it.view.child.id == id }
    private fun chore(id: Long): ChoreView = ui.view!!.chores.single { it.chore.id == id }

    @Test fun ticketsShowLeftPunchesAndTheJarOrTheWaitingAdvance() {
        plus.setDebugOverride(true)
        val m = malak()
        val o = omar()
        call {
            f.kids.save(m, 12_000)
            f.kids.spend(m, 1_000)
            f.kids.spend(m, 1_500)
            f.kids.spend(o, 15_000)
            f.kids.requestAdvance(o, 2_000)
        }
        screen()
        settle("two tickets") { ui.tickets.size == 2 && ticket(o).view.waitingAdvance != null }
        val malakTicket = ticket(m)
        assertEquals(listOf<Any?>(4_500L, 2, 5, 9), listOf(malakTicket.view.left, malakTicket.view.punches, malakTicket.slots, malakTicket.view.age))
        val jar = malakTicket.view.jar!!
        assertEquals(listOf<Any?>(12_000L, 8, 1_000L, 500L), listOf(jar.saved, jar.percent, jar.matchPer, jar.matchAdd))
        assertNull(malakTicket.view.waitingAdvance)

        val omarTicket = ticket(o)
        assertEquals("nothing left: the line names the weekday of the last spend", 0L, omarTicket.view.left)
        assertEquals(DayOfWeek.WEDNESDAY, omarTicket.finishedOn)
        assertEquals(2_000L, omarTicket.view.waitingAdvance!!.amount)
        assertNull(omarTicket.view.jar)
        // 70 + 60 (12 steps of 10 saved, 5 added each) + 150.
        assertEquals(7_000L + 6_000L + 15_000L, ui.total)
        assertEquals(DayOfWeek.SATURDAY, ui.view!!.handoverDay)
    }

    @Test fun jarSavingsAreNotPunches() {
        val m = malak()
        call { f.kids.saveFromLeft(m, 1_000) }
        screen()
        settle("left") { ui.tickets.isNotEmpty() }
        assertEquals(listOf<Any?>(0, 6_000L), listOf(ticket(m).view.punches, ticket(m).view.left))
        assertEquals(1_000L, ticket(m).view.jar!!.saved)
    }

    @Test fun anyChoreAsksWhoAndAnotherChildCannotTakeItOver() {
        plus.setDebugOverride(true)
        val m = malak()
        val o = omar()
        val any = call { f.kids.addChore("جاب العيش", 2_500) }
        val own = call { f.kids.addChore("رتّبت أوضتها", 1_000, m) }
        screen()
        settle("chores") { ui.view?.chores?.size == 2 }

        vm.tapChore(chore(any))
        settle("who sheet") { ui.sheet == KidsSheet.ChooseChild(any) }
        vm.tick(any, o)
        settle("ticked") { chore(any).tickedBy == o }
        assertEquals(listOf(HapticLevel.Confirm), haptics.toList())
        assertEquals(7_000L + 15_000L + 2_500L, ui.total)

        vm.tick(any, m)
        settle("taken") { ui.notice == KidsNotice.CHORE_TAKEN }
        assertEquals("changes nothing", o, chore(any).tickedBy)

        vm.tapChore(chore(own)) // a chore for one child is theirs
        settle("own ticked") { chore(own).tickedBy == m }
        vm.tapChore(chore(any)) // ticked: untick for whoever did it
        settle("unticked") { chore(any).tickedBy == null }
        assertEquals(7_000L + 15_000L + 1_000L, ui.total)
    }

    @Test fun handingOverPaysTheSumOnceAndStartsTheWeekOver() {
        plus.setDebugOverride(true)
        val m = malak()
        val o = omar()
        call { f.kids.spend(m, 2_500) }
        later()
        screen()
        settle("ready") { ui.total == 7_000L + 15_000L && ticket(m).view.punches == 1 }
        assertEquals("70 + 150, the mock's 220", 22_000L, ui.total)
        vm.handOver()
        settle("handed over") { ticket(m).view.punches == 0 && ticket(m).view.left == 7_000L }
        assertEquals(listOf(HapticLevel.Heavy), haptics.toList())
        assertEquals(15_000L, ticket(o).view.left)
        val item = call { db.recurringDao().allNow().single { it.kind == RecurringKind.KIDS } }
        assertEquals("one handover paid: the due date moved a week", LocalDate.of(2026, 11, 28), item.nextDue)
        assertEquals(1, call { db.entryDao().countAllForRecurring(item.id) })
    }

    @Test fun theHandoverDayMovesTheNextHandover() {
        malak()
        screen()
        settle("child") { ui.tickets.size == 1 }
        vm.setHandoverDay(DayOfWeek.THURSDAY)
        settle("thursday") { ui.view?.handoverDay == DayOfWeek.THURSDAY }
        assertEquals(LocalDate.of(2026, 11, 19), ui.view!!.nextHandover)
    }

    @Test fun freeAllowsOneChildAndTheSecondOpensTheComingSheet() {
        malak()
        screen()
        settle("child") { ui.tickets.size == 1 }
        assertFalse(ui.view!!.canAddChild)
        vm.openPlus()
        settle("sheet") { ui.sheet == KidsSheet.Plus }
        try {
            call { f.kids.addChild("عمر", 2013, 3, 15_000) }
            fail("a second child needs Plus")
        } catch (e: PlusRequired) {
            // expected
        }
    }

    @Test fun anExtraChildIsReadOnlyWhenPlusEnds() {
        plus.setDebugOverride(true)
        val m = malak()
        val o = omar()
        val choreId = call { f.kids.addChore("جاب العيش", 2_500, o) }
        screen()
        settle("both editable") { ui.tickets.size == 2 && ui.tickets.all { it.view.editable } }
        plus.setDebugOverride(false)
        settle("extra read-only") { ui.tickets.size == 2 && ticket(m).view.editable && !ticket(o).view.editable }
        assertEquals("still part of the handover", 7_000L + 15_000L, ui.total)
        vm.tick(choreId, o)
        settle("plus sheet") { ui.sheet == KidsSheet.Plus }
        assertNull("nothing ticked", chore(choreId).tickedBy)
    }

    @Test fun anAdvanceAnswerIsConfirmAndTheHandoverTakesItOff() {
        val m = malak()
        val id = call { f.kids.requestAdvance(m, 2_000) }
        screen()
        settle("waiting") { ticket(m).view.waitingAdvance?.id == id }
        vm.answerAdvance(id, true)
        settle("approved") { ticket(m).view.waitingAdvance == null && ticket(m).view.left == 9_000L }
        assertEquals(listOf(HapticLevel.Confirm), haptics.toList())
        assertEquals("70 less the 20 taken", 5_000L, ui.total)
    }

    @Test fun theFirstChildScreenAsksForAPinTwiceAndOnlyMatchingOnesCount() {
        val m = malak()
        screen()
        settle("child") { ui.tickets.size == 1 }
        vm.openChildScreen(m)
        settle("setup") { ui.pinSetup != null }
        assertEquals(PinStage.ENTER, ui.pinSetup!!.stage)
        listOf(1, 2, 3, 4).forEach(vm::setupDigit)
        vm.setupNext()
        settle("repeat") { ui.pinSetup?.stage == PinStage.REPEAT }
        listOf(1, 2, 3, 5).forEach(vm::setupDigit)
        vm.setupNext()
        settle("mismatch") { ui.pinSetup?.mismatch == true }
        assertEquals(PinStage.ENTER, ui.pinSetup!!.stage)
        assertEquals("", ui.pinSetup!!.digits)
        assertFalse(call { f.pin.isSet() })
        assertNull(call { f.settings.current().childModeId })
        assertEquals(List(8) { HapticLevel.Tick }, haptics.toList())

        listOf(1, 2, 3, 4).forEach(vm::setupDigit)
        vm.setupNext()
        settle("repeat again") { ui.pinSetup?.stage == PinStage.REPEAT }
        listOf(1, 2, 3, 4).forEach(vm::setupDigit)
        vm.setupNext()
        settle("pinned") { call { f.settings.current().childModeId } == m }
        assertTrue(call { f.pin.isSet() })
        settle("setup closed") { ui.pinSetup == null }
    }

    @Test fun withAPinTheChildScreenOpensAtOnce() {
        val m = malak()
        call { f.pin.set("4321") }
        screen()
        settle("child") { ui.tickets.size == 1 }
        vm.openChildScreen(m)
        settle("pinned") { call { f.settings.current().childModeId } == m }
        assertNull(ui.pinSetup)
        assertNotNull(call { f.settings.current().childModeId })
    }
}
