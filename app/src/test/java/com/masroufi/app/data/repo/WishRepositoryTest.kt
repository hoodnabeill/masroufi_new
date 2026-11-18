package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.db.WishAnswer
import com.masroufi.app.domain.EntryKind
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

/** Today 2026-11-17 09:00 Cairo. */
@RunWith(AndroidJUnit4::class)
class WishRepositoryTest {
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

    private suspend fun TestScope.setUp(openingBalance: Long = 50_000) {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements(false))
        db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = openingBalance, color = 0))
    }

    private fun millis(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun parksForFortyEightHoursAndTheQuestionComesDue() = runTest {
        setUp()
        val id = f.wishes.park("  ساعة إيد ", 95_000)
        val fresh = f.wishes.observe().first().single()
        assertEquals(listOf<Any>("ساعة إيد", 48L, false), listOf(fresh.wish.name, fresh.hoursLeft, fresh.due))
        assertTrue(f.wishes.dueForAnswer().first().isEmpty())

        clock.instant = clock.instant.plusSeconds(47 * 3600L + 60)
        val almost = f.wishes.observe().first().single()
        assertEquals("rounds up", listOf<Any>(1L, false), listOf(almost.hoursLeft, almost.due))

        clock.instant = clock.instant.plusSeconds(3600)
        assertEquals(listOf(id), f.wishes.dueForAnswer().first().map { it.id })
        assertEquals(0L, f.wishes.observe().first().single().hoursLeft)
    }

    @Test fun anAnsweredWishIsNoLongerDue() = runTest {
        setUp()
        val id = f.wishes.park("jacket", 120_000)
        clock.instant = clock.instant.plusSeconds(49 * 3600L)
        f.wishes.changedMind(id, toJar = false)
        assertTrue(f.wishes.dueForAnswer().first().isEmpty())
        assertFalse(f.wishes.observe().first().single().due)
    }

    @Test fun changedMindMovesAtMostWhatIsSpendable() = runTest {
        setUp(openingBalance = 50_000)
        val id = f.wishes.park("jacket", 120_000)
        assertEquals("capped at spendable", 50_000L, f.wishes.changedMind(id, toJar = true))
        val row = db.jarDao().rowsNow(Pot.JAR).single()
        assertEquals(listOf(50_000L, JarSource.MANUAL), listOf(row.amount, row.source))
        assertEquals("answering twice moves nothing", 0L, f.wishes.changedMind(id, toJar = true))
        assertEquals(50_000L, db.jarDao().totalNow(Pot.JAR))
        assertEquals(120_000L, f.wishes.totalChangedMind().first())
    }

    @Test fun changedMindMovesTheWholePriceWhenThereIsRoom() = runTest {
        setUp(openingBalance = 300_000)
        val id = f.wishes.park("watch", 95_000)
        assertEquals(95_000L, f.wishes.changedMind(id, toJar = true))
        assertEquals(95_000L, db.jarDao().totalNow(Pot.JAR))
    }

    @Test fun jarAndBillsAreNotSpendable() = runTest {
        setUp(openingBalance = 100_000)
        f.jar.deposit(30_000)
        db.recurringDao().insert(
            RecurringEntity(
                name = "rent", amount = 20_000, pocketId = 1, scheduleDay = 20, nextDue = today.plusDays(2), kind = RecurringKind.BILL,
            ),
        )
        assertEquals(50_000L, f.wishes.spendable())
        val id = f.wishes.park("jacket", 120_000)
        assertEquals(50_000L, f.wishes.changedMind(id, toJar = true))
    }

    @Test fun changedMindWithoutTheJarMovesNothing() = runTest {
        setUp()
        val id = f.wishes.park("jacket", 120_000)
        assertEquals(0L, f.wishes.changedMind(id, toJar = false))
        assertTrue(db.jarDao().rowsNow(Pot.JAR).isEmpty())
        assertEquals("it still counts as not bought", 120_000L, f.wishes.totalChangedMind().first())
    }

    @Test fun boughtGivesThePrefillAndIsNotCountedAsChangedMind() = runTest {
        setUp()
        val id = f.wishes.park("jacket", 120_000)
        val prefill = f.wishes.bought(id)
        assertEquals(listOf<Any?>(120_000L, "jacket", null), listOf(prefill.amount, prefill.note, prefill.edit))
        assertEquals(WishAnswer.BOUGHT, db.wishDao().byId(id)!!.answer)
        assertEquals(0L, f.wishes.totalChangedMind().first())
    }

    @Test fun parkRejectsNothingToPark() = runTest {
        setUp()
        assertTrue(runCatching { f.wishes.park(" ", 100) }.isFailure)
        assertTrue(runCatching { f.wishes.park("x", 0) }.isFailure)
    }

    @Test fun contextHasTicketIncomeAndNothingInvented() = runTest {
        setUp()
        val empty = f.wishes.context()
        assertEquals(listOf<Long?>(null, null, null, null), listOf(empty.ticket, empty.monthlyIncome, empty.goldPricePerGram, empty.seasonSaved))

        db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 24_000, base = 24_000))
        listOf(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 25)).forEach { d ->
            db.entryDao().insert(
                EntryEntity(kind = EntryKind.RECEIVED, amount = 900_000, pocketId = 1, happenedAt = millis(d), loggedAt = millis(d)),
            )
        }
        db.entryDao().insert(
            EntryEntity(kind = EntryKind.RECEIVED, amount = 5_000_000, pocketId = 1, happenedAt = millis(today), loggedAt = millis(today)),
        )
        f.jar.setGoldPrice(500_000, today.toEpochDay())
        val ctx = f.wishes.context()
        assertEquals(
            "this month's income is not a complete month",
            listOf<Long?>(24_000L, 900_000L, 500_000L),
            listOf(ctx.ticket, ctx.monthlyIncome, ctx.goldPricePerGram),
        )
        assertNull(ctx.seasonSaved)
    }

    @Test fun incomeFallsBackToTheUsualSalary() = runTest {
        setUp()
        f.settings.setRitualDone(750_000, null)
        assertEquals(750_000L, f.wishes.context().monthlyIncome)
    }
}
