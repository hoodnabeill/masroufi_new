package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.SeasonEntity
import com.masroufi.app.data.db.SeasonKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.time.chrono.HijrahDate

/** Today 2026-11-17 (Hijri year 1448). */
@RunWith(AndroidJUnit4::class)
class SeasonRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(at(today, 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()
    private fun hijri(y: Int, m: Int, d: Int): LocalDate = LocalDate.from(HijrahDate.of(y, m, d))

    private fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
    }

    @Test fun defaultSeasonsSeededOnce() = runTest {
        setUp()
        val first = f.seasons.observe().first()
        f.seasons.observe().first()
        assertEquals(5, db.seasonDao().count())
        assertEquals(
            "soonest first: Ramadan, Eid al-Fitr, Eid al-Adha (1448), summer and school 2027",
            listOf("رمضان", "العيد الصغير", "العيد الكبير", "المصيف", "دخول المدارس"),
            first.map { it.season.name },
        )
        assertEquals(listOf(true, true, true, false, false), first.map { it.approximate })
        assertTrue("targets are typed by the user", first.all { it.season.target == 0L && it.perDayNeeded == 0L })
    }

    @Test fun nextOccurrenceHijriAndGregorian() {
        fun season(kind: SeasonKind, hm: Int? = null, hd: Int? = null, date: LocalDate? = null) =
            SeasonEntity(kind = kind, name = "s", hijriMonth = hm, hijriDay = hd, gregorianDate = date, target = 0, color = 0)
        val ramadan = season(SeasonKind.RAMADAN, 9, 1)
        assertEquals(hijri(1448, 9, 1), nextOccurrence(ramadan, today))
        assertEquals(2027, nextOccurrence(ramadan, today).year)
        val adha = season(SeasonKind.EID_AL_ADHA, 12, 10)
        assertEquals("the day after: next Hijri year", hijri(1449, 12, 10), nextOccurrence(adha, hijri(1448, 12, 11)))
        assertEquals("on the day itself: today", hijri(1448, 12, 10), nextOccurrence(adha, hijri(1448, 12, 10)))
        assertEquals(LocalDate.of(2027, 7, 1), nextOccurrence(season(SeasonKind.SUMMER, date = LocalDate.of(2026, 7, 1)), today))
        assertEquals(LocalDate.of(2026, 11, 20), nextOccurrence(season(SeasonKind.SCHOOL, date = LocalDate.of(2020, 11, 20)), today))
        assertEquals("personal: its own date", LocalDate.of(2027, 3, 3), nextOccurrence(season(SeasonKind.PERSONAL, date = LocalDate.of(2027, 3, 3)), today))
    }

    @Test fun perDayNeededRoundsUpFromSaved() = runTest {
        setUp()
        val id = f.seasons.createPersonal("فرح أختي", LocalDate.of(2026, 12, 17), 100_001)
        f.jar.deposit(50_000)
        f.jar.earmark(10_000, seasonId = id)
        val v = f.seasons.observe().first().single { it.season.id == id }
        assertEquals(30L, v.daysUntil)
        assertEquals(10_000L, v.saved)
        assertEquals("90,001 piastres over 30 days, rounded up", 3_001L, v.perDayNeeded)
        f.seasons.update(v.season.copy(target = 5_000))
        assertEquals("met: nothing needed", 0L, f.seasons.observe().first().single { it.season.id == id }.perDayNeeded)
    }

    @Test fun checklistItems() = runTest {
        setUp()
        val id = f.seasons.createPersonal("عيد ميلاد", LocalDate.of(2026, 12, 1), 0)
        val gift = f.seasons.addItem(id, "هدية", 30_000)
        val cake = f.seasons.addItem(id, "تورتة", 15_000)
        f.seasons.updateItem(f.seasons.observe().first().single { it.season.id == id }.items.single { it.id == gift }.copy(amount = 35_000))
        f.seasons.deleteItem(cake)
        val items = f.seasons.observe().first().single { it.season.id == id }.items
        assertEquals(listOf("هدية" to 35_000L), items.map { it.label to it.amount })
    }

    @Test fun togetherComparesWithJarGrowth() = runTest {
        setUp()
        for (month in listOf(8, 9, 10)) {
            db.jarDao().insert(
                JarMovementEntity(pot = Pot.JAR, amount = 30_000, source = JarSource.MANUAL, at = at(LocalDate.of(2026, month, 10), 12).toEpochMilli()),
            )
        }
        val far = f.seasons.createPersonal("جهاز", LocalDate.of(2027, 2, 17), 120_000) // 3 months away
        f.seasons.createPersonal("سفرية", LocalDate.of(2026, 12, 20), 25_001) // 1 month away
        f.jar.deposit(50_000) // this month: not in the average
        f.jar.earmark(5_000, seasonId = far)
        // ceil(115,000 / 3) = 38,334 + 25,001 = 63,335 needed a month vs 90,000 / 3 = 30,000 usual.
        assertEquals(TogetherView(neededPerMonth = 63_335, usualPerMonth = 30_000, gap = 33_335), f.seasons.together())
    }
}
