package com.masroufi.app.feature.buy

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.WishAnswer
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Today 2026-11-01 09:00 (PlanVmTestBase). The mock's numbers: a 1,800 pound item, a 240 ticket, 12 x 185. */
@RunWith(AndroidJUnit4::class)
class BeforeBuyViewModelTest : PlanVmTestBase() {
    private val today = LocalDate.of(2026, 11, 1)
    private lateinit var vm: BeforeBuyViewModel
    private val state get() = vm.state.value

    private fun cash(opening: Long = 500_000) =
        runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = opening, color = 0)) }

    private fun start() {
        vm = make(BeforeBuyViewModel::class, { it.state }) { BeforeBuyViewModel(f.wishes, plus) }
        settle("the context") { !state.loading }
    }

    private fun type(text: String) = text.forEach { vm.digit(it.digitToInt()) }

    @Test fun onlyTheTilesWithDataShow() {
        cash()
        start()
        assertNull("no price, no numbers", state.result)
        type("1800")
        settle("a price") { state.priceValue == 180_000L }
        assertEquals("no ticket, no income, no gold, no season saved: nothing to say", emptyList<BuyTile>(), state.tiles)
        assertEquals(180_000L, state.result?.price)
    }

    @Test fun ticketIncomeAndGoldShowAndSeasonStaysHiddenAtZero() {
        cash()
        runBlocking {
            db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 24_000, base = 24_000))
            f.settings.setRitualDone(900_000, null) // 9,000 a month: 1,800 is 6 days of work
            f.settings.setGoldPrice(500_000, today.toEpochDay()) // 5,000 a gram: 0.36 g
        }
        start()
        type("1800")
        settle("a price") { state.priceValue == 180_000L }
        assertEquals(listOf(BuyTile.TICKETS, BuyTile.WORK_DAYS, BuyTile.GOLD), state.tiles)
        assertEquals("7 and a half tickets", 75L, state.result?.tickets?.value)
        assertEquals(60L, state.result?.workDays?.value)
        assertEquals(36L, state.result?.goldGramsHundredths)
        assertNull(state.result?.seasonSharePercent)
    }

    @Test fun installmentIsPlusAndUsesTheTypedNumbers() {
        cash()
        start()
        type("1800")
        vm.focus(BuyField.MONTHLY)
        type("185")
        vm.focus(BuyField.MONTHS)
        type("12")
        settle("typed") { state.months.text == "12" }
        assertNull("not Plus: nothing computed", state.installment)

        plus.setDebugOverride(true)
        settle("Plus") { state.installment != null }
        val i = state.installment!!
        assertEquals(listOf(222_000L, 42_000L, 12), listOf(i.total, i.extra, i.months))
        assertEquals("185 a month over 30 days", 617L, i.perDay)
    }

    @Test fun monthsAreWholeNumbersAndAbsurdOnesShowNothing() {
        cash()
        plus.setDebugOverride(true)
        start()
        type("1800")
        vm.focus(BuyField.MONTHLY)
        type("185")
        vm.focus(BuyField.MONTHS)
        vm.digit(1); vm.point(); vm.digit(2)
        settle("typed") { state.months.text == "12" }
        assertEquals("a point does nothing in months", "12", state.months.text)
        type("5") // 125 months
        settle("too long") { state.months.text == "125" }
        assertNull(state.installment)
    }

    @Test fun parkingMakesARowWith48HoursLeftAndClearsTheForm() {
        cash()
        plus.setDebugOverride(true)
        start()
        assertFalse("nothing typed", vm.park())
        vm.setName("سماعة بلوتوث")
        assertFalse("no price yet", vm.park())
        type("1800")
        vm.focus(BuyField.MONTHLY)
        type("185")
        vm.focus(BuyField.MONTHS)
        type("12")
        settle("typed") { state.months.text == "12" }

        assertTrue(vm.park())
        settle("the list gains a row") { state.wishes.size == 1 }
        val row = state.wishes.single()
        assertEquals(listOf<Any?>("سماعة بلوتوث", 180_000L, 48L, false), listOf(row.wish.name, row.wish.price, row.hoursLeft, row.due))
        assertEquals(listOf<Any?>(18_500L, 12), listOf(row.wish.installmentMonthly, row.wish.installmentMonths))
        assertEquals(listOf("", "", "", 1), listOf(state.name, state.price.text, state.months.text, state.parked))
    }

    @Test fun freeUsersParkWithoutTheInstallment() {
        cash()
        start()
        vm.setName("جاكت")
        type("1200")
        assertTrue(vm.park())
        settle("a row") { state.wishes.size == 1 }
        assertNull(state.wishes.single().wish.installmentMonthly)
    }

    @Test fun theRowFallsDueAfter48HoursOnTheInjectedClock() {
        cash()
        start()
        vm.setName("جاكت")
        type("1200")
        vm.park()
        settle("a row") { state.wishes.size == 1 }

        clock.instant = clock.instant.plusSeconds(47 * 3600L)
        main.scheduler.advanceTimeBy(61_000)
        settle("one hour left") { state.wishes.singleOrNull()?.hoursLeft == 1L }
        assertFalse(state.wishes.single().due)

        clock.instant = clock.instant.plusSeconds(3600L)
        main.scheduler.advanceTimeBy(61_000)
        settle("due") { state.wishes.singleOrNull()?.due == true }
        assertEquals(0L, state.wishes.single().hoursLeft)
    }

    @Test fun changedMindMovesOnlyWhatIsSpendableAndTheRowShowsThat() {
        cash(opening = 50_000) // 500 spendable
        start()
        val id = runBlocking { f.wishes.park("ساعة", 95_000) }
        settle("the row") { state.wishes.size == 1 }
        vm.changedMind(state.wishes.single().wish)
        settle("the question") { vm.ask.value != null }
        assertEquals(listOf(id, 95_000L, 50_000L), vm.ask.value!!.let { listOf(it.wishId, it.price, it.offer) })

        vm.jarAnswer(true)
        settle("answered") { state.wishes.single().wish.answer == WishAnswer.CHANGED_MIND }
        assertEquals("the row can say 500, not 950", 50_000L, state.wishes.single().wish.movedToJar)
        assertEquals(95_000L, state.changedMindTotal)
        assertNull(vm.ask.value)
    }

    @Test fun changedMindWithNothingSpendableAnswersWithoutAsking() {
        cash(opening = 0)
        start()
        runBlocking { f.wishes.park("ساعة", 95_000) }
        settle("the row") { state.wishes.size == 1 }
        vm.changedMind(state.wishes.single().wish)
        settle("answered") { state.wishes.single().wish.answer == WishAnswer.CHANGED_MIND }
        assertNull(vm.ask.value)
        assertNull(state.wishes.single().wish.movedToJar)
    }

    @Test fun closingTheJarQuestionAnswersNothing() {
        cash()
        start()
        runBlocking { f.wishes.park("ساعة", 95_000) }
        settle("the row") { state.wishes.size == 1 }
        vm.changedMind(state.wishes.single().wish)
        settle("the question") { vm.ask.value != null }
        vm.askDismissed()
        main.scheduler.runCurrent()
        assertNull(vm.ask.value)
        assertEquals(WishAnswer.WAITING, state.wishes.single().wish.answer)
    }

    @Test fun boughtHandsOverThePrefillAndTheWishWaitsForTheSave() {
        cash()
        start()
        val id = runBlocking { f.wishes.park("ساعة", 95_000) }
        settle("the row") { state.wishes.size == 1 }
        val prefills = mutableListOf<AddPrefill>()
        vm.viewModelScope.launch { vm.prefill.collect { prefills += it } }
        vm.bought(id)
        settle("the prefill") { prefills.isNotEmpty() }
        assertEquals(listOf<Any?>(95_000L, "ساعة", id), prefills.single().let { listOf(it.amount, it.note, it.wishId) })
        assertEquals("cancelling the sheet leaves it waiting", WishAnswer.WAITING, state.wishes.single().wish.answer)
    }
}
