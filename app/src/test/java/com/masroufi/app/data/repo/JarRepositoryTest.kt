package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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
 * Today 2026-11-17. The jar: leftovers 50 EGP (10-30) and 30 EGP (11-10), a 7 EGP round-up (11-12), a 100 EGP deposit
 * and a 20 EGP withdrawal today; a leftover in the emergency pot is not the jar. Total 167 EGP, 117 EGP of it this month.
 */
@RunWith(AndroidJUnit4::class)
class JarRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(d(11, 17), 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()

    private suspend fun move(amount: Long, source: JarSource, date: LocalDate, pot: Pot = Pot.JAR) =
        db.jarDao().insert(JarMovementEntity(pot = pot, amount = amount, source = source, at = at(date, 22).toEpochMilli()))

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        move(5_000, JarSource.TICKET_LEFTOVER, d(10, 30))
        move(3_000, JarSource.TICKET_LEFTOVER, d(11, 10))
        move(700, JarSource.ROUND_UP, d(11, 12))
        move(9_999, JarSource.TICKET_LEFTOVER, d(11, 11), pot = Pot.EMERGENCY)
        f.jar.deposit(10_000)
        f.jar.withdraw(2_000)
    }

    @Test fun jarTotalGrowthAndSources() = runTest {
        setUp()
        val v = f.jar.observe().first()
        assertEquals(16_700L, v.total)
        assertEquals("this calendar month only", 11_700L, v.monthGrowth)
        assertEquals(JarSources(leftoverCount = 2, leftoverSum = 8_000, roundUpSum = 700, manualSum = 10_000), v.sources)
        assertEquals("nothing earmarked: all free", 16_700L, v.free)
    }

    @Test fun earmarkIsAZeroSumPair() = runTest {
        setUp()
        val goal = f.jar.createGoal("موبايل", 500_000)
        f.jar.earmark(4_000, goalId = goal)
        val rows = db.jarDao().rowsNow(Pot.JAR).takeLast(2)
        assertEquals(
            "+x on the goal, -x free, both MANUAL",
            listOf(Triple(4_000L, goal, JarSource.MANUAL), Triple(-4_000L, null, JarSource.MANUAL)),
            rows.map { Triple(it.amount, it.goalId, it.source) },
        )
        var v = f.jar.observe().first()
        assertEquals("total unchanged", 16_700L, v.total)
        assertEquals("growth unchanged", 11_700L, v.monthGrowth)
        assertEquals(4_000L, v.goals.single().earmarked)
        assertEquals(12_700L, v.free)

        f.jar.release(1_500, goalId = goal)
        v = f.jar.observe().first()
        assertEquals(2_500L, v.goals.single().earmarked)
        assertEquals(14_200L, v.free)
        assertEquals("earmark pairs never count as money put in", 10_000L, v.sources.manualSum)
    }

    @Test fun withdrawCappedAtFreeTotal() = runTest {
        setUp()
        f.jar.earmark(4_000, seasonId = 1)
        try {
            f.jar.withdraw(12_701)
            fail("withdrawing above the free total must be refused")
        } catch (e: AboveFreeJar) {
            assertEquals(12_700L, e.free)
        }
        try {
            f.jar.earmark(12_701, seasonId = 1)
            fail("earmarking above the free total must be refused")
        } catch (e: AboveFreeJar) {
            assertEquals(12_700L, e.free)
        }
        f.jar.withdraw(12_700)
        val v = f.jar.observe().first()
        assertEquals(4_000L to 0L, v.total to v.free)
    }

    @Test fun deleteGoalReleasesEarmarks() = runTest {
        setUp()
        val goal = f.jar.createGoal("عربية", 10_000_000)
        f.jar.earmark(6_000, goalId = goal)
        f.jar.deleteGoal(goal)
        val v = f.jar.observe().first()
        assertEquals(emptyList<GoalView>(), v.goals)
        assertEquals(16_700L to 16_700L, v.total to v.free)
        assertEquals(10_000L, v.sources.manualSum)
    }

    @Test fun goldGramsAndLeftToFirstGram() = runTest {
        setUp()
        assertNull("no price typed: no gold view", f.jar.observe().first().gold)
        f.settings.setGoldPrice(400_000, d(11, 15).toEpochDay())
        assertEquals(
            "167 EGP at 4,000 EGP/g: 0.04 g, 3,833 EGP to the first gram",
            GoldView(400_000, d(11, 15), 4, 383_300),
            f.jar.observe().first().gold,
        )
        f.jar.deposit(983_300)
        assertEquals("1,000 EGP: 2.50 g", GoldView(400_000, d(11, 15), 250, null), f.jar.observe().first().gold)
    }
}
