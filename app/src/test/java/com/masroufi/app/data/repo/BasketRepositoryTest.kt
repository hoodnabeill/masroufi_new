package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketCalc
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today 2026-11-17 (a Tuesday) 09:00 Cairo; the 3-month window starts 2026-08-17. */
@RunWith(AndroidJUnit4::class)
class BasketRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val plus = DebugEntitlements(true)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L
    private var wallet = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private suspend fun TestScope.setUp(opening: Long = 100_000) {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, plus)
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = opening, color = 0))
        wallet = db.pocketDao().insert(PocketEntity(name = "wallet", kind = PocketKind.MOBILE_WALLET, openingBalance = opening, color = 0))
    }

    private fun at(day: LocalDate, hour: Int = 12) = ZonedDateTime.of(day, LocalTime.of(hour, 0), zone).toInstant().toEpochMilli()

    /** A priced spend through the same path the add sheet uses. */
    private suspend fun priced(day: LocalDate, item: String, unitPrice: Long, quantity: Int = 1, note: String? = null) {
        f.entries.log(
            NewEntry(
                kind = EntryKind.SPENT, amount = unitPrice * quantity, pocketId = cash, note = note, happenedAt = at(day),
                trackedItemId = f.basket.itemIdFor(item), unitPrice = unitPrice,
            ),
        )
    }

    private suspend fun data() = (f.basket.read() as BasketResult.Ready).data

    private suspend fun plainSpend(day: LocalDate, amount: Long, pocket: Long = cash, hour: Int = 12) {
        db.entryDao().insert(EntryEntity(kind = EntryKind.SPENT, amount = amount, pocketId = pocket, happenedAt = at(day, hour), loggedAt = at(day, hour)))
    }

    @Test fun lockedWithoutPlusAndNeverThrows() = runTest {
        setUp()
        plus.setDebugOverride(false)
        assertEquals(BasketResult.Locked, f.basket.read())
        assertNull(f.basket.applySuggestedTicket())
        plus.setDebugOverride(true)
        assertTrue(f.basket.read() is BasketResult.Ready)
    }

    @Test fun nothingIsInventedWithoutData() = runTest {
        setUp()
        val d = data()
        assertEquals(emptyList<BasketItemView>(), d.items)
        assertNull(d.change)
        assertNull(d.suggestion)
        assertEquals(HabitFacts(null, null, null), d.habits)
    }

    @Test fun anItemIsTrackedAfterTwoPricedEntriesUnderTheSameName() = runTest {
        setUp()
        priced(today.minusDays(10), "Milk", 2_000)
        assertTrue("one entry is not enough", data().items.isEmpty())
        priced(today.minusDays(3), " milk ", 2_200)
        val item = data().items.single()
        assertEquals(listOf("Milk", 2_200L), listOf(item.item.name, item.lastPrice))
        assertEquals("both entries point at one item", 1, db.trackedItemDao().allNow().size)
        assertEquals(listOf(item.item.id), db.entryDao().pricedEntries().map { it.trackedItemId }.distinct())
    }

    @Test fun arabicSpellingsOfTheSameNameMatch() = runTest {
        setUp()
        assertEquals(f.basket.itemIdFor("أرز"), f.basket.itemIdFor("ارز "))
    }

    @Test fun anAddedItemShowsBeforeItHasPrices() = runTest {
        setUp()
        f.basket.track("زيت")
        val item = data().items.single()
        assertEquals(listOf("زيت", null, null), listOf(item.item.name, item.lastPrice, item.changePercent))
        assertTrue(item.item.addedByUser)
        assertEquals("tracking twice is one item", f.basket.track("زيت"), f.basket.track("زيت "))
    }

    @Test fun itemChangePlacesAndFrequency() = runTest {
        setUp()
        priced(LocalDate.of(2026, 9, 20), "Tomato", 1_500, note = "Hyper")
        priced(LocalDate.of(2026, 10, 20), "Tomato", 1_800, note = "Friday market")
        priced(LocalDate.of(2026, 11, 10), "Tomato", 2_200, note = "Friday market")
        val item = data().items.single()
        assertEquals("latest vs first in the window: +47%", 47, item.changePercent)
        assertEquals("Friday market", item.lastPlace)
        assertEquals(CheapestPlace("Hyper", 1_500), item.cheapest)
        assertEquals("3 entries over about 2 months", 2, item.timesPerMonth)
    }

    @Test fun anOldPriceOutsideTheWindowIsNotTheFirstPrice() = runTest {
        setUp()
        priced(LocalDate.of(2026, 5, 1), "Oil", 4_000)
        priced(LocalDate.of(2026, 10, 1), "Oil", 8_000)
        priced(LocalDate.of(2026, 11, 1), "Oil", 8_500)
        assertEquals("8,000 to 8,500 is 6.25%, not the 112% from the May price", 6, data().items.single().changePercent)
    }

    @Test fun noCheapestPlaceFromOnePlace() = runTest {
        setUp()
        priced(today.minusDays(20), "Eggs", 5_000, note = "Hyper")
        priced(today.minusDays(5), "Eggs", 5_500, note = "Hyper")
        priced(today.minusDays(1), "Eggs", 5_100)
        val item = data().items.single()
        assertNull(item.cheapest)
        assertNull("the latest entry has no note: no place is made up", item.lastPlace)
    }

    @Test fun basketChangeFromUsualQuantitiesAndTheSuggestedTicket() = runTest {
        setUp()
        db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 24_000, base = 24_000))
        priced(LocalDate.of(2026, 10, 25), "Rice", 1_000, quantity = 2)
        priced(LocalDate.of(2026, 11, 10), "Rice", 1_100, quantity = 2)
        val d = data()
        assertEquals("4 a month at 10 then, at 11 now", BasketChange(then = 4_000, now = 4_400, percent = 10), d.change)
        assertEquals(SuggestedTicket(current = 24_000, suggested = 26_400), d.suggestion)
    }

    @Test fun aFallingBasketSuggestsNothing() = runTest {
        setUp()
        db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 24_000, base = 24_000))
        priced(LocalDate.of(2026, 10, 25), "Rice", 1_100)
        priced(LocalDate.of(2026, 11, 10), "Rice", 1_000)
        val d = data()
        assertEquals(-9, d.change?.percent)
        assertNull(d.suggestion)
    }

    @Test fun noSuggestionWithoutATicket() = runTest {
        setUp()
        priced(LocalDate.of(2026, 10, 25), "Rice", 1_000)
        priced(LocalDate.of(2026, 11, 10), "Rice", 1_100)
        assertEquals(10, data().change?.percent)
        assertNull(data().suggestion)
    }

    @Test fun applyingRaisesTodaysOpenTicketAndTheTicketHoldsItUntilPayday() = runTest {
        setUp(opening = 30_000)
        f.settings.setPayday(Payday.Day(25))
        db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 24_000, base = 24_000))
        priced(LocalDate.of(2026, 10, 25), "Rice", 1_000, quantity = 2)
        priced(LocalDate.of(2026, 11, 10), "Rice", 1_100, quantity = 2)

        assertEquals(26_400L, f.basket.applySuggestedTicket())
        val row = db.dayTicketDao().forDate(today)!!
        assertEquals(listOf(26_400L, 26_400L), listOf(row.base, row.amount))
        val s = f.settings.current()
        assertEquals(listOf<Long?>(26_400L, LocalDate.of(2026, 11, 25).toEpochDay()), listOf(s.ticketRaise, s.ticketRaiseUntil))

        // Tomorrow the money alone gives 12,500 a day; the accepted raise holds until payday.
        val tomorrow = today.plusDays(1)
        val raised = f.tickets.basisToday(s, tomorrow, startAfterPayday = false).calc as TicketCalc.Printed
        assertEquals(26_400L, raised.base)
        val after = f.tickets.basisToday(s, LocalDate.of(2026, 11, 25), startAfterPayday = false).calc as TicketCalc.Printed
        assertTrue("payday ends the raise", after.base != 26_400L)
    }

    @Test fun mostExpensiveWeekdayAgainstTheRest() = runTest {
        setUp()
        for (i in 28 downTo 1) {
            val d = today.minusDays(i.toLong())
            plainSpend(d, if (d.dayOfWeek == DayOfWeek.FRIDAY) 20_000 else 5_000)
        }
        val w = data().habits.weekday!!
        assertEquals(listOf(DayOfWeek.FRIDAY, 20_000L, 4_800L), listOf(w.day, w.average, w.restAverage))
    }

    @Test fun noWeekdayFactFromLessThanTwoWeeks() = runTest {
        setUp()
        for (i in 6 downTo 1) plainSpend(today.minusDays(i.toLong()), 5_000)
        assertNull(data().habits.weekday)
    }

    @Test fun lateNightEntriesThisMonthOnly() = runTest {
        setUp()
        plainSpend(LocalDate.of(2026, 11, 3), 14_000, hour = 22)
        plainSpend(LocalDate.of(2026, 11, 10), 14_000, hour = 21)
        plainSpend(LocalDate.of(2026, 11, 11), 3_000, hour = 20)
        plainSpend(LocalDate.of(2026, 10, 30), 9_000, hour = 23)
        assertEquals(LateNightFact(count = 2, sum = 28_000), data().habits.lateNight)
    }

    @Test fun cashVersusWalletAverage() = runTest {
        setUp()
        repeat(3) { plainSpend(today.minusDays(it + 1L), 8_200, cash) }
        repeat(3) { plainSpend(today.minusDays(it + 1L), 10_000, wallet) }
        assertEquals(-18, data().habits.cashVsWalletPercent)
    }

    @Test fun cashVersusWalletNeedsThreeOfEach() = runTest {
        setUp()
        repeat(3) { plainSpend(today.minusDays(it + 1L), 8_200, cash) }
        repeat(2) { plainSpend(today.minusDays(it + 1L), 10_000, wallet) }
        assertNull(data().habits.cashVsWalletPercent)
    }

    @Test fun editingAnEntryKeepsItsItemAndUnitPrice() = runTest {
        setUp()
        priced(today.minusDays(1), "Milk", 2_000)
        val e = db.entryDao().pricedEntries().single()
        f.entries.edit(e.id, NewEntry(kind = EntryKind.SPENT, amount = 4_000, pocketId = cash, happenedAt = e.happenedAt))
        val kept = db.entryDao().byId(e.id)!!
        assertEquals(listOf(e.trackedItemId, 2_000L, 4_000L), listOf(kept.trackedItemId, kept.unitPrice, kept.amount))
    }
}
