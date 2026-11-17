package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketCalc
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Payday Day(25), today 2026-11-12 09:00 (13 days left) unless a test says otherwise. */
@RunWith(AndroidJUnit4::class)
class OnboardingRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val clock = TestClock(at(nov12), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private val ob get() = f.onboarding

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()

    private fun TestScope.setUp() { f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements()) }

    private fun printed(amount: Long) = TicketCalc.Printed(amount, amount, 0)

    @Test fun previewMatchesMock() = runTest {
        setUp()
        ob.setPayday(Payday.Day(25))
        ob.setMoney(587_000)
        ob.setFixedCosts(listOf(FixedCost("الإيجار", 275_000)))
        assertEquals(printed(24_000), ob.previewTicket())
        assertEquals("the preview writes nothing", null, db.dayTicketDao().forDate(nov12))
    }

    @Test fun skippedMoneyPrintsStarter() = runTest {
        setUp()
        ob.setMoney(null)
        assertTrue(f.settings.current().moneyUnknown)
        assertEquals(printed(10_000), ob.previewTicket())
        val t = f.tickets.printToday()
        assertEquals(listOf(10_000L, 10_000L, 0L, false), listOf(t.amount, t.base, t.carriedIn, t.endOfMonth))
        ob.setMoney(587_000)
        assertFalse(f.settings.current().moneyUnknown)
    }

    @Test fun fixedCostsBecomeBillsBeforePayday() = runTest {
        setUp()
        ob.setMoney(587_000)
        ob.setFixedCosts(listOf(FixedCost("الإيجار", 275_000), FixedCost("الجمعية", 50_000), FixedCost("قسط", 30_000)))
        val items = db.recurringDao().all().first()
        assertEquals(listOf(RecurringKind.BILL, RecurringKind.GAMEYA, RecurringKind.INSTALLMENT), items.map { it.kind })
        assertEquals(listOf("الإيجار", "الجمعية", "قسط"), items.map { it.name })
        assertEquals(setOf(LocalDate.of(2026, 11, 24)), items.map { it.nextDue }.toSet())
        assertEquals(setOf("onboarding"), items.map { it.source }.toSet())
        assertEquals(setOf(db.pocketDao().firstCash()!!.id), items.map { it.pocketId }.toSet())
        assertTrue(items.none { it.paidThisCycle })
        ob.setFixedCosts(listOf(FixedCost("النت", 20_000)))
        assertEquals(listOf("النت"), db.recurringDao().all().first().map { it.name })
    }

    @Test fun moneyLessThanFixedIsEndOfMonth() = runTest {
        setUp()
        ob.setMoney(100_000)
        ob.setFixedCosts(listOf(FixedCost("الإيجار", 140_000)))
        val calc = ob.previewTicket()
        assertTrue(calc is TicketCalc.EndOfMonth)
        assertEquals(40_000L, calc.shortfall)
    }

    @Test fun stepPersists() = runTest {
        setUp()
        assertEquals(0, f.settings.current().onboardingStep)
        ob.setStep(3)
        assertEquals(3, f.settings.current().onboardingStep)
    }

    @Test fun finishPrintsFreshTicket() = runTest {
        setUp()
        f.seed.ensureSeeded()
        val stale = f.tickets.printToday() // Home printed on a fresh install: money 0 -> end of month
        assertTrue(stale.endOfMonth)
        ob.setMoney(587_000)
        ob.setFixedCosts(listOf(FixedCost("الإيجار", 275_000)))
        ob.finish(reminderHour = 21)
        val row = db.dayTicketDao().forDate(nov12)!!
        assertEquals(listOf(24_000L, false), listOf(row.amount, row.endOfMonth))
        val s = f.settings.current()
        assertEquals(listOf(true, 21, ONBOARDING_END_STEP), listOf(s.onboarded, s.reminderHour, s.onboardingStep))
        assertEquals(nov12.toEpochDay(), s.firstOpenDay)
    }

    @Test fun paydayNearPreviewUsesNextCycle() = runTest {
        setUp()
        ob.setPayday(Payday.Day(13)) // tomorrow
        ob.setMoney(390_000)
        assertTrue(ob.paydayIsNear())
        assertEquals("today: one day left", printed(390_000), ob.previewTicket())
        assertEquals("after payday: the 30-day cycle starting 13 Nov", printed(13_000), ob.previewTicket(startAfterPayday = true))
        ob.finish(reminderHour = null, startAfterPayday = true)
        assertEquals(13_000L, db.dayTicketDao().forDate(nov12)!!.amount)
    }

    @Test fun setMoneyAfterEntriesKeepsBalanceExact() = runTest {
        setUp()
        ob.setMoney(10_000)
        val cash = db.pocketDao().firstCash()!!
        f.entries.log(NewEntry(kind = EntryKind.SPENT, amount = 3_000, pocketId = cash.id, happenedAt = clock.millis()))
        ob.setMoney(5_000)
        assertEquals(5_000L, db.pocketDao().balanceOf(cash.id))
    }
}
