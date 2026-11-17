package com.masroufi.app.feature.close

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.BiggestPunch
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Payday Day(25), one cash pocket with 3,900 EGP: on 2026-11-12 (13 days left) the ticket is 300; tomorrow has 12 days. */
@RunWith(AndroidJUnit4::class)
class CloseDayViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var clock: TestClock
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        clock = TestClock(at(nov12, 9), zone)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0)) }
    }

    @After fun close() {
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()

    private fun settle(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (true) {
            main.scheduler.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            check(System.nanoTime() < end) { "timed out waiting for $what" }
            Thread.sleep(5)
        }
    }

    private fun spend(amount: Long, category: Long? = null) = runBlocking {
        f.entries.log(NewEntry(EntryKind.SPENT, amount, cash, categoryId = category, happenedAt = clock.millis()))
    }

    private fun category(name: String) = runBlocking { db.categoryDao().insert(CategoryEntity(name = name, icon = "grid", color = 0)) }

    /** Prints the day's ticket, then opens the screen's view model for it. */
    private fun vm(date: LocalDate = nov12): CloseDayViewModel {
        runBlocking { f.tickets.printToday() }
        return CloseDayViewModel(f.home, f.tickets, SavedStateHandle(mapOf("date" to date.toEpochDay())))
    }

    private fun CloseDayViewModel.ready(): CloseDayUi.Ready {
        settle("ready") { state.value is CloseDayUi.Ready }
        return state.value as CloseDayUi.Ready
    }

    /** 6 punches, 168 spent, the biggest 90 in بقالة: 132 left of the 300 ticket. */
    private fun sixPunches() {
        val groceries = category("بقالة")
        val other = category("قهوة")
        spend(9_000, groceries)
        spend(2_000, other); spend(1_800, other); spend(1_000, other); spend(2_000, other); spend(1_000, other)
    }

    @Test fun biggestEntryLine() {
        sixPunches()
        val s = vm().ready()
        assertEquals(BiggestPunch("بقالة", 9_000), s.biggest)
        assertEquals(16_800L, s.spent)
        assertEquals(6, s.holes)
        assertEquals(13_200L, s.leftover)
    }

    @Test fun noEntriesNoBiggest() {
        val s = vm().ready()
        assertEquals(null, s.biggest)
        assertEquals(30_000L, s.leftover)
    }

    @Test fun tomorrowLineNumbers() {
        sixPunches()
        val s = vm().ready()
        // Spendable 3,732. Carried: 132 + (3,732 - 132) / 12 = 432. In a pot: (3,732 - 132) / 12 = 300.
        assertEquals(43_200L, s.tomorrowWith)
        assertEquals(30_000L, s.tomorrowWithout)
        assertEquals(13_200L, s.jarAfter)
    }

    @Test fun destinationDefaultsFromSettings() {
        runBlocking { f.settings.setLeftoverDefault(LeftoverDestination.EMERGENCY) }
        assertEquals(LeftoverDestination.EMERGENCY, vm().ready().defaultDestination)
    }

    @Test fun zeroLeftoverHidesDestinations() {
        spend(30_000)
        val s = vm().ready()
        assertEquals(0L, s.leftover)
        assertEquals(0L, s.over)
    }

    @Test fun overCopyUsesSpread() {
        spend(33_500)
        val s = vm().ready()
        // 35 over, 12 days after today in the cycle: ceil(35 / 12) = 3 a day.
        assertEquals(3_500L, s.over)
        assertEquals(12, s.spreadDays)
        assertEquals(300L, s.spreadPerDay)
        assertEquals(0L, s.leftover)
    }

    @Test fun overOnLastDayNoSpread() {
        val nov24 = LocalDate.of(2026, 11, 24)
        clock.instant = at(nov24, 9)
        spend(391_000) // the morning ticket is the whole 3,900 (one day left): 10 over
        val s = vm(nov24).ready()
        assertEquals(1_000L, s.over)
        assertEquals(0, s.spreadDays)
        assertEquals(0L, s.spreadPerDay)
    }

    @Test fun tearTwiceOneCard() {
        sixPunches()
        val model = vm()
        model.ready()
        model.tear(LeftoverDestination.JAR)
        model.tear(LeftoverDestination.JAR)
        settle("done") { model.tear.value == TearState.Done }
        model.tear(LeftoverDestination.JAR)
        main.scheduler.runCurrent()
        val moves = runBlocking { db.jarDao().forDay(nov12) }.filter { it.source == JarSource.TICKET_LEFTOVER }
        assertEquals(1, moves.size)
        assertEquals(13_200L, moves.single().amount)
        assertEquals(1, runBlocking { db.proverbDao().numbers() }.size)
    }
}
