package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

/** Today 2026-11-17 09:00 Cairo; round-ups off. */
@RunWith(AndroidJUnit4::class)
class RollRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov = YearMonth.of(2026, 11)
    private val clock = TestClock(at(nov.atDay(17), 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L
    private var wallet = 0L
    private var tile = 0L
    private var usd = 0L
    private var coffee = 0L
    private var transport = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()
    private fun d(day: Int) = nov.atDay(day)

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        suspend fun pocket(name: String, kind: PocketKind, counts: Boolean = true, currency: String = "EGP") =
            db.pocketDao().insert(PocketEntity(name = name, kind = kind, currency = currency, openingBalance = 390_000, color = 0, countsInTicket = counts))
        cash = pocket("cash", PocketKind.CASH)
        wallet = pocket("wallet", PocketKind.MOBILE_WALLET)
        tile = pocket("tile", PocketKind.UNDER_TILE, counts = false)
        usd = pocket("usd", PocketKind.BANK, currency = "USD")
        coffee = db.categoryDao().insert(CategoryEntity(name = "قهوة", icon = "cup", color = 0))
        transport = db.categoryDao().insert(CategoryEntity(name = "مواصلات", icon = "bus", color = 0))
    }

    private suspend fun log(
        kind: EntryKind, amount: Long, from: Long, date: LocalDate, hour: Int = 10, to: Long? = null, fee: Long = 0,
        categoryId: Long? = null, note: String? = null, recurringId: Long? = null,
    ) = f.entries.log(
        NewEntry(kind, amount, from, to, categoryId, note, at(date, hour).toEpochMilli(), recurringId, fee),
    )

    private suspend fun ticket(date: LocalDate, spent: Long, status: TicketStatus) =
        db.dayTicketDao().insert(DayTicketEntity(date = date, amount = 30_000, base = 30_000, spent = spent, status = status))

    /** A month of mixed entries, then ticket rows (inserted last so no recalc rewrites their spent). */
    private suspend fun TestScope.seedMonth() {
        setUp()
        val rent = db.recurringDao().insert(
            RecurringEntity(name = "rent", amount = 250_000, pocketId = cash, scheduleDay = 6, nextDue = d(6), kind = RecurringKind.BILL),
        )
        log(EntryKind.RECEIVED, 900_000, cash, d(3))
        log(EntryKind.RECEIVED, 10_000, usd, d(4))
        log(EntryKind.SPENT, 3_000, cash, d(5), categoryId = coffee, note = "الصبح")
        log(EntryKind.SPENT, 250_000, cash, d(6), recurringId = rent)
        log(EntryKind.MOVED, 50_000, cash, d(7), to = wallet, fee = 700)
        log(EntryKind.SPENT, 4_000, tile, d(8))
        log(EntryKind.SPENT, 5_000, usd, d(9), hour = 10)
        log(EntryKind.ADJUST, -2_000, cash, d(9), hour = 11)
        log(EntryKind.SPENT, 7_000, cash, LocalDate.of(2026, 10, 30))
        ticket(d(10), 25_000, TicketStatus.CLOSED_BY_HAND)
        ticket(d(11), 40_000, TicketStatus.CLOSED_AUTO)
        ticket(d(12), 0, TicketStatus.OPEN)
        ticket(LocalDate.of(2026, 10, 31), 0, TicketStatus.CLOSED_AUTO)
    }

    private suspend fun days(filter: RollFilter) = f.roll.month(nov, filter).first().days

    @Test fun rollMonthSummary() = runTest {
        seedMonth()
        val m = f.roll.month(nov).first()
        // EGP only: 3,000 spend + 250,000 rent + 700 move fee + 4,000 from the tile; the adjustment and USD stay out.
        assertEquals(RollSummary(spent = 257_700, received = 900_000, ticketsUnder = 1, ticketsClosed = 2), m.summary)
        assertEquals((12 downTo 3).map(::d), m.days.map { it.date })
        assertEquals(DayKind.TICKET, m.days.first { it.date == d(12) }.kind)
        assertEquals("a ticket day without entries still shows", emptyList<Any>(), m.days.first { it.date == d(12) }.entries)
        val nov9 = m.days.first { it.date == d(9) }
        assertEquals(DayKind.NON_TICKET, nov9.kind)
        assertEquals("newest first", listOf(EntryKind.ADJUST, EntryKind.SPENT), nov9.entries.map { it.kind })
        val coffeeRow = m.days.first { it.date == d(5) }.entries.single()
        assertEquals(listOf<Any?>("قهوة", "cash", "الصبح"), listOf(coffeeRow.categoryName, coffeeRow.pocketName, coffeeRow.note))
        assertEquals("wallet", m.days.first { it.date == d(7) }.entries.single().toPocketName)
        assertEquals(listOf(nov, YearMonth.of(2026, 10)), f.roll.months().first())
    }

    @Test fun rollFilters() = runTest {
        seedMonth()
        val spent = days(RollFilter.SPENT)
        assertEquals("spends and fee-paying moves", listOf(9, 8, 7, 6, 5).map(::d), spent.map { it.date })
        assertEquals("the adjustment is not a spend", listOf(EntryKind.SPENT), spent.first().entries.map { it.kind })
        assertEquals(listOf(4, 3).map(::d), days(RollFilter.INCOME).map { it.date })
        assertEquals(listOf(d(6)), days(RollFilter.RECURRING).map { it.date })
        assertEquals("the summary ignores the filter", f.roll.month(nov).first().summary, f.roll.month(nov, RollFilter.INCOME).first().summary)
    }

    @Test fun rollSearchNormalised() = runTest {
        setUp()
        val byCategory = log(EntryKind.SPENT, 1_000, cash, d(5), categoryId = coffee)
        val byNote = log(EntryKind.SPENT, 2_000, cash, d(7), categoryId = transport, note = "قهوة مع احمد")
        val hamza = log(EntryKind.SPENT, 3_000, cash, d(8), note = "أكل")
        log(EntryKind.SPENT, 4_000, cash, d(9), note = "بنزين")
        assertEquals("ه matches ة, newest first", listOf(byNote, byCategory), f.roll.search("قهوه").first().map { it.id })
        assertEquals("ا matches أ", listOf(hamza), f.roll.search("اكل").first().map { it.id })
        assertEquals("category name", listOf(byNote), f.roll.search("مواصلات").first().map { it.id })
        assertEquals(emptyList<Long>(), f.roll.search("  ").first().map { it.id })
        assertEquals(emptyList<Long>(), f.roll.search("xyz").first().map { it.id })
    }
}
