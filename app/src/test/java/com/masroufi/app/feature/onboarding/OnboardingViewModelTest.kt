package com.masroufi.app.feature.onboarding

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.FixedCost
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
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

private const val RENT = "الإيجار"

/**
 * Payday Day(25), today Thursday 2026-11-12 09:00 (13 days left). Main is a StandardTestDispatcher driven by hand, as in
 * HomeViewModelTest (Room and DataStore run on real threads).
 */
@RunWith(AndroidJUnit4::class)
class OnboardingViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val clock = TestClock(ZonedDateTime.of(nov12, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: OnboardingViewModel

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

    private fun newVm(): OnboardingViewModel {
        val factory = viewModelFactory { initializer { OnboardingViewModel(f.onboarding, f.settings, clock) } }
        vm = ViewModelProvider.create(store, factory)[OnboardingViewModel::class]
        settle("the saved step is read") { ui.loaded }
        return vm
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

    private val ui get() = vm.state.value
    private fun saved() = runBlocking { f.settings.current() }
    private fun type(digits: String) = digits.forEach { vm.digit(it.digitToInt()) }

    @Test fun flowSavesEachAnswer() {
        newVm()
        vm.next()
        settle("payday step saved") { ui.step == STEP_PAYDAY && saved().onboardingStep == STEP_PAYDAY }

        vm.pickPayday(Payday.Day(15))
        assertEquals("nothing is saved until the user moves on", Payday.Day(25), saved().payday)
        vm.next()
        settle("money step saved") { ui.step == STEP_MONEY && saved().onboardingStep == STEP_MONEY }
        assertEquals(Payday.Day(15), saved().payday)

        vm.next()
        main.scheduler.runCurrent()
        assertEquals("an empty amount does not move on", STEP_MONEY, ui.step)
        type("5870")
        vm.next()
        settle("fixed step saved") { ui.step == STEP_FIXED && saved().onboardingStep == STEP_FIXED }
        val cash = runBlocking { db.pocketDao().firstCash()!!.id }
        assertEquals(587_000L, runBlocking { db.pocketDao().balanceOf(cash) })

        vm.tapFixed(RENT)
        type("2500")
        vm.confirmFixed()
        vm.printTicket()
        settle("reveal saved") { ui.step == STEP_REVEAL && ui.reveal != null && saved().onboardingStep == STEP_REVEAL }
        val items = runBlocking { db.recurringDao().all().first() }
        assertEquals(listOf(RENT to 250_000L), items.map { it.name to it.amount })
        assertEquals("payday 15 on 11-12: 3 days to 11-15", 3, ui.reveal!!.days)
        assertEquals(15, ui.reveal!!.paydayDay)
        assertFalse(ui.reveal!!.endOfMonth)
    }

    @Test fun resumeAtSavedStep() {
        runBlocking {
            f.settings.setPayday(Payday.Day(5))
            f.onboarding.setMoney(587_000)
            f.onboarding.setFixedCosts(listOf(FixedCost(RENT, 250_000)))
            f.onboarding.setStep(STEP_FIXED)
        }
        newVm()
        assertEquals(STEP_FIXED, ui.step)
        assertEquals(Payday.Day(5), ui.payday)
        assertEquals(587_000L, ui.money.toPiastres())
        assertEquals(mapOf(RENT to 250_000L), ui.fixed)
    }

    @Test fun paydayEchoDays() {
        newVm()
        assertEquals("payday 25 on 11-12", 13, ui.daysLeft)
        vm.pickPayday(Payday.Day(5))
        assertEquals(23, ui.daysLeft)
        vm.pickPayday(Payday.LastDay)
        assertEquals(18, ui.daysLeft)
        vm.pickPayday(Payday.NotFixed)
        assertEquals("a 30-day window from today", 30, ui.daysLeft)
    }

    @Test fun fixedChipToggle() {
        runBlocking { f.settings.setOnboardingStep(STEP_FIXED) }
        newVm()
        vm.tapFixed(RENT)
        assertEquals(RENT, ui.openFixed)
        vm.tapFixed("النت")
        assertEquals("one inline keypad at a time", "النت", ui.openFixed)
        vm.confirmFixed()
        assertTrue("no amount, nothing stored", ui.fixed.isEmpty())
        type("250")
        vm.confirmFixed()
        assertEquals(mapOf("النت" to 25_000L), ui.fixed)
        assertNull(ui.openFixed)
        assertEquals(25_000L, ui.fixedTotal)
        vm.tapFixed("النت")
        assertTrue("tapping a filled chip removes it", ui.fixed.isEmpty())
    }

    @Test fun revealOpensAddSheet() {
        runBlocking {
            f.onboarding.setMoney(587_000)
            f.onboarding.setFixedCosts(listOf(FixedCost(RENT, 275_000)))
            f.onboarding.setStep(STEP_REVEAL)
        }
        newVm()
        settle("the reveal") { ui.reveal != null }
        assertEquals(24_000L, ui.reveal!!.amount)
        val events = mutableListOf<OnboardingEvent>()
        vm.viewModelScope.launch { vm.events.collect { events += it } }
        vm.pickReminder(null)
        vm.finish()
        settle("the finished event") { events.isNotEmpty() }
        assertEquals(listOf<OnboardingEvent>(OnboardingEvent.Finished), events)
        val s = saved()
        assertTrue(s.onboarded)
        assertNull("no reminder", s.reminderHour)
        assertNotNull("the first ticket is printed", runBlocking { db.dayTicketDao().forDate(nov12) })
    }

    @Test fun revealShortfall() {
        runBlocking {
            f.onboarding.setMoney(200_000)
            f.onboarding.setFixedCosts(listOf(FixedCost(RENT, 240_000)))
            f.onboarding.setStep(STEP_REVEAL)
        }
        newVm()
        settle("the reveal") { ui.reveal != null }
        val r = ui.reveal!!
        assertEquals(listOf(true, 0L, 40_000L), listOf(r.endOfMonth, r.amount, r.shortfall))
    }

    @Test fun nearPaydayAsksFirst() {
        runBlocking {
            f.settings.setPayday(Payday.Day(13)) // tomorrow
            f.onboarding.setMoney(587_000)
            f.onboarding.setStep(STEP_REVEAL)
        }
        newVm()
        settle("the question") { ui.askNear }
        assertNull(ui.reveal)
        vm.answerNear(true)
        settle("the reveal from the next cycle") { ui.reveal != null }
        assertFalse(ui.askNear)
        assertEquals("payday tomorrow, counted from after it: the next cycle is 30 days", 30, ui.reveal!!.days)
    }
}
