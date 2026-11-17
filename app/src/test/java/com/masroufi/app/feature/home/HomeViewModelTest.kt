package com.masroufi.app.feature.home

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.add.FeedbackBus
import com.masroufi.app.feature.add.Pending
import com.masroufi.app.feature.add.PendingPunches
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
import org.junit.Assert.assertNull
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

/**
 * Payday Day(25), one cash pocket with 3,900 EGP: on Thursday 2026-11-12 (13 days left) the ticket is 300.
 *
 * Main is a StandardTestDispatcher driven by hand. runTest is not used on purpose: it auto-advances virtual time
 * whenever its scheduler idles, which would fire the midnight delay while a test waits for Room's real threads.
 */
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val nov13 = nov12.plusDays(1)
    private val clock = TestClock(at(nov12, 9), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private val pending = PendingPunches()
    private val process = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: HomeViewModel
    private var cash = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
        process.registry.currentState = Lifecycle.State.CREATED
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0)) }
    }

    @After fun close() {
        store.clear()
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()

    private fun newVm(): HomeViewModel {
        val factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    f.home, f.tickets, f.entries, db.categoryDao(), db.pocketDao(), pending, process.lifecycle, clock, FeedbackBus(), f.settings, f.onboarding,
                )
            }
        }
        vm = ViewModelProvider.create(store, factory)[HomeViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} } // a screen collecting it
        return vm
    }

    /** Runs Main's due tasks until [cond] holds; Room and DataStore work on real threads meanwhile. */
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

    private val ready get() = vm.state.value as? HomeUiState.Ready
    private fun row(date: LocalDate) = runBlocking { db.dayTicketDao().forDate(date) }
    private fun entries(): List<EntryEntity> = runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }
    private fun category(name: String) = runBlocking { db.categoryDao().insert(CategoryEntity(name = name, icon = "coffee", color = 0)) }
    private fun spend(amount: Long) = runBlocking {
        f.entries.log(NewEntry(EntryKind.SPENT, amount, cash, happenedAt = clock.millis()))
    }

    @Test fun printsOnStart() {
        newVm()
        settle("today's ticket") { ready?.today == nov12 }
        clock.instant = at(nov13, 9)
        main.scheduler.runCurrent()
        assertNull("nothing re-prints before the app starts again", row(nov13))
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        settle("the new day's ticket after ON_START") { row(nov13) != null && ready?.today == nov13 }
    }

    @Test fun midnightReprintsTicket() {
        clock.instant = ZonedDateTime.of(nov12, LocalTime.of(23, 59), zone).toInstant()
        newVm()
        settle("today's ticket") { ready?.today == nov12 }
        clock.instant = at(nov13, 0).plusSeconds(5)
        main.scheduler.advanceTimeBy(30_000)
        main.scheduler.runCurrent()
        assertNull("the timer waits for midnight + 1 s (61 s after 23:59)", row(nov13))
        main.scheduler.advanceTimeBy(32_000)
        settle("the ticket printed at midnight") { row(nov13) != null && ready?.today == nov13 }
    }

    @Test fun repeatChipLogsOnce() {
        val coffee = category("قهوة")
        newVm()
        settle("ready") { ready != null }
        val chip = RepeatChip(coffee, "قهوة", 2_500, cash)
        vm.punchRepeat(chip)
        vm.punchRepeat(chip) // a double tap inside 400 ms
        settle("one stored hole") { ready?.ticket?.holes == 1 && pending.pending.value.isEmpty() }
        val e = entries().single()
        assertEquals(listOf(EntryKind.SPENT, 2_500L, coffee, cash), listOf(e.kind, e.amount, e.categoryId, e.pocketId))
        clock.instant = clock.instant.plusMillis(500)
        vm.punchRepeat(chip)
        settle("a second hole after 400 ms") { ready?.ticket?.holes == 2 && pending.pending.value.isEmpty() }
        assertEquals(2, entries().size)
    }

    @Test fun quickLogParses() {
        val coffee = category("قهوة")
        newVm()
        settle("ready") { ready != null }
        val prefills = mutableListOf<AddPrefill>()
        vm.viewModelScope.launch { vm.prefill.collect { prefills += it } }

        vm.quickText = "25 قهوة"
        vm.submitQuickLog()
        settle("logged straight away") { entries().size == 1 && vm.quickText.isEmpty() }
        val e = entries().single()
        assertEquals(listOf(2_500L, coffee, cash), listOf(e.amount, e.categoryId, e.pocketId))
        assertEquals("no sheet for a full parse", emptyList<AddPrefill>(), prefills)

        clock.instant = clock.instant.plusMillis(500)
        vm.quickText = "40 حاجة غريبة"
        vm.submitQuickLog()
        settle("handed to the sheet") { prefills.isNotEmpty() }
        assertEquals(AddPrefill(4_000, null, "40 حاجة غريبة"), prefills.single())
        assertEquals("no category: nothing logged", 1, entries().size)
        assertEquals("", vm.quickText)
    }

    @Test fun overTicketSubLine() {
        newVm()
        settle("ready") { ready?.ticket != null }
        assertEquals(30_000L, ready?.ticket?.amount)
        spend(33_500)
        settle("over the ticket") { ready?.ticket?.subLine is SubLine.Over }
        assertEquals(SubLine.Over(3_500, spread = true), ready?.ticket?.subLine)
        assertEquals(-3_500L, ready?.ticket?.left)
    }

    @Test fun lastDayOmitsSpreadSentence() {
        clock.instant = at(LocalDate.of(2026, 11, 24), 9)
        newVm()
        settle("ready") { ready?.ticket != null }
        assertEquals("one day left: the whole balance", 390_000L, ready?.ticket?.amount)
        spend(395_000)
        settle("over the ticket") { ready?.ticket?.subLine is SubLine.Over }
        assertEquals(SubLine.Over(5_000, spread = false), ready?.ticket?.subLine)
    }

    @Test fun pendingAddsToTicket() {
        newVm()
        settle("ready") { ready?.ticket != null }
        val id = pending.add(Pending(0, 5_000))
        settle("the pending punch on the ticket") { ready?.ticket?.holes == 1 }
        assertEquals(5_000L, ready?.ticket?.spent)
        assertEquals(SubLine.Spent(30_000, 5_000, 1), ready?.ticket?.subLine)
        assertEquals("spendable already without it", 385_000L, ready?.cycle?.spendable)
        pending.remove(id)
        settle("back to none") { ready?.ticket?.holes == 0 }
        assertEquals(SubLine.None, ready?.ticket?.subLine)
    }

    @Test fun tomorrowPreviewAfterClose() {
        newVm()
        settle("ready") { ready?.ticket != null }
        val closed = runBlocking { f.tickets.closeDay(nov12, LeftoverDestination.TOMORROW) }
        settle("closed") { ready?.ticket == null }
        // 3,900 with the 300 leftover carried: (3,900 - 300) / 12 days + 300.
        assertEquals(TomorrowUi(60_000, closed.proverbNumber), ready?.tomorrow)
    }
}
