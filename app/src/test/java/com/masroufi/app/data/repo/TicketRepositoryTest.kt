package com.masroufi.app.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.masroufi.app.core.Payday
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.overspendPerDay
import com.masroufi.app.domain.streak
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TestClock(var instant: Instant, private val zoneId: ZoneId = ZoneId.of("Africa/Cairo")) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = TestClock(instant, zone)
    override fun instant(): Instant = instant
}

/**
 * Payday Day(25). Unless a test says otherwise: one cash pocket with 3,900 EGP (390000 piastres), today 2026-11-12
 * (13 days left: 390000 / 13 = 30000), leftover default JAR, round-ups off.
 */
@RunWith(AndroidJUnit4::class)
class TicketRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val nov13 = nov12.plusDays(1)
    private val clock = TestClock(at(nov12, 9), zone)
    private val plus = DebugEntitlements()
    private lateinit var db: TazkaraDatabase
    private lateinit var settings: SettingsStore
    private lateinit var tickets: TicketRepository
    private lateinit var entries: EntryRepository
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()
    private fun millis(date: LocalDate, hour: Int) = at(date, hour).toEpochMilli()
    private fun today(date: LocalDate) { clock.instant = at(date, 9) }

    private suspend fun TestScope.setUp(opening: Long = 390_000) {
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") })
        tickets = TicketRepository(
            db, db.pocketDao(), db.entryDao(), db.dayTicketDao(), db.recurringDao(), db.jarDao(), db.proverbDao(),
            db.stampDao(), settings, clock,
        )
        entries = EntryRepository(db, db.entryDao(), db.pocketDao(), db.jarDao(), db.recurringDao(), tickets, settings, plus, clock)
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = opening, color = 0))
    }

    private suspend fun spend(amount: Long, date: LocalDate = nov12, hour: Int = 10) =
        entries.log(NewEntry(kind = EntryKind.SPENT, amount = amount, pocketId = cash, happenedAt = millis(date, hour)))

    private suspend fun row(date: LocalDate): DayTicketEntity =
        checkNotNull(db.dayTicketDao().forDate(date)) { "expected a ticket row for $date" }

    private suspend fun jarTotal() = db.jarDao().totalNow(Pot.JAR)
    private suspend fun leftoverMovements(date: LocalDate): List<JarMovementEntity> =
        db.jarDao().forDay(date).filter { it.source == JarSource.TICKET_LEFTOVER }

    private fun assertTicket(what: String, amount: Long, base: Long, carriedIn: Long, actual: DayTicketEntity) =
        assertEquals(what, Triple(amount, base, carriedIn), Triple(actual.amount, actual.base, actual.carriedIn))

    @Test fun printTodayIsIdempotent() = runTest {
        setUp()
        val first = tickets.printToday()
        assertTicket("11-12: 390000 / 13 days = 30000", 30_000, 30_000, 0, first)
        assertEquals(TicketStatus.OPEN, first.status)
        assertFalse(first.endOfMonth)
        spend(5_000)
        val second = tickets.printToday()
        assertEquals("same row on second print", first.id, second.id)
        assertTicket("ticket unchanged by spending", 30_000, 30_000, 0, second)
        assertEquals("spent recorded by log()", 5_000L, second.spent)
        assertEquals(1, second.entryCount)
        assertEquals("one row only", 1, db.dayTicketDao().allNow().size)
    }

    @Test fun overTicketSpreadsTomorrow() = runTest {
        setUp()
        tickets.printToday()
        spend(33_500) // 3500 over the 30000 ticket
        assertEquals(33_500L, row(nov12).spent)
        today(nov13)
        val tomorrow = tickets.printToday()
        // Spendable 390000 - 33500 = 356500, reserved 0 (no leftover), 12 days: floorToPound(356500 / 12 = 29708) = 29700.
        assertTicket("11-13 after overspend", 29_700, 29_700, 0, tomorrow)
        assertEquals("11-12 auto-closed with leftover 0", 0L, row(nov12).leftover)
        assertEquals("no jar money from an overspent day", 0L, jarTotal())
        // Without the overspend 11-13 would be 360000 / 12 = 30000: it falls by 300 = overspendPerDay(3500, 12).
        assertEquals(300L, overspendPerDay(3_500, 12))
        assertEquals(overspendPerDay(3_500, 12), 30_000L - tomorrow.base)
    }

    @Test fun zeroPoolPrintsEndOfMonth() = runTest {
        setUp()
        // Reserved = unpaid bill due 11-20 (300000) + jar (60000) + emergency (30000) = 390000 = Spendable.
        db.recurringDao().insert(bill(300_000, LocalDate.of(2026, 11, 20)))
        db.jarDao().insert(JarMovementEntity(pot = Pot.JAR, amount = 60_000, source = JarSource.MANUAL, at = 0))
        db.jarDao().insert(JarMovementEntity(pot = Pot.EMERGENCY, amount = 30_000, source = JarSource.MANUAL, at = 0))
        val t = tickets.printToday()
        assertTrue("end-of-month row", t.endOfMonth)
        assertTicket("end-of-month prints no amount", 0, 0, 0, t)
    }

    @Test fun reservedSkipsPaidAndPaydayBills() = runTest {
        setUp()
        db.recurringDao().insert(bill(10_000, LocalDate.of(2026, 11, 15), paid = true))
        db.recurringDao().insert(bill(50_000, LocalDate.of(2026, 11, 25))) // due on payday: next cycle's
        assertTicket("nothing reserved: 390000 / 13", 30_000, 30_000, 0, tickets.printToday())
    }

    @Test fun paydayTodayStartsNewCycle() = runTest {
        setUp()
        today(LocalDate.of(2026, 11, 25))
        // Cycle 11-25..12-24 = 30 days: 390000 / 30 = 13000.
        assertTicket("payday starts a 30-day cycle", 13_000, 13_000, 0, tickets.printToday())
    }

    @Test fun incomeMidDayKeepsTodaysTicket() = runTest {
        setUp()
        tickets.printToday()
        entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = 100_000, pocketId = cash, happenedAt = millis(nov12, 12)))
        val t = row(nov12)
        assertTicket("today's ticket unchanged by income", 30_000, 30_000, 0, t)
        assertEquals("income does not punch", 0L, t.spent)
        assertEquals(0, t.entryCount)
        today(nov13)
        // 11-12 auto-closes, leftover 30000 to the jar. Spendable 490000 - reserved 30000 = 460000 / 12 = 38333 -> 38300.
        assertTicket("tomorrow larger", 38_300, 38_300, 0, tickets.printToday())
    }

    @Test fun editOnClosedDayAdjustsJarMovement() = runTest {
        setUp()
        tickets.printToday()
        spend(22_800)
        val r = tickets.closeDay(nov12, LeftoverDestination.JAR)
        assertEquals("30000 - 22800", 7_200L, r.leftover)
        assertEquals(listOf(7_200L), leftoverMovements(nov12).map { it.amount })
        spend(2_000, hour = 20)
        assertEquals("movement follows the new leftover 30000 - 24800", listOf(5_200L), leftoverMovements(nov12).map { it.amount })
        val t = row(nov12)
        assertEquals(24_800L, t.spent)
        assertEquals(2, t.entryCount)
        assertEquals(5_200L, t.leftover)
        assertEquals(5_200L, jarTotal())
    }

    @Test fun leftoverNeverNegative() = runTest {
        setUp()
        tickets.printToday()
        spend(22_800)
        tickets.closeDay(nov12, LeftoverDestination.JAR)
        spend(10_000, hour = 20) // spent 32800 > 30000
        assertEquals("movement removed", emptyList<JarMovementEntity>(), leftoverMovements(nov12))
        assertEquals("jar not negative", 0L, jarTotal())
        assertEquals(0L, row(nov12).leftover)
        assertEquals(32_800L, row(nov12).spent)
    }

    @Test fun missedDayAutoClosesNoCard() = runTest {
        setUp()
        tickets.printToday()
        today(nov13)
        val t13 = tickets.printToday()
        val t12 = row(nov12)
        assertEquals(TicketStatus.CLOSED_AUTO, t12.status)
        assertNull("no proverb for an auto-close", t12.proverbNumber)
        assertEquals(LeftoverDestination.JAR, t12.destination)
        assertEquals(30_000L, t12.leftover)
        assertEquals(listOf(30_000L), leftoverMovements(nov12).map { it.amount })
        assertEquals(emptyList<Int>(), db.proverbDao().numbers())
        assertEquals(emptyList<StampKind>(), db.stampDao().kinds())
        val days = db.dayTicketDao().allNow().map { DayResult(it.date, it.status, it.amount, it.spent) }
        assertEquals("streak broken", 0, streak(days, nov13))
        // Spendable 390000 - jar 30000 = 360000 / 12 = 30000.
        assertTicket("11-13", 30_000, 30_000, 0, t13)
    }

    @Test fun gapDaysGetNoTicketAndNoJarMoney() = runTest {
        setUp()
        tickets.printToday()
        val nov16 = LocalDate.of(2026, 11, 16)
        today(nov16)
        val t16 = tickets.printToday()
        assertEquals(listOf(nov12, nov16), db.dayTicketDao().allNow().map { it.date })
        assertEquals("jar holds 11-12's leftover only", 30_000L, jarTotal())
        // (390000 - 30000) / 9 days = 40000.
        assertTicket("11-16", 40_000, 40_000, 0, t16)
    }

    @Test fun closedLeftoverToJarLowersTomorrow() = runTest {
        setUp()
        tickets.printToday()
        spend(22_800)
        assertEquals(7_200L, tickets.closeDay(nov12, LeftoverDestination.JAR).leftover)
        assertEquals(7_200L, jarTotal())
        today(nov13)
        // Spendable 367200 - jar 7200 = 360000 / 12 = 30000 (without reserving the jar: 367200 / 12 -> 30600).
        assertTicket("11-13", 30_000, 30_000, 0, tickets.printToday())
    }

    @Test fun carryToTomorrow() = runTest {
        setUp()
        tickets.printToday()
        spend(22_800)
        assertEquals(7_200L, tickets.closeDay(nov12, LeftoverDestination.TOMORROW).leftover)
        assertEquals("carry is not jar money", 0L, jarTotal())
        today(nov13)
        // Spendable 367200; carry 7200 leaves the pool: 360000 / 12 = 30000 base; amount 30000 + 7200.
        assertTicket("11-13 with carry", 37_200, 30_000, 7_200, tickets.printToday())
    }

    @Test fun autoCloseYesterdayWithTomorrowDefaultCarries() = runTest {
        setUp()
        settings.setLeftoverDefault(LeftoverDestination.TOMORROW)
        tickets.printToday()
        today(nov13)
        val t13 = tickets.printToday()
        assertEquals(LeftoverDestination.TOMORROW, row(nov12).destination)
        assertEquals(0L, jarTotal())
        // Spendable 390000; carry 30000 leaves the pool: 360000 / 12 = 30000 base; amount 60000.
        assertTicket("11-13 carries yesterday's 30000", 60_000, 30_000, 30_000, t13)
    }

    @Test fun tomorrowDestinationNotYesterdayGoesToJar() = runTest {
        setUp()
        settings.setLeftoverDefault(LeftoverDestination.TOMORROW)
        tickets.printToday()
        val nov14 = LocalDate.of(2026, 11, 14)
        today(nov14)
        val t14 = tickets.printToday()
        assertEquals(TicketStatus.CLOSED_AUTO, row(nov12).status)
        assertEquals("older day's leftover goes to the jar", LeftoverDestination.JAR, row(nov12).destination)
        assertEquals(30_000L, jarTotal())
        // No carry; (390000 - 30000) / 11 days = 32727 -> 32700.
        assertTicket("11-14", 32_700, 32_700, 0, t14)
    }

    @Test fun roundUpOnlyWithPlus() = runTest {
        setUp()
        settings.setRoundUpStep(5)
        tickets.printToday()
        val free = spend(2_300)
        assertEquals("no round-up without Plus", emptyList<JarMovementEntity>(), db.jarDao().forEntry(free))
        assertEquals(0L, db.entryDao().byId(free)?.roundUp)
        assertEquals(2_300L, row(nov12).spent)
        entries.delete(free)

        plus.setDebugOverride(true)
        val id = spend(2_300)
        val moves = db.jarDao().forEntry(id)
        assertEquals("2300 rounds up to 2500", listOf(Triple(Pot.JAR, JarSource.ROUND_UP, 200L)), moves.map { Triple(it.pot, it.source, it.amount) })
        assertEquals(200L, db.entryDao().byId(id)?.roundUp)
        assertEquals("round-up counts as spent", 2_500L, row(nov12).spent)
        assertEquals(200L, jarTotal())
    }

    @Test fun closeDayTwiceIsIdempotent() = runTest {
        setUp()
        tickets.printToday()
        spend(22_800)
        val first = tickets.closeDay(nov12, LeftoverDestination.JAR)
        assertEquals(7_200L, first.leftover)
        assertNotNull("hand close earns a proverb", first.proverbNumber)
        assertEquals(setOf(StampKind.FIRST_TICKET), first.newStamps)
        val second = tickets.closeDay(nov12, LeftoverDestination.TOMORROW)
        assertEquals(CloseResult(7_200, null, emptySet()), second)
        assertEquals("no double jar money", 7_200L, jarTotal())
        assertEquals(1, leftoverMovements(nov12).size)
        val t = row(nov12)
        assertEquals(TicketStatus.CLOSED_BY_HAND, t.status)
        assertEquals(LeftoverDestination.JAR, t.destination)
        assertEquals(first.proverbNumber, t.proverbNumber)
        assertEquals(listOf(first.proverbNumber), db.proverbDao().numbers())
    }

    @Test fun deleteRemovesRoundUpMovement() = runTest {
        setUp()
        settings.setRoundUpStep(5)
        plus.setDebugOverride(true)
        tickets.printToday()
        val id = spend(2_300)
        assertEquals(200L, jarTotal())
        entries.delete(id)
        assertNull(db.entryDao().byId(id))
        assertEquals(emptyList<JarMovementEntity>(), db.jarDao().forEntry(id))
        assertEquals(0L, jarTotal())
        assertEquals(0L, row(nov12).spent)
        assertEquals(0, row(nov12).entryCount)
    }

    @Test fun editMovesEntryToOtherDay() = runTest {
        setUp()
        tickets.printToday()
        val id = spend(5_000)
        today(nov13)
        // 11-12 auto-closes: leftover 25000 to the jar. (385000 - 25000) / 12 = 30000.
        assertTicket("11-13", 30_000, 30_000, 0, tickets.printToday())
        assertEquals(25_000L, jarTotal())

        entries.edit(id, NewEntry(kind = EntryKind.SPENT, amount = 5_000, pocketId = cash, happenedAt = millis(nov13, 10)))
        val t12 = row(nov12)
        assertEquals("old day emptied", 0L to 0, t12.spent to t12.entryCount)
        assertEquals("old day's leftover back to 30000", 30_000L, t12.leftover)
        assertEquals(listOf(30_000L), leftoverMovements(nov12).map { it.amount })
        val t13 = row(nov13)
        assertEquals("new day punched", 5_000L to 1, t13.spent to t13.entryCount)
        assertEquals("printed amount unchanged", 30_000L, t13.amount)
    }

    @Test fun endOfMonthRowHasZeroAmount() = runTest {
        setUp(opening = 1_200) // 1200 / 13 = 92 -> floors to 0: end of month
        val t = tickets.printToday()
        assertTrue(t.endOfMonth)
        assertTicket("end-of-month row", 0, 0, 0, t)
        spend(500)
        assertEquals("nothing left of a zero ticket", 0L, tickets.closeDay(nov12, LeftoverDestination.JAR).leftover)
        assertEquals(0L, jarTotal())
    }

    @Test fun noteOver120Rejected() = runTest {
        setUp()
        try {
            NewEntry(kind = EntryKind.SPENT, amount = 100, pocketId = cash, note = "x".repeat(121), happenedAt = millis(nov12, 10))
            fail("a 121-char note must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        val id = entries.log(
            NewEntry(kind = EntryKind.SPENT, amount = 100, pocketId = cash, note = "x".repeat(120), happenedAt = millis(nov12, 10)),
        )
        assertEquals(120, db.entryDao().byId(id)?.note?.length)
    }

    @Test fun foreignCurrencySpendDoesNotPunch() = runTest {
        setUp()
        settings.setRoundUpStep(5)
        plus.setDebugOverride(true)
        val usd = db.pocketDao().insert(
            PocketEntity(name = "usd", kind = PocketKind.BANK, currency = "USD", openingBalance = 50_000, color = 0),
        )
        val old = db.pocketDao().insert(
            PocketEntity(name = "old", kind = PocketKind.CASH, openingBalance = 50_000, color = 0, archived = true),
        )
        val outside = db.pocketDao().insert(
            PocketEntity(name = "outside", kind = PocketKind.CASH, openingBalance = 50_000, color = 0, countsInTicket = false),
        )
        tickets.printToday()
        val dollars = entries.log(NewEntry(kind = EntryKind.SPENT, amount = 5_000, pocketId = usd, happenedAt = millis(nov12, 10)))
        // log() refuses an archived pocket, so a spend from before the pocket was archived is inserted directly.
        val archived = db.entryDao().insert(
            EntryEntity(kind = EntryKind.SPENT, amount = 2_300, pocketId = old, happenedAt = millis(nov12, 11), loggedAt = 0),
        )
        val uncounted = entries.log(NewEntry(kind = EntryKind.SPENT, amount = 1_700, pocketId = outside, happenedAt = millis(nov12, 12)))
        tickets.recalcDay(nov12)
        val t = row(nov12)
        assertEquals("USD, archived and not-counted spends do not punch", 0L to 0, t.spent to t.entryCount)
        assertEquals("no round-up for USD", 0L, db.entryDao().byId(dollars)?.roundUp)
        assertEquals("no round-up for archived", 0L, db.entryDao().byId(archived)?.roundUp)
        assertEquals("no round-up for a pocket outside the ticket", 0L, db.entryDao().byId(uncounted)?.roundUp)
        assertEquals(0L, jarTotal())
    }

    @Test fun entryLoggedBeforePrintCounts() = runTest {
        setUp()
        spend(5_000, hour = 0) // 00:00, before the first print of the day
        val t = tickets.printToday()
        // Morning Spendable = 385000 + today's 5000 = 390000: the same 30000 as without the spend.
        assertTicket("ticket ignores the pre-print spend", 30_000, 30_000, 0, t)
        assertEquals("pre-print spend punches", 5_000L to 1, t.spent to t.entryCount)
        assertEquals("stored row", 5_000L to 1, row(nov12).let { it.spent to it.entryCount })
    }

    @Test fun futureDatedEntryRejected() = runTest {
        setUp()
        tickets.printToday()
        try {
            spend(1_000, date = nov13)
            fail("an entry dated tomorrow must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        val id = spend(1_000)
        try {
            entries.edit(id, NewEntry(kind = EntryKind.SPENT, amount = 1_000, pocketId = cash, happenedAt = millis(nov13, 10)))
            fail("an edit dated tomorrow must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        assertEquals("only the valid entry stored", listOf(id), db.entryDao().forDay(0, Long.MAX_VALUE).map { it.id })
        assertEquals(1_000L, row(nov12).spent)
    }

    @Test fun roundUpKeptOnEditWhenOff() = runTest {
        setUp()
        settings.setRoundUpStep(5)
        plus.setDebugOverride(true)
        tickets.printToday()
        val id = spend(2_300)
        assertEquals(200L, jarTotal())
        plus.setDebugOverride(false)
        entries.edit(id, NewEntry(kind = EntryKind.SPENT, amount = 2_400, pocketId = cash, happenedAt = millis(nov12, 10)))
        assertEquals("old round-up kept while off", 200L, db.entryDao().byId(id)?.roundUp)
        assertEquals(listOf(200L), db.jarDao().forEntry(id).map { it.amount })
        assertEquals("2400 + kept 200", 2_600L, row(nov12).spent)
        entries.edit(
            id,
            NewEntry(kind = EntryKind.SPENT, amount = 2_400, pocketId = cash, happenedAt = millis(nov12, 10), recurringId = 1),
        )
        assertEquals("a fixed bill loses its round-up", 0L, db.entryDao().byId(id)?.roundUp)
        assertEquals(emptyList<JarMovementEntity>(), db.jarDao().forEntry(id))
        assertEquals("fixed bill does not punch", 0L, row(nov12).spent)
        assertEquals(0L, jarTotal())
    }

    @Test fun emergencyDestination() = runTest {
        setUp()
        tickets.printToday()
        spend(22_800)
        assertEquals(7_200L, tickets.closeDay(nov12, LeftoverDestination.EMERGENCY).leftover)
        assertEquals(listOf(Pot.EMERGENCY to 7_200L), leftoverMovements(nov12).map { it.pot to it.amount })
        assertEquals(0L, jarTotal())
        assertEquals(7_200L, db.jarDao().totalNow(Pot.EMERGENCY))
        today(nov13)
        // Spendable 367200 - emergency 7200 = 360000 / 12 = 30000.
        assertTicket("emergency money is reserved", 30_000, 30_000, 0, tickets.printToday())
    }

    @Test fun notFixedAnchorsOnLatestIncome() = runTest {
        setUp()
        settings.setPayday(Payday.NotFixed)
        val nov2 = LocalDate.of(2026, 11, 2)
        entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = 100_000, pocketId = cash, happenedAt = millis(nov2, 10)))
        // Cycle 11-02..12-01: 20 days left on 11-12. Spendable 490000 / 20 = 24500 (a 30-day window would give 16300).
        assertTicket("rolling 30 days from the income", 24_500, 24_500, 0, tickets.printToday())
    }

    @Test fun roundUpSkippedForRecurringAndReceived() = runTest {
        setUp()
        settings.setRoundUpStep(5)
        plus.setDebugOverride(true)
        tickets.printToday()
        val bill = entries.log(
            NewEntry(kind = EntryKind.SPENT, amount = 2_300, pocketId = cash, happenedAt = millis(nov12, 10), recurringId = 1),
        )
        val income = entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = 2_300, pocketId = cash, happenedAt = millis(nov12, 11)))
        assertEquals("no round-ups", 0L to 0L, db.entryDao().byId(bill)?.roundUp to db.entryDao().byId(income)?.roundUp)
        assertEquals(0L, jarTotal())
        assertEquals("neither punches", 0L to 0, row(nov12).let { it.spent to it.entryCount })
    }

    @Test fun closeDayMissingDateThrows() = runTest {
        setUp()
        try {
            tickets.closeDay(nov12, LeftoverDestination.JAR)
            fail("closing a day with no ticket must throw")
        } catch (expected: IllegalStateException) {
        }
    }

    @Test fun movedWithoutTargetRejected() = runTest {
        setUp()
        val at = millis(nov12, 10)
        val bad = listOf<Pair<String, () -> NewEntry>>(
            "move without target" to { NewEntry(kind = EntryKind.MOVED, amount = 100, pocketId = cash, happenedAt = at) },
            "move to itself" to { NewEntry(kind = EntryKind.MOVED, amount = 100, pocketId = cash, toPocketId = cash, happenedAt = at) },
            "spend with target" to { NewEntry(kind = EntryKind.SPENT, amount = 100, pocketId = cash, toPocketId = 99, happenedAt = at) },
        )
        for ((what, make) in bad) {
            try {
                make()
                fail("$what must be rejected")
            } catch (expected: IllegalArgumentException) {
            }
        }
        NewEntry(kind = EntryKind.MOVED, amount = 100, pocketId = cash, toPocketId = cash + 1, happenedAt = at) // valid
    }

    @Test fun everyOpenDayBeforeTodayAutoCloses() = runTest {
        setUp()
        settings.setLeftoverDefault(LeftoverDestination.TOMORROW)
        val nov10 = LocalDate.of(2026, 11, 10)
        val nov11 = LocalDate.of(2026, 11, 11)
        db.dayTicketDao().insert(DayTicketEntity(date = nov10, amount = 10_000, base = 10_000))
        db.dayTicketDao().insert(DayTicketEntity(date = nov11, amount = 20_000, base = 20_000))
        val t12 = tickets.printToday()
        assertEquals(TicketStatus.CLOSED_AUTO to LeftoverDestination.JAR, row(nov10).let { it.status to it.destination })
        assertEquals(TicketStatus.CLOSED_AUTO to LeftoverDestination.TOMORROW, row(nov11).let { it.status to it.destination })
        assertEquals("only 11-10's 10000 in the jar", 10_000L, jarTotal())
        // Spendable 390000 - jar 10000 = 380000; carry 20000 leaves 360000 / 13 = 27692 -> 27600; amount 47600.
        assertTicket("11-12 carries 11-11", 47_600, 27_600, 20_000, t12)
    }

    @Test fun secondSpendAfterPrintNotDoublePunched() = runTest {
        setUp()
        spend(5_000, hour = 0) // before the print
        tickets.printToday()
        spend(2_000)
        val t = row(nov12)
        assertEquals("spent is the sum, each entry punched once", 7_000L to 2, t.spent to t.entryCount)
    }

    @Test fun usdIncomeDoesNotAnchorNotFixed() = runTest {
        setUp()
        settings.setPayday(Payday.NotFixed)
        val usd = db.pocketDao().insert(PocketEntity(name = "usd", kind = PocketKind.BANK, currency = "USD", color = 0))
        entries.log(NewEntry(kind = EntryKind.RECEIVED, amount = 100_000, pocketId = usd, happenedAt = millis(LocalDate.of(2026, 11, 2), 10)))
        // No EGP income: the anchor is today, a full 30 days. 390000 / 30 = 13000 (the USD income would leave 20 days).
        assertTicket("USD income is not an anchor", 13_000, 13_000, 0, tickets.printToday())
    }

    @Test fun payingBillStopsReservingIt() = runTest {
        setUp()
        val nov20 = LocalDate.of(2026, 11, 20)
        val nov25 = LocalDate.of(2026, 11, 25)
        val billId = db.recurringDao().insert(bill(50_000, nov20))
        assertEquals("reserved before paying", 50_000L, db.recurringDao().unpaidDueBefore(nov25))
        fun pay(hour: Int, id: Long? = billId) =
            NewEntry(kind = EntryKind.SPENT, amount = 50_000, pocketId = cash, happenedAt = millis(nov12, hour), recurringId = id)
        val paying = entries.log(pay(10))
        assertEquals("not reserved once paid", 0L, db.recurringDao().unpaidDueBefore(nov25))
        entries.delete(paying)
        assertEquals("reserved again after delete", 50_000L, db.recurringDao().unpaidDueBefore(nov25))
        val first = entries.log(pay(10))
        val second = entries.log(pay(11))
        entries.delete(first)
        assertEquals("another entry in the cycle still pays it", 0L, db.recurringDao().unpaidDueBefore(nov25))
        entries.edit(second, pay(11, id = null))
        assertEquals("edited to no bill: reserved again", 50_000L, db.recurringDao().unpaidDueBefore(nov25))
    }

    @Test fun entryInputChecksRejected() = runTest {
        setUp()
        val usd = db.pocketDao().insert(PocketEntity(name = "usd", kind = PocketKind.BANK, currency = "USD", color = 0))
        val old = db.pocketDao().insert(PocketEntity(name = "old", kind = PocketKind.CASH, color = 0, archived = true))
        val other = db.pocketDao().insert(PocketEntity(name = "other", kind = PocketKind.BANK, color = 0))
        val at = millis(nov12, 10)
        val bad = listOf(
            "missing pocket" to NewEntry(kind = EntryKind.SPENT, amount = 100, pocketId = 999, happenedAt = at),
            "archived pocket" to NewEntry(kind = EntryKind.SPENT, amount = 100, pocketId = old, happenedAt = at),
            "missing target" to NewEntry(kind = EntryKind.MOVED, amount = 100, pocketId = cash, toPocketId = 999, happenedAt = at),
            "archived target" to NewEntry(kind = EntryKind.MOVED, amount = 100, pocketId = cash, toPocketId = old, happenedAt = at),
            "move across currencies" to NewEntry(kind = EntryKind.MOVED, amount = 100, pocketId = cash, toPocketId = usd, happenedAt = at),
            "fee on a spend" to NewEntry(kind = EntryKind.SPENT, amount = 100, pocketId = cash, happenedAt = at, fee = 5),
        )
        val good = entries.log(NewEntry(kind = EntryKind.SPENT, amount = 100, pocketId = cash, happenedAt = at))
        for ((what, e) in bad) {
            try {
                entries.log(e)
                fail("log: $what must be rejected")
            } catch (expected: IllegalArgumentException) {
            }
            try {
                entries.edit(good, e)
                fail("edit: $what must be rejected")
            } catch (expected: IllegalArgumentException) {
            }
        }
        entries.log(NewEntry(kind = EntryKind.MOVED, amount = 100, pocketId = cash, toPocketId = other, happenedAt = at, fee = 5))
    }

    private fun bill(amount: Long, due: LocalDate, paid: Boolean = false) = RecurringEntity(
        name = "bill", amount = amount, pocketId = cash, scheduleDay = due.dayOfMonth, nextDue = due, paidThisCycle = paid,
        kind = RecurringKind.BILL,
    )
}
