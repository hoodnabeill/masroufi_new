package com.masroufi.app.feature.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.data.repo.SubscriptionAnswer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Today 2026-11-01. */
@RunWith(AndroidJUnit4::class)
class SubscriptionsViewModelTest : PlanVmTestBase() {
    private val today = LocalDate.of(2026, 11, 1)
    private lateinit var vm: SubscriptionsViewModel
    private var cash = 0L
    private val state get() = vm.state.value

    @Before fun open() {
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, color = 0)) }
        vm = make(SubscriptionsViewModel::class, { it.state }) { SubscriptionsViewModel(f.subscriptions, f.recurring, f.people, f.settings, clock, deletes) }
    }

    private fun sub(
        name: String,
        amount: Long,
        due: LocalDate = today.plusDays(10),
        type: ScheduleType = ScheduleType.MONTHLY,
        trialEnd: LocalDate? = null,
        yearlyPrice: Long? = null,
        shared: Int = 1,
    ) = runBlocking { f.subscriptions.add(name, amount, cash, due, type, trialEnd, yearlyPrice, shared) }

    @Test fun totalsTicketsAndYearAndRowsByNextRenewal() {
        runBlocking { db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 10_000, base = 10_000)) }
        sub("شاهد", 120_000, LocalDate.of(2027, 3, 3), ScheduleType.YEARLY, yearlyPrice = 120_000)
        sub("نت البيت", 25_000, LocalDate.of(2026, 11, 20))
        settle("two rows") { state.rows.size == 2 }
        assertEquals(35_000L, state.monthlyTotal)
        assertEquals(25_000L * 12 + 120_000, state.perYear)
        assertEquals(3L, state.tickets)
        assertEquals(listOf("نت البيت", "شاهد"), state.rows.map { it.view.item.name })
        assertEquals(19L, state.rows.first().daysTo)
        assertFalse("a name already added is not a chip", "نت البيت" in state.chips)
        assertTrue("نتفليكس" in state.chips)
    }

    @Test fun noTicketMeansNoTicketCount() {
        sub("نت البيت", 25_000)
        settle("row") { state.rows.size == 1 }
        assertEquals(0L, state.tickets)
    }

    @Test fun trialCardIsTheNearestEndingWithinSevenDays() {
        sub("far", 7_000, trialEnd = today.plusDays(10))
        sub("ended", 7_000, trialEnd = today.minusDays(1))
        val mid = sub("mid", 7_000, trialEnd = today.plusDays(5))
        val near = sub("near", 7_000, trialEnd = today.plusDays(3))
        settle("four rows") { state.rows.size == 4 }
        assertEquals("near", state.trial?.view?.item?.name)
        assertEquals(3L, state.trial?.view?.trialDaysLeft)
        assertFalse(state.trial!!.reminded)

        vm.remindAboutTrial(state.trial!!.view)
        settle("reminded") { state.trial?.reminded == true }
        assertEquals(today.plusDays(2).toEpochDay(), runBlocking { f.settings.current() }.trialReminders[near])

        vm.keepAfterTrial(state.trial!!.view)
        settle("next trial") { state.trial?.view?.item?.id == mid }
        assertNull(runBlocking { f.recurring.get(near) }?.trialEnd)
    }

    @Test fun noTrialCardWhenNothingEndsSoon() {
        sub("far", 7_000, trialEnd = today.plusDays(8))
        settle("row") { state.rows.size == 1 }
        assertNull(state.trial)
    }

    @Test fun questionIsDueOnceAThenGoneAfterAnswering() {
        val id = sub("نتفليكس", 22_000)
        settle("question") { state.question?.view?.item?.id == id }
        assertNull("no saving line without two not-really answers", state.question?.view?.stopSaving)

        vm.answer(id, SubscriptionAnswer.YES)
        settle("answered") { state.question == null }
        assertEquals(1, state.rows.size)
    }

    @Test fun twoNotReallyShowTheSavingLineAndGoldWhenThereIsAPrice() {
        val id = runBlocking {
            db.recurringDao().insert(
                RecurringEntity(
                    name = "جيم", amount = 40_000, pocketId = cash, scheduleDay = 5, nextDue = today.plusDays(4),
                    kind = RecurringKind.SUBSCRIPTION, answers = "NOT_REALLY,NOT_REALLY",
                ),
            )
        }
        settle("question") { state.question?.view?.item?.id == id }
        assertEquals(480_000L, state.question?.view?.stopSaving)
        assertNull("no gold price typed", state.question?.grams)

        runBlocking { f.settings.setGoldPrice(1_000, 1) }
        settle("grams") { state.question?.grams == 480L }

        vm.answer(id, SubscriptionAnswer.STOPPED)
        settle("stopped") { state.question == null && state.rows.isEmpty() }
    }

    @Test fun sharedRowAndYearlyHint() {
        val net = sub("نتفليكس", 22_000, LocalDate.of(2026, 11, 23), shared = 4)
        sub("أنغامي", 10_000, LocalDate.of(2026, 11, 9), yearlyPrice = 100_000)
        settle("two rows") { state.rows.size == 2 }
        val shared = state.rows.first { it.view.item.id == net }.view
        assertEquals(5_500L, shared.userShare)
        assertEquals(16_500L, shared.othersOwe)
        assertEquals(4, shared.item.sharedWith)
        assertEquals(20_000L, state.rows.first { it.view.item.name == "أنغامي" }.view.yearlySaving)
        assertNotNull(state.rows.first { it.view.item.name == "أنغامي" })
    }

    @Test fun sharedPersonIsTheGroupsEntryInPeople() {
        val netflix = sub("نتفليكس", 20_000, shared = 3)
        val view = checkNotNull(runBlocking { f.subscriptions.observe().first() }.items.firstOrNull { it.item.id == netflix })
        assertNull("no payment yet, no person", runBlocking { vm.sharedPersonId(view) })
        val id = runBlocking { f.people.addPerson(com.masroufi.app.domain.sharedGroupName("نتفليكس", 2)) }
        assertEquals(id, runBlocking { vm.sharedPersonId(view) })
    }
}
