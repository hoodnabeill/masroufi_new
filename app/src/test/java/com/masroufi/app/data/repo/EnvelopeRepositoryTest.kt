package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.R
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.SplitRule
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

/**
 * Payday Day(25). The app was first opened 2026-10-20; today 2026-11-12, cycle 10-25..11-25 (31 days, day 19 today,
 * 13 days to payday). Last cycle 09-25..10-25.
 */
@RunWith(AndroidJUnit4::class)
class EnvelopeRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(d(10, 20), 9), zone)
    private val plus = DebugEntitlements()
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
    private fun today(date: LocalDate) { clock.instant = at(date, 9) }

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, plus)
        f.settings.current() // first open: 10-20
        today(d(11, 12))
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0))
    }

    private suspend fun category(name: String) = db.categoryDao().insert(CategoryEntity(name = name, icon = "grid", color = 0))

    private suspend fun spend(amount: Long, category: Long, date: LocalDate, pocket: Long = cash, isFee: Boolean = false) =
        db.entryDao().insert(
            EntryEntity(
                kind = EntryKind.SPENT, amount = amount, pocketId = pocket, categoryId = category, happenedAt = at(date, 10).toEpochMilli(),
                loggedAt = 0, isFee = isFee,
            ),
        )

    private suspend fun envelope(name: String, monthly: Long, vararg categories: Long, kind: EnvelopeKind = EnvelopeKind.SPENDING) =
        f.envelopes.create(name, 0, monthly, kind, categories.toSet())

    private suspend fun views() = f.envelopes.observe().first().associateBy { it.name }

    @Test fun envelopeLines() = runTest {
        setUp()
        val food = category("food")
        val transport = category("transport")
        val fun_ = category("fun")
        val bills = category("bills")
        val tile = db.pocketDao().insert(PocketEntity(name = "tile", kind = PocketKind.UNDER_TILE, color = 0, countsInTicket = false))
        val usd = db.pocketDao().insert(PocketEntity(name = "usd", kind = PocketKind.BANK, currency = "USD", color = 0))
        envelope("food", 100_000, food)
        envelope("transport", 100_000, transport)
        envelope("fun", 10_000, fun_)
        val billsId = envelope("bills", 50_000, bills, kind = EnvelopeKind.FIXED)
        db.recurringDao().insert(
            RecurringEntity(
                name = "net", amount = 50_000, pocketId = cash, scheduleDay = 20, nextDue = d(11, 20), envelopeId = billsId,
                paidThisCycle = true, kind = RecurringKind.BILL,
            ),
        )
        spend(90_000, food, d(11, 1))
        spend(5_000, food, d(11, 2), pocket = tile) // an EGP pocket outside the ticket still spends from the envelope
        spend(99_999, food, d(11, 2), pocket = usd) // not EGP
        spend(1_000, food, d(11, 2), isFee = true) // a fee
        spend(70_000, food, d(10, 24)) // last cycle
        spend(10_000, transport, d(11, 3))
        spend(40_000, transport, d(10, 1)) // last cycle, by its day 19 (10-13)
        spend(1_000, transport, d(10, 20)) // last cycle, after its day 19: not compared
        spend(20_000, fun_, d(11, 4))

        val v = views()
        val foodView = checkNotNull(v["food"])
        assertEquals("food: SPENT in EGP pockets this cycle", Triple(95_000L, 5_000L, 0.95f), Triple(foodView.spent, foodView.left, foodView.bar))
        // ceil(5000 * 19 / 95000) = 1 day
        assertEquals(EnvelopeLine.RunsOut(d(11, 13)), foodView.line)
        assertFalse("nothing by day 19 last cycle", foodView.betterThanLast)

        val transportView = checkNotNull(v["transport"])
        // Runs out after payday; 10000 * 31 <= 100000 * 19.
        assertEquals(EnvelopeLine.OnPace, transportView.line)
        assertTrue("10000 so far vs 40000 by the same day", transportView.betterThanLast)

        val funView = checkNotNull(v["fun"])
        assertEquals("overspent: no line, bar full", null to 1f, funView.line to funView.bar)
        assertEquals(-10_000L, funView.left)

        val billsView = checkNotNull(v["bills"])
        assertTrue("fixed envelope with its bill paid", billsView.sealed)
        assertEquals(R.string.paper_sealed to null, billsView.paidLabelRes to billsView.line)
        assertFalse(checkNotNull(v["food"]).sealed)
        assertNull(foodView.paidLabelRes)
    }

    @Test fun runsOutNeedsFiveDaysOfData() = runTest {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, plus)
        today(d(11, 10))
        f.settings.current() // first open 11-10: three days of data on 11-12
        today(d(11, 12))
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0))
        val food = category("food")
        envelope("food", 100_000, food)
        spend(95_000, food, d(11, 11))
        assertNull(checkNotNull(views()["food"]).line)
    }

    @Test fun fifthEnvelopeNeedsPlus() = runTest {
        setUp()
        val ids = (1..4).map { envelope("e$it", 0) }
        assertTrue("a 5th on Free", runCatching { envelope("e5", 0) }.exceptionOrNull() is PlusRequired)
        f.envelopes.archive(ids[0])
        envelope("e5", 0) // an archived one frees a place
        assertTrue("still 4 active", runCatching { envelope("e6", 0) }.exceptionOrNull() is PlusRequired)
        plus.setDebugOverride(true)
        envelope("e6", 0)
        assertEquals(5, views().size)
    }

    @Test fun moveAdjustsCurrentCycleOnly() = runTest {
        setUp()
        val a = envelope("a", 100_000)
        val b = envelope("b", 50_000)
        f.envelopes.move(a, b, 30_000)
        assertEquals(70_000L to 80_000L, views().let { checkNotNull(it["a"]).monthly to checkNotNull(it["b"]).monthly })
        val tooMuch = runCatching { f.envelopes.move(a, b, 70_001) }.exceptionOrNull()
        assertTrue("more than a has left", tooMuch is IllegalArgumentException)
        today(d(11, 26)) // next cycle
        assertEquals("saved amounts again", 100_000L to 50_000L, views().let { checkNotNull(it["a"]).monthly to checkNotNull(it["b"]).monthly })
    }

    @Test fun splitAppliedSummary() = runTest {
        setUp()
        val a = envelope("a", 0)
        val b = envelope("b", 0)
        assertNull("never split", f.envelopes.lastSplit())
        f.envelopes.saveSplit(listOf(SplitRule.Fixed(a, 100_000), SplitRule.Percent(b, 50)))
        val result = f.envelopes.applySplit(500_000)
        assertEquals(mapOf(a to 100_000L, b to 200_000L), result.shares)
        assertEquals("unassigned stored", 500_000L to 200_000L, f.envelopes.lastSplit())
        val bad = runCatching { f.envelopes.saveSplit(listOf(SplitRule.Percent(a, 60), SplitRule.Percent(b, 50))) }.exceptionOrNull()
        assertTrue("percent total over 100", bad is IllegalArgumentException)
        assertEquals("a bad split stores nothing", listOf(SplitRule.Fixed(a, 100_000), SplitRule.Percent(b, 50)), f.envelopes.savedSplit())
    }

    @Test fun suggestionsFromSeededCategories() = runTest {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, plus)
        f.seed.ensureSeeded()
        val cats = db.categoryDao().all().first().associateBy({ it.name }, { it.id })
        val s = f.envelopes.suggestDefaults(householdSize = 3)
        assertEquals(
            listOf(
                EnvelopeSuggestion("الأكل والبقالة", EnvelopeKind.SPENDING, setOf(cats.getValue("أكل"), cats.getValue("بقالة"))),
                EnvelopeSuggestion("المواصلات", EnvelopeKind.SPENDING, setOf(cats.getValue("مواصلات"))),
                EnvelopeSuggestion("الفواتير والنت", EnvelopeKind.FIXED, setOf(cats.getValue("فواتير"))),
                EnvelopeSuggestion("الخروجات", EnvelopeKind.SPENDING, setOf(cats.getValue("قهوة"))),
            ),
            s,
        )
        assertTrue("no invented amounts", s.all { it.monthly == 0L })
    }
}
