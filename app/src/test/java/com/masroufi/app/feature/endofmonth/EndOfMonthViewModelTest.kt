package com.masroufi.app.feature.endofmonth

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
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.StampKind
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.ui.AppRootViewModel
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
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The mock (LastDays.dc.html): today Monday 2026-11-16, payday Day(25), 790 EGP in the cash pocket and a 250 bill due
 * Nov 20: 540 over 9 days = 60 a day, the bill outside the pool.
 */
@RunWith(AndroidJUnit4::class)
class EndOfMonthViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov16 = LocalDate.of(2026, 11, 16)
    private val nov17 = nov16.plusDays(1)
    private val nov20 = LocalDate.of(2026, 11, 20)
    private val clock = TestClock(at(nov16), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: EndOfMonthViewModel
    private var cash = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
    }

    @After fun close() {
        store.clear()
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()

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

    /** DataStore and Room work runs on the io scope while Main is pumped by [settle]: never block Main on it. */
    private fun <T> onIo(block: suspend () -> T): T {
        val out = java.util.concurrent.atomic.AtomicReference<Result<T>?>(null)
        io.launch { out.set(runCatching { block() }) }
        settle("an io task") { out.get() != null }
        return out.get()!!.getOrThrow()
    }

    private fun bill(name: String, amount: Long, due: LocalDate) = runBlocking {
        db.recurringDao().insert(
            RecurringEntity(name = name, amount = amount, pocketId = cash, scheduleDay = due.dayOfMonth, nextDue = due, kind = RecurringKind.BILL),
        )
    }

    private fun category(name: String, essential: Boolean = false) =
        runBlocking { db.categoryDao().insert(CategoryEntity(name = name, icon = "coffee", color = 0, essential = essential)) }

    private fun spend(amount: Long, category: Long? = null) = runBlocking {
        f.entries.log(NewEntry(EntryKind.SPENT, amount, cash, categoryId = category, happenedAt = clock.millis()))
    }

    /** The cash pocket holds [opening]; the screen's view model is created and collected. */
    private fun screen(opening: Long = 79_000, withBill: Boolean = true): EndOfMonthViewModel {
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = opening, color = 0)) }
        if (withBill) bill("النت", 25_000, nov20)
        return openScreen()
    }

    private fun openScreen(): EndOfMonthViewModel {
        vm = ViewModelProvider.create(store, viewModelFactory { initializer { EndOfMonthViewModel(f.eom) } })[EndOfMonthViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} } // a screen collecting it
        settle("the first state") { !ui.loading }
        return vm
    }

    private val ui get() = vm.state.value

    @Test fun heroNumbersStripAndBillLine() {
        screen()
        val s = ui.state!!
        assertEquals("the pool, outside the bill", 54_000L, ui.heroAmount)
        assertEquals(9, s.daysLeft)
        assertEquals("a day", 6_000L, s.ticket)
        assertFalse(ui.shortfall)
        assertEquals("nine small tickets from today to the day before payday", (0L..8L).map { nov16.plusDays(it) }, s.strip.map { it.date })
        assertTrue("every one is 60", s.strip.all { it.amount == 6_000L })
        assertEquals("only the bill day is marked", listOf(nov20), s.strip.filter { it.bills.isNotEmpty() }.map { it.date })
        val b = ui.bills.single()
        assertEquals(Triple("النت", 25_000L, nov20), Triple(b.name, b.amount, b.date))
        assertEquals(LocalDate.of(2026, 11, 25), s.payday)
    }

    @Test fun moreBillsAreCountedAfterTheFirst() {
        screen()
        bill("الكهربا", 18_000, LocalDate.of(2026, 11, 23))
        settle("two bills") { ui.bills.size == 2 }
        assertEquals("earliest first", listOf("النت", "الكهربا"), ui.bills.map { it.name })
        assertEquals("both leave the pool: 790 - 250 - 180", 36_000L, ui.heroAmount)
    }

    @Test fun shortfallStatesWhatIsShort() {
        screen(opening = 20_000) // 200 against a 250 bill
        assertTrue(ui.shortfall)
        assertEquals("the hero shows how far short", 5_000L, ui.heroAmount)
        assertEquals(-5_000L, ui.state!!.pool)
        assertEquals("no small ticket without a pool", 0L, ui.state!!.ticket)
    }

    @Test fun togglesPauseAndUnpauseTheHabit() {
        val coffee = category("قهوة")
        val groceries = category("بقالة", essential = true)
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 79_000, color = 0)) }
        spend(3_100, coffee)
        spend(9_000, groceries)
        openScreen()
        val habit = ui.state!!.habits.single()
        assertEquals(Triple(coffee, 3_100L, false), Triple(habit.categoryId, habit.spent, habit.paused))
        vm.setPaused(coffee, true)
        settle("paused") { ui.state!!.habits.single().paused }
        vm.setPaused(coffee, false)
        settle("unpaused") { !ui.state!!.habits.single().paused }
    }

    @Test fun aHabitPausedBeforeSwitchingOnStaysPaused() {
        val coffee = category("قهوة")
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 79_000, color = 0)) }
        spend(3_100, coffee)
        openScreen()
        vm.setPaused(coffee, true)
        settle("paused") { ui.state!!.habits.single().paused }
        vm.toggleMode()
        settle("on") { ui.modeOn }
        assertTrue(ui.state!!.habits.single().paused)
    }

    @Test fun challengeLineIsComputedAndTheSecondNoSpendDayEarnsTheStamp() {
        screen()
        assertEquals("one more no-spend day: 60 / 8 days, to the pound", 700L, ui.state!!.boost)
        assertEquals(0, ui.challengeDone)
        vm.toggleMode()
        settle("on") { ui.modeOn }
        onIo { f.tickets.printToday(); f.tickets.closeDay(nov16, LeftoverDestination.TOMORROW) }
        settle("one day") { ui.challengeDone == 1 }
        assertFalse(ui.challengeEarned)
        clock.instant = at(nov17)
        onIo { f.tickets.printToday(); f.tickets.closeDay(nov17, LeftoverDestination.TOMORROW) }
        settle("two days") { ui.challengeEarned }
        assertEquals(2, ui.challengeDone)
        assertTrue(onIo { db.stampDao().kinds() }.contains(StampKind.TIGHT_FIST))
    }

    @Test fun rescueRowsAreHiddenAtZeroAndShowWhenThereIsMoney() {
        screen()
        assertFalse(ui.hasRescue)
        assertNull(ui.state!!.rescue.freeJar)
        runBlocking { db.jarDao().insert(JarMovementEntity(pot = Pot.EMERGENCY, amount = 60_000, source = JarSource.MANUAL, at = 0)) }
        settle("the emergency pot") { ui.hasRescue }
        val r = ui.state!!.rescue
        assertEquals(listOf(null, null, 60_000L), listOf(r.freeJar, r.owedToMe, r.emergency))
    }

    @Test fun theButtonSwitchesOnUntilPaydayAndOffAgain() {
        screen()
        assertFalse(ui.modeOn)
        vm.toggleMode()
        settle("on") { ui.modeOn }
        assertEquals("until payday", LocalDate.of(2026, 11, 25), ui.state!!.payday)
        assertTrue(onIo { f.settings.current().eomOn })
        vm.toggleMode()
        settle("off") { !ui.modeOn }
        assertFalse(onIo { f.settings.current().eomOn })
    }

    @Test fun plusIsHiddenWhileTheModeIsOn() {
        screen()
        val root = ViewModelProvider.create(store, viewModelFactory { initializer { AppRootViewModel(f.settings, f.eomMode) } })[AppRootViewModel::class]
        root.viewModelScope.launch { root.endOfMonth.collect {} }
        settle("the mode is off") { !root.endOfMonth.value }
        vm.toggleMode()
        settle("the mode is on") { root.endOfMonth.value }
        vm.toggleMode()
        settle("the mode is off again") { !root.endOfMonth.value }
    }
}
