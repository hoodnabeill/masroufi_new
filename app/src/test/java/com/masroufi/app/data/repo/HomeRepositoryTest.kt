package com.masroufi.app.data.repo

import androidx.compose.ui.graphics.toArgb
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.PayCycle
import com.masroufi.app.core.Payday
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.ProverbCollectedEntity
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TomorrowBasis
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Payday Day(25), today Thursday 2026-11-12 (week from Saturday 11-07), one cash pocket with 3,900 EGP. */
@RunWith(AndroidJUnit4::class)
class HomeRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val clock = TestClock(at(nov12, 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()
    private fun millis(date: LocalDate, hour: Int) = at(date, hour).toEpochMilli()

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0))
    }

    private suspend fun category(name: String) =
        db.categoryDao().insert(CategoryEntity(name = name, icon = "grid", color = 0))

    private suspend fun spend(amount: Long, categoryId: Long?, date: LocalDate = nov12, hour: Int = 10) = f.entries.log(
        NewEntry(kind = EntryKind.SPENT, amount = amount, pocketId = cash, categoryId = categoryId, happenedAt = millis(date, hour)),
    )

    private fun day(date: LocalDate, status: TicketStatus = TicketStatus.CLOSED_BY_HAND) =
        DayTicketEntity(date = date, amount = 30_000, base = 30_000, status = status)

    @Test fun homeEmitsAfterLog() = runTest {
        setUp()
        f.tickets.printToday()
        val seen = Channel<HomeData>(Channel.UNLIMITED)
        val job = launch(Dispatchers.IO) { f.home.observeHome().collect { seen.send(it) } }
        try {
            // Real-time timeout: runTest's virtual clock would fire withTimeout immediately.
            withContext(Dispatchers.Default) { withTimeout(10_000) {
                val first = seen.receive()
                assertEquals("fresh ticket: nothing spent, no holes", 0L to 0, first.today?.let { it.spent to it.entryCount })
                spend(5_000, categoryId = null)
                // Upstreams re-emit one by one; wait for the emission that carries both the punched row and the balance.
                var next = seen.receive()
                while (next.today?.spent != 5_000L || next.spendable != 385_000L) next = seen.receive()
                assertEquals("one hole for the spend", 1, next.today?.entryCount)
                assertEquals("ticket amount unchanged", 30_000L, next.today?.amount)
            } }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun todayRekeysAfterMidnightOnLog() = runTest {
        setUp()
        val nov13 = nov12.plusDays(1)
        val nov14 = nov12.plusDays(2)
        f.settings.setPayday(Payday.Day(14)) // so the cycle changes when the day does
        clock.instant = at(nov13, 23)
        f.tickets.printToday()
        val seen = Channel<HomeData>(Channel.UNLIMITED)
        val job = launch(Dispatchers.IO) { f.home.observeHome().collect { seen.send(it) } }
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000) {
                var h = seen.receive()
                while (h.today?.date != nov13) h = seen.receive()
                assertEquals("Friday 11-13: cycle 10-14..11-14", PayCycle(LocalDate.of(2026, 10, 14), nov14), h.cycle)
                clock.instant = at(nov14, 0).plusSeconds(60) // 00:01 Saturday, no new ticket printed yet
                spend(5_000, categoryId = null, date = nov14, hour = 0)
                while (h.cycle.start != nov14) h = seen.receive()
                assertEquals("payday 11-14 starts the new cycle", PayCycle(nov14, LocalDate.of(2026, 12, 14)), h.cycle)
                assertEquals("new week from Saturday 11-14 has no rows yet", emptyList<DayTicketEntity>(), h.week)
                assertEquals("no ticket for 11-14 yet, and 11-13's row is not today's", null, h.today)
            } }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun homeTotals() = runTest {
        setUp()
        for (d in 9..11) db.dayTicketDao().insert(day(LocalDate.of(2026, 11, d)))
        db.dayTicketDao().insert(day(LocalDate.of(2026, 11, 8), TicketStatus.CLOSED_AUTO))
        db.jarDao().insert(JarMovementEntity(pot = Pot.JAR, amount = 7_200, source = JarSource.TICKET_LEFTOVER, at = 0))
        db.jarDao().insert(JarMovementEntity(pot = Pot.EMERGENCY, amount = 999, source = JarSource.MANUAL, at = 0))
        db.proverbDao().insert(ProverbCollectedEntity(number = 3, collectedOn = nov12))
        db.proverbDao().insert(ProverbCollectedEntity(number = 7, collectedOn = nov12))
        fun bill(amount: Long, due: LocalDate, paid: Boolean = false) = RecurringEntity(
            name = "bill", amount = amount, pocketId = cash, scheduleDay = due.dayOfMonth, nextDue = due, paidThisCycle = paid,
            kind = RecurringKind.BILL,
        )
        db.recurringDao().insert(bill(50_000, LocalDate.of(2026, 11, 20)))
        db.recurringDao().insert(bill(10_000, LocalDate.of(2026, 12, 15))) // paid: next due is after payday
        db.recurringDao().insert(bill(70_000, LocalDate.of(2026, 11, 25))) // due on payday: next cycle's
        val h = f.home.observeHome().first()
        assertEquals("unpaid bills due before payday 11-25", 50_000L, h.unpaidBills)
        assertEquals("11-09..11-11 closed by hand; 11-08 auto-closed ends it", 3, h.streak)
        assertEquals("jar pot only, not the emergency pot", 7_200L, h.jarTotal)
        assertEquals("two proverbs collected", 2, h.collected)
        assertEquals("counting pockets", 390_000L, h.spendable)
        assertEquals("payday 25: cycle 10-25..11-25", PayCycle(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25)), h.cycle)
        assertEquals("no ticket printed today", null, h.today)
    }

    @Test fun weekRowsSaturdayFirst() = runTest {
        setUp()
        val fri6 = LocalDate.of(2026, 11, 6)
        val sat7 = LocalDate.of(2026, 11, 7)
        val tue10 = LocalDate.of(2026, 11, 10)
        for (d in listOf(fri6, sat7, tue10)) db.dayTicketDao().insert(day(d))
        f.tickets.printToday() // auto-closes nothing open; prints 11-12
        val h = f.home.observeHome().first()
        assertEquals("week 11-07 (Sat)..11-13 (Fri); last Friday is the previous week", listOf(sat7, tue10, nov12), h.week.map { it.date })
        assertEquals(DayOfWeek.SATURDAY, h.week.first().date.dayOfWeek)
        assertEquals("today's row is the printed one", nov12, h.today?.date)
    }

    @Test fun repeatChipsNeedTwo() = runTest {
        setUp()
        val coffee = category("قهوة")
        val transport = category("مواصلات")
        val food = category("أكل")
        spend(2_500, coffee, hour = 8)
        spend(2_500, coffee, date = nov12.minusDays(1))
        spend(3_000, coffee) // same category, other amount: its own group of one
        spend(1_000, transport) // once only
        spend(4_000, food)
        spend(4_000, food, date = nov12.minusDays(30)) // 10-13: outside today and the 29 days before
        spend(1_500, categoryId = 999, hour = 11) // a deleted category
        spend(1_500, categoryId = 999, hour = 12)
        spend(1_200, categoryId = null, hour = 13) // no category
        spend(1_200, categoryId = null, hour = 14)
        // Would pair with the single coffee 3000 spend if income counted.
        f.entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = 3_000, pocketId = cash, categoryId = coffee, happenedAt = millis(nov12, 15)))
        val repeats = f.home.observeHome().first().repeats
        assertEquals(
            "only coffee 2500 repeats in the window with a category that still exists",
            listOf(RepeatChip(coffee, "قهوة", 2_500, cash)),
            repeats,
        )
    }

    @Test fun repeatChipsOrder() = runTest {
        setUp()
        val a = category("a")
        val b = category("b")
        val c = category("c")
        val d = category("d")
        for (h in listOf(8, 9, 10)) spend(1_000, a, date = nov12.minusDays(5), hour = h) // 3 times, oldest of the top three
        spend(2_000, b, date = nov12.minusDays(4))
        spend(2_000, b, hour = 11) // newest pair
        spend(3_000, c, date = nov12.minusDays(4))
        spend(3_000, c, date = nov12.minusDays(1))
        spend(4_000, d, date = nov12.minusDays(9))
        spend(4_000, d, date = nov12.minusDays(8)) // oldest pair: past the limit of 3
        val repeats = f.home.observeHome().first().repeats
        assertEquals(
            "most repeated first, then most recent; at most 3",
            listOf("a" to 1_000L, "b" to 2_000L, "c" to 3_000L),
            repeats.map { it.categoryName to it.amount },
        )
    }

    @Test fun nullCategoryDoesNotTakeASlot() = runTest {
        setUp()
        val a = category("a")
        val b = category("b")
        val c = category("c")
        for (h in 8..11) spend(1_200, categoryId = null, hour = h) // the most repeated group, but no category
        for (cat in listOf(a, b, c)) for (h in 12..13) spend(1_000 * cat, cat, hour = h)
        val repeats = f.home.observeHome().first().repeats
        assertEquals("three named chips, none lost to the nameless group", listOf("a", "b", "c"), repeats.map { it.categoryName }.sorted())
    }

    @Test fun archivedPocketChipHidden() = runTest {
        setUp()
        val coffee = category("قهوة")
        val wallet = db.pocketDao().insert(PocketEntity(name = "wallet", kind = PocketKind.MOBILE_WALLET, openingBalance = 10_000, color = 0))
        for (h in 8..9) f.entries.log(
            NewEntry(kind = EntryKind.SPENT, amount = 2_500, pocketId = wallet, categoryId = coffee, happenedAt = millis(nov12, h)),
        )
        assertEquals("the pair shows while the pocket is live", 1, f.home.observeHome().first().repeats.size)
        db.pocketDao().update(checkNotNull(db.pocketDao().byId(wallet)).copy(archived = true))
        assertEquals("no chip for an archived pocket", emptyList<RepeatChip>(), f.home.observeHome().first().repeats)
    }

    @Test fun seedIsIdempotent() = runTest {
        val seed = SeedData(db, db.pocketDao(), db.categoryDao(), clock) // empty database: no setUp()
        (1..3).map { async(Dispatchers.IO) { seed.ensureSeeded() } }.awaitAll()
        seed.ensureSeeded() // a pocket exists now: nothing more

        val pockets = db.pocketDao().all().first()
        assertEquals("one cash pocket after three concurrent calls and a fourth", 1, pockets.size)
        val p = pockets.single()
        assertEquals(listOf("الكاش", PocketKind.CASH, 0L, true), listOf(p.name, p.kind, p.openingBalance, p.countsInTicket))
        assertEquals("cash pocket is yellow", Tz.yellow.toArgb(), p.color)

        val cats = db.categoryDao().all().first().sortedBy { it.id }
        assertEquals(
            "categories in order, with essential flags",
            listOf(
                "قهوة" to false, "مواصلات" to true, "أكل" to false, "بقالة" to true, "فواتير" to true,
                "إيجار" to true, "صحة" to true, "تعليم" to true, "حاجة تانية" to false,
            ),
            cats.map { it.name to it.essential },
        )
        assertEquals(
            "icons by TzIcons name",
            listOf("coffee", "transport", "food", "groceries", "bell", "shop", "grid", "glasses", "grid"),
            cats.map { it.icon },
        )
        val iconNames = TzIcons.all.map { it.name }.toSet()
        assertTrue("every icon exists in TzIcons: ${cats.map { it.icon } - iconNames}", cats.all { it.icon in iconNames })
        val palette = listOf(Tz.sky, Tz.lilac, Tz.turquoise, Tz.yellow, Tz.carnelian).map { it.toArgb() }
        assertEquals("colours cycle sky, lilac, turquoise, yellow, carnelian (copies of Tz)", List(9) { palette[it % 5] }, cats.map { it.color })
    }

    @Test fun tomorrowPreviewOnDayBeforePayday() = runTest {
        setUp()
        val nov24 = LocalDate.of(2026, 11, 24)
        // Tomorrow is payday 25: its ticket is spread over the new cycle (11-25 to 12-24 = 30 days), not over today's.
        assertEquals(TomorrowBasis(30, 0), f.home.tomorrowBasis(nov24))
        assertEquals("an ordinary day stays in its cycle", TomorrowBasis(12, 0), f.home.tomorrowBasis(nov12))
        fun bill(amount: Long, due: LocalDate, paid: Boolean = false) = RecurringEntity(
            name = "bill", amount = amount, pocketId = cash, scheduleDay = due.dayOfMonth, nextDue = due, paidThisCycle = paid,
            kind = RecurringKind.BILL,
        )
        db.recurringDao().insert(bill(10_000, LocalDate.of(2026, 11, 20))) // still unpaid from the old cycle: stays reserved
        db.recurringDao().insert(bill(20_000, LocalDate.of(2026, 12, 5))) // due in the new cycle
        db.recurringDao().insert(bill(40_000, LocalDate.of(2026, 12, 28))) // after the new cycle's payday
        assertEquals(TomorrowBasis(30, 30_000), f.home.tomorrowBasis(nov24))
    }
}
