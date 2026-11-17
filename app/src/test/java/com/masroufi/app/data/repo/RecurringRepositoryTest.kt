package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.PayCycle
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
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

/** Payday Day(25), today 2026-11-12 (cycle 10-25..11-25), one counting cash pocket with 3,900 EGP. */
@RunWith(AndroidJUnit4::class)
class RecurringRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(d(11, 12), 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()
    private fun millis(date: LocalDate, hour: Int = 10) = at(date, hour).toEpochMilli()
    private fun today(date: LocalDate) { clock.instant = at(date, 9) }

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0))
    }

    private suspend fun bill(amount: Long, due: LocalDate, pocket: Long = cash, paid: Boolean = false, day: Int = due.dayOfMonth) =
        db.recurringDao().insert(
            RecurringEntity(
                name = "bill", amount = amount, pocketId = pocket, scheduleDay = day, nextDue = due, paidThisCycle = paid,
                kind = RecurringKind.BILL,
            ),
        )

    private suspend fun item(id: Long) = checkNotNull(db.recurringDao().byId(id))

    private fun pay(id: Long, date: LocalDate) =
        NewEntry(kind = EntryKind.SPENT, amount = 50_000, pocketId = cash, happenedAt = millis(date), recurringId = id)

    @Test fun billFromUnderTileNotReserved() = runTest {
        setUp()
        val tile = db.pocketDao().insert(PocketEntity(name = "tile", kind = PocketKind.UNDER_TILE, color = 0, countsInTicket = false))
        val usd = db.pocketDao().insert(PocketEntity(name = "usd", kind = PocketKind.BANK, currency = "USD", color = 0))
        val old = db.pocketDao().insert(PocketEntity(name = "old", kind = PocketKind.CASH, color = 0, archived = true))
        bill(50_000, d(11, 20))
        bill(70_000, d(11, 20), pocket = tile)
        bill(30_000, d(11, 20), pocket = usd)
        bill(20_000, d(11, 20), pocket = old)
        assertEquals("only the counting EGP pocket's bill is reserved", 50_000L, db.recurringDao().unpaidDueBefore(d(11, 25)))
    }

    @Test fun paidAdvancesAtPayday() = runTest {
        setUp()
        val paid = bill(50_000, d(11, 20), paid = true)
        val endOfMonth = bill(30_000, d(10, 31), day = 31, paid = true)
        f.tickets.printToday() // 11-12: first print, nothing due before 10-25
        assertEquals("nothing moves inside the cycle", d(11, 20) to true, item(paid).let { it.nextDue to it.paidThisCycle })

        today(d(11, 25))
        f.tickets.printToday()
        assertEquals("paid bill: next month, unpaid again", d(12, 20) to false, item(paid).let { it.nextDue to it.paidThisCycle })
        assertEquals("day 31 clamps to 30 November", d(11, 30) to false, item(endOfMonth).let { it.nextDue to it.paidThisCycle })
        assertEquals("both reserved in the new cycle", 80_000L, db.recurringDao().unpaidDueBefore(d(12, 25)))
    }

    @Test fun unpaidOverdueStaysReserved() = runTest {
        setUp()
        val unpaid = bill(50_000, d(11, 20))
        today(d(11, 25))
        f.tickets.printToday()
        assertEquals("unpaid at payday: stays due", d(11, 20) to false, item(unpaid).let { it.nextDue to it.paidThisCycle })
        assertEquals("still reserved in the new cycle", 50_000L, db.recurringDao().unpaidDueBefore(d(12, 25)))
    }

    @Test fun lateBillPaymentAdvances() = runTest {
        setUp()
        val late = bill(50_000, d(11, 20))
        today(d(11, 26)) // cycle 11-25..12-25
        f.tickets.printToday()
        f.entries.log(pay(late, d(11, 26)))
        assertEquals("paid late: due date moves past the cycle start, still paid", d(12, 20) to true, item(late).let { it.nextDue to it.paidThisCycle })
        assertEquals("nothing reserved for it now", 0L, db.recurringDao().unpaidDueBefore(d(12, 25)))

        today(d(12, 26))
        f.tickets.printToday()
        assertEquals("next payday: next month, unpaid", LocalDate.of(2027, 1, 20) to false, item(late).let { it.nextDue to it.paidThisCycle })
    }

    @Test fun advanceDueBySchedule() {
        assertEquals(d(12, 31), advanceDue(d(11, 30), 31, ScheduleType.MONTHLY, d(12, 25)))
        assertEquals("several months at once", d(2, 28).plusYears(1), advanceDue(d(10, 31), 31, ScheduleType.MONTHLY, LocalDate.of(2027, 2, 1)))
        assertEquals(LocalDate.of(2027, 3, 1), advanceDue(d(3, 1), 1, ScheduleType.YEARLY, d(11, 25)))
        assertEquals("already due later: unchanged", d(12, 5), advanceDue(d(12, 5), 5, ScheduleType.MONTHLY, d(11, 25)))
    }

    @Test fun resetIdempotent() = runTest {
        setUp()
        bill(50_000, d(10, 20), paid = true)
        bill(30_000, d(9, 30), day = 31, paid = true)
        bill(20_000, d(12, 1))
        bill(10_000, d(11, 1))
        val cycle = PayCycle(d(11, 25), d(12, 25))
        f.recurring.resetForNewCycle(cycle)
        val once = db.recurringDao().allNow()
        assertEquals(
            "paid ones advance past the start; the later and the unpaid overdue untouched",
            listOf(d(12, 20), d(11, 30), d(12, 1), d(11, 1)), once.map { it.nextDue },
        )
        assertTrue("all unpaid", once.none { it.paidThisCycle })
        f.recurring.resetForNewCycle(cycle)
        assertEquals("a second run changes nothing", once, db.recurringDao().allNow())
    }

    @Test fun oldCyclePaymentDoesNotMarkCurrent() = runTest {
        setUp()
        today(d(11, 26)) // cycle 11-25..12-25
        val id = bill(50_000, d(12, 20))
        val old = f.entries.log(pay(id, d(11, 20)))
        assertFalse("a payment dated last cycle does not mark this cycle", item(id).paidThisCycle)
        val current = f.entries.log(pay(id, d(11, 26)))
        assertTrue(item(id).paidThisCycle)
        f.entries.delete(old)
        assertTrue("deleting an old-cycle payment never un-marks the current cycle", item(id).paidThisCycle)
        f.entries.edit(current, pay(id, d(11, 24)))
        assertFalse("moved back into the old cycle: this cycle is unpaid", item(id).paidThisCycle)
    }

    @Test fun markPaidLogsWithoutPunching() = runTest {
        setUp()
        val id = f.recurring.add("الجمعية", 50_000, cash, d(11, 20))
        assertEquals("kind by name, as onboarding", RecurringKind.GAMEYA, item(id).kind)
        f.tickets.printToday()
        val entryId = f.recurring.markPaid(id, cash)
        val entry = checkNotNull(db.entryDao().byId(entryId))
        assertEquals(Triple(EntryKind.SPENT, 50_000L, id), Triple(entry.kind, entry.amount, entry.recurringId))
        assertTrue("marked paid", item(id).paidThisCycle)
        assertEquals("reserved money: no punch", 0L, checkNotNull(db.dayTicketDao().forDate(d(11, 12))).spent)
        val row = f.recurring.observe().first().single()
        assertEquals(Triple("cash", d(11, 20), true), Triple(row.pocketName, row.nextDue, row.paidThisCycle))
    }

    @Test fun deleteArchivesWhilePaid() = runTest {
        setUp()
        val unused = f.recurring.add("نت", 20_000, cash, d(11, 20))
        val used = f.recurring.add("كهربا", 30_000, cash, d(11, 20))
        f.recurring.markPaid(used, cash)
        f.recurring.delete(unused)
        f.recurring.delete(used)
        assertNull("no entries: deleted", db.recurringDao().byId(unused))
        assertTrue("paid by an entry: archived", item(used).archived)
        assertEquals("archived is hidden", emptyList<Long>(), f.recurring.observe().first().map { it.id })
    }
}
