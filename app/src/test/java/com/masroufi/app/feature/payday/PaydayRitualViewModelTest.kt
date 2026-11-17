package com.masroufi.app.feature.payday

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.repo.JarShare
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Payday 2026-11-25 (default Day(25)): cash 1,000 EGP and a 10,000 EGP salary just received into it. */
@RunWith(AndroidJUnit4::class)
class PaydayRitualViewModelTest : PlanVmTestBase() {
    private var entryId = 0L
    private val salary = 1_000_000L
    private val state get() = vm.state.value
    private lateinit var vm: PaydayRitualViewModel

    @Before fun open() {
        clock.instant = at(LocalDate.of(2026, 11, 25))
        runBlocking {
            val cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 100_000, color = 0))
            entryId = f.entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = salary, pocketId = cash, happenedAt = clock.millis()))
        }
    }

    private fun start() {
        vm = make(PaydayRitualViewModel::class, { it.state }) {
            PaydayRitualViewModel(f.ritual, f.jar, db.entryDao(), db.pocketDao(), f.settings, SavedStateHandle(mapOf("entryId" to entryId)))
        }
        settle("preview") { state.view != null }
    }

    @Test fun defaultShareIsTenPercentThenTheLastOne() {
        start()
        assertEquals(JarShare.Percent(10), state.share)
        assertEquals(100_000L, state.view!!.jarShare)
        assertEquals("cash", state.pocketName)
        assertEquals(salary, state.salary)
    }

    @Test fun defaultShareComesFromSettings() {
        runBlocking { f.settings.setRitualDone(900_000, 15) }
        start()
        assertEquals(JarShare.Percent(15), state.share)
        assertEquals(150_000L, state.view!!.jarShare)
    }

    @Test fun chipChangesRecomputeThePreview() {
        start()
        val before = state.view!!
        vm.setPercent(5)
        settle("5%") { state.view?.jarShare == 50_000L }
        val after = state.view!!
        assertEquals(before.forTickets + 50_000, after.forTickets)
        assertTrue("less to the jar, a bigger ticket", after.ticket > before.ticket)
    }

    @Test fun amountShareIsATypedAmount() {
        start()
        vm.openAmount()
        assertTrue(state.amountSheet)
        vm.setAmount(30_000)
        settle("amount") { state.view?.jarShare == 30_000L }
        assertEquals(JarShare.Amount(30_000), state.share)
        assertFalse(state.amountSheet)
    }

    @Test fun printAppliesOnceAndFinishes() {
        start()
        vm.print()
        vm.print() // double tap
        settle("finished") { state.finished }
        assertTrue(state.printed)
        assertEquals("the share went to the jar once", 100_000L, runBlocking { db.jarDao().totalNow(Pot.JAR) })
        assertEquals(entryId, runBlocking { f.settings.current().lastRitualForEntryId })
        assertEquals(salary, runBlocking { f.settings.current().usualSalary })
    }

    @Test fun skipAppliesNothing() {
        start()
        vm.skip()
        settle("finished") { state.finished }
        assertFalse(state.printed)
        assertEquals(0L, runBlocking { db.jarDao().totalNow(Pot.JAR) })
        assertNull(runBlocking { f.settings.current().usualSalary })
        assertEquals("not offered again", entryId, runBlocking { f.settings.current().lastRitualForEntryId })
    }

    @Test fun debtsStepIsHiddenWithoutDebts() {
        start()
        assertTrue(state.debts.isEmpty())
        assertEquals(0L, state.view!!.debts)
    }

    @Test fun debtsStepListsWhatIOweAndRepaysTheTickedOnes() {
        val (mama, papa) = runBlocking {
            f.people.addPerson("ماما") to f.people.addPerson("بابا")
        }
        val owedMama = runBlocking { f.people.borrow(mama, 50_000, null) }
        val owedPapa = runBlocking { f.people.borrow(papa, 30_000, null) }
        runBlocking { f.people.lend(papa, 10_000, null) } // what they owe me is not step 2
        start()
        assertEquals(setOf(owedMama, owedPapa), state.debts.map { it.debt.id }.toSet())
        assertEquals("all ticked, as drawn", setOf(owedMama, owedPapa), state.repay)
        assertEquals(80_000L, state.view!!.debts)
        val ticketsWith = state.view!!.forTickets
        vm.toggleDebt(owedPapa)
        settle("untick") { state.view?.debts == 50_000L }
        assertEquals(ticketsWith + 30_000, state.view!!.forTickets)
        vm.print()
        settle("finished") { state.finished }
        assertTrue(runBlocking { db.peopleDao().debtById(owedMama)!!.settled })
        assertFalse(runBlocking { db.peopleDao().debtById(owedPapa)!!.settled })
        assertEquals("repaid out of the salary's pocket", 50_000L, -runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }.filter { it.debtId == owedMama }.sumOf { it.amount })
    }
}
