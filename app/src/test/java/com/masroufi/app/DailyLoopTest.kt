package com.masroufi.app

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.feature.add.AddViewModel
import com.masroufi.app.feature.add.FeedbackBus
import com.masroufi.app.feature.add.PendingPunches
import com.masroufi.app.feature.close.CloseDayUi
import com.masroufi.app.feature.close.CloseDayViewModel
import com.masroufi.app.feature.close.TearState
import com.masroufi.app.feature.home.HomeUiState
import com.masroufi.app.feature.home.HomeViewModel
import com.masroufi.app.feature.proverb.ProverbRewardUi
import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.proverb.ProverbRewardViewModel
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
import org.junit.Assert.assertNotNull
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

/**
 * Prompt 3's "done means", driven through the view models: the sample data (opening 5,870 in the cash pocket, payday 25,
 * an unpaid 2,750 bill due 2026-11-20) prints a 240 ticket on Thursday 2026-11-12 09:00; spends from the add sheet punch
 * it; the close screen tears it to the jar; the reward shows a card; Home shows tomorrow. Main is driven by hand as in
 * HomeViewModelTest (Room and DataStore run on real threads).
 */
@RunWith(AndroidJUnit4::class)
class DailyLoopTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val clock = TestClock(ZonedDateTime.of(nov12, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private val pending = PendingPunches()
    private val bus = FeedbackBus()
    private val plus = DebugEntitlements()
    private val process = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var home: HomeViewModel
    private lateinit var add: AddViewModel
    private var cash = 0L
    private var coffee = 0L
    private var lunch = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, plus)
        process.registry.currentState = Lifecycle.State.CREATED
        runBlocking {
            f.seed.ensureSeeded()
            val pocket: PocketEntity = db.pocketDao().all().first().single()
            cash = pocket.id
            db.pocketDao().update(pocket.copy(openingBalance = 587_000))
            f.settings.setPayday(Payday.Day(25))
            db.recurringDao().insert(
                RecurringEntity(
                    name = "Sample bill", amount = 275_000, pocketId = cash, scheduleDay = 20,
                    nextDue = LocalDate.of(2026, 11, 20), kind = RecurringKind.BILL,
                ),
            )
            val categories = db.categoryDao().all().first().sortedBy { it.id }
            coffee = categories[0].id
            lunch = categories[1].id
        }
        home = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    HomeViewModel(
                        f.home, f.tickets, f.entries, db.categoryDao(), db.pocketDao(), pending, process.lifecycle, clock, bus,
                        f.settings, f.onboarding, PendingDeletes(f.entries, io), f.wishes, f.eom,
                    )
                }
            },
        )[HomeViewModel::class]
        home.viewModelScope.launch { home.state.collect {} } // a screen collecting it
        add = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    AddViewModel(f.entries, db.entryDao(), db.categoryDao(), db.pocketDao(), db.dayTicketDao(), f.settings, plus, pending, PendingDeletes(f.entries, io), bus, f.ritual, f.basket, f.wishes, clock)
                }
            },
        )[AddViewModel::class]
        add.viewModelScope.launch { add.state.collect {} } // the sheet collecting it
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

    private val ready get() = home.state.value as? HomeUiState.Ready
    private fun stored() = runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE).size }

    /** Logs [pounds] in [category] through the add sheet and waits until it is stored and Home shows it. */
    private fun log(pounds: String, category: Long, holes: Int, spent: Long) {
        clock.instant = clock.instant.plusMillis(450) // past the 400 ms double-tap guard
        add.open(null)
        settle("sheet ready") {
            add.state.value.form.pockets.isNotEmpty() && add.state.value.today != null && add.state.value.form.pocketId != null
        }
        pounds.forEach { add.digit(it.digitToInt()) }
        add.selectCategory(category)
        settle("amount and category typed") { add.state.value.canSave && add.state.value.form.categoryId == category }
        assertTrue("save accepted", add.save())
        settle("entry $holes stored") { stored() == holes }
        settle("Home shows $holes holes and $spent spent") { ready?.ticket?.let { it.holes == holes && it.spent == spent } == true }
        settle("the sheet's placeholder is pruned") { pending.pending.value.isEmpty() }
    }

    private fun printed() {
        settle("today's ticket") { ready?.ticket != null }
        assertEquals("(5,870 - 2,750) / 13 days = 240", 24_000L, ready!!.ticket!!.amount)
    }

    @Test fun spendsPunchTheTicketAndARepeatChipAppears() {
        printed()
        log("50", coffee, holes = 1, spent = 5_000)
        settle("first-of-day feedback") { home.feedback.value != null }
        assertEquals("19,000 left after the first 50", 19_000L, home.feedback.value!!.left)
        assertTrue("the day's first hole", home.feedback.value!!.firstOfDay)
        log("80", lunch, holes = 2, spent = 13_000)
        log("98", lunch, holes = 3, spent = 22_800)
        assertEquals("1,200 left of 24,000 after 50 + 80 + 98", 1_200L, ready!!.ticket!!.left)
        assertTrue("no chip before a repeat", ready!!.repeats.isEmpty())

        log("98", lunch, holes = 4, spent = 32_600)
        settle("a chip for 98 in lunch") { ready?.repeats?.any { it.categoryId == lunch && it.amount == 9_800L } == true }
        val chip = ready!!.repeats.first { it.categoryId == lunch && it.amount == 9_800L }
        clock.instant = clock.instant.plusMillis(450)
        home.punchRepeat(chip)
        settle("the chip's hole is stored") { stored() == 5 }
        settle("Home shows the chip's hole") { ready?.ticket?.let { it.holes == 5 && it.spent == 42_400L } == true }
        // Home shows the chip's save the way it shows the sheet's: the same feedback line, here 184 over.
        settle("the chip's feedback") { home.feedback.value?.left == -18_400L }
        assertEquals(false, home.feedback.value!!.firstOfDay)
    }

    @Test fun fullDay() {
        printed()
        log("50", coffee, holes = 1, spent = 5_000)
        log("80", lunch, holes = 2, spent = 13_000)
        log("30", coffee, holes = 3, spent = 16_000)
        assertEquals(8_000L, ready!!.ticket!!.left)
        log("30", coffee, holes = 4, spent = 19_000)
        assertEquals("19,000 spent: 5,000 left", 5_000L, ready!!.ticket!!.left)

        // Close: the screen shows the leftover; tearing sends it to the jar.
        val close = CloseDayViewModel(f.home, f.tickets, SavedStateHandle(mapOf("date" to nov12.toEpochDay())))
        settle("close screen") { close.state.value is CloseDayUi.Ready }
        val c = close.state.value as CloseDayUi.Ready
        assertEquals(24_000L, c.ticket)
        assertEquals(19_000L, c.spent)
        assertEquals(4, c.holes)
        assertEquals("leftover", 5_000L, c.leftover)
        close.tear(LeftoverDestination.JAR)
        settle("tear done") { close.tear.value == TearState.Done }
        val row = runBlocking { db.dayTicketDao().forDate(nov12) }!!
        assertEquals(TicketStatus.CLOSED_BY_HAND, row.status)
        assertEquals(5_000L, row.leftover)
        assertEquals("the jar movement equals the leftover", 5_000L, runBlocking { db.jarDao().totalNow(Pot.JAR) })
        val card = row.proverbNumber
        assertNotNull("a card was drawn", card)
        assertTrue(card!! in 1..ProverbCatalog.all.size)

        // Reward: ticket number 1 and the drawn card.
        val reward = ProverbRewardViewModel(db.dayTicketDao(), db.proverbDao(), SavedStateHandle(mapOf("date" to nov12.toEpochDay())))
        settle("reward") { reward.state.value is ProverbRewardUi.Ready }
        val r = reward.state.value as ProverbRewardUi.Ready
        assertEquals("first hand-closed ticket", 1, r.ticketNumber)
        assertEquals(card, r.proverb.number)
        assertEquals(5_000L, r.leftover)

        // Home: tomorrow's preview, the jar holding the leftover.
        settle("Home shows tomorrow") { ready?.let { it.ticket == null && it.tomorrow != null } == true }
        val h = ready!!
        assertEquals("jar", 5_000L, h.jarTotal)
        assertEquals("(5,680 - 2,750 - 50) / 12 days = 240", 24_000L, h.tomorrow!!.amount)
        assertEquals(card, h.tomorrow!!.proverbNumber)
        assertNull(h.ticket)
    }
}
