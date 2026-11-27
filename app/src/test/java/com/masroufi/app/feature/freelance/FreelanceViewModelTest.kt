package com.masroufi.app.feature.freelance

import kotlinx.coroutines.flow.first
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.SuggestionKind
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** Today 2026-11-01; one counting cash pocket; a self-salary of 7,000 on the 1st unless a test says otherwise. */
@RunWith(AndroidJUnit4::class)
class FreelanceViewModelTest : PlanVmTestBase() {
    private lateinit var vm: FreelanceViewModel
    private var cash = 0L
    private val state get() = vm.state.value

    @Before fun open() {
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 500_000, color = 0)) }
    }

    /** The mode on (set up before the ViewModel exists, so no settings edit is in flight when tests read). */
    private fun on(salary: Long = 700_000, day: Int = 1) {
        runBlocking { f.freelance.turnOn(salary, day) }
        start()
    }

    private fun start() {
        vm = make(FreelanceViewModel::class, { it.state }) { FreelanceViewModel(f.freelance, clock, deletes) }
    }

    private fun vault() = runBlocking { checkNotNull(db.pocketDao().vault()).id }

    private fun income(year: Int, month: Int, amount: Long) = runBlocking {
        f.entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = amount, pocketId = vault(), happenedAt = at(LocalDate.of(year, month, 1)).toEpochMilli()))
    }

    @Test fun firstVisitWithTheModeOffShowsNothingYetThenTurnsOn() {
        start()
        settle("loaded") { !state.loading }
        assertFalse(state.on)
        assertFalse(state.empty)

        vm.turnOn(700_000, 1)
        settle("on and this month marked paid") { state.on && state.daysToPayday == 30 }
        assertEquals(700_000L, state.salary)
        assertEquals(1, state.selfPayday)
        assertNotNull("the vault was created", runBlocking { db.pocketDao().vault() })
        assertTrue("on with nothing yet is the empty state", state.empty)
    }

    @Test fun turningOnWithAnInvalidDayIsSwallowed() {
        start()
        settle("loaded") { !state.loading }
        vm.turnOn(0, 1)
        vm.turnOn(700_000, 40)
        settle("nothing") { true }
        assertFalse(state.on)
    }

    private fun vaultHolds(amount: Long) {
        on()
        income(2026, 10, amount)
        settle("vault") { state.vault == amount }
    }

    @Test fun bufferTwoMonthsAndAHalf() {
        vaultHolds(1_840_000) // 18,400 / 7,000 = 2.63
        assertEquals(listOf(2, true, 5), listOf(state.bufferMonths, state.bufferHalf, state.buffer))
        assertEquals("2.5 of the tank's 4 months", 0.625f, state.tank, 0.001f)
        assertEquals("today + 2 months + 15 days", LocalDate.of(2027, 1, 16), state.safeUntil)
    }

    @Test fun bufferExactlyOneMonthHasNoHalf() {
        vaultHolds(700_000)
        assertEquals(listOf(1, false), listOf(state.bufferMonths, state.bufferHalf))
        assertEquals(LocalDate.of(2026, 12, 1), state.safeUntil)
    }

    @Test fun bufferHalfAMonth() {
        vaultHolds(350_000)
        assertEquals(listOf(0, true, 1), listOf(state.bufferMonths, state.bufferHalf, state.buffer))
        assertEquals(LocalDate.of(2026, 11, 16), state.safeUntil)
    }

    @Test fun bufferNoneWhenTheVaultIsUnderHalfAMonth() {
        vaultHolds(100_000)
        assertEquals(listOf(0, false, 0), listOf(state.bufferMonths, state.bufferHalf, state.buffer))
        assertEquals(0f, state.tank, 0.0001f)
    }

    @Test fun bufferFullTankStopsAtFourMonths() {
        vaultHolds(7_000_000) // 10 months
        assertEquals(1f, state.tank, 0.0001f)
        assertEquals(10, state.bufferMonths)
    }

    @Test fun chartHasSixMonthsAnEmptyMonthAndTheSalaryLine() {
        on()
        income(2026, 6, 900_000)
        income(2026, 7, 800_000)
        income(2026, 9, 1_000_000)
        income(2026, 10, 1_100_000)
        income(2026, 11, 500_000)
        settle("bars") { state.bars.sumOf { it.amount } == 4_300_000L }
        assertEquals((6..11).map { YearMonth.of(2026, it) }, state.bars.map { it.month })
        assertEquals(
            listOf(BarTone.OVER, BarTone.OVER, BarTone.EMPTY, BarTone.OVER, BarTone.OVER, BarTone.UNDER),
            state.bars.map { it.tone },
        )
        assertEquals("a month with nothing is drawn as a sliver", 0f, state.bars[2].fraction, 0f)
        assertEquals(YearMonth.of(2026, 8), state.emptyMonth)
        assertEquals(716_666L, state.average)
        val top = 1_100_000f * 1.1f
        assertEquals(1_100_000f / top, state.bars[4].fraction, 0.001f)
        assertEquals("the dashed line is the salary", 700_000f / top, checkNotNull(state.line), 0.001f)
    }

    @Test fun raiseSuggestionAppliesOnTheTapAndGoesAway() {
        on()
        (6..11).forEach { income(2026, it, 1_200_000) }
        settle("raise") { state.suggestion != null }
        val sg = checkNotNull(state.suggestion)
        assertEquals(listOf(SuggestionKind.RAISE, 1_200_000L), listOf(sg.kind, sg.amount))
        assertEquals("nothing changed before the tap", 700_000L, state.salary)
        vm.applySuggestion(sg)
        settle("applied") { state.salary == 1_200_000L && state.suggestion == null }
    }

    @Test fun keepingTheSalaryHidesTheSuggestion() {
        on()
        (6..11).forEach { income(2026, it, 1_200_000) }
        settle("raise") { state.suggestion != null }
        vm.keepSalary()
        settle("kept") { state.suggestion == null }
        assertEquals(700_000L, state.salary)
    }

    @Test fun lowerSuggestionWhenTheBufferIsUnderAMonth() {
        on()
        income(2026, 11, 300_000)
        settle("lower") { state.suggestion != null }
        val sg = checkNotNull(state.suggestion)
        assertEquals(listOf(SuggestionKind.LOWER, 50_000L), listOf(sg.kind, sg.amount))
    }

    @Test fun overdueRowsCarryTheirDaysAndMarkPaidMovesMoneyIntoTheVault() {
        on()
        vm.addJob("شركة النور", "تصميم موقع", 500_000, -9) // due 2026-10-23
        vm.addJob("محل هدوم", "لوجو", 350_000, 19) // due 2026-11-20
        settle("two jobs") { state.jobs.size == 2 }
        assertEquals(850_000L, state.owed)
        val late = state.jobs[0]
        assertEquals(listOf("شركة النور", true, 9), listOf(late.job.client, late.overdue, late.daysLate))
        assertEquals(listOf(false, 0), state.jobs[1].let { listOf(it.overdue, it.daysLate) })

        vm.markPaid(late.job.id)
        settle("paid") { state.jobs.size == 1 }
        assertEquals(350_000L, state.owed)
        assertEquals("the money is in the vault", 500_000L, state.vault)
        val entry = runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }.single { it.kind == EntryKind.RECEIVED }
        assertEquals(listOf(vault(), 500_000L, "شركة النور · تصميم موقع"), listOf(entry.pocketId, entry.amount, entry.note))
        assertEquals("and it counts as this month's income", 500_000L, state.bars.last().amount)
    }

    @Test fun editAndDeleteAJob() {
        on()
        vm.addJob("عميل", "شغل", 100_000, 19)
        settle("job") { state.jobs.size == 1 }
        val job = state.jobs.single().job
        vm.editJob(job, "عميل تاني", "شغل جديد", 250_000, null)
        settle("edited") { state.jobs.single().job.amount == 250_000L }
        val edited = state.jobs.single().job
        assertEquals(listOf("عميل تاني", "شغل جديد", LocalDate.of(2026, 11, 20)), listOf(edited.client, edited.label, edited.dueDate))
        vm.editJob(edited, "عميل تاني", "شغل جديد", 250_000, 30)
        settle("new date") { state.jobs.single().job.dueDate == LocalDate.of(2026, 12, 1) }
        vm.deleteJob(job.id)
        settle("hidden") { state.jobs.isEmpty() }
        assertEquals("still stored inside the undo window", 1, readOnIo { f.freelance.observe().first() }.jobs.size)
        passFiveSeconds()
        settle("deleted") { readOnIo { f.freelance.observe().first() }.jobs.isEmpty() }
    }

    @Test fun workCameLandsInTheVaultWithAnOptionalNote() {
        on()
        vm.workCame(1_000_000, "شركة النور", "لوجو")
        settle("vault") { state.vault == 1_000_000L }
        val entry = runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }.single { it.kind == EntryKind.RECEIVED }
        assertEquals(listOf(vault(), "شركة النور · لوجو"), listOf(entry.pocketId, entry.note))
        assertFalse("the empty state is gone", state.empty)
        assertEquals(1_000_000L, state.bars.last().amount)

        vm.workCame(500_000, "", "  ")
        settle("second") { state.vault == 1_500_000L }
        val notes = runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }.filter { it.kind == EntryKind.RECEIVED }.map { it.note }
        assertTrue("a blank client and label leave no note", notes.contains(null))
    }

    @Test fun workCameWithNothingTypedIsRefused() {
        on()
        vm.workCame(0, "", "")
        settle("nothing") { true }
        assertEquals(0L, state.vault)
        assertNull(runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }.firstOrNull { it.kind == EntryKind.RECEIVED })
    }

    @Test fun nextPaydayAndTheDailyTicketWhenItIsAhead() {
        on(day = 25)
        settle("loaded") { state.daysToPayday == 24 }
        assertTrue("the daily ticket is the salary less fixed costs over the cycle", state.dailyTicket > 0)
        assertEquals(25, state.selfPayday)
    }
}
