package com.masroufi.app.feature.pockets

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
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
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

/** Today 2026-11-17 09:00 Cairo. Cash 1,360, wallet 1,150, bank 1,900, under the tile 2,000. Main is driven by hand. */
@RunWith(AndroidJUnit4::class)
class PocketsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: PocketsViewModel
    private var cash = 0L
    private var wallet = 0L
    private var bank = 0L
    private var tile = 0L
    private var coffee = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
        runBlocking {
            fun pocket(name: String, kind: PocketKind, opening: Long, counts: Boolean = true) =
                PocketEntity(name = name, kind = kind, openingBalance = opening, color = 0xFFFFC72C.toInt(), countsInTicket = counts)
            cash = db.pocketDao().insert(pocket("الكاش", PocketKind.CASH, 136_000))
            wallet = db.pocketDao().insert(pocket("فودافون كاش", PocketKind.MOBILE_WALLET, 115_000))
            bank = db.pocketDao().insert(pocket("حساب البنك", PocketKind.BANK, 190_000))
            tile = db.pocketDao().insert(pocket("تحت البلاطة", PocketKind.UNDER_TILE, 200_000, counts = false))
            coffee = db.categoryDao().insert(CategoryEntity(name = "قهوة", icon = "coffee", color = 0))
        }
        vm = ViewModelProvider.create(store, viewModelFactory { initializer { PocketsViewModel(f.pockets, db.categoryDao(), clock) } })[PocketsViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} } // a screen collecting it
        settle("pockets") { !state.loading }
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
    private fun balance(id: Long) = runBlocking { db.pocketDao().balanceOf(id) }
    private fun entryCount() = runBlocking { db.entryDao().countFlow().first() }

    @Test fun totalCountsEveryEgpPocketAndRowsSplitCashFromTheRest() {
        assertEquals(136_000L + 115_000 + 190_000 + 200_000, state.total)
        assertEquals(cash, state.cash?.view?.pocket?.id)
        assertEquals(listOf(wallet, bank, tile), state.others.map { it.view.pocket.id })
    }

    @Test fun counterStartsFromTheBalanceAndMakesTheSameTotal() {
        assertEquals(136_000L, state.counter.total)
        assertEquals(Triple(6, 1, 1), Triple(state.counter.n200, state.counter.n100, state.counter.n50))
        assertEquals(1_000L, state.counter.change.toPiastres())
    }

    @Test fun counterMathAndReconcile() {
        vm.step(200, -1)
        settle("one note less") { state.counter.n200 == 5 }
        assertEquals(116_000L, state.counter.total)
        vm.setChange("25.5")
        settle("change typed") { state.counter.change.toPiastres() == 2_550L }
        assertEquals(1_150L * 100 + 2_550, state.counter.total)

        vm.reconcile()
        settle("reconciled") { state.reconciled == ReconcileResult.Done && state.counter.total == 117_550L }
        assertEquals("the pocket now holds what was counted", 117_550L, balance(cash))
    }

    @Test fun reconcileWithoutADifferenceWritesNothing() {
        val before = entryCount()
        vm.reconcile()
        settle("already") { state.reconciled == ReconcileResult.Already }
        assertEquals(before, entryCount())
    }

    @Test fun noteCountsNeverGoBelowZero() {
        repeat(3) { vm.step(50, -1) }
        settle("floor") { state.counter.n50 == 0 }
        vm.step(50, -1)
        settle("still zero") { state.counter.n50 == 0 }
        assertEquals(0, state.counter.n50)
    }

    @Test fun cashCounterOfSplitsAnyBalance() {
        assertEquals(CashCounter(), CashCounter.of(0))
        assertEquals(123_456L, CashCounter.of(123_456).total)
        assertEquals(0L, CashCounter.of(-500).total)
    }

    @Test fun pastedMessageGivesAPrefillAndRecordsNothingYet() {
        vm.pasteSms("تم خصم 40 جنيه من محفظتك. رصيدك الحالي 1,150 جنيه.")
        settle("parsed") { state.sms is SmsState.Parsed }
        val preview = (state.sms as SmsState.Parsed).preview
        assertTrue("1,150 is the wallet's balance", preview.matches)
        assertEquals(wallet, preview.pocket?.id)
        val prefill = checkNotNull(vm.smsPrefill())
        assertEquals(EntryKind.SPENT, prefill.type)
        assertEquals(4_000L, prefill.amount)
        assertEquals(wallet, prefill.pocketId)
        assertEquals(115_000L, prefill.smsBalance)
        assertNull("recorded only when the entry saves", runBlocking { db.pocketDao().byId(wallet) }?.lastSmsBalance)
    }

    @Test fun balanceThatMatchesNoPocketIsNotMarkedMatching() {
        vm.pasteSms("تم خصم 40 جنيه من محفظتك. رصيدك الحالي 999 جنيه.")
        settle("parsed") { state.sms is SmsState.Parsed }
        val preview = (state.sms as SmsState.Parsed).preview
        assertFalse(preview.matches)
        assertEquals("falls back to the first wallet", wallet, preview.pocket?.id)
    }

    @Test fun merchantTextThatNamesACategoryFillsIt() {
        vm.pasteSms("تم خصم 40 جنيه من محفظتك لدى قهوة الحي. رصيدك الحالي 1,150 جنيه.")
        settle("parsed") { state.sms is SmsState.Parsed }
        val preview = (state.sms as SmsState.Parsed).preview
        assertEquals(coffee, preview.categoryId)
        assertEquals(coffee, vm.smsPrefill()?.categoryId)
    }

    @Test fun nothingReadableLeavesNoPrefill() {
        vm.pasteSms("hello world")
        settle("unreadable") { state.sms == SmsState.Unreadable }
        assertNull(vm.smsPrefill())
        vm.pasteSms(null)
        settle("still unreadable") { state.sms == SmsState.Unreadable }
        vm.clearSms()
        settle("cleared") { state.sms == SmsState.Idle }
        assertNull(vm.smsPrefill())
    }

    @Test fun quickActionsPrefillTransfers() {
        val atm = vm.atmPrefill()
        assertEquals(EntryKind.MOVED, atm.type)
        assertEquals(bank to cash, atm.pocketId to atm.toPocketId)

        val topUp = vm.topUpPrefill()
        assertEquals(EntryKind.MOVED, topUp.type)
        assertEquals(bank to wallet, topUp.pocketId to topUp.toPocketId)

        val generic = vm.transferPrefill()
        assertEquals(EntryKind.MOVED, generic.type)
        assertNull(generic.pocketId)
        assertNull(generic.toPocketId)
    }
}
