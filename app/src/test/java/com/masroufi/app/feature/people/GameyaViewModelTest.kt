package com.masroufi.app.feature.people

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.repo.NewGameyaMember
import com.masroufi.app.data.repo.SlotState
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

/** Today 2026-11-01. The mock: 10 people, 2,000 a month, started in August (month 4), the user is turn 8 (March). */
@RunWith(AndroidJUnit4::class)
class GameyaViewModelTest : PlanVmTestBase() {
    private lateinit var vm: GameyaViewModel
    private var cash = 0L
    private val names = listOf("منى", "طارق", "سلمى", "هدى", "عمرو", "نهى", "حسام", "دينا", "شريف")
    private val state get() = vm.state.value

    @Before fun open() {
        plus.setDebugOverride(true)
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 1_000_000, color = 0)) }
        vm = make(GameyaViewModel::class, { it.state }) { GameyaViewModel(f.gameyas, f.pockets, plus, deletes) }
        settle("loaded") { !state.loading && state.pockets.isNotEmpty() }
    }

    private fun create(name: String = "جمعية الشغل", others: List<String> = names, myTurn: Int = 8) = runBlocking {
        f.gameyas.create(name, 200_000, YearMonth.of(2026, 8), myTurn, others.map { NewGameyaMember(it) }, cash)
    }

    private fun member(name: String) = runBlocking { db.gameyaDao().membersOf(state.current!!.gameya.id).first { it.name == name } }

    @Test fun emptyWhenThereIsNoGameya() {
        assertTrue(state.empty)
        assertNull(state.current)
    }

    @Test fun wheelStatesAndTheTurnCardNumbers() {
        create()
        settle("gameya") { state.current != null }
        val v = state.current!!
        assertEquals(listOf(SlotState.PAID_OUT, SlotState.PAID_OUT, SlotState.PAID_OUT, SlotState.THIS_MONTH), v.wheel.take(4).map { it.state })
        assertTrue(v.wheel.drop(4).all { it.state == SlotState.WAITING })
        assertEquals("the user sits at turn 8", 8, v.wheel.single { it.isMe }.turn)
        assertEquals(listOf(10, 4), listOf(v.people, v.monthNumber))
        assertEquals("November to March", 4L, v.monthsUntil)
        assertEquals(2_000_000L, v.payout)
        assertEquals(YearMonth.of(2027, 3), v.myTurnMonth)
        assertEquals(0L, v.myPaidSoFar)
    }

    @Test fun paidAndUnpaidFollowTheTicks() {
        create()
        settle("gameya") { state.current != null }
        assertEquals(9, state.current!!.unpaidMembers.size)
        vm.togglePaid(member("عمرو").id)
        vm.togglePaid(member("دينا").id)
        settle("two ticked") { state.current!!.paidMembers.size == 2 }
        assertEquals(listOf("عمرو", "دينا"), state.current!!.paidMembers.map { it.name })
        assertEquals(2, state.current!!.paidCount)
        assertFalse(state.current!!.unpaidMembers.any { it.name == "عمرو" })
        vm.togglePaid(member("عمرو").id)
        settle("one left") { state.current!!.paidMembers.size == 1 }
        assertEquals(listOf("دينا"), state.current!!.paidMembers.map { it.name })
    }

    @Test fun payingMyInstallmentMarksItPaidAndMovesTheMoney() {
        create()
        settle("gameya") { state.current != null && !state.current!!.myPaidThisMonth }
        vm.openPay()
        settle("sheet") { state.sheet == GameyaSheet.Pay }
        vm.payMine(cash)
        settle("paid") { state.current!!.myPaidThisMonth && state.sheet == null }
        assertEquals(800_000L, runBlocking { db.pocketDao().balanceOf(cash) })
        assertEquals(1, state.current!!.paidCount)
    }

    @Test fun swappingTurnsGivesMeTheirSeatAndThemMine() {
        create()
        settle("gameya") { state.current != null }
        val hoda = member("هدى")
        vm.swapWith(hoda.id)
        settle("swapped") { state.current!!.gameya.myTurn == 4 }
        val v = state.current!!
        assertEquals(4, v.wheel.single { it.isMe }.turn)
        assertEquals("هدى", v.wheel.single { it.turn == 8 }.member?.name)
        assertEquals(YearMonth.of(2026, 11), v.myTurnMonth)
    }

    @Test fun aSecondGameyaOnFreeAsksForPlusButTheFirstDoesNot() {
        plus.setDebugOverride(false)
        var went = 0
        vm.requestNew { went++ }
        assertEquals("no gameya yet: the form opens", 1, went)
        assertFalse(state.plusSheet)
        create()
        settle("gameya") { state.current != null }
        vm.requestNew { went++ }
        settle("card") { state.plusSheet }
        assertEquals("the form did not open", 1, went)
        vm.dismissPlus()
        settle("closed") { !state.plusSheet }
        plus.setDebugOverride(true)
        vm.requestNew { went++ }
        assertEquals("Plus can add more", 2, went)
    }

    @Test fun severalGameyasShowOneAtATime() {
        val first = create("الأولى")
        create("التانية", others = listOf("أ", "ب"), myTurn = 1)
        settle("both") { state.gameyas.size == 2 }
        assertEquals(first, state.current!!.gameya.id)
        val second = state.gameyas.last().gameya.id
        vm.select(second)
        settle("switched") { state.current!!.gameya.id == second }
        assertEquals("التانية", state.current!!.gameya.name)
    }

    @Test fun planRowsAddEditDelete() {
        create()
        settle("gameya") { state.current != null }
        vm.savePlan(null, " العيد الكبير ", 900_000)
        settle("row") { state.current!!.plan.size == 1 }
        val row = state.current!!.plan.single()
        assertEquals(listOf<Any>("العيد الكبير", 900_000L), listOf(row.label, row.amount))
        assertEquals(2_000_000L - 900_000L, state.current!!.unplanned)
        vm.savePlan(row, "العيد", 1_000_000)
        settle("edited") { state.current!!.plan.single().amount == 1_000_000L }
        vm.deletePlan(row.id)
        settle("gone") { state.current!!.plan.isEmpty() }
    }
}
