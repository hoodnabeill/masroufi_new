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
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
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

/** Today 2026-11-17, payday 25: the cycle is 10-25..11-25. */
@RunWith(AndroidJUnit4::class)
class FixedCostsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: FixedCostsViewModel
    private var cash = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
        runBlocking { cash = db.pocketDao().insert(PocketEntity(name = "الكاش", kind = PocketKind.CASH, openingBalance = 500_000, color = 0)) }
        vm = ViewModelProvider.create(store, viewModelFactory { initializer { FixedCostsViewModel(f.recurring, f.envelopes, clock) } })[FixedCostsViewModel::class]
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

    private fun item(name: String, nextDue: LocalDate, amount: Long = 50_000) = runBlocking {
        db.recurringDao().insert(
            RecurringEntity(name = name, amount = amount, pocketId = cash, scheduleDay = nextDue.dayOfMonth, nextDue = nextDue, kind = RecurringKind.BILL),
        )
    }

    private fun due(name: String) = vm.state.value.items.first { it.row.name == name }.due

    @Test fun noItemsIsAnEmptyListNotALoadingOne() {
        settle("loaded") { !vm.state.value.loading }
        assertEquals(emptyList<FixedCostUi>(), vm.state.value.items)
    }

    @Test fun dueStatesFollowTheNextPayday() {
        item("paid", LocalDate.of(2026, 12, 8)) // on or after the next payday: paid for this cycle
        item("onPayday", LocalDate.of(2026, 11, 25))
        item("soon", LocalDate.of(2026, 11, 20))
        item("today", today)
        item("late", LocalDate.of(2026, 11, 8))
        settle("five items") { vm.state.value.items.size == 5 }
        assertEquals(DueState.Paid, due("paid"))
        assertEquals(DueState.Paid, due("onPayday"))
        assertEquals(DueState.Due(LocalDate.of(2026, 11, 20)), due("soon"))
        assertEquals(DueState.Due(today), due("today"))
        assertEquals(DueState.Overdue, due("late"))
    }

    @Test fun payingOpensTheAddSheetWithTheCostAndSavingItMarksItPaid() {
        val id = item("rent", LocalDate.of(2026, 11, 20), amount = 250_000)
        settle("listed") { vm.state.value.items.size == 1 }
        val prefill = vm.payPrefill(vm.state.value.items.single().row)
        assertEquals(250_000L, prefill.amount)
        assertEquals(EntryKind.SPENT, prefill.type)
        assertEquals(cash, prefill.pocketId)
        assertEquals(id, prefill.recurringId)
        assertEquals("rent", prefill.note)

        // What the add sheet's save does with that prefill: the entry pays the item, which moves its due date on.
        runBlocking { f.recurring.markPaid(id, cash) }
        settle("paid") { due("rent") == DueState.Paid }
    }

    @Test fun theKidsHandoverIsNotAPlainPaymentItsRowOpensTheKidsScreen() {
        item("rent", LocalDate.of(2026, 11, 20), amount = 250_000)
        val job = io.launch { f.kids.addChild("ملك", 2017, 3, 7_000) }
        settle("child added") { job.isCompleted }
        settle("listed") { vm.state.value.kids != null && vm.state.value.items.size == 1 }
        assertEquals(listOf("rent"), vm.state.value.items.map { it.row.name })
        assertEquals(listOf(RecurringKind.KIDS, 7_000L), vm.state.value.kids!!.let { listOf(it.kind, it.amount) })
    }

    @Test fun aNewCostIsDueOnItsNextDayOfTheMonth() {
        assertEquals(LocalDate.of(2026, 11, 20), nextDueOn(20, today))
        assertEquals("today counts", today, nextDueOn(17, today))
        assertEquals("a day already gone goes to next month", LocalDate.of(2026, 12, 5), nextDueOn(5, today))
        assertEquals("short months clamp", LocalDate.of(2026, 11, 30), nextDueOn(31, today))
    }
}
