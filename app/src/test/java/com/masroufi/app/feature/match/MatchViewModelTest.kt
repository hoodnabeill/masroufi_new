package com.masroufi.app.feature.match

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.MatchRepository
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EventKind
import com.masroufi.app.domain.HatTrick
import com.masroufi.app.domain.MatchSeen
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.weekStart
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today is Thursday 2026-11-19 09:00 Cairo; the week is Saturday 11-14 to Friday 11-20. Every ticket is 100. */
@RunWith(AndroidJUnit4::class)
class MatchViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 19)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private val plus = DebugEntitlements(false)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, plus)
    }

    @After fun close() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

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

    /** A screen opening: the view model, collected, until it has a view. */
    private fun visit(): MatchViewModel {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { MatchViewModel(MatchRepository(db.dayTicketDao(), plus, clock), f.settings) } },
        )[MatchViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} }
        settle("match") { vm.state.value.view != null }
        return vm
    }

    private fun day(date: LocalDate, spent: Long, status: TicketStatus = TicketStatus.CLOSED_BY_HAND) = runBlocking {
        db.dayTicketDao().insert(
            DayTicketEntity(date = date, amount = 10_000, base = 10_000, spent = spent, status = status, leftover = maxOf(0, 10_000 - spent)),
        )
    }

    private fun d(n: Int) = LocalDate.of(2026, 11, n)

    /** Sat +40, Sun +18, Mon -35, Tue +72, Wed +5, Thu open with 16 left. */
    private fun mockWeek() {
        day(d(14), 6_000); day(d(15), 8_200); day(d(16), 13_500); day(d(17), 2_800); day(d(18), 9_500)
        day(d(19), 8_400, TicketStatus.OPEN)
    }

    @Test fun eventWordingFollowsTheMock() {
        mockWeek()
        val ui = visit().state.value
        assertEquals(
            listOf(EventKind.GOAL, EventKind.GOAL, EventKind.AGAINST, EventKind.BEST, EventKind.LAST_MINUTE, EventKind.OPEN),
            ui.events.map { it.kind },
        )
        assertEquals(listOf(4_000L, 1_800L, 3_500L, 7_200L, 500L, 1_600L), ui.events.map { it.amount })
        assertEquals("two goals in a row up to yesterday, today open", HatTrick.ONE_AWAY, ui.view!!.hatTrick)
    }

    @Test fun aLoneGoalIsNotTheBestDayAndAnExactCloseIsOnTheLimit() {
        day(d(14), 10_000)
        day(d(15), 4_000)
        assertEquals(listOf(EventKind.EXACT, EventKind.GOAL), visit().state.value.events.map { it.kind })
    }

    @Test fun firstWeekHasOneWeekAndNoLeague() {
        day(d(19), 4_000, TicketStatus.OPEN)
        val ui = visit().state.value
        assertTrue(ui.view!!.firstWeek)
        assertEquals(listOf(EventKind.OPEN), ui.events.map { it.kind })
        assertEquals(0, ui.view!!.lockedWeeks)
    }

    @Test fun weeksBeyondFourAreLockedWithoutPlusAndCounted() {
        (0..5).forEach { day(today.minusDays(7L * it), 5_000) }
        val free = visit().state.value.view!!
        assertEquals(4, free.league.size)
        assertEquals(2, free.lockedWeeks)
        plus.setDebugOverride(true)
        val withPlus = visit()
        settle("plus") { withPlus.state.value.view?.lockedWeeks == 0 }
        assertEquals(6, withPlus.state.value.view!!.league.size)
    }

    @Test fun goalsSinceTheLastVisitAreFreshAndTheScoreIsStored() {
        mockWeek()
        runBlocking { f.settings.setMatchSeen(weekStart(today), 3, 1) }
        val ui = visit().state.value
        assertEquals(1, ui.fresh.forGoals)
        assertEquals(0, ui.fresh.againstGoals)
        assertEquals(3, ui.fresh.from)
        assertEquals("the latest closed day", setOf(d(18)), ui.fresh.dates)
        assertEquals(listOf(d(18)), ui.events.filter { it.fresh }.map { it.date })
        settle("seen stored") { runBlocking { f.settings.current().matchSeen } == MatchSeen(weekStart(today).toEpochDay(), 4, 1) }

        val again = visit().state.value
        assertEquals(0, again.fresh.forGoals + again.fresh.againstGoals)
        assertTrue(again.events.none { it.fresh })
    }

    @Test fun aFirstVisitCountsTheWholeWeekAndALaterGoalAgainstAloneIsFresh() {
        day(d(14), 6_000)
        day(d(15), 13_000)
        val first = visit().state.value
        assertEquals(1, first.fresh.forGoals)
        assertEquals(1, first.fresh.againstGoals)
        settle("stored") { runBlocking { f.settings.current().matchSeen } != null }

        day(d(16), 12_000)
        val next = visit().state.value
        assertEquals(0, next.fresh.forGoals)
        assertEquals(1, next.fresh.againstGoals)
        assertNotNull(next.events.firstOrNull { it.fresh && it.kind == EventKind.AGAINST })
    }

    @Test fun aNewWeekStartsFromZero() {
        mockWeek()
        runBlocking { f.settings.setMatchSeen(weekStart(today).minusDays(7), 6, 0) }
        assertEquals(4, visit().state.value.fresh.forGoals)
    }
}
