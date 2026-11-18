package com.masroufi.app.feature.story

import android.os.Looper
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
import com.masroufi.app.data.repo.TestClock
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

/** The album's "الحكايات" list. */
@RunWith(AndroidJUnit4::class)
class StoryListViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(ZonedDateTime.of(LocalDate.of(2026, 11, 19), LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements(false))
    }

    @After fun close() {
        store.clear()
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

    private fun visit(): StoryListViewModel {
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { StoryListViewModel(f.story) } })[StoryListViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} }
        settle("list") { !vm.state.value.loading }
        return vm
    }

    @Test fun noStoriesIsEmpty() {
        val vm = visit()
        assertTrue(vm.state.value.empty)
    }

    @Test fun monthsAreListedNewestFirstWithTheirCountsAndTheNewOne() {
        runBlocking {
            db.storyDao().insert(entity(fullPayload(), start = sepStart, end = octStart, shownAt = 1_000))
            db.storyDao().insert(entity(thinPayload(), start = octStart, end = novStart))
        }
        val vm = visit()
        val rows = vm.state.value.rows
        assertEquals(listOf(octStart, sepStart), rows.map { it.cycleStart })
        assertEquals(listOf(3 to 31, 26 to 31), rows.map { it.torn to it.cycleDays })
        assertEquals(listOf(true, false), rows.map { it.isNew })
    }

    @Test fun aRowOpensTheSavedStoryNotARecalculation() {
        val id = runBlocking { db.storyDao().insert(entity(fullPayload(), start = sepStart, end = octStart, shownAt = 1_000)) }
        val vm = visit()
        assertEquals(id, vm.state.value.rows.single().id)
        var opened: com.masroufi.app.data.repo.MonthStory? = null
        val read = io.launch { opened = f.story.get(id) }
        settle("story") { read.isCompleted }
        assertEquals(fullPayload(), opened!!.payload)
        assertEquals(26, opened!!.payload.torn)
    }
}
