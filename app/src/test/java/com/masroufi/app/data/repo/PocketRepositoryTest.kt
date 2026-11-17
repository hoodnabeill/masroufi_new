package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

/** Today 2026-11-17 09:00 Cairo; cash 3,900 EGP, wallet 5,000 EGP, bank 10,000 EGP; round-ups off. */
@RunWith(AndroidJUnit4::class)
class PocketRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov17 = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(at(nov17, 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L
    private var wallet = 0L
    private var bank = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()
    private fun millis(day: Int, hour: Int = 10, month: Int = 11) = at(LocalDate.of(2026, month, day), hour).toEpochMilli()

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = f.pockets.addPocket("cash", PocketKind.CASH, 390_000, countsInTicket = true, color = 0)
        wallet = f.pockets.addPocket("wallet", PocketKind.MOBILE_WALLET, 500_000, countsInTicket = true, color = 0)
        bank = f.pockets.addPocket("bank", PocketKind.BANK, 1_000_000, countsInTicket = true, color = 0)
    }

    private suspend fun log(kind: EntryKind, amount: Long, from: Long, day: Int, to: Long? = null, fee: Long = 0, month: Int = 11) =
        f.entries.log(NewEntry(kind = kind, amount = amount, pocketId = from, toPocketId = to, fee = fee, happenedAt = millis(day, month = month)))

    private suspend fun balance(id: Long) = db.pocketDao().balanceOf(id)

    @Test fun adjustChangesBalanceNotTicket() = runTest {
        setUp()
        val printed = f.tickets.printToday()
        log(EntryKind.ADJUST, -5_000, cash, 17)
        assertEquals("a negative adjustment lowers the balance", 385_000L, balance(cash))
        val row = checkNotNull(db.dayTicketDao().forDate(nov17))
        assertEquals("no hole, nothing spent", 0L to 0, row.spent to row.entryCount)
        assertEquals("printed amount unchanged", printed.amount, row.amount)

        assertThrows(IllegalArgumentException::class.java) { NewEntry(EntryKind.ADJUST, 0, cash, happenedAt = 0) }
        assertThrows(IllegalArgumentException::class.java) { NewEntry(EntryKind.SPENT, -100, cash, happenedAt = 0) }
        val withFee = runCatching { log(EntryKind.ADJUST, 1_000, cash, 17, fee = 100) }.exceptionOrNull()
        assertTrue("an adjustment carries no fee", withFee is IllegalArgumentException)
    }

    @Test fun adjustIsNotIncome() = runTest {
        setUp()
        log(EntryKind.ADJUST, 10_000, cash, 17)
        assertEquals(400_000L, balance(cash))
        assertNull("an adjustment never anchors the pay cycle", db.entryDao().latestReceivedAt())
        val summary = f.roll.month(YearMonth.of(2026, 11)).first().summary
        assertEquals("not received, not spent", 0L to 0L, summary.received to summary.spent)
    }

    @Test fun reconcileWritesDifference() = runTest {
        setUp()
        log(EntryKind.SPENT, 2_000, cash, 17)
        val id = checkNotNull(f.pockets.reconcile(cash, 380_000))
        val e = checkNotNull(db.entryDao().byId(id))
        assertEquals(listOf<Any?>(EntryKind.ADJUST, -8_000L, null, null), listOf(e.kind, e.amount, e.note, e.categoryId))
        assertEquals(380_000L, balance(cash))
        assertNull("equal: nothing written", f.pockets.reconcile(cash, 380_000))
        assertEquals(2, db.entryDao().countFlow().first())
        f.pockets.reconcile(cash, 381_000)
        assertEquals("counted more: a positive adjustment", 381_000L, balance(cash))
    }

    @Test fun cashCountMath() {
        assertEquals((2 * 200 + 3 * 100 + 50) * 100L + 750, cashCount(2, 3, 1, 750))
        assertEquals(0L, cashCount(0, 0, 0, 0))
        assertThrows(IllegalArgumentException::class.java) { cashCount(-1, 0, 0, 0) }
    }

    @Test fun movedThisMonthAndFees() = runTest {
        setUp()
        log(EntryKind.RECEIVED, 100_000, wallet, 2)
        log(EntryKind.SPENT, 20_000, wallet, 5)
        log(EntryKind.MOVED, 50_000, wallet, 10, to = cash, fee = 1_000) // cash-out
        log(EntryKind.MOVED, 30_000, wallet, 12, to = bank, fee = 500) // transfer
        log(EntryKind.MOVED, 10_000, bank, 14, to = wallet, fee = 200) // in; the bank pays this fee
        log(EntryKind.MOVED, 5_000, wallet, 15, to = cash, fee = 300) // cash-out
        log(EntryKind.ADJUST, -1_000, wallet, 16)
        log(EntryKind.SPENT, 99_000, wallet, 31, month = 10)
        assertEquals(100_000L + 20_000 + 51_000 + 30_500 + 10_000 + 5_300, f.pockets.movedThisMonth(wallet))
        assertEquals(FeesSummary(total = 1_800, cashOut = 2 to 1_300L, transfer = 1 to 500L), f.pockets.feesThisMonth(wallet))
        assertEquals(FeesSummary(total = 200, cashOut = 0 to 0L, transfer = 1 to 200L), f.pockets.feesThisMonth(bank))
    }

    @Test fun limitWarning() = runTest {
        setUp()
        fun bill(amount: Long, from: Long, due: LocalDate, paid: Boolean = false) =
            RecurringEntity(name = "r", amount = amount, pocketId = from, scheduleDay = due.dayOfMonth, nextDue = due, paidThisCycle = paid, kind = RecurringKind.BILL)
        db.recurringDao().insert(bill(50_000, wallet, LocalDate.of(2026, 11, 25)))
        db.recurringDao().insert(bill(1_000_000, wallet, LocalDate.of(2026, 12, 5))) // next month
        db.recurringDao().insert(bill(1_000_000, wallet, LocalDate.of(2026, 11, 20), paid = true))
        db.recurringDao().insert(bill(1_000_000, bank, LocalDate.of(2026, 11, 20))) // another pocket
        log(EntryKind.MOVED, 200_000, wallet, 3, to = cash)
        assertNull("no limit typed", f.pockets.limitWarning(wallet))

        val p = checkNotNull(db.pocketDao().byId(wallet))
        f.pockets.update(p.copy(monthlyLimit = 300_000))
        assertNull("200,000 moved + 50,000 due fits 300,000", f.pockets.limitWarning(wallet))
        db.recurringDao().insert(bill(80_000, wallet, LocalDate.of(2026, 11, 28)))
        assertEquals("330,000 passes: room left 100,000", 100_000L, f.pockets.limitWarning(wallet))
        log(EntryKind.MOVED, 150_000, wallet, 4, to = cash)
        assertEquals("already over", -50_000L, f.pockets.limitWarning(wallet))
    }

    @Test fun smsRecorded() = runTest {
        setUp()
        f.pockets.recordSms(wallet, 123_450)
        val p = checkNotNull(db.pocketDao().byId(wallet))
        assertEquals(123_450L to clock.millis(), p.lastSmsBalance to p.lastSmsAt)
        val views = f.pockets.observePockets().first()
        assertEquals(listOf(cash, wallet, bank), views.map { it.pocket.id })
        f.pockets.archive(bank)
        assertEquals("archived pockets leave the list", listOf(cash, wallet), f.pockets.observePockets().first().map { it.pocket.id })
    }
}
