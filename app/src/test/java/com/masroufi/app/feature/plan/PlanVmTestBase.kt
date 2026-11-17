package com.masroufi.app.feature.plan

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.reflect.KClass

/** An in-memory database, the repositories over it, a controllable main dispatcher and a Plus switch. */
abstract class PlanVmTestBase {
    @get:Rule val tmp = TemporaryFolder()

    protected val zone: ZoneId = ZoneId.of("Africa/Cairo")
    protected val clock = TestClock(at(LocalDate.of(2026, 11, 1)), zone)
    protected val plus = DebugEntitlements()
    protected val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    protected lateinit var db: TazkaraDatabase
    protected lateinit var f: RepoFixture

    protected fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()

    @Before fun openBase() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, plus)
    }

    @After fun closeBase() {
        store.clear()
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

    /** Builds a ViewModel and keeps its state hot, like a screen collecting it. */
    protected fun <T : ViewModel> make(cls: KClass<T>, state: (T) -> StateFlow<*>?, create: () -> T): T {
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { create() } })[cls]
        state(vm)?.let { s -> vm.viewModelScope.launch { s.collect {} } }
        return vm
    }

    protected fun settle(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (true) {
            main.scheduler.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            check(System.nanoTime() < end) { "timed out waiting for $what" }
            Thread.sleep(5)
        }
    }
}
