package com.masroufi.app.feature.pockets

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
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

/** Today 2026-11-17 09:00 Cairo. Cash 3,900, wallet 5,000 (the pocket under test), bank 10,000. Main is driven by hand. */
@RunWith(AndroidJUnit4::class)
class PocketDetailViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: PocketDetailViewModel
    private var cash = 0L
    private var wallet = 0L
    private var bank = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
        runBlocking {
            fun pocket(name: String, kind: PocketKind, opening: Long) =
                PocketEntity(name = name, kind = kind, openingBalance = opening, color = 0xFFFF6B4A.toInt())
            cash = db.pocketDao().insert(pocket("الكاش", PocketKind.CASH, 390_000))
            wallet = db.pocketDao().insert(pocket("فودافون كاش", PocketKind.MOBILE_WALLET, 500_000))
            bank = db.pocketDao().insert(pocket("حساب البنك", PocketKind.BANK, 1_000_000))
        }
        vm = detail(wallet)
        settle("pocket") { !state.loading }
    }

    @After fun close() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

    private fun detail(id: Long): PocketDetailViewModel {
        val store = ViewModelStore().also { stores += it } // one store per view model: the id differs
        val v = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer { PocketDetailViewModel(f.pockets, db.entryDao(), db.categoryDao(), clock, SavedStateHandle(mapOf("id" to id))) }
            },
        )[PocketDetailViewModel::class]
        v.viewModelScope.launch { v.state.collect {} } // a screen collecting it
        return v
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
    private fun at(day: Int, hour: Int = 8) = ZonedDateTime.of(LocalDate.of(2026, 11, day), LocalTime.of(hour, 0), zone).toInstant().toEpochMilli()

    private fun move(amount: Long, from: Long, to: Long, day: Int, fee: Long = 0) = runBlocking {
        f.entries.log(NewEntry(EntryKind.MOVED, amount, from, toPocketId = to, fee = fee, happenedAt = at(day)))
    }

    private fun bill(name: String, amount: Long, due: LocalDate) = runBlocking {
        db.recurringDao().insert(
            RecurringEntity(name = name, amount = amount, pocketId = wallet, scheduleDay = due.dayOfMonth, nextDue = due, kind = RecurringKind.BILL),
        )
    }

    private fun limit(piastres: Long) = runBlocking {
        f.pockets.update(checkNotNull(db.pocketDao().byId(wallet)).copy(monthlyLimit = piastres))
    }

    @Test fun noLimitNoWarning() {
        assertNull(state.limit)
        assertNull(state.warningRoom)
        assertEquals("the hero follows the balance", 500_000L, state.balance)
    }

    @Test fun limitWarningNamesTheUpcomingPaymentOnlyWhenThereIsOne() {
        limit(300_000)
        move(200_000, wallet, cash, day = 3)
        settle("moved counted") { state.limit == 300_000L && state.moved == 200_000L }
        assertNull("200,000 moved fits 300,000", state.warningRoom)

        bill("قسط الجمعية", 150_000, LocalDate.of(2026, 11, 25))
        move(1_000, wallet, cash, day = 4) // wakes the screen: a payment alone is not an entry
        settle("warning") { state.warningRoom != null }
        assertEquals("300,000 - 201,000 moved", 99_000L, state.warningRoom)
        assertEquals("قسط الجمعية", state.upcoming?.name)
    }

    @Test fun alreadyOverTheLimitHasARoomBelowZeroAndNoSentenceAboutAPayment() {
        limit(100_000)
        move(150_000, wallet, cash, day = 3)
        settle("over") { state.warningRoom != null }
        assertEquals(-50_000L, state.warningRoom)
        assertNull(state.upcoming)
    }

    @Test fun feeRowsGroupCashOutAndTransfer() {
        move(50_000, wallet, cash, day = 3, fee = 1_000) // cash-out
        move(30_000, wallet, bank, day = 4, fee = 500) // transfer
        move(5_000, wallet, cash, day = 5, fee = 300) // cash-out
        settle("fees") { state.fees.total == 1_800L }
        assertEquals(2 to 1_300L, state.fees.cashOut)
        assertEquals(1 to 500L, state.fees.transfer)
        assertTrue("cash-outs cost more: the tip shows", state.feeTip)
    }

    @Test fun noTipWhenTransfersCostMoreOrThereAreNoFees() {
        assertFalse(state.feeTip)
        move(30_000, wallet, bank, day = 4, fee = 900)
        move(5_000, wallet, cash, day = 5, fee = 300)
        settle("fees") { state.fees.total == 1_200L }
        assertFalse(state.feeTip)
    }

    @Test fun switchesPersist() {
        assertTrue(state.countsInTicket)
        vm.setCountsInTicket(false)
        settle("counts off") { !state.countsInTicket }
        vm.setLowBalanceAlert(true)
        settle("alert on") { state.lowBalanceAlert }
        assertEquals("200 pounds until one is typed", DEFAULT_LOW_BALANCE, state.lowBalanceThreshold)
        vm.setLowBalanceThreshold(35_000)
        settle("threshold") { state.lowBalanceThreshold == 35_000L }
        vm.setLimit(120_000)
        settle("limit") { state.limit == 120_000L }

        val stored = checkNotNull(runBlocking { db.pocketDao().byId(wallet) })
        assertFalse(stored.countsInTicket)
        assertTrue(stored.lowBalanceAlert)
        assertEquals(35_000L, stored.lowBalanceThreshold)
        assertEquals(120_000L, stored.monthlyLimit)
    }

    @Test fun reconcileWritesTheDifference() {
        vm.reconcile(480_000)
        settle("reconciled") { state.reconciled == ReconcileResult.Done && state.balance == 480_000L }
        vm.reconcile(480_000)
        settle("matches now") { state.reconciled == ReconcileResult.Already }
    }

    @Test fun matchesTheLastMessageOnlyWhileTheBalanceIsTheSame() {
        runBlocking { f.pockets.recordSms(wallet, 500_000) }
        settle("matches") { state.matchesSms && state.smsAt == clock.millis() }
        move(1_000, wallet, cash, day = 3)
        settle("no longer") { !state.matchesSms }
    }

    @Test fun actionsPrefillTheSheet() {
        assertEquals(EntryKind.RECEIVED to wallet, vm.receivePrefill().let { it.type to it.pocketId })
        assertEquals(EntryKind.MOVED to wallet, vm.transferPrefill().let { it.type to it.pocketId })
        val out = vm.cashOutPrefill()
        assertEquals(Triple(EntryKind.MOVED, wallet, cash), Triple(out.type, out.pocketId, out.toPocketId))
    }

    @Test fun cashPocketHasNoCashOutTargetAndShowsTheCounter() {
        vm = detail(cash)
        settle("cash pocket") { !state.loading && state.id == cash }
        assertTrue(state.isCash)
        assertNull(state.cashPocketId)
        assertEquals(390_000L, state.counter.total)
    }

    @Test fun archiveLeavesTheScreen() {
        vm.archive()
        settle("gone") { state.missing }
        assertTrue(runBlocking { db.pocketDao().byId(wallet) }!!.archived)
    }
}
