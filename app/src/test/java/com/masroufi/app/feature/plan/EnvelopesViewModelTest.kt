package com.masroufi.app.feature.plan

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
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.SplitRule
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Payday 25 (the default). First opened 2026-10-20, today 2026-11-12: cycle 10-25..11-25, day 19, 13 days to payday.
 * Food runs out on the 13th, transport is on pace and better than last cycle, bills is sealed, pace is just on pace.
 */
@RunWith(AndroidJUnit4::class)
class EnvelopesViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(LocalDate.of(2026, 10, 20)), zone)
    private val plus = DebugEntitlements()
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: EnvelopesViewModel
    private var cash = 0L

    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, plus)
        runBlocking {
            f.settings.current() // first open: 10-20
            clock.instant = at(d(11, 12))
            cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0))
        }
        vm = ViewModelProvider.create(store, viewModelFactory { initializer { EnvelopesViewModel(f.envelopes, f.recurring, plus) } })[EnvelopesViewModel::class]
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
    private fun card(name: String) = checkNotNull(state.cards.firstOrNull { it.view.name == name }) { "no card $name" }

    private fun category(name: String) = runBlocking { db.categoryDao().insert(CategoryEntity(name = name, icon = "grid", color = 0)) }

    private fun spend(amount: Long, category: Long, date: LocalDate) = runBlocking {
        db.entryDao().insert(
            EntryEntity(
                kind = EntryKind.SPENT, amount = amount, pocketId = cash, categoryId = category,
                happenedAt = at(date).toEpochMilli(), loggedAt = 0,
            ),
        )
    }

    private fun envelope(name: String, monthly: Long, vararg categories: Long, kind: EnvelopeKind = EnvelopeKind.SPENDING) =
        runBlocking { f.envelopes.create(name, 0, monthly, kind, categories.toSet()) }

    private fun setMonthly(name: String, monthly: Long) = runBlocking {
        val (env, categories) = checkNotNull(f.envelopes.saved(card(name).view.id))
        f.envelopes.update(env.copy(monthly = monthly), categories)
    }

    private fun fill() {
        val food = category("food")
        val transport = category("transport")
        val pace = category("pace")
        val bills = category("bills")
        envelope("food", 100_000, food)
        envelope("transport", 100_000, transport)
        envelope("pace", 100_000, pace)
        val billsId = envelope("bills", 50_000, bills, kind = EnvelopeKind.FIXED)
        runBlocking {
            db.recurringDao().insert(
                RecurringEntity(
                    name = "net", amount = 50_000, pocketId = cash, scheduleDay = 20, nextDue = d(12, 20), envelopeId = billsId,
                    kind = RecurringKind.BILL,
                ),
            )
        }
        spend(95_000, food, d(11, 1))
        spend(10_000, transport, d(11, 3))
        spend(40_000, transport, d(10, 1)) // last cycle, by the same day
        spend(20_000, pace, d(11, 3))
        settle("four cards") { state.cards.size == 4 }
    }

    @Test fun linesMapToWhatTheCardsSay() {
        fill()
        assertEquals(CardLine.RunsOut(13), card("food").line)
        assertEquals(CardLine.BetterThanLast, card("transport").line)
        assertEquals(CardLine.OnPace, card("pace").line)
        assertEquals("sealed: the bill's amount and its day", CardLine.Sealed(50_000, 20), card("bills").line)
    }

    @Test fun theTipNeedsOneRunningOutAndOneWithRoom() {
        fill()
        // Transport has the most left (900), half of it in whole pounds goes to food.
        val tip = checkNotNull(state.tip)
        assertEquals(card("transport").view.id, tip.fromId)
        assertEquals(card("food").view.id, tip.toId)
        assertEquals(45_000L, tip.amount)

        setMonthly("food", 1_000_000)
        settle("nobody runs out") { state.tip == null }
    }

    @Test fun theSplitCardIsHiddenUntilASplitWasApplied() {
        fill()
        assertNull(state.split)
        val a = card("food").view.id
        runBlocking {
            f.envelopes.saveSplit(listOf(SplitRule.Fixed(a, 100_000)))
            f.envelopes.applySplit(900_000)
        }
        settle("split shown") { state.split != null }
        val split = checkNotNull(state.split)
        assertEquals(900_000L, split.income)
        assertEquals(800_000L, split.unassigned)
        assertEquals(d(10, 25), split.cycleStart)
    }

    @Test fun aFifthEnvelopeOnFreeShowsThePlusCardAndPlusOpensTheScreen() {
        fill()
        assertFalse("4 on Free", vm.requestNew())
        settle("plus card") { state.sheet == EnvelopeSheet.Plus }
        vm.dismissSheet()
        settle("closed") { state.sheet == null }
        plus.setDebugOverride(true)
        assertTrue(vm.requestNew())
        assertNull(state.sheet)
    }

    @Test fun theFormCatchesPlusRequiredAndShowsThePlusCard() {
        fill()
        val form = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { EnvelopeFormViewModel(f.envelopes, f.recurring, db.categoryDao(), SavedStateHandle()) } },
        )[EnvelopeFormViewModel::class]
        settle("form loaded") { form.form.value.loaded }
        form.setName("دروس")
        form.save()
        settle("plus card") { form.form.value.plus }
        assertFalse(form.form.value.saved)
        assertEquals("nothing was created", 4, state.cards.size)
    }

    @Test fun aDropOpensTheMoveSheetAndConfirmingMovesTheAmount() {
        fill()
        val transport = card("transport").view
        val food = card("food").view
        vm.requestMove(transport.id, food.id)
        settle("move sheet") { state.sheet == EnvelopeSheet.Move(transport.id, food.id, transport.left) }
        vm.confirmMove(transport.id, food.id, 30_000)
        settle("moved") { card("food").view.monthly == 130_000L }
        assertEquals(70_000L, card("transport").view.monthly)
        assertNull("the sheet closed", state.sheet)

        // More than what is left is capped at what is left.
        vm.confirmMove(transport.id, food.id, 99_999_999)
        settle("capped") { card("transport").view.left == 0L }
        assertEquals(130_000L + 60_000L, card("food").view.monthly)
    }

    @Test fun aDropFromAnEmptyEnvelopeDoesNothing() {
        fill()
        setMonthly("pace", 0)
        settle("pace empty") { card("pace").view.monthly == 0L }
        vm.requestMove(card("pace").view.id, card("food").view.id)
        settle("still closed") { true }
        assertNull(state.sheet)
    }

    @Test fun suggestMakesTheFourStartersAndOpensTheFirstOne() {
        runBlocking { f.seed.ensureSeeded() }
        settle("empty") { !state.loading }
        assertTrue(state.cards.isEmpty())
        val opened = mutableListOf<Long>()
        vm.viewModelScope.launch { vm.openEdit.collect { opened += it } }
        settle("collecting") { true }
        vm.suggest()
        settle("four starters") { state.cards.size == 4 && opened.isNotEmpty() }
        assertEquals(setOf("الأكل والبقالة", "المواصلات", "الفواتير والنت", "الخروجات"), state.cards.map { it.view.name }.toSet())
        assertTrue("monthly amounts are left for the user", state.cards.all { it.view.monthly == 0L })
        assertEquals(state.cards.first { it.view.name == "الأكل والبقالة" }.view.id, opened.single())
        assertNotNull(runBlocking { f.envelopes.saved(opened.single()) })
    }
}
