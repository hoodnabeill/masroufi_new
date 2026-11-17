package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private suspend fun bill(amount: Long, due: LocalDate, pocket: Long = cash, day: Int = due.dayOfMonth) =
        db.recurringDao().insert(
            RecurringEntity(
                name = "bill", amount = amount, pocketId = pocket, scheduleDay = day, nextDue = due,
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

    private suspend fun reserved(before: LocalDate) = db.recurringDao().unpaidDueBefore(before)

    @Test fun overdueBillStaysReservedUntilPaid() = runTest {
        setUp()
        val id = bill(50_000, d(11, 5))
        f.tickets.printToday()
        assertEquals("overdue: reserved", 50_000L, reserved(d(11, 25)))
        f.recurring.markPaid(id, cash)
        assertEquals("paid: next due is December", d(12, 5), item(id).nextDue)
        assertEquals("not reserved any more", 0L, reserved(d(11, 25)))

        val carried = bill(30_000, d(11, 20))
        today(d(11, 26)) // payday passed with it unpaid
        f.tickets.printToday()
        assertEquals("unpaid across payday: still due", d(11, 20), item(carried).nextDue)
        assertEquals("both still reserved in the new cycle (30,000 overdue + 50,000 due 12-05)", 80_000L, reserved(d(12, 25)))
    }

    @Test fun latePaymentClearsOnlyThatMonth() = runTest {
        setUp()
        val id = bill(50_000, d(10, 20)) // October overdue, November due before payday 11-25
        f.tickets.printToday()
        f.recurring.markPaid(id, cash)
        assertEquals(d(11, 20), item(id).nextDue)
        assertEquals("paid October: November still reserved", 50_000L, reserved(d(11, 25)))
        f.recurring.markPaid(id, cash)
        assertEquals(d(12, 20), item(id).nextDue)
        assertEquals("paid both: none reserved", 0L, reserved(d(11, 25)))
    }

    @Test fun deletingPaymentRestoresDue() = runTest {
        setUp()
        val id = bill(50_000, d(10, 20))
        f.tickets.printToday()
        val first = f.entries.log(pay(id, d(11, 12)))
        val second = f.entries.log(NewEntry(kind = EntryKind.SPENT, amount = 50_000, pocketId = cash, happenedAt = millis(d(11, 12), 11), recurringId = id))
        assertEquals(d(12, 20), item(id).nextDue)
        f.entries.delete(first)
        assertEquals(d(11, 20), item(id).nextDue)
        f.entries.delete(second)
        assertEquals(d(10, 20), item(id).nextDue)
        assertEquals("reserved again", 50_000L, reserved(d(11, 25)))
    }

    @Test fun deletingOlderPaymentMovesBack() = runTest {
        setUp()
        val id = bill(50_000, d(10, 20))
        f.tickets.printToday()
        val older = f.entries.log(pay(id, d(11, 12)))
        f.entries.log(NewEntry(kind = EntryKind.SPENT, amount = 50_000, pocketId = cash, happenedAt = millis(d(11, 12), 11), recurringId = id))
        f.entries.delete(older)
        assertEquals("one payment left: one period past the start", d(11, 20), item(id).nextDue)
    }

    @Test fun normalPaymentReservesNextCycleAgain() = runTest {
        setUp()
        val id = bill(50_000, d(11, 20))
        f.tickets.printToday()
        f.entries.log(pay(id, d(11, 12)))
        assertEquals(d(12, 20), item(id).nextDue)
        assertEquals("paid in November: not reserved now", 0L, reserved(d(11, 25)))

        today(d(11, 26)) // new cycle 11-25..12-25
        f.tickets.printToday()
        assertEquals("December's due date is before the next payday: reserved", 50_000L, reserved(d(12, 25)))
    }

    @Test fun shiftedDueBySchedule() {
        fun r(due: LocalDate, day: Int, type: ScheduleType = ScheduleType.MONTHLY) =
            RecurringEntity(name = "r", amount = 1, pocketId = 1, scheduleDay = day, nextDue = due, scheduleType = type, kind = RecurringKind.BILL)
        assertEquals("31 clamps to 30 November", d(11, 30), r(d(10, 31), 31).shiftedDue(1))
        assertEquals("and back to 31 December", d(12, 31), r(d(11, 30), 31).shiftedDue(1))
        assertEquals(d(10, 31), r(d(11, 30), 31).shiftedDue(-1))
        assertEquals(LocalDate.of(2027, 3, 1), r(d(3, 1), 1, ScheduleType.YEARLY).shiftedDue(1))
    }

    @Test fun markPaidLogsWithoutPunching() = runTest {
        setUp()
        val id = f.recurring.add("الجمعية", 50_000, cash, d(11, 20))
        assertEquals("kind by name, as onboarding", RecurringKind.GAMEYA, item(id).kind)
        f.tickets.printToday()
        val entryId = f.recurring.markPaid(id, cash)
        val entry = checkNotNull(db.entryDao().byId(entryId))
        assertEquals(Triple(EntryKind.SPENT, 50_000L, id), Triple(entry.kind, entry.amount, entry.recurringId))
        assertEquals("reserved money: no punch", 0L, checkNotNull(db.dayTicketDao().forDate(d(11, 12))).spent)
        val row = f.recurring.observe().first().single()
        assertEquals(Pair("cash", d(12, 20)), Pair(row.pocketName, row.nextDue))
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
