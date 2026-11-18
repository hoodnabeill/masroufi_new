package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.EnvelopeCategoryEntity
import com.masroufi.app.data.db.EnvelopeEntity
import com.masroufi.app.data.db.EnvelopeKind
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** Payday 25: the cycle 2026-10-25 .. 11-24 (31 days) closes when 11-25 prints. */
@RunWith(AndroidJUnit4::class)
class StoryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(LocalDate.of(2026, 11, 25)), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    private val oct25 = LocalDate.of(2026, 10, 25)
    private val sep25 = LocalDate.of(2026, 9, 25)
    private val aug25 = LocalDate.of(2026, 8, 25)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 5_000_000, color = 0))
    }

    /** One printed day per char from [start]: U torn under (spent 50 of 100), O torn over (150), N never torn (closed by itself). */
    private suspend fun rows(start: LocalDate, pattern: String, spent: Map<Int, Long> = emptyMap()) {
        pattern.forEachIndexed { i, c ->
            db.dayTicketDao().insert(
                DayTicketEntity(
                    date = start.plusDays(i.toLong()), amount = 10_000, base = 10_000,
                    spent = when (c) { 'U' -> spent[i] ?: 5_000L; 'O' -> 15_000L; else -> 0L },
                    status = if (c == 'N') TicketStatus.CLOSED_AUTO else TicketStatus.CLOSED_BY_HAND,
                ),
            )
        }
    }

    private fun mock() = "U".repeat(22) + "O".repeat(4) + "N".repeat(5)

    private suspend fun built(): MonthStory {
        f.tickets.printToday()
        return f.story.all().first().single()
    }

    private suspend fun spend(date: LocalDate, amount: Long, category: Long?, recurringId: Long? = null, note: String? = null) {
        db.entryDao().insert(
            EntryEntity(
                kind = EntryKind.SPENT, amount = amount, pocketId = cash, categoryId = category, happenedAt = at(date).toEpochMilli(),
                loggedAt = 0, recurringId = recurringId, note = note,
            ),
        )
    }

    private suspend fun category(name: String) = db.categoryDao().insert(CategoryEntity(name = name, icon = "grid", color = 0))

    private suspend fun envelope(name: String, monthly: Long, vararg categories: Long, kind: EnvelopeKind = EnvelopeKind.SPENDING): Long {
        val id = db.envelopeDao().insert(EnvelopeEntity(name = name, color = 0, monthly = monthly, kind = kind))
        categories.forEach { db.envelopeDao().link(EnvelopeCategoryEntity(id, it)) }
        return id
    }

    @Test fun mockNumbers26Of31Torn22Under4Over5Missed() = runTest {
        setUp()
        rows(oct25, mock())
        rows(sep25, "U".repeat(19) + "N".repeat(11))
        rows(aug25, "U".repeat(24) + "N".repeat(7))
        val s = built()
        val p = s.payload
        assertEquals(listOf(31, 26, 22, 4, 5), listOf(p.cycleDays, p.torn, p.under, p.over, p.never))
        assertEquals(mock(), p.grid)
        assertFalse(p.thin)
        assertEquals(listOf(oct25, LocalDate.of(2026, 11, 25)), listOf(s.cycleStart, s.cycleEnd))
        assertNull("not opened yet", s.shownAt)
    }

    @Test fun comparesWithThePreviousCycleAndTheBestEarlierOne() = runTest {
        setUp()
        rows(oct25, mock())
        rows(sep25, "U".repeat(19) + "N".repeat(11)) // "سبتمبر كان 19"
        rows(aug25, "U".repeat(24) + "N".repeat(7))
        val c = built().payload.comparison!!
        assertEquals(listOf<Any?>(sep25.toEpochDay(), oct25.toEpochDay(), 19), listOf(c.prevStart, c.prevEnd, c.prevTorn))
        assertEquals(listOf<Any?>(aug25.toEpochDay(), 24), listOf(c.bestStart, c.bestTorn))
        assertTrue("26 beats 24 and 19", c.isBest)
    }

    @Test fun bestMonthIsOnlySaidWhenStrictlyTrue() = runTest {
        setUp()
        rows(oct25, mock())
        rows(sep25, "U".repeat(19) + "N".repeat(11))
        rows(aug25, "U".repeat(26) + "N".repeat(5)) // ties the 26 torn days of this one
        val tie = built().payload.comparison!!
        assertEquals(26, tie.bestTorn)
        assertFalse("a tie is not the best month", tie.isBest)
    }

    @Test fun anEarlierBetterMonthIsNamedAndNothingIsBestWhenThereIsNoEarlierMonth() = runTest {
        setUp()
        rows(oct25, mock())
        rows(sep25, "U".repeat(19) + "N".repeat(11))
        rows(aug25, "U".repeat(29) + "N".repeat(2))
        val better = built().payload.comparison!!
        assertEquals(listOf(29, false), listOf(better.bestTorn, better.isBest))
    }

    @Test fun firstEverMonthHasNothingToCompareWith() = runTest {
        setUp()
        rows(oct25, mock())
        val c = built().payload.comparison!!
        assertEquals(listOf<Any?>(null, null, false), listOf(c.prevTorn, c.bestTorn, c.isBest))
    }

    @Test fun aThinMonthIsShortWithNoComparisonsAndNoSuggestion() = runTest {
        setUp()
        rows(oct25, "U".repeat(3) + "O".repeat(2) + "N".repeat(26))
        rows(sep25, "U".repeat(19) + "N".repeat(11))
        val p = built().payload
        assertTrue(p.thin)
        assertEquals(listOf(5, 3, 2, 26), listOf(p.torn, p.under, p.over, p.never))
        assertNull(p.comparison)
        assertEquals(StoryRule.NEUTRAL, p.suggestion.rule)
    }

    @Test fun sevenTornDaysIsTheFirstFullStory() = runTest {
        setUp()
        rows(oct25, "U".repeat(7) + "N".repeat(24))
        assertFalse(built().payload.thin)
    }

    @Test fun sixTornDaysIsThin() = runTest {
        setUp()
        rows(oct25, "U".repeat(3) + "O".repeat(3) + "N".repeat(25))
        assertTrue(built().payload.thin)
    }

    @Test fun theBuildIsIdempotentAndReplayNeverRecalculates() = runTest {
        setUp()
        rows(oct25, mock())
        val first = built()
        val c = category("قهوة")
        spend(LocalDate.of(2026, 11, 10), 99_000, c) // a late edit of the closed month
        f.tickets.printToday() // the same day again
        f.story.buildIfDue(LocalDate.of(2026, 11, 25), f.settings.current())
        db.dayTicketDao().delete(db.dayTicketDao().forDate(LocalDate.of(2026, 11, 25))!!.id)
        f.tickets.printToday() // today re-printed: still one story
        val all = f.story.all().first()
        assertEquals(1, all.size)
        assertEquals(first.payload, all.single().payload)
        assertEquals("the stored payload is what replays", 0L, all.single().payload.totalSpent)
    }

    @Test fun nothingIsBuiltInTheMiddleOfACycle() = runTest {
        setUp()
        rows(oct25, mock())
        rows(LocalDate.of(2026, 11, 25), "UUU")
        clock.instant = at(LocalDate.of(2026, 11, 28))
        f.tickets.printToday() // the last printed day is in this cycle: an upgrade mid-cycle, not a new cycle
        assertTrue(f.story.all().first().isEmpty())
    }

    @Test fun aBrandNewUserHasNoStory() = runTest {
        setUp()
        f.tickets.printToday()
        assertTrue(f.story.all().first().isEmpty())
    }

    @Test fun unseenFlowAndMarkShown() = runTest {
        setUp()
        rows(oct25, mock())
        val s = built()
        assertEquals(s.id, f.story.unseen().first()?.id)
        f.story.markShown(s.id)
        assertNull(f.story.unseen().first())
        val shown = f.story.get(s.id)!!.shownAt
        assertNotNull(shown)
        clock.instant = at(LocalDate.of(2026, 12, 1))
        f.story.markShown(s.id)
        assertEquals("only the first opening counts", shown, f.story.get(s.id)!!.shownAt)
    }

    @Test fun topThreeEnvelopesTheLargestEntryJarCardsAndStreak() = runTest {
        setUp()
        rows(oct25, "UUUUUNUUU" + "N".repeat(22), spent = mapOf(3 to 0L))
        val (c1, c2, c3, c4, loose) = listOf("أكل", "مواصلات", "قهوة", "نت", "حاجة").map { category(it) }
        envelope("E1", 0, c1)
        envelope("E2", 0, c2)
        envelope("E3", 0, c3)
        envelope("E4", 0, c4)
        spend(LocalDate.of(2026, 10, 26), 5_000, c1)
        spend(LocalDate.of(2026, 10, 27), 12_000, c2)
        spend(LocalDate.of(2026, 10, 28), 8_000, c2)
        spend(LocalDate.of(2026, 11, 3), 10_000, c3)
        spend(LocalDate.of(2026, 11, 4), 1_000, c4)
        spend(LocalDate.of(2026, 11, 5), 99_000, loose, note = "تلاجة") // no envelope holds it
        spend(LocalDate.of(2026, 10, 24), 77_000, c1) // the day before the cycle
        spend(LocalDate.of(2026, 11, 25), 77_000, c1) // payday itself belongs to the next one
        val p = built().payload
        assertEquals(listOf("E2", "E3", "E1"), p.topEnvelopes.map { it.name })
        assertEquals(listOf(20_000L, 10_000L, 5_000L), p.topEnvelopes.map { it.spent })
        assertEquals(135_000L, p.totalSpent)
        assertEquals(StoryLargest(99_000, LocalDate.of(2026, 11, 5).toEpochDay(), "حاجة", "تلاجة"), p.largestEntry)
        assertEquals("the day with nothing spent is the best day", StoryBestDay(oct25.plusDays(3).toEpochDay(), 10_000), p.bestDay)
        assertEquals("five days in a row, then a gap", 5, p.longestStreak)
    }

    @Test fun jarMovedCountsDepositsInTheCycleOnly() = runTest {
        setUp()
        rows(oct25, mock())
        fun move(amount: Long, source: JarSource, day: LocalDate) = JarMovementEntity(pot = Pot.JAR, amount = amount, source = source, at = at(day).toEpochMilli())
        db.jarDao().insert(move(500, JarSource.TICKET_LEFTOVER, LocalDate.of(2026, 10, 30)))
        db.jarDao().insert(move(20, JarSource.ROUND_UP, LocalDate.of(2026, 11, 2)))
        db.jarDao().insert(move(1_000, JarSource.MANUAL, LocalDate.of(2026, 11, 20)))
        db.jarDao().insert(move(-300, JarSource.MANUAL, LocalDate.of(2026, 11, 21))) // a withdrawal is not "moved to the jar"
        db.jarDao().insert(move(9_999, JarSource.MANUAL, LocalDate.of(2026, 10, 20)))
        db.proverbDao().insert(ProverbCollectedEntity(1, LocalDate.of(2026, 10, 26)))
        db.proverbDao().insert(ProverbCollectedEntity(2, LocalDate.of(2026, 11, 24)))
        db.proverbDao().insert(ProverbCollectedEntity(3, LocalDate.of(2026, 11, 25)))
        val p = built().payload
        assertEquals(1_520L, p.jarMoved)
        assertEquals(2, p.cardsWon)
    }

    // The suggestion rules, first match wins (see StoryRepository.suggestion).

    @Test fun suggestionEnvelopeRanOutEarliest() = runTest {
        setUp()
        rows(oct25, mock())
        val a = category("أكل")
        val b = category("قهوة")
        envelope("الأكل", 10_000, a)
        envelope("القهوة", 10_000, b)
        envelope("نت", 1_000, category("نت"), kind = EnvelopeKind.FIXED) // a fixed bill reaching its amount is no news
        spend(LocalDate.of(2026, 10, 29), 12_000, a) // day 5
        spend(LocalDate.of(2026, 11, 2), 10_000, b) // day 9
        spend(LocalDate.of(2026, 11, 22), 50_000, category("بنزين")) // an envelope-less spend does not matter
        val s = built().payload.suggestion
        assertEquals(StorySuggestion(StoryRule.ENVELOPE_RAN_OUT, "الأكل", 5, 31, 10_000), s)
    }

    @Test fun anEnvelopeThatRanOutInTheLastDaysIsNotThePoint() = runTest {
        setUp()
        rows(oct25, mock())
        val a = category("أكل")
        envelope("الأكل", 10_000, a)
        spend(LocalDate.of(2026, 11, 22), 12_000, a) // day 29 of 31
        assertEquals(StoryRule.NEUTRAL, built().payload.suggestion.rule)
    }

    @Test fun suggestionHabitGrew() = runTest {
        setUp()
        rows(oct25, mock())
        val coffee = category("قهوة")
        val food = category("أكل")
        spend(LocalDate.of(2026, 10, 10), 10_000, coffee)
        spend(LocalDate.of(2026, 10, 12), 10_000, food)
        spend(LocalDate.of(2026, 11, 1), 30_000, coffee)
        spend(LocalDate.of(2026, 11, 2), 11_000, food) // grew by less than 100 pounds
        spend(LocalDate.of(2026, 11, 3), 90_000, category("إيجار"), recurringId = 99) // a bill is not a habit
        val s = built().payload.suggestion
        assertEquals(StorySuggestion(StoryRule.HABIT_GREW, "قهوة", 30_000, 10_000), s)
    }

    @Test fun suggestionWeekdayMostOftenOver() = runTest {
        setUp()
        // 10-25 is a Sunday: over on three Sundays and one Wednesday.
        val pattern = "U".repeat(31).toCharArray().also { listOf(0, 7, 14, 3).forEach { i -> it[i] = 'O' } }.concatToString()
        rows(oct25, pattern)
        assertEquals(StorySuggestion(StoryRule.WEEKDAY_OVER, null, 7, 3, 4), built().payload.suggestion)
    }

    @Test fun twoWeekdaysTyingIsNoSuggestion() = runTest {
        setUp()
        val pattern = "U".repeat(31).toCharArray().also { listOf(0, 7, 3, 10).forEach { i -> it[i] = 'O' } }.concatToString()
        rows(oct25, pattern)
        assertEquals(StoryRule.NEUTRAL, built().payload.suggestion.rule)
    }

    @Test fun suggestionNeutralWhenNothingApplies() = runTest {
        setUp()
        rows(oct25, mock())
        assertEquals(StorySuggestion(StoryRule.NEUTRAL), built().payload.suggestion)
    }
}
