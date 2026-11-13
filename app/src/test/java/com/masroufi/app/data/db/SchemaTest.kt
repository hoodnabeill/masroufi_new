package com.masroufi.app.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SchemaTest {
    private lateinit var db: TazkaraDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private suspend fun pocket(opening: Long, kind: PocketKind = PocketKind.CASH, counts: Boolean = true, archived: Boolean = false) =
        db.pocketDao().insert(PocketEntity(name = "p", kind = kind, openingBalance = opening, color = 0, countsInTicket = counts, archived = archived))

    private suspend fun entry(kind: EntryKind, amount: Long, from: Long, to: Long? = null, fee: Long = 0, roundUp: Long = 0) =
        db.entryDao().insert(EntryEntity(kind = kind, amount = amount, pocketId = from, toPocketId = to, happenedAt = 0, loggedAt = 0, fee = fee, roundUp = roundUp))

    private suspend fun balances() = db.pocketDao().balances().first().associate { it.pocketId to it.balance }

    @Test fun dayTicketDateIsUnique() = runTest {
        val date = LocalDate.of(2026, 11, 13)
        db.dayTicketDao().insert(DayTicketEntity(date = date, amount = 24_000, base = 24_000))
        try {
            db.dayTicketDao().insert(DayTicketEntity(date = date, amount = 1, base = 1))
            fail("second ticket for the same date must be rejected")
        } catch (expected: SQLiteConstraintException) {
        }
    }

    @Test fun balancesIncludeMoves() = runTest {
        val cash = pocket(100_000)
        val wallet = pocket(0, PocketKind.MOBILE_WALLET)
        entry(EntryKind.SPENT, 2_000, cash)
        entry(EntryKind.MOVED, 5_000, cash, wallet)
        assertEquals(mapOf(cash to 93_000L, wallet to 5_000L), balances())
    }

    @Test fun feeReducesBalance() = runTest {
        val cash = pocket(100_000)
        val wallet = pocket(0)
        entry(EntryKind.MOVED, 5_000, cash, wallet, fee = 500)
        assertEquals(94_500L, balances().getValue(cash))
        assertEquals(5_000L, balances().getValue(wallet))
    }

    @Test fun roundUpNotDeducted() = runTest {
        val cash = pocket(100_000)
        entry(EntryKind.SPENT, 2_000, cash, roundUp = 300)
        assertEquals(98_000L, balances().getValue(cash))
    }

    @Test fun jarTotalSigned() = runTest {
        db.jarDao().insert(JarMovementEntity(pot = Pot.JAR, amount = 7_200, source = JarSource.TICKET_LEFTOVER, at = 0))
        db.jarDao().insert(JarMovementEntity(pot = Pot.JAR, amount = -2_000, source = JarSource.MANUAL, at = 0))
        db.jarDao().insert(JarMovementEntity(pot = Pot.EMERGENCY, amount = 999, source = JarSource.MANUAL, at = 0))
        assertEquals(5_200L, db.jarDao().total(Pot.JAR).first())
        assertEquals(5_200L, db.jarDao().totalNow(Pot.JAR))
    }

    @Test fun balancesReEmitOnInsert() = runTest {
        val cash = pocket(100_000)
        val seen = Channel<List<PocketBalance>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.IO) { db.pocketDao().balances().collect { seen.send(it) } }
        try {
            // Real-time timeout: runTest's virtual clock would fire withTimeout immediately.
            withContext(Dispatchers.Default) { withTimeout(10_000) {
                assertEquals(100_000L, seen.receive().single { it.pocketId == cash }.balance)
                entry(EntryKind.SPENT, 2_000, cash)
                assertEquals(98_000L, seen.receive().single { it.pocketId == cash }.balance)
            } }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun forDayBoundary() = runTest {
        val cash = pocket(0)
        fun at(t: Long) = EntryEntity(kind = EntryKind.SPENT, amount = 1, pocketId = cash, happenedAt = t, loggedAt = t)
        db.entryDao().insert(at(1_000))
        db.entryDao().insert(at(2_000))
        assertEquals(listOf(1_000L), db.entryDao().forDay(1_000, 2_000).map { it.happenedAt })
    }

    @Test fun rangeReturnsDaysInclusive() = runTest {
        val d = LocalDate.of(2026, 11, 10)
        fun t(offset: Long, status: TicketStatus) = DayTicketEntity(date = d.plusDays(offset), amount = 1, base = 1, status = status)
        for ((o, s) in listOf(0L to TicketStatus.OPEN, 1L to TicketStatus.CLOSED_BY_HAND, 2L to TicketStatus.OPEN, 3L to TicketStatus.OPEN)) {
            db.dayTicketDao().insert(t(o, s))
        }
        assertEquals(listOf(d.plusDays(1), d.plusDays(2)), db.dayTicketDao().range(d.plusDays(1), d.plusDays(2)).first().map { it.date })
    }

    @Test fun countingTotalNetsSpentAndInternalMove() = runTest {
        val a = pocket(10_000)
        val b = pocket(20_000, PocketKind.BANK)
        entry(EntryKind.SPENT, 1_000, a)
        entry(EntryKind.MOVED, 3_000, a, b)
        db.pocketDao().insert(PocketEntity(name = "usd", kind = PocketKind.BANK, currency = "USD", openingBalance = 50_000, color = 0))
        assertEquals(29_000L, db.pocketDao().countingBalanceTotal())
    }

    @Test fun unpaidDueBefore() = runTest {
        val day = LocalDate.of(2026, 11, 10)
        fun r(amount: Long, due: LocalDate, paid: Boolean) =
            RecurringEntity(name = "r", amount = amount, pocketId = 1, scheduleDay = 1, nextDue = due, paidThisCycle = paid, kind = RecurringKind.BILL)
        db.recurringDao().insert(r(1_000, day.minusDays(1), false))
        db.recurringDao().insert(r(2_000, day.minusDays(5), false))
        db.recurringDao().insert(r(4_000, day.minusDays(1), true))
        db.recurringDao().insert(r(8_000, day, false))
        assertEquals(3_000L, db.recurringDao().unpaidDueBefore(day))
    }

    @Test fun countingTotalExcludesUnderTileAndArchived() = runTest {
        pocket(10_000)
        pocket(20_000, PocketKind.BANK)
        pocket(40_000, PocketKind.UNDER_TILE, counts = false)
        pocket(80_000, archived = true)
        assertEquals(30_000L, db.pocketDao().countingBalanceTotal())
    }
}
