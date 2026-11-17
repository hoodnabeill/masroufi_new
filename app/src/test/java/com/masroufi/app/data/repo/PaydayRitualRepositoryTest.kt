package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Payday Day(25), today is payday 2026-11-25 (cycle 11-25..12-25, 30 days). Cash 1,000 EGP and a 500 EGP bill due 12-05
 * from it, unpaid.
 */
@RunWith(AndroidJUnit4::class)
class PaydayRitualRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 25)
    private val clock = TestClock(at(today, 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 100_000, color = 0))
        db.recurringDao().insert(
            RecurringEntity(
                name = "نت", amount = 50_000, pocketId = cash, scheduleDay = 5, nextDue = LocalDate.of(2026, 12, 5),
                kind = RecurringKind.BILL,
            ),
        )
    }

    private suspend fun income(amount: Long, pocket: Long = cash) =
        f.entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = amount, pocketId = pocket, happenedAt = clock.millis()))

    @Test fun firstRitualOfferedWhenIncomeCoversFixedCosts() = runTest {
        setUp()
        assertFalse("below the 500 EGP unpaid", f.ritual.shouldOffer(income(49_999)))
        val tile = db.pocketDao().insert(PocketEntity(name = "tile", kind = PocketKind.UNDER_TILE, color = 0, countsInTicket = false))
        assertFalse("not a counting pocket", f.ritual.shouldOffer(income(1_000_000, tile)))
        val salary = income(1_000_000)
        assertTrue(f.ritual.shouldOffer(salary))
        f.ritual.markOffered(salary)
        assertFalse("offered once per income", f.ritual.shouldOffer(salary))
    }

    @Test fun laterRitualsNeedHalfTheUsualSalary() = runTest {
        setUp()
        f.settings.setRitualDone(1_000_000, 10)
        assertTrue(f.ritual.shouldOffer(income(500_000)))
        assertFalse(f.ritual.shouldOffer(income(499_999)))
    }

    @Test fun previewInFixedOrder() = runTest {
        setUp()
        db.dayTicketDao().insert(DayTicketEntity(date = LocalDate.of(2026, 10, 25), amount = 0, base = 0, endOfMonth = true, status = TicketStatus.CLOSED_AUTO))
        db.dayTicketDao().insert(DayTicketEntity(date = LocalDate.of(2026, 10, 26), amount = 25_000, base = 25_000, status = TicketStatus.CLOSED_AUTO))
        income(1_000_000)
        // Cash 11,000 EGP; reserved 500 bill + 1,000 jar share; 9,500 / 30 days = 316.66 -> 316 EGP.
        assertEquals(
            RitualView(jarShare = 100_000, fixed = 50_000, forTickets = 850_000, ticket = 31_600, lastCycleTicket = 25_000),
            f.ritual.preview(1_000_000, JarShare.Percent(10)),
        )
        assertEquals("an amount above the salary is capped", 1_000_000L, f.ritual.preview(1_000_000, JarShare.Amount(2_000_000)).jarShare)
        assertEquals("never below zero", 0L, f.ritual.preview(1_000_000, JarShare.Amount(2_000_000)).forTickets)
    }

    @Test fun applyStoresShareAndReprints() = runTest {
        setUp()
        assertEquals("before the salary: 500 / 30 -> 16 EGP", 1_600L, f.tickets.printToday().amount)
        val salary = income(1_000_000)
        f.ritual.apply(1_000_000, JarShare.Percent(10), salary)
        assertEquals(100_000L, db.jarDao().totalNow(Pot.JAR))
        val s = f.settings.current()
        assertEquals(Triple(1_000_000L, 10, salary), Triple(s.usualSalary, s.lastJarSharePercent, s.lastRitualForEntryId))
        assertEquals("split applied to the salary", 1_000_000L, s.lastSplitIncome)
        assertEquals("re-printed with the salary in", 31_600L, db.dayTicketDao().forDate(today)?.amount)
        assertFalse(f.ritual.shouldOffer(salary))

        f.ritual.apply(800_000, JarShare.Amount(50_000))
        assertNull("an amount is not a percent", f.settings.current().lastJarSharePercent)
    }

    @Test fun applyKeepsAPunchedTicket() = runTest {
        setUp()
        f.tickets.printToday()
        f.entries.log(NewEntry(kind = EntryKind.SPENT, amount = 1_000, pocketId = cash, happenedAt = clock.millis()))
        income(1_000_000)
        f.ritual.apply(1_000_000, JarShare.Percent(5))
        assertEquals("today already punched: the ticket stays", 1_600L, db.dayTicketDao().forDate(today)?.amount)
        assertEquals(50_000L, db.jarDao().totalNow(Pot.JAR))
    }
}
