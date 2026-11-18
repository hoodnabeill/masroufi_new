package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today 2026-11-12, payday 25, one counting cash pocket with 50,000 EGP. */
@RunWith(AndroidJUnit4::class)
class InstallmentRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(LocalDate.of(2026, 11, 12)), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()
    private fun today(date: LocalDate) { clock.instant = at(date) }

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 5_000_000, color = 0))
    }

    /** The mock's three plans: 185 x 2 left, 650 x 7 left, 500 x 15 left = 1,335 a month, 12,420 still owed. */
    private suspend fun mockPlans(): Triple<Long, Long, Long> = Triple(
        f.installments.add("الموبايل", 18_500, day = 5, pocketId = cash, totalCount = 12, paidBefore = 10),
        f.installments.add("التلاجة", 65_000, day = 12, pocketId = cash, totalCount = 24, paidBefore = 17),
        f.installments.add("العربية", 50_000, day = 20, pocketId = cash, totalCount = 20, paidBefore = 5),
    )

    @Test fun mockNumbersMonthlyPerDayRemainingFreedomAndGrowth() = runTest {
        setUp()
        val (phone, fridge, car) = mockPlans()
        val v = f.installments.view()
        assertEquals(133_500L, v.monthly)
        assertEquals("1,335 / 30 = 44.5: the tie goes down to 44", 4_400L, v.perDay)
        assertEquals(1_242_000L, v.remaining)
        assertEquals("the last plan ends in January 2028", YearMonth.of(2028, 1), v.freedom)
        // The phone's next payment is 12-05 (the 11-05 one is past) and 2 are left; the fridge's is today.
        assertEquals(
            listOf(
                TimelineStep(phone, YearMonth.of(2027, 1), 600),
                TimelineStep(fridge, YearMonth.of(2027, 5), 2_100),
                TimelineStep(car, YearMonth.of(2028, 1), 1_600),
            ),
            v.timeline,
        )
        assertEquals("soonest due first", listOf(fridge, car, phone), v.plans.map { it.item.id })
    }

    @Test fun perDayRoundsToTheNearestPoundAndGrowthFloors() {
        assertEquals(4_400L, installmentPerDay(133_500)) // 44.5
        assertEquals(4_500L, installmentPerDay(133_501)) // 44.5003
        assertEquals(4_400L, installmentPerDay(131_999)) // 43.99
        assertEquals(0L, installmentPerDay(0))
        assertEquals(600L, ticketGrowth(18_500)) // 6.17
        assertEquals(2_100L, ticketGrowth(65_000)) // 21.67
        assertEquals(1_600L, ticketGrowth(50_000)) // 16.67
        assertEquals(0L, ticketGrowth(2_999))
    }

    @Test fun openEndedPlanCountsMonthlyButNotRemainingOrFreedom() = runTest {
        setUp()
        f.installments.add("قسط", 30_000, day = 20, pocketId = cash)
        mockPlans()
        val v = f.installments.view()
        assertEquals(163_500L, v.monthly)
        assertEquals(1_242_000L, v.remaining)
        assertEquals(YearMonth.of(2028, 1), v.freedom)
        assertEquals(3, v.timeline.size)
        val open = v.plans.first { it.total == null }
        assertNull(open.left)
        assertNull(open.endMonth)
        assertNull(open.progress)
    }

    @Test fun paidCountComesFromEntriesSoDeletingOneFixesIt() = runTest {
        setUp()
        val id = f.installments.add("الموبايل", 18_500, day = 12, pocketId = cash, totalCount = 12, paidBefore = 3)
        f.recurring.markPaid(id, cash)
        val second = f.recurring.markPaid(id, cash)
        assertEquals(5, f.installments.view().plans.single().paid)
        f.entries.delete(second)
        val plan = f.installments.view().plans.single()
        assertEquals(listOf(4, 8), listOf(plan.paid, plan.left))
        assertEquals(YearMonth.of(2027, 7), plan.endMonth) // next payment 12-12, 8 left
    }

    @Test fun dayOfLoggingHappensOnceAndUndoByDeleteSticks() = runTest {
        setUp()
        val id = f.installments.add("الموبايل", 18_500, day = 12, pocketId = cash, totalCount = 12, paidBefore = 3)
        f.tickets.printToday()
        val entries = db.entryDao().forDay(at(d(11, 12)).toEpochMilli() - 3_600_000, at(d(11, 13)).toEpochMilli())
        assertEquals("one payment against the plan", listOf(id), entries.map { it.recurringId })
        assertEquals(18_500L, entries.single().amount)
        assertEquals(cash, entries.single().pocketId)
        assertEquals("the due date moved on", d(12, 12), db.recurringDao().byId(id)!!.nextDue)
        assertEquals(4, f.installments.view().plans.single().paid)
        assertEquals(5_000_000L - 18_500, db.pocketDao().balanceOf(cash))

        f.installments.logDue(d(11, 12))
        f.tickets.printToday()
        assertEquals("nothing is logged twice", 1, db.entryDao().countAllForRecurring(id))

        f.entries.delete(entries.single().id) // the roll's undo
        val back = db.recurringDao().byId(id)!!
        assertEquals(listOf(d(11, 12), 3), listOf(back.nextDue, f.installments.view().plans.single().paid))
        f.installments.logDue(d(11, 12))
        today(d(11, 13))
        f.tickets.printToday()
        assertEquals("an undone payment is not logged again by itself", 0, db.entryDao().countAllForRecurring(id))
    }

    @Test fun planDueTomorrowIsNotLoggedToday() = runTest {
        setUp()
        val id = f.installments.add("الموبايل", 18_500, day = 13, pocketId = cash, totalCount = 12)
        f.tickets.printToday()
        assertEquals(0, db.entryDao().countAllForRecurring(id))
        today(d(11, 13))
        f.tickets.printToday()
        assertEquals(1, db.entryDao().countAllForRecurring(id))
    }

    @Test fun lastPaymentFinishesThePlanAndItReservesNothing() = runTest {
        setUp()
        val id = f.installments.add("الموبايل", 18_500, day = 12, pocketId = cash, totalCount = 3, paidBefore = 2)
        assertEquals("due today, unpaid: reserved", 18_500L, db.recurringDao().unpaidDueBefore(d(11, 25)))
        f.tickets.printToday()
        val v = f.installments.view()
        val plan = v.plans.single()
        assertTrue(plan.finished)
        assertEquals(listOf(3, 0), listOf(plan.paid, plan.left))
        assertNull(plan.endMonth)
        assertEquals(0L, v.monthly)
        assertEquals(0L, v.remaining)
        assertNull(v.freedom)
        // The next due date (12-12) is before the following payday (12-25), yet the finished plan holds nothing back.
        assertEquals("finished plans reserve nothing", 0L, db.recurringDao().unpaidDueBefore(d(12, 25)))
        today(d(12, 12))
        f.tickets.printToday()
        assertEquals("and never log again", 1, db.entryDao().countAllForRecurring(id))
    }

    @Test fun earlyPayoffOnlyForTheLeastRemainingWhenTheFreeJarCoversItOncePerMonth() = runTest {
        setUp()
        val (phone, _, _) = mockPlans() // phone: 2 left x 185 = 370 is the least
        f.installments.add("قسط", 1_000, day = 20, pocketId = cash) // open-ended: never suggested
        f.jar.deposit(30_000)
        assertNull("30,000 < 37,000", f.installments.earlyPayoff())
        f.jar.deposit(10_000)
        val goal = f.jar.createGoal("سفر", 500_000)
        f.jar.earmark(5_000, goalId = goal)
        assertNull("40,000 in the jar but only 35,000 is free", f.installments.earlyPayoff())
        f.jar.release(5_000, goalId = goal)
        val offer = checkNotNull(f.installments.earlyPayoff())
        assertEquals(listOf(phone, 37_000L), listOf(offer.plan.item.id, offer.amount))

        f.installments.dismissEarlyPayoff(phone)
        assertNull("dismissed for the month", f.installments.earlyPayoff())
        today(d(12, 1))
        assertEquals("back next month", phone, f.installments.earlyPayoff()?.plan?.item?.id)
    }

    @Test fun payOffLogsTheEntryWithdrawsFromTheJarAndFinishesThePlan() = runTest {
        setUp()
        val (phone, _, _) = mockPlans()
        f.jar.deposit(40_000)
        f.installments.payOff(phone, 36_500) // the user typed a different amount

        val plan = f.installments.view().plans.first { it.item.id == phone }
        assertTrue(plan.finished)
        assertEquals(listOf(12, 0), listOf(plan.paid, plan.left))
        assertEquals("one entry for the confirmed amount", 1, db.entryDao().countAllForRecurring(phone))
        assertEquals(5_000_000L - 36_500, db.pocketDao().balanceOf(cash))
        assertEquals("jar withdrew the same", 3_500L, f.jar.observe().first().total)
        assertEquals(1_242_000L - 37_000, f.installments.view().remaining)
        assertEquals(133_500L - 18_500, f.installments.view().monthly)
    }

    @Test fun payOffAboveTheFreeJarChangesNothing() = runTest {
        setUp()
        val (phone, _, _) = mockPlans()
        f.jar.deposit(10_000)
        try {
            f.installments.payOff(phone, 37_000)
            fail("the jar does not cover it")
        } catch (e: AboveFreeJar) {
            assertEquals(10_000L, e.free)
        }
        assertEquals(0, db.entryDao().countAllForRecurring(phone))
        assertNotNull(f.installments.view().plans.first { it.item.id == phone }.left)
    }

    @Test fun editMovesTheDueDayAndDeleteArchivesAPlanWithPayments() = runTest {
        setUp()
        val id = f.installments.add("الموبايل", 18_500, day = 12, pocketId = cash, totalCount = 12)
        f.installments.edit(id, "موبايل", 20_000, day = 28, pocketId = cash, totalCount = 10, paidBefore = 2)
        val item = db.recurringDao().byId(id)!!
        assertEquals(listOf("موبايل", 20_000L, d(11, 28), 10, 2), listOf(item.name, item.amount, item.nextDue, item.totalCount, item.paidBefore))
        f.recurring.markPaid(id, cash)
        f.installments.delete(id)
        assertTrue(f.installments.view().plans.isEmpty())
        assertTrue(db.recurringDao().byId(id)!!.archived)
        val other = f.installments.add("تاني", 1_000, day = 1, pocketId = cash)
        f.installments.delete(other)
        assertNull(db.recurringDao().byId(other))
    }
}
