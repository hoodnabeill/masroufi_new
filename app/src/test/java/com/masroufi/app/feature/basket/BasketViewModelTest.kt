package com.masroufi.app.feature.basket

import com.masroufi.app.data.prefs.Settings
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.SuggestedTicket
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

/** Today is Tuesday 2026-11-17 09:00 Cairo (the 3-month window starts 2026-08-17). */
@RunWith(AndroidJUnit4::class)
class BasketViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val plus = DebugEntitlements(true)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, plus)
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 100_000, color = 0)) }
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

    /** Reads the settings off the main thread while the test dispatcher runs (see ProfileViewModelTest.saved). */
    private fun saved(): Settings {
        var s: Settings? = null
        val read = io.launch { s = f.settings.current() }
        settle("settings read") { read.isCompleted }
        return s!!
    }

    private fun visit(): BasketViewModel {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { BasketViewModel(f.basket) } })[BasketViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} }
        settle("basket") { !vm.state.value.loading }
        return vm
    }

    private fun at(day: LocalDate) = ZonedDateTime.of(day, LocalTime.of(12, 0), zone).toInstant().toEpochMilli()

    private fun priced(day: LocalDate, item: String, unitPrice: Long, quantity: Int = 1, note: String? = null) = runBlocking {
        f.entries.log(
            NewEntry(
                kind = EntryKind.SPENT, amount = unitPrice * quantity, pocketId = cash, note = note, happenedAt = at(day),
                trackedItemId = f.basket.itemIdFor(item), unitPrice = unitPrice,
            ),
        )
    }

    private fun ticket(day: LocalDate = today) = runBlocking {
        db.dayTicketDao().insert(DayTicketEntity(date = day, amount = 24_000, base = 24_000))
    }

    private fun rice(from: Long, to: Long) {
        priced(LocalDate.of(2026, 10, 25), "Rice", from, quantity = 2)
        priced(LocalDate.of(2026, 11, 10), "Rice", to, quantity = 2)
    }

    private fun BasketViewModel.line(name: String) = state.value.items.single { it.name == name }.line

    @Test fun withoutPlusTheBasketIsLockedAndShowsNothingOfTheUser() {
        plus.setDebugOverride(false)
        rice(1_000, 1_100)
        val ui = visit().state.value
        assertTrue(ui.locked)
        assertEquals(emptyList<BasketItemUi>(), ui.items)
        assertNull(ui.hero)
        assertNull(ui.suggestion)
        assertFalse(ui.empty)
    }

    @Test fun withPlusTheSameDataIsShown() {
        rice(1_000, 1_100)
        val ui = visit().state.value
        assertFalse(ui.locked)
        assertEquals(listOf("Rice"), ui.items.map { it.name })
    }

    @Test fun noItemsIsTheEmptyState() {
        val ui = visit().state.value
        assertTrue(ui.empty)
        assertNull(ui.hero)
        assertFalse(ui.hasHabits)
    }

    @Test fun aRiseIsTheHeroWithThenNowAndTheMonthItStartsFrom() {
        rice(1_000, 1_100)
        val hero = visit().state.value.hero!!
        assertEquals(HeroUi(percent = 10, then = 4_000, now = 4_400, sinceMonth = 10, trend = Trend.UP), hero)
    }

    @Test fun aFallIsTheDownHero() {
        rice(1_100, 1_000)
        assertEquals(listOf(Trend.DOWN, -9), visit().state.value.hero!!.let { listOf(it.trend, it.percent) })
    }

    @Test fun anUnchangedBasketIsTheSameHero() {
        priced(LocalDate.of(2026, 10, 26), "Salt", 500)
        priced(LocalDate.of(2026, 11, 11), "Salt", 500)
        val hero = visit().state.value.hero!!
        assertEquals(listOf(Trend.SAME, 0), listOf(hero.trend, hero.percent))
    }

    @Test fun itemLinesSayOnlyWhatTheDataSupports() {
        // last place with the price before it
        priced(LocalDate.of(2026, 9, 20), "Tomato", 1_500, note = "Hyper")
        priced(LocalDate.of(2026, 10, 20), "Tomato", 1_800, note = "Friday market")
        priced(LocalDate.of(2026, 11, 10), "Tomato", 2_200, note = "Friday market")
        // no place on the last entry: the cheapest place named earlier
        priced(LocalDate.of(2026, 9, 20), "Oil", 8_000, note = "Hyper")
        priced(LocalDate.of(2026, 10, 20), "Oil", 8_500, note = "Corner")
        priced(LocalDate.of(2026, 11, 10), "Oil", 8_400)
        // often: five in three weeks, no places
        listOf(26, 29, 1, 4, 7).forEachIndexed { i, d -> priced(LocalDate.of(2026, if (i < 2) 10 else 11, d), "Bus", 700L + i * 10) }
        // went down, rarely
        priced(LocalDate.of(2026, 10, 1), "Chicken", 10_500)
        priced(LocalDate.of(2026, 11, 1), "Chicken", 9_800)
        // unchanged over the whole window
        priced(LocalDate.of(2026, 8, 20), "Card", 5_000)
        priced(LocalDate.of(2026, 11, 10), "Card", 5_000)
        // a price that rose, once a month, no place: nothing to say
        priced(LocalDate.of(2026, 10, 1), "Bread", 100)
        priced(LocalDate.of(2026, 11, 1), "Bread", 120)
        // tracked by hand, no entries yet
        runBlocking { f.basket.track("Sugar") }

        val vm = visit()
        assertEquals(ItemLine.LastPlace("Friday market", was = 1_500), vm.line("Tomato"))
        assertEquals(ItemLine.Cheapest("Hyper", 8_000), vm.line("Oil"))
        assertEquals(ItemLine.Times(5), vm.line("Bus"))
        assertEquals(ItemLine.Fell, vm.line("Chicken"))
        assertEquals(ItemLine.Flat, vm.line("Card"))
        assertNull(vm.line("Bread"))
        val sugar = vm.state.value.items.single { it.name == "Sugar" }
        assertEquals(listOf(null, null, null), listOf(sugar.lastPrice, sugar.changePercent, sugar.line))
        assertEquals(47, vm.state.value.items.single { it.name == "Tomato" }.changePercent)
        assertEquals(2_200L, vm.state.value.items.single { it.name == "Tomato" }.lastPrice)
    }

    @Test fun theSuggestionShowsOnlyWhenTheBasketRose() {
        ticket()
        rice(1_100, 1_000)
        assertNull("a fall suggests nothing", visit().state.value.suggestion)
    }

    @Test fun theSuggestionFollowsTheRule() {
        ticket()
        rice(1_000, 1_100)
        assertEquals(SuggestedTicket(current = 24_000, suggested = 26_400), visit().state.value.suggestion)
    }

    @Test fun raisingStoresTheRaiseAndTheCardGivesWayToTheConfirmation() {
        ticket()
        rice(1_000, 1_100)
        val vm = visit()
        vm.raise()
        settle("raised") { vm.state.value.raisedTo == 26_400L && vm.state.value.suggestion == null }
        val s = saved()
        assertEquals(26_400L, s.ticketRaise)
        assertEquals(26_400L, runBlocking { db.dayTicketDao().forDate(today)!!.base })
        assertNull("the raise holds, so nothing more is suggested", visit().state.value.suggestion)
    }

    @Test fun leavingItHidesTheSuggestionForThisMonthOnly() {
        ticket()
        ticket(LocalDate.of(2026, 12, 2))
        rice(1_000, 1_100)
        val vm = visit()
        vm.leave()
        settle("dismissed") { vm.state.value.suggestion == null }
        assertEquals("2026-11", saved().suggestionDismissedMonth)
        assertNull("still hidden on a new visit this month", visit().state.value.suggestion)
        assertEquals("nothing was raised", null, saved().ticketRaise)

        clock.instant = ZonedDateTime.of(LocalDate.of(2026, 12, 2), LocalTime.of(9, 0), zone).toInstant()
        assertEquals("next month it is offered again", SuggestedTicket(24_000, 26_400), visit().state.value.suggestion)
    }

    @Test fun trackingAnItemByNameListsItAndABlankNameDoesNothing() {
        val vm = visit()
        vm.openAdd()
        settle("sheet") { vm.state.value.addSheet }
        vm.track("  ")
        settle("closed") { !vm.state.value.addSheet }
        assertTrue(vm.state.value.items.isEmpty())
        vm.track("Sugar")
        settle("tracked") { vm.state.value.items.map { it.name } == listOf("Sugar") }
    }

    @Test fun habitFactsShowOnlyWithData() {
        assertFalse(visit().state.value.hasHabits)
        runBlocking {
            f.entries.log(NewEntry(kind = EntryKind.SPENT, amount = 14_000, pocketId = cash, happenedAt = ZonedDateTime.of(LocalDate.of(2026, 11, 10), LocalTime.of(22, 0), zone).toInstant().toEpochMilli()))
        }
        val ui = visit().state.value
        assertTrue(ui.hasHabits)
        assertEquals(1, ui.habits.lateNight?.count)
        assertNull(ui.habits.weekday)
        assertNull(ui.habits.cashVsWalletPercent)
    }
}
