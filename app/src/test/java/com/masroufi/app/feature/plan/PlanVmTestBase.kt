package com.masroufi.app.feature.plan

import com.masroufi.app.feature.roll.PendingDeletes
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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

    /** The app-wide waiting deletes; their 5 s runs on the test clock ([passFiveSeconds]). */
    protected val deletes by lazy { PendingDeletes(f.entries, CoroutineScope(main)) }

    /** The undo window of every waiting delete closes (virtual time); the real writes then run when [settle] pumps. */
    protected fun passFiveSeconds() {
        main.scheduler.advanceTimeBy(5_001)
        main.scheduler.runCurrent()
    }

    /** A repository read on the io scope while Main is pumped (never `runBlocking` on the test thread with a delete in flight). */
    protected fun <T> readOnIo(block: suspend () -> T): T {
        var result: Result<T>? = null
        val job = io.launch { result = runCatching { block() } }
        settle("a repository read") { job.isCompleted }
        return result!!.getOrThrow()
    }

    /** The user leaves every screen: their ViewModels are cleared. */
    protected fun leaveScreens() = store.clear()

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
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V = create() as V
            },
        )[cls.java]
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
