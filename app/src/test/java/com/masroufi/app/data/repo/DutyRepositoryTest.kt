package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today 2026-11-17. */
@RunWith(AndroidJUnit4::class)
class DutyRepositoryTest {
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

    private fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
    }

    @Test fun suggestionIsWhatTheyGaveAtMyLatestOccasion() = runTest {
        setUp()
        val a = f.people.addPerson("خالتي")
        val b = f.people.addPerson("عمو")
        val nobody = f.people.addPerson("جاري")
        val graduation = f.duty.addOccasion("تخرّجي", LocalDate.of(2024, 6, 1), OccasionOwner.MINE)
        val wedding = f.duty.addOccasion("فرحي", LocalDate.of(2026, 6, 10), OccasionOwner.MINE)
        val theirs = f.duty.addOccasion("فرح ابنهم", LocalDate.of(2025, 1, 1), OccasionOwner.THEIRS)
        f.duty.addGift(graduation, a, 30_000, GiftDirection.RECEIVED)
        f.duty.addGift(wedding, a, 30_000, GiftDirection.RECEIVED)
        f.duty.addGift(wedding, a, 20_000, GiftDirection.RECEIVED)
        f.duty.addGift(wedding, b, 70_000, GiftDirection.RECEIVED)
        f.duty.addGift(theirs, b, 99_000, GiftDirection.GIVEN)

        assertEquals("both gifts at the latest occasion", 50_000L, f.duty.suggestion(a))
        assertEquals("what I gave them does not count", 70_000L, f.duty.suggestion(b))
        assertNull(f.duty.suggestion(nobody))
    }

    @Test fun upcomingOccasionOfSomeoneElseWithTheSuggestion() = runTest {
        setUp()
        val a = f.people.addPerson("ابن خالتي")
        val wedding = f.duty.addOccasion("فرحي", LocalDate.of(2026, 6, 10), OccasionOwner.MINE)
        f.duty.addGift(wedding, a, 50_000, GiftDirection.RECEIVED)
        f.duty.addOccasion("فرح قديم", today.minusDays(1), OccasionOwner.THEIRS, a)
        f.duty.addOccasion("فرحي التاني", today.plusDays(2), OccasionOwner.MINE)
        f.duty.addOccasion("فرح بعيد", LocalDate.of(2027, 2, 1), OccasionOwner.THEIRS)
        assertNull(f.duty.upcoming()?.person)

        f.duty.addOccasion("فرح ابن خالتك", LocalDate.of(2026, 12, 20), OccasionOwner.THEIRS, a)
        val next = checkNotNull(f.duty.upcoming())
        assertEquals(listOf<Any?>("فرح ابن خالتك", 33L, 50_000L), listOf(next.occasion.name, next.daysUntil, next.gaveMe))
    }

    @Test fun ownOccasionTotalsAndViews() = runTest {
        setUp()
        val a = f.people.addPerson("أ")
        val b = f.people.addPerson("ب")
        val wedding = f.duty.addOccasion("فرحي", LocalDate.of(2026, 6, 10), OccasionOwner.MINE)
        val theirs = f.duty.addOccasion("فرحهم", LocalDate.of(2026, 7, 1), OccasionOwner.THEIRS)
        f.duty.addGift(wedding, a, 50_000, GiftDirection.RECEIVED)
        f.duty.addGift(wedding, a, 10_000, GiftDirection.RECEIVED)
        f.duty.addGift(wedding, b, 70_000, GiftDirection.RECEIVED)
        f.duty.addGift(theirs, a, 40_000, GiftDirection.GIVEN)

        assertEquals("130,000 piastres from 2 people", 130_000L to 2, f.duty.ownTotals(wedding))
        assertNull("not my occasion", f.duty.ownTotals(theirs))
        val views = f.duty.observe().first()
        assertEquals("newest first", listOf("فرحهم", "فرحي"), views.map { it.occasion.name })
        assertEquals(listOf(40_000L, 0L), views.map { it.givenTotal })
        f.duty.deleteOccasion(wedding)
        assertEquals(1, db.dutyDao().giftsNow().size)
    }
}
