package com.masroufi.app.feature.settings

import android.content.Context
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
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.repo.AlbumRepository
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.ui.AppLocales
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

/** Today is Tuesday 2026-11-17 09:00 Cairo. */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    private class FakeLocales : AppLocales {
        val tags = mutableListOf<String>()
        override fun set(languageTag: String) { tags += languageTag }
    }

    private val locales = FakeLocales()

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(context, TazkaraDatabase::class.java).allowMainThreadQueries().build()
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

    private fun visit(): SettingsViewModel {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    SettingsViewModel(
                        f.settings, f.onboarding,
                        AlbumRepository(db.dayTicketDao(), db.proverbDao(), db.stampDao(), clock, DebugEntitlements()),
                        f.plusRepo, f.billing, locales, clock,
                    )
                }
            },
        )[SettingsViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} }
        settle("settings") { !vm.state.value.loading }
        return vm
    }

    /** Reads through io while the test dispatcher keeps running (see ProfileViewModelTest.saved). */
    private fun saved(): Settings {
        var s: Settings? = null
        val read = io.launch { s = f.settings.current() }
        settle("settings read") { read.isCompleted }
        return s!!
    }

    @Test fun rowsShowTheStoredValuesAndTicketSoundsStartOff() {
        val ui = visit().state.value
        assertEquals(Payday.Day(25), ui.payday)
        assertEquals(LeftoverDestination.JAR, ui.leftover)
        assertEquals(0, ui.roundUpStep)
        assertTrue(ui.vibration)
        assertFalse(ui.ticketSounds)
        assertFalse(ui.hideAmounts)
        assertNull(ui.lastBackup)
    }

    @Test fun eachSwitchSavesAtOnce() {
        val vm = visit()
        vm.setVibration(false)
        settle("vibration") { !saved().vibration }
        vm.setTicketSounds(true)
        settle("sounds") { saved().ticketSounds }
        vm.setHideAmounts(true)
        settle("hide") { saved().hideAmounts }
        settle("ui") { vm.state.value.let { !it.vibration && it.ticketSounds && it.hideAmounts } }
    }

    @Test fun theChoicesSaveAndCloseTheirSheet() {
        val vm = visit()
        vm.open(SettingsSheet.Payday)
        settle("open") { vm.state.value.sheet == SettingsSheet.Payday }
        vm.setPayday(Payday.Day(10))
        settle("payday") { saved().payday == Payday.Day(10) }
        assertNull(vm.state.value.sheet)
        vm.setReminderHour(22)
        settle("hour") { saved().reminderHour == 22 }
        vm.setReminderHour(null)
        settle("no hour") { saved().reminderHour == null }
        vm.setLeftover(LeftoverDestination.EMERGENCY)
        settle("leftover") { saved().leftoverDefault == LeftoverDestination.EMERGENCY }
        vm.setRoundUpStep(10)
        settle("round") { saved().roundUpStep == 10 }
    }

    @Test fun theLanguageSwitchCallsTheLocaleApi() {
        val vm = visit()
        vm.setLanguage("en")
        vm.setLanguage("ar")
        assertEquals(listOf("en", "ar"), locales.tags)
    }
}
