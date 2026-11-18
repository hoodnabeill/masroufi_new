package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.TicketStatus
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today is Tuesday 2026-11-17; with no trial the usage window is Nov 4..17. */
@RunWith(AndroidJUnit4::class)
class LossRowsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun TestScope.setUp(plus: Boolean = false) {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements(plus))
    }

    private suspend fun handClosed(date: LocalDate) {
        db.dayTicketDao().insert(
            DayTicketEntity(date = date, amount = 10_000, base = 10_000, spent = 5_000, status = TicketStatus.CLOSED_BY_HAND),
        )
    }

    /** The paywall mock's numbers: 0.37 g, 86 pounds of round-ups, 5 seasons, 38 names, 13 streak days kept. */
    private suspend fun mockData() {
        f.jar.setGoldPrice(490_000, today.toEpochDay())
        f.jar.deposit(175_400)
        db.jarDao().insert(
            JarMovementEntity(pot = Pot.JAR, amount = 8_600, source = JarSource.ROUND_UP, at = clock.millis(), updatedAt = clock.millis()),
        )
        val seasonIds = (1..5).map { f.seasons.createPersonal("موسم $it", today.plusDays(10L * it), 100_000) }
        f.jar.earmark(20_000, seasonId = seasonIds.first())

        val mine = f.duty.addOccasion("فرحي", today.minusDays(3), OccasionOwner.MINE)
        (1..38).forEach { f.duty.addGift(mine, f.people.addPerson("اسم $it"), 10_000, GiftDirection.RECEIVED) }
        f.duty.addOccasion("فرح ابن خالتي", today.plusDays(5), OccasionOwner.THEIRS)

        // 13 days closed by hand (Oct 28..Nov 9), Nov 10 missed, Nov 11..16 closed.
        (0L..12L).forEach { handClosed(LocalDate.of(2026, 10, 28).plusDays(it)) }
        (11L..16L).forEach { handClosed(LocalDate.of(2026, 11, it.toInt())) }
    }

    @Test fun rowsCarryTheMocksNumbersInUsageOrder() = runTest {
        setUp()
        mockData()
        val rows = f.lossRows.build().rows
        assertEquals(
            listOf(
                PlusFeature.DUTY, PlusFeature.SEASONS, PlusFeature.GOLD, PlusFeature.ROUND_UPS, PlusFeature.SAFETY_TICKET,
            ),
            rows.map { it.feature },
        )
        val gold = rows.filterIsInstance<LossRow.Gold>().single()
        assertEquals("0.37 g in hundredths", 37L, gold.value)
        assertEquals(490_000L, gold.pricePerGram)
        assertEquals(8_600L, rows.filterIsInstance<LossRow.RoundUps>().single().value)
        val seasons = rows.filterIsInstance<LossRow.Seasons>().single()
        assertEquals(5L, seasons.value)
        assertEquals("موسم 1", seasons.nearestName)
        assertEquals(20_000L, seasons.nearestSaved)
        assertEquals(10L, seasons.nearestDaysUntil)
        val duty = rows.filterIsInstance<LossRow.Duty>().single()
        assertEquals(38L, duty.value)
        assertEquals("فرح ابن خالتي", duty.nextName)
        assertEquals(5L, duty.nextDaysUntil)
        assertEquals(13L, rows.filterIsInstance<LossRow.SafetyTicket>().single().value)
    }

    @Test fun usageCountsEventsInTheWindow() = runTest {
        setUp()
        mockData()
        val usage = f.lossRows.build().rows.associate { it.feature to it.usage }
        assertEquals("deposits in the window + the typed gold price", 3, usage[PlusFeature.GOLD])
        assertEquals("round-up rows", 1, usage[PlusFeature.ROUND_UPS])
        assertEquals("5 seasons edited + 1 earmark", 6, usage[PlusFeature.SEASONS])
        assertEquals("the occasion + its 38 gifts", 39, usage[PlusFeature.DUTY])
        assertEquals("missed days bridged", 1, usage[PlusFeature.SAFETY_TICKET])
    }

    @Test fun onlyRowsAboveZeroAndThePlainVersionWhenNoneIsLeft() = runTest {
        setUp()
        val empty = f.lossRows.build()
        assertTrue(empty.plain)
        assertEquals(PLAIN_FEATURES, empty.plainFeatures)
        assertEquals(3, empty.plainFeatures.size)
        assertNull(empty.first)

        // a jar without a gold price, five untouched seeded seasons and a zero round-up month are not rows
        f.jar.deposit(50_000)
        f.seasons.observe().first() // seeds the five empty defaults
        assertTrue(f.lossRows.build().plain)

        f.jar.setGoldPrice(490_000, today.toEpochDay())
        assertEquals(listOf(PlusFeature.GOLD), f.lossRows.build().rows.map { it.feature })
    }

    @Test fun basketAndEnvelopesBeyondFourAppearWhenPresent() = runTest {
        setUp(plus = true)
        (1..6).forEach { f.envelopes.create("ظرف $it", 0, 1_000, EnvelopeKind.SPENDING, emptySet()) }
        f.basket.track("طماطم")
        val rows = f.lossRows.build().rows
        assertEquals(2L, rows.filterIsInstance<LossRow.Envelopes>().single().value)
        assertEquals(1L, rows.filterIsInstance<LossRow.Basket>().single().value)
        assertEquals("two envelopes created in the window", 2, rows.filterIsInstance<LossRow.Envelopes>().single().usage)
    }

    @Test fun openedFromAFeatureItsOwnRowComesFirstDescribedByTheUsersData() = runTest {
        setUp()
        mockData()
        val gold = f.lossRows.build(PlusFeature.GOLD)
        assertEquals("the feature's row first although it was not the most used", PlusFeature.GOLD, gold.rows.first().feature)
        val pitch = checkNotNull(gold.first)
        assertEquals(listOf(184_000L, 37L, 490_000L), listOf(pitch.amount, pitch.gramsHundredths, pitch.pricePerGram))

        val zakat = checkNotNull(f.lossRows.build(PlusFeature.ZAKAT).first)
        assertEquals(184_000L, zakat.amount)
        val seasons = checkNotNull(f.lossRows.build(PlusFeature.SEASONS).first)
        assertEquals(5, seasons.count)
        val kids = checkNotNull(f.lossRows.build(PlusFeature.KIDS).first)
        assertNull("nothing to say: the UI uses the plain line", kids.count)
        assertEquals(PlusFeature.KIDS, kids.feature)
    }

    @Test fun anEmptyUserOpenedFromAFeatureGetsThePlainVersionAndThePitch() = runTest {
        setUp()
        val r = f.lossRows.build(PlusFeature.MATCH_HISTORY)
        assertTrue(r.plain)
        assertEquals(PlusFeature.MATCH_HISTORY, r.first?.feature)
        assertNull(r.first?.count)
    }

    // --- the pure ordering ----------------------------------------------------------------------------------

    private fun every() = listOf(
        LossRow.Gold(37, 490_000, usage = 2),
        LossRow.RoundUps(8_600, usage = 2),
        LossRow.Seasons(5, "رمضان", 1_000, 30, usage = 9),
        LossRow.Duty(38, null, null, usage = 4),
        LossRow.SafetyTicket(13, usage = 0),
        LossRow.Basket(3, usage = 7),
        LossRow.Envelopes(2, usage = 1),
    )

    @Test fun orderingIsByUsageThenTheExampleOrderAndCappedAtFive() {
        val rows = orderLossRows(every())
        assertEquals(5, rows.size)
        assertEquals(
            listOf(PlusFeature.SEASONS, PlusFeature.BASKET, PlusFeature.DUTY, PlusFeature.GOLD, PlusFeature.ROUND_UPS),
            rows.map { it.feature },
        )
    }

    @Test fun rowsAtZeroAreDroppedAndFirstGoesOnTop() {
        val withZero = every().map { if (it is LossRow.Seasons) LossRow.Seasons(0, "", 0, 0, 9) else it }
        val rows = orderLossRows(withZero, first = PlusFeature.ENVELOPES)
        assertFalse(rows.any { it.feature == PlusFeature.SEASONS })
        assertEquals(PlusFeature.ENVELOPES, rows.first().feature)
        assertEquals(5, rows.size)
        assertTrue(orderLossRows(emptyList()).isEmpty())
    }

    @Test fun aFirstFeatureWithNoRowChangesNothing() {
        assertEquals(orderLossRows(every()), orderLossRows(every(), first = PlusFeature.KIDS))
    }
}
