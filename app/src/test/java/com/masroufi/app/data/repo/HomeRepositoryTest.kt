package com.masroufi.app.data.repo

import androidx.compose.ui.graphics.toArgb
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.PayCycle
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.ProverbCollectedEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
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
                // Upstreams re-emit one by one; wait for the emission that carries the punched row.
                var next = seen.receive()
                while (next.today?.spent != 5_000L) next = seen.receive()
                assertEquals("one hole for the spend", 1, next.today?.entryCount)
                assertEquals("ticket amount unchanged", 30_000L, next.today?.amount)
                assertEquals("spendable follows the spend: 390000 - 5000", 385_000L, next.spendable)
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
        val h = f.home.observeHome().first()
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
        f.entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = 2_500, pocketId = cash, categoryId = coffee, happenedAt = millis(nov12, 15)))
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
            listOf("coffee", "transport", "food", "groceries", "roll", "shop", "add", "glasses", "grid"),
            cats.map { it.icon },
        )
        val iconNames = TzIcons.all.map { it.name }.toSet()
        assertTrue("every icon exists in TzIcons: ${cats.map { it.icon } - iconNames}", cats.all { it.icon in iconNames })
        val palette = listOf(Tz.sky, Tz.lilac, Tz.turquoise, Tz.yellow, Tz.carnelian).map { it.toArgb() }
        assertEquals("colours cycle sky, lilac, turquoise, yellow, carnelian", List(9) { palette[it % 5] }, cats.map { it.color })
    }
}
