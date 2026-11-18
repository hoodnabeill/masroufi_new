package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today 2026-11-12, one counting cash pocket with 5,000 EGP, a self-salary of 7,000 on the 1st of each month. */
@RunWith(AndroidJUnit4::class)
class FreelanceRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(LocalDate.of(2026, 11, 12)), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun day(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)
    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()
    private fun today(date: LocalDate) { clock.instant = at(date) }

    private suspend fun TestScope.setUp(salary: Long = 700_000, selfPayday: Int = 1) {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 500_000, color = 0))
        f.freelance.turnOn(salary, selfPayday)
    }

    private suspend fun vaultId() = checkNotNull(db.pocketDao().vault()).id
    private suspend fun vaultBalance() = db.pocketDao().balanceOf(vaultId())

    private suspend fun income(date: LocalDate, amount: Long) =
        f.entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = amount, pocketId = vaultId(), happenedAt = at(date).toEpochMilli()))

    private suspend fun salaryPaid(date: LocalDate, amount: Long) = f.entries.log(
        NewEntry(kind = EntryKind.MOVED, amount = amount, pocketId = vaultId(), toPocketId = cash, happenedAt = at(date).toEpochMilli()),
    )

    /** The mock: June..November 9,000 / 8,000 / 0 / 10,000 / 11,000 / 9,000 = 47,000, 28,600 of it paid out: 18,400 in the vault. */
    private suspend fun mockVault() {
        income(day(2026, 6, 10), 900_000)
        income(day(2026, 7, 10), 800_000)
        income(day(2026, 9, 10), 1_000_000)
        income(day(2026, 10, 10), 1_100_000)
        income(day(2026, 11, 10), 900_000)
        salaryPaid(day(2026, 10, 25), 2_860_000)
    }

    @Test fun bufferMonthsRoundDownToTheNearestHalf() {
        assertEquals("the mock: 18,400 / 7,000 = 2.5", 5, bufferHalves(1_840_000, 700_000))
        assertEquals(2, bufferHalves(700_000, 700_000))
        assertEquals(0, bufferHalves(349_999, 700_000))
        assertEquals(1, bufferHalves(350_000, 700_000))
        assertEquals(2, bufferHalves(1_049_999, 700_000)) // 1.49 months is one month
        assertEquals(3, bufferHalves(1_050_000, 700_000))
        assertEquals("never negative", 0, bufferHalves(-5_000, 700_000))
    }

    @Test fun safeUntilIsTodayPlusWholeMonthsAndFifteenDaysForAHalf() {
        val today = day(2026, 11, 12)
        assertEquals(day(2027, 1, 27), safeUntil(today, 5)) // two months and a half
        assertEquals(day(2027, 1, 12), safeUntil(today, 4))
        assertEquals(day(2026, 11, 27), safeUntil(today, 1))
        assertEquals(today, safeUntil(today, 0))
    }

    @Test fun mockVaultBufferSafeUntilAndNextPayday() = runTest {
        setUp()
        f.freelance.workCame(1_840_000)
        val v = f.freelance.view()
        assertEquals(listOf(true, 1_840_000L, 5), listOf(v.on, v.vault, v.bufferHalves))
        assertEquals(day(2027, 1, 27), v.safeUntil)
        assertEquals(700_000L, v.salary)
        assertEquals("this month's 1st is past: the first payment is next month's", day(2026, 12, 1), v.nextSelfPayday)
    }

    @Test fun dailyTicketComesFromTheSalaryLessFixedCostsOverTheCycle() = runTest {
        setUp()
        assertEquals("7,000 over the 31 days from 12-01", 22_500L, f.freelance.dailyTicket())
        f.installments.add("الموبايل", 30_000, day = 5, pocketId = cash, totalCount = 12)
        assertEquals("(7,000 - 300) / 31", 21_600L, f.freelance.dailyTicket())
        f.freelance.turnOff()
        assertEquals(0L, f.freelance.dailyTicket())
    }

    @Test fun turnOnCreatesTheVaultOnceAndOffRestoresThePreviousPayday() = runTest {
        setUp()
        val vault = checkNotNull(db.pocketDao().vault())
        assertEquals(listOf(PocketKind.VAULT, false), listOf(vault.kind, vault.countsInTicket))
        assertEquals(Payday.Day(1), f.settings.current().payday)
        f.freelance.turnOn(800_000, 5) // an edit: same vault, and "off" still goes back to the original payday
        assertEquals(1, db.pocketDao().all().first().count { it.kind == PocketKind.VAULT })
        assertEquals(listOf(800_000L, 5, Payday.Day(5)), f.settings.current().let { listOf(it.selfSalary, it.selfPayday, it.payday) })
        f.freelance.turnOff()
        val s = f.settings.current()
        assertEquals(listOf(false, Payday.Day(25)), listOf(s.freelanceOn, s.payday))
        assertNull(f.freelance.view().safeUntil)
    }

    @Test fun offRestoresANotFixedPayday() = runTest {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        f.settings.setPayday(Payday.NotFixed)
        f.freelance.turnOn(700_000, 1)
        assertEquals(Payday.Day(1), f.settings.current().payday)
        f.freelance.turnOff()
        assertEquals(Payday.NotFixed, f.settings.current().payday)
    }

    @Test fun selfPaydayMovesTheSalaryFromTheVaultOnceAMonthAndOffersTheRitual() = runTest {
        setUp()
        f.freelance.workCame(1_840_000)
        today(day(2026, 12, 1))
        val ticket = f.tickets.printToday()
        assertEquals(1_140_000L, vaultBalance())
        assertEquals(1_200_000L, db.pocketDao().balanceOf(cash))
        assertEquals("the ticket is cut from the money that arrived: 12,000 / 31", 38_700L, ticket.amount)
        val pay = checkNotNull(f.freelance.selfPay())
        assertEquals(listOf(YearMonth.of(2026, 12), 700_000L, false), listOf(pay.month, pay.paid, pay.short))
        val entry = checkNotNull(db.entryDao().byId(checkNotNull(pay.entryId)))
        assertEquals(listOf(EntryKind.MOVED, vaultId(), cash, 0L), listOf(entry.kind, entry.pocketId, entry.toPocketId, entry.fee))

        assertTrue("the ritual is offered for the move", f.ritual.shouldOffer(entry.id))
        f.ritual.markOffered(entry.id)
        assertFalse("once", f.ritual.shouldOffer(entry.id))

        f.tickets.printToday()
        today(day(2026, 12, 20))
        f.tickets.printToday()
        assertEquals("once a month", 1_140_000L, vaultBalance())

        today(day(2027, 1, 3)) // not opened on the 1st: the first open after it pays
        f.tickets.printToday()
        assertEquals(440_000L, vaultBalance())
        assertFalse(checkNotNull(f.freelance.selfPay()).short)

        today(day(2027, 2, 1)) // 4,400 left against a 7,000 salary
        f.tickets.printToday()
        assertEquals(0L, vaultBalance())
        assertEquals(listOf(440_000L, true), checkNotNull(f.freelance.selfPay()).let { listOf(it.paid, it.short) })
    }

    @Test fun vaultShortOnPaydayPaysWhatIsThereAndSaysSo() = runTest {
        setUp()
        f.freelance.workCame(300_000)
        today(day(2026, 12, 1))
        f.tickets.printToday()
        assertEquals("never negative", 0L, vaultBalance())
        assertEquals(800_000L, db.pocketDao().balanceOf(cash))
        val pay = checkNotNull(f.freelance.selfPay())
        assertEquals(listOf(300_000L, true), listOf(pay.paid, pay.short))
        assertTrue(f.ritual.shouldOffer(checkNotNull(pay.entryId)))
        assertEquals("the ritual builds the ticket on what was paid", 300_000L, f.ritual.preview(pay.paid, JarShare.Percent(0)).forTickets)
    }

    @Test fun emptyVaultMakesNoEntryButIsRemembered() = runTest {
        setUp()
        val before = db.entryDao().forDay(0, Long.MAX_VALUE).size
        today(day(2026, 12, 1))
        f.tickets.printToday()
        assertEquals(before, db.entryDao().forDay(0, Long.MAX_VALUE).size)
        val pay = checkNotNull(f.freelance.selfPay())
        assertEquals(listOf(null, 0L, true), listOf(pay.entryId, pay.paid, pay.short))
        assertEquals(500_000L, db.pocketDao().balanceOf(cash))
    }

    @Test fun selfPaydayWaitsForItsDayThenRunsOnTheFirstOpenAfterIt() = runTest {
        setUp(selfPayday = 20)
        f.freelance.workCame(1_840_000)
        f.tickets.printToday() // 11-12: not yet
        assertEquals(1_840_000L, vaultBalance())
        assertEquals(day(2026, 11, 20), f.freelance.nextSelfPayday())
        today(day(2026, 11, 22))
        f.tickets.printToday()
        assertEquals(1_140_000L, vaultBalance())
        assertEquals(day(2026, 12, 20), f.freelance.nextSelfPayday())
    }

    @Test fun nothingMovesWhileTheModeIsOff() = runTest {
        setUp()
        f.freelance.workCame(1_840_000)
        f.freelance.turnOff()
        today(day(2026, 12, 1))
        f.tickets.printToday()
        assertEquals(1_840_000L, vaultBalance())
        assertNull(f.freelance.selfPay())
    }

    @Test fun sixMonthsOfIncomeAndTheAverageFromTheMock() = runTest {
        setUp()
        mockVault()
        val v = f.freelance.view()
        assertEquals(
            listOf(900_000L, 800_000L, 0L, 1_000_000L, 1_100_000L, 900_000L),
            v.income.map { it.amount },
        )
        assertEquals(listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 11)), listOf(v.income.first().month, v.income.last().month))
        assertEquals("7,833 a month", 783_333L, v.average)
        assertEquals(1_840_000L, v.vault)
    }

    @Test fun raiseTheMocksSuggestionAndApplyingIt() = runTest {
        setUp()
        mockVault()
        assertEquals(SalarySuggestion(SuggestionKind.RAISE, 750_000), f.freelance.suggestion())
        assertEquals("nothing changed without a tap", 700_000L, f.settings.current().selfSalary)
        f.freelance.applySuggestion(SalarySuggestion(SuggestionKind.RAISE, 750_000))
        assertEquals(750_000L, f.settings.current().selfSalary)
        assertNull("once a month", f.freelance.suggestion())
    }

    @Test fun raiseNeedsTenPercentAndTwoMonthsOfBuffer() = runTest {
        setUp(salary = 750_000) // 7,833 is under 8,250
        mockVault()
        assertNull(f.freelance.suggestion())
        f.freelance.turnOn(700_000, 1)
        assertNotNull(f.freelance.suggestion())
        salaryPaid(day(2026, 11, 11), 540_000) // 13,000 left: 1.86 months
        assertNull("buffer under two months", f.freelance.suggestion())
    }

    @Test fun lowerWhenTheBufferIsUnderOneMonth() = runTest {
        setUp(salary = 800_000)
        mockVault()
        assertNull("the buffer is 2.3 months", f.freelance.suggestion())
        salaryPaid(day(2026, 11, 11), 1_200_000) // 6,400 left: under one month
        assertEquals(SalarySuggestion(SuggestionKind.LOWER, 750_000), f.freelance.suggestion())
    }

    @Test fun keepingItSilencesThreeMonths() = runTest {
        setUp()
        mockVault()
        f.freelance.keepSalary()
        assertNull(f.freelance.suggestion())
        today(day(2027, 1, 15))
        income(day(2027, 1, 10), 2_000_000) // keeps the raise eligible in January and February
        assertNull("November, December, January: silent", f.freelance.suggestion())
        assertEquals(700_000L, f.settings.current().selfSalary)
        today(day(2027, 2, 1))
        assertEquals(SalarySuggestion(SuggestionKind.RAISE, 800_000), f.freelance.suggestion())
    }

    @Test fun overdueIsTheDayAfterTheDueDate() = runTest {
        setUp()
        val late = f.freelance.addJob("شركة النور", "تصميم موقع", 500_000, day(2026, 11, 3))
        f.freelance.addJob("محل هدوم", "لوجو", 350_000, day(2026, 11, 12))
        f.freelance.addJob("أحمد", "بوستر", 100_000, day(2026, 11, 11))
        val jobs = f.freelance.jobs()
        assertEquals(listOf(true, false, true), jobs.sortedBy { it.job.id }.map { it.overdue })
        assertEquals(listOf(9, 0, 1), jobs.sortedBy { it.job.id }.map { it.daysLate })
        assertEquals(late, jobs.first().job.id)
        assertEquals(950_000L, f.freelance.view().owed)
    }

    @Test fun markPaidReceivesIntoTheVaultAndDeletingTheEntryUnpaysTheJob() = runTest {
        setUp()
        val id = f.freelance.addJob("شركة النور", "تصميم موقع", 500_000, day(2026, 11, 3))
        f.freelance.markPaid(id)
        f.freelance.markPaid(id) // a second tap changes nothing
        val job = checkNotNull(db.clientJobDao().byId(id))
        val entry = checkNotNull(db.entryDao().byId(checkNotNull(job.paidEntryId)))
        assertEquals(listOf(EntryKind.RECEIVED, vaultId(), 500_000L), listOf(entry.kind, entry.pocketId, entry.amount))
        assertEquals(500_000L, vaultBalance())
        assertEquals(1, db.entryDao().forDay(0, Long.MAX_VALUE).count { it.kind == EntryKind.RECEIVED })
        val paid = f.freelance.jobs().single()
        assertEquals(listOf(true, false), listOf(paid.paid, paid.overdue))
        assertEquals(0L, f.freelance.view().owed)

        f.entries.delete(entry.id)
        assertNull(db.clientJobDao().byId(id)!!.paidEntryId)
        assertTrue(f.freelance.jobs().single().let { !it.paid && it.overdue })
        assertEquals(0L, vaultBalance())
        assertEquals(500_000L, f.freelance.view().owed)
    }

    @Test fun workCameLandsInTheVaultAndCreatesItWhenMissing() = runTest {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        assertNull(db.pocketDao().vault())
        val id = f.freelance.workCame(250_000, "لوجو")
        val entry = checkNotNull(db.entryDao().byId(id))
        assertEquals(listOf(EntryKind.RECEIVED, 250_000L, "لوجو"), listOf(entry.kind, entry.amount, entry.note))
        assertEquals(vaultId(), entry.pocketId)
        assertEquals(250_000L, vaultBalance())
    }

    @Test fun jobsCrud() = runTest {
        setUp()
        val id = f.freelance.addJob("أحمد", "بوستر", 100_000, day(2026, 11, 20))
        f.freelance.editJob(checkNotNull(db.clientJobDao().byId(id)).copy(amount = 120_000, label = "بوستر 2"))
        assertEquals(listOf(120_000L, "بوستر 2"), db.clientJobDao().byId(id)!!.let { listOf(it.amount, it.label) })
        f.freelance.deleteJob(id)
        assertTrue(f.freelance.jobs().isEmpty())
    }
}
