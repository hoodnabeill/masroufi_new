package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.ProverbCollectedEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.TicketStatus
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today 2026-11-17 (a Tuesday). */
@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {
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

    private suspend fun stamps() = db.stampDao().all().first().associate { it.kind to it.earnedOn }

    private suspend fun closedRow(date: LocalDate, spent: Long = 5_000, status: TicketStatus = TicketStatus.CLOSED_BY_HAND) =
        db.dayTicketDao().insert(
            DayTicketEntity(date = date, amount = 10_000, base = 10_000, spent = spent, status = status, leftover = maxOf(0, 10_000 - spent)),
        )

    @Test fun firstTicketStampIsWrittenOnceAndKeepsItsDate() = runTest {
        setUp()
        db.dayTicketDao().insert(DayTicketEntity(date = today.minusDays(1), amount = 10_000, base = 10_000))
        db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 10_000, base = 10_000))
        f.tickets.closeDay(today.minusDays(1), LeftoverDestination.JAR)
        f.tickets.closeDay(today, LeftoverDestination.JAR)
        assertEquals(mapOf(StampKind.FIRST_TICKET to today.minusDays(1)), stamps())
        assertEquals(1, db.stampDao().kinds().size)
    }

    @Test fun firstGramNeedsAPriceAndOneGramInTheJar() = runTest {
        setUp()
        f.jar.deposit(60_000)
        assertTrue("no price typed", stamps().isEmpty())
        f.jar.setGoldPrice(50_000, today.toEpochDay())
        assertEquals(mapOf(StampKind.FIRST_GRAM to today), stamps())
    }

    @Test fun firstGramWhenDepositReachesThePrice() = runTest {
        setUp()
        f.jar.setGoldPrice(50_000, today.toEpochDay())
        f.jar.deposit(49_999)
        assertTrue(stamps().isEmpty())
        f.jar.deposit(1)
        assertEquals(setOf(StampKind.FIRST_GRAM), stamps().keys)
        f.jar.deposit(10_000) // idempotent
        assertEquals(1, db.stampDao().kinds().size)
    }

    @Test fun firstGramFromTheCloseDayLeftover() = runTest {
        setUp()
        f.jar.setGoldPrice(5_000, today.toEpochDay())
        db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 10_000, base = 10_000))
        f.tickets.closeDay(today, LeftoverDestination.JAR) // 100 EGP leftover into the jar
        assertTrue(StampKind.FIRST_GRAM in stamps())
    }

    @Test fun settledNeedsADebtThenNoOpenOnes() = runTest {
        setUp()
        f.stamps.checkSettled()
        assertTrue("no debt ever", stamps().isEmpty())
        val ahmed = f.people.addPerson("أحمد")
        val one = f.people.lend(ahmed, 10_000, null)
        val two = f.people.lend(ahmed, 5_000, null)
        f.people.repay(one, 10_000, null)
        assertTrue("one still open", stamps().isEmpty())
        f.people.repay(two, 2_000, null)
        assertTrue("partial repayment", stamps().isEmpty())
        f.people.repay(two, 3_000, null)
        assertEquals(setOf(StampKind.SETTLED), stamps().keys)
    }

    @Test fun tightFistIsNeverEarnedYet() = runTest {
        setUp()
        (1L..9L).forEach { closedRow(today.minusDays(it), spent = 0) }
        assertTrue(StampKind.TIGHT_FIST !in stamps())
    }

    @Test fun albumReadsLevelStreakStubsAndCards() = runTest {
        setUp()
        (1L..7L).forEach { closedRow(today.minusDays(it)) }
        db.proverbDao().insert(ProverbCollectedEntity(number = 3, collectedOn = today.minusDays(1)))
        val a = f.album().observe().first()
        assertEquals(2, a.level)
        assertEquals("ماشي", a.levelName)
        assertEquals(7, a.handClosed)
        assertEquals(20, a.nextLevelAt)
        assertEquals(13, a.ticketsToNext)
        assertEquals(7, a.streak)
        assertEquals(7, a.longestStreak)
        assertEquals(7, a.daysUnder)
        assertEquals(1, a.proverbs.size)
        assertEquals(3, a.proverbs.single().proverb.number)
        assertEquals(49, a.lockedProverbs)
        assertEquals(7, a.stubs.size)
        assertEquals(today.minusDays(1), a.stubs.first().date)
    }

    @Test fun milestonesAreUnseenUntilMarked() = runTest {
        setUp()
        val repo = MilestoneRepository(db.dayTicketDao(), db.stampDao(), f.settings)
        assertTrue(repo.unseen().first().isEmpty())
        (1L..7L).forEach { closedRow(today.minusDays(it)) }
        f.jar.setGoldPrice(1_000, today.toEpochDay())
        f.jar.deposit(1_000)
        val unseen = repo.unseen().first()
        assertEquals(listOf("level:2", "stamp:FIRST_GRAM"), unseen.map { it.key })
        repo.markSeen(unseen[0])
        assertEquals(listOf("stamp:FIRST_GRAM"), repo.unseen().first().map { it.key })
        repo.markSeen(unseen[1])
        assertTrue(repo.unseen().first().isEmpty())
    }

    @Test fun matchRepositoryGatesHistoryByPlus() = runTest {
        setUp(plus = false)
        (0L..5L).forEach { closedRow(LocalDate.of(2026, 10, 3).plusDays(7 * it)) }
        val m = MatchRepository(db.dayTicketDao(), DebugEntitlements(false), clock).observe().first()
        assertEquals(4, m.league.size)
        assertEquals(3, m.lockedWeeks)
        val plus = MatchRepository(db.dayTicketDao(), DebugEntitlements(true), clock).observe().first()
        assertEquals(0, plus.lockedWeeks)
    }

    @Test fun albumShowsTheSafetyTicketOnlyWithPlus() = runTest {
        setUp()
        // Tuesday 2026-11-17: closed by hand on the 16th, 14th and 13th; the 15th was missed.
        closedRow(today.minusDays(1))
        closedRow(today.minusDays(3))
        closedRow(today.minusDays(4))
        val free = f.album(DebugEntitlements(false)).observe().first()
        assertEquals(listOf(1, 0), listOf(free.streak, free.protectedDays))
        val plus = f.album(DebugEntitlements(true)).observe().first()
        assertEquals(listOf(3, 2), listOf(plus.streak, plus.protectedDays))
    }

    private fun RepoFixture.album(plus: DebugEntitlements = DebugEntitlements()) = AlbumRepository(db.dayTicketDao(), db.proverbDao(), db.stampDao(), clock, plus)
}
