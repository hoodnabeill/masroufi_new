package com.masroufi.app.feature.add

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
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.feature.roll.PendingDeletes
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
 * Payday Day(25), one cash pocket with 3,900 EGP: on Thursday 2026-11-12 the ticket is 300. Main is driven by hand
 * as in HomeViewModelTest (Room and DataStore run on real threads).
 */
@RunWith(AndroidJUnit4::class)
class AddViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val clock = TestClock(ZonedDateTime.of(nov12, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private val pending = PendingPunches()
    private val bus = FeedbackBus()
    private val deletes by lazy { PendingDeletes(f.entries, io) }
    private val plus = DebugEntitlements()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: AddViewModel
    private val events = mutableListOf<PunchFeedback>()
    private var cash = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, plus)
        cash = pocket("الكاش", 390_000)
        runBlocking { f.tickets.printToday() }
    }

    @After fun close() {
        store.clear()
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

    private fun pocket(name: String, opening: Long = 0) =
        runBlocking { db.pocketDao().insert(PocketEntity(name = name, kind = PocketKind.CASH, openingBalance = opening, color = 0)) }

    private fun category(name: String) = runBlocking { db.categoryDao().insert(CategoryEntity(name = name, icon = "coffee", color = 0)) }

    private fun newVm(): AddViewModel {
        val factory = viewModelFactory {
            initializer {
                AddViewModel(
                    f.entries, db.entryDao(), db.categoryDao(), db.pocketDao(), db.dayTicketDao(), f.settings, plus, pending, deletes, bus, clock,
                )
            }
        }
        vm = ViewModelProvider.create(store, factory)[AddViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} } // the sheet collecting it
        vm.viewModelScope.launch { bus.events.collect { events += it } }
        return vm
    }

    /** Runs Main's due tasks until [cond] holds; Room and DataStore work on real threads meanwhile. */
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
    private fun entries(): List<EntryEntity> = runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }

    /** Opens the sheet and waits for the pockets and today's ticket. */
    private fun opened(prefill: AddPrefill? = null) {
        vm.open(prefill)
        settle("pockets and today's row") { state.form.pockets.isNotEmpty() && state.today != null && state.form.pocketId != null }
    }

    private fun typeAmount(text: String) = text.forEach { if (it == '.') vm.point() else vm.digit(it.digitToInt()) }

    @Test fun doubleTapLogsOnce() {
        newVm()
        opened()
        typeAmount("25")
        settle("amount typed") { state.amount == 2_500L }
        assertTrue(vm.save())
        assertFalse("second tap inside 400 ms is ignored", vm.save())
        settle("stored") { entries().size == 1 }
        assertEquals(listOf(EntryKind.SPENT, 2_500L, cash), entries().single().let { listOf(it.kind, it.amount, it.pocketId) })
        clock.instant = clock.instant.plusMillis(450)
        assertTrue("a tap after 400 ms saves", vm.save())
        settle("second entry") { entries().size == 2 }
    }

    @Test fun spentPunchesAndReportsLeft() {
        newVm()
        opened()
        typeAmount("25")
        settle("live line") { state.leftAfter == 27_500L }
        assertTrue(vm.save())
        assertEquals("the hole shows before storage", listOf(2_500L), pending.pending.value.map { it.punched })
        settle("feedback") { events.isNotEmpty() }
        assertEquals(PunchFeedback.Saved(27_500, firstOfDay = true), events.single())
    }

    @Test fun editSavesInPlaceAndDeleteGoesToTheUndo() {
        newVm()
        val id = runBlocking { f.entries.log(NewEntry(EntryKind.SPENT, 2_500, cash, note = "فول", happenedAt = clock.millis())) }
        val old = runBlocking { db.entryDao().byId(id)!! }
        opened(AddPrefill(2_500, null, "فول", EntryKind.SPENT, cash, null, 0, EditTarget(id, old.happenedAt, null, 0, 2_500)))
        settle("form loaded") { state.amount == 2_500L && state.form.edit != null }
        vm.digit(0)
        settle("typed") { state.amount == 25_000L }
        assertTrue(vm.save())
        settle("edited in place") { entries().single().amount == 25_000L }
        assertEquals(id, entries().single().id)
        assertTrue("an edit shows no placeholder punch", pending.pending.value.isEmpty())
        vm.delete()
        assertEquals(setOf(id), deletes.pending.value.keys)
        assertEquals("nothing is written before the 5 s", 1, entries().size)
    }

    @Test fun transferNeedsTwoPockets() {
        newVm()
        opened()
        vm.setType(EntryKind.MOVED)
        typeAmount("50")
        settle("amount typed") { state.amount == 5_000L }
        assertFalse("one pocket: no target", state.canSave)
        assertFalse(vm.save())

        val bank = pocket("البنك")
        opened()
        vm.setType(EntryKind.MOVED)
        typeAmount("50")
        settle("target picked") { state.form.toPocketId == bank && state.amount == 5_000L }
        assertTrue(state.canSave)
        vm.selectToPocket(cash)
        settle("same pocket") { state.form.toPocketId == cash }
        assertFalse("from and to must differ", state.canSave)
        assertFalse(vm.save())
        assertEquals("nothing stored", 0, entries().size)
    }

    @Test fun transferFeePunchesPending() {
        val bank = pocket("البنك")
        newVm()
        opened()
        vm.setType(EntryKind.MOVED)
        typeAmount("100")
        vm.focusFee(true)
        typeAmount("5")
        settle("fee typed") { state.form.fee.toPiastres() == 500L && state.form.toPocketId == bank }
        assertEquals("the keypad typed into the fee", 10_000L, state.amount)
        assertTrue(vm.save())
        assertEquals(listOf(500L), pending.pending.value.map { it.punched })
        settle("stored") { entries().size == 1 }
        val e = entries().single()
        assertEquals(listOf(EntryKind.MOVED, 10_000L, cash, bank, 500L), listOf(e.kind, e.amount, e.pocketId, e.toPocketId, e.fee))
    }

    @Test fun optimisticRollbackOnFailure() {
        newVm()
        opened()
        typeAmount("25")
        vm.setNote("فول")
        settle("typed") { state.amount == 2_500L && state.form.note == "فول" }
        // The pocket is archived behind the sheet's back: storage refuses the entry.
        runBlocking { db.pocketDao().update(db.pocketDao().byId(cash)!!.copy(archived = true)) }
        assertTrue(vm.save())
        assertEquals("shown before storage answers", 1, pending.pending.value.size)
        settle("rolled back") { events.isNotEmpty() && pending.pending.value.isEmpty() }
        assertEquals(PunchFeedback.Failed, events.single())
        assertEquals(0, entries().size)
        vm.open(null)
        settle("reopened (the archived pocket is gone from the list)") { state.form.pockets.none { it.id == cash } }
        assertEquals("typed values kept", "25", state.form.amount.text)
        assertEquals("فول", state.form.note)
    }

    @Test fun categoriesLastUsedFirst() {
        val a = category("أ")
        val b = category("ب")
        val c = category("ج")
        runBlocking {
            f.entries.log(NewEntry(EntryKind.SPENT, 100, cash, categoryId = c, happenedAt = clock.millis() - 2_000))
            f.entries.log(NewEntry(EntryKind.SPENT, 100, cash, categoryId = a, happenedAt = clock.millis() - 1_000))
        }
        newVm()
        opened()
        assertEquals(listOf(a, c, b), state.form.categories.map { it.id })
    }

    @Test fun prefillApplied() {
        category("قهوة")
        val food = category("أكل")
        newVm()
        opened(AddPrefill(4_000, food, "40 حاجة غريبة"))
        assertEquals("40", state.form.amount.text)
        assertEquals(food, state.form.categoryId)
        assertEquals("40 حاجة غريبة", state.form.note)
        assertEquals(cash, state.form.pocketId)
    }

    @Test fun roundUpOfferOnlyWithPlus() {
        runBlocking { f.settings.setRoundUpStep(10) }
        newVm()
        opened()
        typeAmount("25")
        settle("step read") { state.roundUpStep == 10 && state.amount == 2_500L }
        assertEquals("free tier: no line", 0L, state.roundUpOffer)
        plus.setDebugOverride(true)
        settle("plus on") { state.plus }
        assertEquals("25 -> 30: 5 to the jar", 500L, state.roundUpOffer)
        assertEquals("the round-up punches too", 3_000L, state.punched)
        vm.setRoundUp(false)
        settle("toggle off") { !state.form.roundUpOn }
        assertEquals(2_500L, state.punched)
    }
}
