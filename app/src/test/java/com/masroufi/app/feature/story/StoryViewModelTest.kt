package com.masroufi.app.feature.story

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.StoryPayload
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.ui.haptics.HapticLevel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/**
 * Main is a StandardTestDispatcher driven by hand (as in the Home tests): the 5 s segment runs on its virtual time, never
 * on a real sleep; Room and DataStore work on real threads while [settle] pumps Main.
 */
@RunWith(AndroidJUnit4::class)
class StoryViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 19)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private val haptics = mutableListOf<HapticLevel>()

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

    /** Moves virtual time on Main by [ms], then runs what is due at that instant. */
    private fun advance(ms: Long) {
        main.scheduler.advanceTimeBy(ms)
        main.scheduler.runCurrent()
    }

    private fun save(payload: StoryPayload): Long = runBlocking { db.storyDao().insert(entity(payload)) }

    /** A screen opening [id]: collects what the screen does. */
    private fun visit(id: Long): StoryViewModel {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { StoryViewModel(SavedStateHandle(mapOf("id" to id)), f.story, f.settings) } },
        )[StoryViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} }
        vm.viewModelScope.launch { vm.haptics.collect { haptics += it } }
        settle("story") { !vm.state.value.loading }
        return vm
    }

    @Test fun aFullStoryHasFiveSlidesAndAThinOneTwo() {
        val full = visit(save(fullPayload()))
        assertEquals(StorySlide.entries, full.state.value.slides)
        assertEquals(StorySlide.OPENING, full.state.value.slide)
        val thin = visit(runBlocking { db.storyDao().insert(entity(thinPayload(), start = novStart, end = novStart.plusMonths(1))) })
        assertEquals(listOf(StorySlide.OPENING, StorySlide.JAR), thin.state.value.slides)
    }

    @Test fun aMissingStoryIsSaidSo() {
        val vm = visit(99)
        assertTrue(vm.state.value.missing)
        assertNull(vm.state.value.slide)
    }

    @Test fun theSegmentFillsOver5sAndMovesToTheNextSlide() {
        val vm = visit(save(fullPayload()))
        assertEquals(0f, vm.progress.value, 0f)
        advance(2_500)
        assertEquals(0.5f, vm.progress.value, 0.001f)
        assertEquals(0, vm.state.value.index)
        advance(2_500)
        settle("second slide") { vm.state.value.index == 1 }
        assertEquals(0f, vm.progress.value, 0.001f)
        assertEquals(listOf(HapticLevel.Tick), haptics)
    }

    @Test fun holdingPausesTheSegment() {
        val vm = visit(save(fullPayload()))
        advance(1_000)
        vm.hold(true)
        assertTrue(vm.held.value)
        advance(20_000)
        assertEquals(0.2f, vm.progress.value, 0.001f)
        assertEquals(0, vm.state.value.index)
        vm.hold(false)
        advance(4_000)
        settle("second slide after the hold") { vm.state.value.index == 1 }
    }

    @Test fun theLastSlideStaysFull() {
        val vm = visit(save(fullPayload()))
        advance(60_000)
        settle("last slide") { vm.state.value.index == 4 }
        assertEquals(1f, vm.progress.value, 0f)
        assertTrue(vm.state.value.isLast)
        vm.next()
        settle("still last") { vm.state.value.index == 4 }
        advance(30_000)
        assertEquals(4, vm.state.value.index)
    }

    @Test fun nextAndBackMoveOneSlideWithATick() {
        val vm = visit(save(fullPayload()))
        vm.next()
        vm.next()
        settle("third slide") { vm.state.value.index == 2 }
        vm.back()
        settle("back to the second") { vm.state.value.index == 1 }
        assertEquals(0f, vm.progress.value, 0f)
        assertEquals(listOf(HapticLevel.Tick, HapticLevel.Tick, HapticLevel.Tick), haptics)
        vm.back()
        vm.back() // on the first slide it only starts again
        settle("first slide") { vm.state.value.index == 0 }
        assertEquals(0, vm.state.value.index)
    }

    @Test fun theMilestoneBuzzesOnceWhenTheBestMonthLineShows() {
        val vm = visit(save(fullPayload(isBest = true)))
        vm.next()
        settle("grid slide") { vm.state.value.slide == StorySlide.GRID }
        assertEquals(listOf(HapticLevel.Milestone), haptics)
        vm.back()
        vm.next()
        settle("grid slide again") { haptics.size == 3 }
        assertEquals(listOf(HapticLevel.Milestone, HapticLevel.Tick, HapticLevel.Tick), haptics)
    }

    @Test fun noMilestoneWhenItIsNotTheBestMonth() {
        val vm = visit(save(fullPayload(isBest = false)))
        vm.next()
        settle("grid slide") { vm.state.value.slide == StorySlide.GRID }
        assertEquals(listOf(HapticLevel.Tick), haptics)
    }

    @Test fun theShareSwitchIsOnByDefaultAndRemembered() {
        val id = save(fullPayload())
        val vm = visit(id)
        assertTrue(vm.state.value.hideAmounts)
        vm.setHideAmounts(false)
        settle("switch off") { !vm.state.value.hideAmounts }
        // A later visit reads the saved choice.
        val again = visit(id)
        assertFalse(again.state.value.hideAmounts)
    }

    @Test fun closingMarksAFirstViewAndOnlyOnce() {
        val id = save(fullPayload())
        val vm = visit(id)
        val first = io.launch { vm.markShown() }
        settle("marked") { first.isCompleted }
        var at: Long? = null
        val read = io.launch { at = f.story.get(id)?.shownAt }
        settle("read") { read.isCompleted }
        assertNotNull(at)
        // A replay of a seen story changes nothing.
        clock.instant = clock.instant.plusSeconds(3_600)
        val second = io.launch { vm.markShown() }
        settle("marked again") { second.isCompleted }
        var again: Long? = null
        val read2 = io.launch { again = f.story.get(id)?.shownAt }
        settle("read again") { read2.isCompleted }
        assertEquals(at, again)
    }
}
