package com.masroufi.app.widget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.data.repo.toEntry
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** The stored day summary is rewritten by the print, punch, edit, delete and close paths; the one-tap action logs Home's entry. */
@RunWith(AndroidJUnit4::class)
class DaySummaryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val clock = TestClock(at(nov12, 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L
    private var coffee = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()
    private fun millis(date: LocalDate, hour: Int) = at(date, hour).toEpochMilli()

    /** 3,900 EGP in cash, 13 days left: today's ticket is 300 EGP (30,000 piastres). */
    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0))
        coffee = db.categoryDao().insert(CategoryEntity(name = "قهوة", icon = "c", color = 0))
    }

    private suspend fun summary() = checkNotNull(f.summaryStore.read()) { "no summary stored" }

    private suspend fun spend(amount: Long, hour: Int = 10) =
        f.entries.log(NewEntry(EntryKind.SPENT, amount, cash, categoryId = coffee, happenedAt = millis(nov12, hour)))

    @Test fun printingWritesTheSummaryAndRedrawsTheWidgets() = runTest {
        setUp()
        f.tickets.printToday()
        val s = summary()
        assertEquals(listOf(nov12.toEpochDay(), true, true), listOf(s.day, s.printed, s.open))
        assertEquals(listOf(30_000L, 30_000L), listOf(s.amount, s.left))
        assertEquals(0, s.punches)
        assertTrue(f.widgets.updates > 0)
    }

    @Test fun aPunchAnEditAndADeleteRewriteIt() = runTest {
        setUp()
        f.tickets.printToday()
        val id = spend(2_500)
        assertEquals(27_500L, summary().left)
        assertEquals(1, summary().punches)
        val before = f.widgets.updates
        f.entries.edit(id, NewEntry(EntryKind.SPENT, 4_000, cash, categoryId = coffee, happenedAt = millis(nov12, 10)))
        assertEquals(26_000L, summary().left)
        assertTrue("the edit redrew the widgets", f.widgets.updates > before)
        f.entries.delete(id)
        assertEquals(30_000L, summary().left)
        assertEquals(0, summary().punches)
    }

    @Test fun closingTheDayMarksItClosed() = runTest {
        setUp()
        f.tickets.printToday()
        spend(10_000)
        f.tickets.closeDay(nov12, LeftoverDestination.JAR)
        val s = summary()
        assertFalse(s.open)
        assertEquals(20_000L, s.left)
    }

    @Test fun theMidnightPrintWritesTheNewDay() = runTest {
        setUp()
        f.tickets.printToday()
        clock.instant = at(nov12.plusDays(1), 0)
        f.tickets.printToday() // what MidnightPrintWorker calls
        val s = summary()
        assertEquals(listOf(nov12.plusDays(1).toEpochDay(), true), listOf(s.day, s.open))
    }

    @Test fun theSummaryCarriesTheRepeatChipsAndTheLockSettings() = runTest {
        setUp()
        f.tickets.printToday()
        spend(2_500, 10)
        spend(2_500, 11)
        f.settings.setLockOn(true)
        f.daySummary.refresh()
        val s = summary()
        assertEquals(listOf(RepeatChip(coffee, "قهوة", 2_500, cash)), s.chips)
        assertEquals(listOf(true, false), listOf(s.lockOn, s.hideAmounts))
    }

    @Test fun theRepeatActionLogsHomesChipEntryAndUpdatesTheSummary() = runTest {
        setUp()
        f.tickets.printToday()
        spend(2_500, 10)
        spend(2_500, 11)
        val chip = summary().chips.single()
        val punch = WidgetPunch(f.summaryStore, f.settings, f.entries, clock)
        val before = f.widgets.updates
        assertTrue(punch.punch(chip))
        val logged = db.entryDao().forDay(millis(nov12, 0), millis(nov12.plusDays(1), 0)).maxByOrNull { it.id }!!
        // The same fields Home's chip logs: HomeViewModel.punchRepeat builds this through RepeatChip.toEntry.
        assertEquals(chip.toEntry(logged.happenedAt).let { listOf(it.kind, it.amount, it.pocketId, it.categoryId) }, entryFields(logged))
        assertEquals(listOf(EntryKind.SPENT, 2_500L, cash, coffee), entryFields(logged))
        assertEquals(22_500L, summary().left)
        assertEquals(3, summary().punches)
        assertTrue("the widgets redrew at once", f.widgets.updates > before)
    }

    @Test fun theRepeatActionDoesNothingWhileTheLockIsOn() = runTest {
        setUp()
        f.tickets.printToday()
        spend(2_500, 10)
        spend(2_500, 11)
        f.settings.setLockOn(true)
        f.daySummary.refresh()
        val chip = summary().chips.single()
        assertFalse(WidgetPunch(f.summaryStore, f.settings, f.entries, clock).punch(chip))
        assertEquals(2, summary().punches)
    }

    @Test fun theRepeatActionIgnoresAClosedDayAndAChipThatIsGone() = runTest {
        setUp()
        f.tickets.printToday()
        spend(2_500, 10)
        spend(2_500, 11)
        val chip = summary().chips.single()
        val punch = WidgetPunch(f.summaryStore, f.settings, f.entries, clock)
        assertFalse("a chip the summary does not show", punch.punch(chip.copy(amount = 9_900)))
        f.tickets.closeDay(nov12, LeftoverDestination.JAR)
        assertFalse("a closed day", punch.punch(chip))
        assertNotNull(summary())
    }

    private fun entryFields(e: EntryEntity) = listOf(e.kind, e.amount, e.pocketId, e.categoryId)
}
