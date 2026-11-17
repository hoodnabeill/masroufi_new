package com.masroufi.app.feature.roll

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.RollFilter
import com.masroufi.app.data.repo.RollSummary
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketStatus
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today 2026-11-17 09:00 Cairo. Main is driven by hand as in HomeViewModelTest (Room runs on real threads). */
@RunWith(AndroidJUnit4::class)
class RollViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov = YearMonth.of(2026, 11)
    private val today = nov.atDay(17)
    private val clock = TestClock(at(today, 9), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var deletes: PendingDeletes
    private lateinit var vm: RollViewModel
    private var cash = 0L
    private var coffee = 0L
    private var foul = 0L
    private var tea = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
        deletes = PendingDeletes(f.entries, CoroutineScope(main)) // its 5 s runs on the test clock
        runBlocking {
            cash = db.pocketDao().insert(PocketEntity(name = "الكاش", kind = PocketKind.CASH, openingBalance = 390_000, color = 0))
            coffee = db.categoryDao().insert(CategoryEntity(name = "قهوة", icon = "coffee", color = 0))
            log(EntryKind.RECEIVED, 900_000, nov.atDay(3), 9)
            tea = log(EntryKind.SPENT, 4_000, nov.atDay(5), 10, coffee, "شاي بالنعناع")
            foul = log(EntryKind.SPENT, 3_000, today, 8, null, "فول")
            fun ticket(day: Int, spent: Long, count: Int, status: TicketStatus) = DayTicketEntity(
                date = nov.atDay(day), amount = 30_000, base = 30_000, spent = spent, entryCount = count, status = status,
            )
            db.dayTicketDao().insert(ticket(5, 4_000, 1, TicketStatus.CLOSED_AUTO))
            db.dayTicketDao().insert(ticket(6, 40_000, 2, TicketStatus.CLOSED_AUTO))
            db.dayTicketDao().insert(ticket(17, 3_000, 1, TicketStatus.OPEN))
        }
        vm = ViewModelProvider.create(store, viewModelFactory { initializer { RollViewModel(f.roll, db.categoryDao(), deletes, clock) } })[RollViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} } // a screen collecting it
    }

    @After fun close() {
        store.clear()
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()

    private suspend fun log(kind: EntryKind, amount: Long, date: LocalDate, hour: Int, category: Long? = null, note: String? = null) =
        f.entries.log(NewEntry(kind, amount, cash, categoryId = category, note = note, happenedAt = at(date, hour).toEpochMilli()))

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

    private val state get() = vm.state.value
    private fun stored(id: Long) = runBlocking { db.entryDao().byId(id) }
    private fun todaysIds() = state.days.firstOrNull { it.date == today }?.entries?.map { it.id }.orEmpty()
    private fun loaded() = settle("the month") { !state.loading && state.days.size == 4 }

    @Test fun summaryNumbers() {
        loaded()
        assertEquals(RollSummary(spent = 7_000, received = 900_000, ticketsUnder = 1, ticketsClosed = 2), state.summary)
        assertEquals(listOf(17, 6, 5, 3).map(nov::atDay), state.days.map { it.date })
        assertEquals("this month is always offered", listOf(nov), state.months)
    }

    @Test fun filterSwitching() {
        loaded()
        vm.setFilter(RollFilter.INCOME)
        settle("income only") { state.filter == RollFilter.INCOME && state.days.map { it.date } == listOf(nov.atDay(3)) }
        vm.setFilter(RollFilter.SPENT)
        settle("spent only") { state.days.map { it.date } == listOf(17, 5).map(nov::atDay) }
        vm.setFilter(RollFilter.RECURRING)
        settle("nothing recurring") { state.filter == RollFilter.RECURRING && state.days.isEmpty() && !state.loading }
        vm.setFilter(RollFilter.ALL)
        settle("all again") { state.days.size == 4 }
    }

    @Test fun searchResultsAndNoMatch() {
        loaded()
        vm.openSearch()
        vm.setQuery("قهوه") // the category name, with the ة typed as ه
        settle("by category") { state.results?.map { it.id } == listOf(tea) }
        vm.setQuery("فول")
        settle("by note") { state.results?.map { it.id } == listOf(foul) }
        vm.setQuery("مفيش زيها")
        settle("no match") { state.results?.isEmpty() == true && !state.loading }
        vm.closeSearch()
        settle("roll again") { !state.searching && state.results == null && state.days.isNotEmpty() }
    }

    @Test fun pendingDeleteHidesThenDeletesAfterFiveSeconds() {
        loaded()
        deletes.schedule(PendingDelete(foul, 3_000, today))
        settle("hidden with the undo bar") { foul !in todaysIds() && state.undoVisible }
        assertNotNull("nothing written yet", stored(foul))
        assertEquals("the numbers leave it out", 4_000L, state.summary.spent)
        main.scheduler.advanceTimeBy(4_900)
        main.scheduler.runCurrent()
        assertNotNull("still inside the 5 s", stored(foul))
        main.scheduler.advanceTimeBy(200)
        settle("really deleted") { stored(foul) == null }
        main.scheduler.advanceTimeBy(400)
        settle("bar gone") { !state.undoVisible && foul !in todaysIds() }
    }

    @Test fun undoRestoresTheRowAndNeverWrites() {
        loaded()
        deletes.schedule(PendingDelete(foul, 3_000, today))
        settle("hidden") { foul !in todaysIds() }
        vm.undo()
        settle("back") { foul in todaysIds() && !state.undoVisible }
        assertEquals(7_000L, state.summary.spent)
        main.scheduler.advanceTimeBy(10_000)
        main.scheduler.runCurrent()
        assertNotNull("undone: the timer never deletes", stored(foul))
        assertTrue(deletes.pending.value.isEmpty())
    }

    @Test fun leavingTheScreenWritesWaitingDeletes() {
        loaded()
        deletes.schedule(PendingDelete(foul, 3_000, today))
        assertTrue(vm.flush())
        settle("written at once") { stored(foul) == null }
        assertFalse("nothing left to flush", vm.flush())
        assertNull(stored(foul))
    }
}
