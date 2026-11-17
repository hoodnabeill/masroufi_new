package com.masroufi.app.feature.plan

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
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class JarViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(LocalDate.of(2026, 11, 1)), zone)
    private val plus = DebugEntitlements()
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: JarViewModel

    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, plus)
        vm = ViewModelProvider.create(store, viewModelFactory { initializer { JarViewModel(f.jar, f.seasons, f.settings, plus, clock) } })[JarViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} } // a screen collecting it
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

    private val state get() = vm.state.value
    private val total get() = state.jar?.total

    @Test fun anUntouchedJarIsEmpty() {
        settle("loaded") { state.jar != null }
        assertTrue(state.empty)
        runBlocking { f.jar.deposit(1_000) }
        settle("not empty") { !state.empty }
    }

    @Test fun plusGatesTheGoldAndRoundUpCards() {
        settle("loaded") { state.jar != null }
        assertFalse(state.isPlus)
        plus.setDebugOverride(true)
        settle("plus") { state.isPlus }
    }

    @Test fun goldNeedsAPriceThenShowsGramsAndWhatIsLeft() {
        runBlocking { f.jar.deposit(184_000) }
        settle("jar") { total == 184_000L }
        assertNull("no price: the prompt row", state.jar?.gold)

        vm.setGoldPrice(500_000)
        settle("gold") { state.jar?.gold != null }
        val gold = checkNotNull(state.jar?.gold)
        assertEquals(36L, gold.gramsHundredths)
        assertEquals(316_000L, gold.leftToFirstGram)
    }

    @Test fun roundUpStepIsWrittenToSettings() {
        settle("loaded") { state.jar != null }
        vm.setRoundUpStep(10)
        settle("step") { state.roundUpStep == 10 }
        vm.setRoundUpStep(0)
        settle("off") { state.roundUpStep == 0 }
    }

    @Test fun withdrawIsCappedAtTheFreeTotal() {
        val goal = runBlocking { f.jar.createGoal("phone", 100_000) }
        runBlocking { f.jar.deposit(10_000) }
        vm.earmark(goal, 6_000)
        settle("earmarked") { state.jar?.free == 4_000L }

        vm.withdraw(5_000) // above the free 4,000: nothing moves
        main.scheduler.advanceUntilIdle()
        settle("still there") { true }
        assertEquals(10_000L, total)
        assertEquals(4_000L, state.jar?.free)

        vm.withdraw(4_000)
        settle("withdrawn") { total == 6_000L }
        assertEquals(0L, state.jar?.free)
    }

    @Test fun depositEarmarkAndReleaseReachTheRepository() {
        val goal = runBlocking { f.jar.createGoal("phone", 100_000) }
        vm.deposit(20_000)
        settle("deposited") { total == 20_000L }
        vm.earmark(goal, 7_000)
        settle("earmarked") { state.goals.firstOrNull()?.view?.earmarked == 7_000L }
        vm.release(goal, 2_000)
        settle("released") { state.goals.firstOrNull()?.view?.earmarked == 5_000L }
        assertEquals(20_000L, total)
    }

    @Test fun theEtaAppearsOnlyAfterThirtyDaysOfHistory() {
        val goal = runBlocking { f.jar.createGoal("phone", 100_000) }
        runBlocking { f.jar.deposit(10_000) }
        settle("deposit") { total == 10_000L }
        assertNull("day 0", state.goals.single { it.view.goal.id == goal }.eta)

        clock.instant = at(LocalDate.of(2026, 11, 20))
        runBlocking { f.jar.deposit(1_000) }
        settle("day 19") { total == 11_000L }
        assertNull("under 30 days", state.goals.single().eta)

        val later = LocalDate.of(2026, 12, 11) // 40 days in
        clock.instant = at(later)
        runBlocking { f.jar.deposit(9_000) }
        settle("day 40") { total == 20_000L }
        // 20,000 in 40 days = 500 a day; 100,000 left -> 200 days.
        assertEquals(later.plusDays(200), state.goals.single().eta)
        assertNotNull(state.goals.single().eta)
    }
}
