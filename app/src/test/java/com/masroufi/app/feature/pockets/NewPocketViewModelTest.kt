package com.masroufi.app.feature.pockets

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.CATEGORY_COLORS
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class NewPocketViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(ZonedDateTime.of(LocalDate.of(2026, 11, 17), LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: NewPocketViewModel

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
        vm = ViewModelProvider.create(store, viewModelFactory { initializer { NewPocketViewModel(f.pockets) } })[NewPocketViewModel::class]
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

    private val form get() = vm.form.value
    private fun pockets(): List<PocketEntity> = runBlocking { f.pockets.observePockets().first().map { it.pocket } }

    @Test fun aBlankNameCannotBeSaved() {
        assertFalse(form.canSave)
        vm.setName("   ")
        assertFalse(form.canSave)
        vm.save()
        settle("nothing happens") { true }
        assertTrue(pockets().isEmpty())
        assertFalse(form.saved)
    }

    @Test fun underTheTileStartsOutOfTheTicketUntilTheUserChoosesOtherwise() {
        assertTrue("a wallet counts by default", form.countsInTicket)
        vm.setKind(PocketKind.UNDER_TILE)
        assertFalse(form.countsInTicket)
        vm.setKind(PocketKind.BANK)
        assertTrue(form.countsInTicket)
        vm.setKind(PocketKind.UNDER_TILE)
        vm.setCountsInTicket(true) // the user's own choice sticks
        vm.setKind(PocketKind.CASH)
        vm.setKind(PocketKind.UNDER_TILE)
        assertTrue(form.countsInTicket)
    }

    @Test fun savesTheTypedPocketAndColoursCycle() {
        vm.setName("  فودافون كاش ")
        vm.setKind(PocketKind.BANK)
        "120.5".forEach { if (it == '.') vm.point() else vm.digit(it.digitToInt()) }
        vm.save()
        settle("saved") { form.saved }
        val first = pockets().single()
        assertEquals("فودافون كاش", first.name)
        assertEquals(PocketKind.BANK, first.kind)
        assertEquals(12_050L, first.openingBalance)
        assertTrue(first.countsInTicket)
        assertEquals("the first pocket takes the first data colour", CATEGORY_COLORS[0], first.color)

        runBlocking { f.pockets.addPocket("x", PocketKind.CASH, 0, true, 0) }
        val second = NewPocketViewModel(f.pockets)
        second.setName("تحت البلاطة")
        second.setKind(PocketKind.UNDER_TILE)
        second.save()
        settle("second saved") { second.form.value.saved }
        val stored = pockets().last()
        assertEquals(CATEGORY_COLORS[2], stored.color) // two pockets existed
        assertFalse(stored.countsInTicket)
    }
}
