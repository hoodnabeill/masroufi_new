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
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.NotifKind
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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

@RunWith(AndroidJUnit4::class)
class NotificationSettingsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(ZonedDateTime.of(LocalDate.of(2026, 11, 17), LocalTime.of(9, 0), zone).toInstant(), zone)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

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

    private fun visit(): NotificationSettingsViewModel {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { NotificationSettingsViewModel(f.settings) } })[NotificationSettingsViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} }
        settle("notification settings") { !vm.state.value.loading }
        return vm
    }

    /** Reads through io while the test dispatcher keeps running (see ProfileViewModelTest.saved). */
    private fun saved(): Settings {
        var s: Settings? = null
        val read = io.launch { s = f.settings.current() }
        settle("settings read") { read.isCompleted }
        return s!!
    }

    @Test fun defaultsAreEveningOnlyWithAnHourMorningOffTheRestOn() {
        val ui = visit().state.value
        assertFalse(ui.on.getValue(NotifKind.EVENING)) // no reminder hour yet
        assertFalse(ui.on.getValue(NotifKind.MORNING))
        NotifKind.entries.filter { it != NotifKind.EVENING && it != NotifKind.MORNING }.forEach { assertTrue("$it", ui.on.getValue(it)) }
    }

    @Test fun aSwitchSavesAtOnceAndSurvivesAReopen() {
        val vm = visit()
        vm.setKind(NotifKind.WISH, false)
        vm.setKind(NotifKind.MORNING, true)
        settle("switches saved") { !saved().notifEnabled(NotifKind.WISH) && saved().notifEnabled(NotifKind.MORNING) }
        val again = visit().state.value
        assertFalse(again.on.getValue(NotifKind.WISH))
        assertTrue(again.on.getValue(NotifKind.MORNING))
        vm.setKind(NotifKind.WISH, true)
        settle("switch back on") { saved().notifEnabled(NotifKind.WISH) }
    }

    @Test fun theEveningSwitchIsTheReminderHour() {
        val vm = visit()
        vm.setKind(NotifKind.EVENING, true)
        settle("evening on") { saved().reminderHour == 21 && vm.state.value.on.getValue(NotifKind.EVENING) }
        vm.setKind(NotifKind.EVENING, false)
        settle("evening off") { saved().reminderHour == null && !vm.state.value.on.getValue(NotifKind.EVENING) }
        vm.setReminderHour(22)
        settle("hour saved") { saved().reminderHour == 22 && vm.state.value.on.getValue(NotifKind.EVENING) }
        assertFalse(vm.state.value.sheet)
    }

    @Test fun aRefusedPermissionLeavesTheSwitchOffAndSaysSo() {
        val vm = visit()
        vm.permissionResult(NotifKind.MORNING, false)
        settle("denied shown") { vm.state.value.denied }
        assertFalse(vm.state.value.on.getValue(NotifKind.MORNING))
        assertFalse(saved().notifEnabled(NotifKind.MORNING))
        assertNull(saved().reminderHour)
    }

    @Test fun aGrantedPermissionTurnsTheAskingSwitchOn() {
        val vm = visit()
        vm.permissionResult(NotifKind.MORNING, false)
        settle("denied shown") { vm.state.value.denied }
        vm.permissionResult(NotifKind.MORNING, true)
        settle("on after grant") { saved().notifEnabled(NotifKind.MORNING) && !vm.state.value.denied && vm.state.value.on[NotifKind.MORNING] == true }
    }
}
