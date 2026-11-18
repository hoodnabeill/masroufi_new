package com.masroufi.app.feature.album

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
import com.masroufi.app.data.db.ProverbCollectedEntity
import com.masroufi.app.data.db.StampEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.AlbumRepository
import com.masroufi.app.data.repo.Milestone
import com.masroufi.app.data.repo.MilestoneRepository
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.LockHint
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today is Thursday 2026-11-19 09:00 Cairo. */
@RunWith(AndroidJUnit4::class)
class AlbumViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 19)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements(false))
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

    /** A screen opening: collects both flows the screen does. */
    private fun visit(): AlbumViewModel {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    AlbumViewModel(
                        AlbumRepository(db.dayTicketDao(), db.proverbDao(), db.stampDao(), clock, DebugEntitlements()),
                        MilestoneRepository(db.dayTicketDao(), db.stampDao(), f.settings),
                    )
                }
            },
        )[AlbumViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} }
        vm.viewModelScope.launch { vm.celebration.collect {} }
        settle("album") { !vm.state.value.loading }
        return vm
    }

    private fun handClosed(n: Int) = runBlocking {
        (1..n).forEach {
            db.dayTicketDao().insert(
                DayTicketEntity(
                    date = today.minusDays(it.toLong()), amount = 10_000, base = 10_000, spent = 5_000,
                    status = TicketStatus.CLOSED_BY_HAND, leftover = 5_000,
                ),
            )
        }
    }

    private fun collect(number: Int, daysAgo: Long) = runBlocking {
        db.proverbDao().insert(ProverbCollectedEntity(number, today.minusDays(daysAgo)))
    }

    @Test fun levelAndProgressLines() {
        handClosed(8)
        val ui = visit().state.value
        val a = ui.album!!
        assertEquals(2, a.level)
        assertEquals("ماشي", a.levelName)
        assertEquals(8, a.handClosed)
        assertEquals(20, a.nextLevelAt)
        assertEquals(12, a.ticketsToNext)
        assertEquals("(8 - 7) / (20 - 7): through level 2, not from zero", 1f / 13f, ui.progress, 1e-6f)
        assertEquals(8, a.streak)
        assertEquals(8, a.longestStreak)
        assertEquals(8, a.daysUnder)
    }

    @Test fun noCardsIsTheEmptyAlbumWithAllThreeHints() {
        val ui = visit().state.value
        assertTrue(ui.empty)
        assertEquals(listOf(LockHint.TOMORROW, LockHint.FULL_WEEK, LockHint.RARE), ui.locked)
        assertEquals(50, ui.totalCards)
    }

    @Test fun cardsAreNewestFirstAndOwningTheFullWeekCardDropsItsHint() {
        collect(1, 3)
        collect(45, 1)
        val ui = visit().state.value
        assertFalse(ui.empty)
        assertEquals(listOf(45, 1), ui.cards.map { it.proverb.number })
        assertEquals(listOf(LockHint.TOMORROW, LockHint.RARE), ui.locked)
    }

    @Test fun earnedStampsComeFirstThenTheLockedOnes() {
        runBlocking { db.stampDao().insert(StampEntity(StampKind.FIRST_TICKET, today.minusDays(5))) }
        val stamps = visit().state.value.stamps
        assertEquals(StampKind.entries.size, stamps.size)
        assertEquals(StampKind.FIRST_TICKET, stamps.first().kind)
        assertEquals(today.minusDays(5), stamps.first().earnedOn)
        assertTrue(stamps.drop(1).all { it.earnedOn == null })
    }

    @Test fun aNewLevelAndStampAreCelebratedOnceThenMarkedSeen() {
        handClosed(7)
        runBlocking { db.stampDao().insert(StampEntity(StampKind.FIRST_TICKET, today.minusDays(7))) }
        val vm = visit()
        settle("celebration") { vm.celebration.value != null }
        assertEquals(Milestone.NewLevel(2, "ماشي"), vm.celebration.value)

        vm.celebrated()
        settle("seen") { vm.celebration.value == null }
        assertEquals(
            "the stamp is marked with the level, not played after it",
            setOf("level:2", "stamp:FIRST_TICKET"), runBlocking { f.settings.current().milestonesSeen },
        )

        val again = visit()
        main.scheduler.runCurrent()
        assertNull(again.celebration.value)
        assertEquals(emptyList<Milestone>(), runBlocking { MilestoneRepository(db.dayTicketDao(), db.stampDao(), f.settings).unseen().first() })
    }
}
