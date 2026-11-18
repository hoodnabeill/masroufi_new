package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.DebtEntity
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Payday Day(25). The mock (LastDays.dc.html): today Nov 16, 790 EGP in the cash pocket and a 250 bill due Nov 20, so
 * Spendable - Reserved = 540 over 9 days = 60 a day, the bill outside the pool. Offer tests run on Nov 12 with 3,900.
 */
@RunWith(AndroidJUnit4::class)
class EndOfMonthRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val nov16 = LocalDate.of(2026, 11, 16)
    private val nov20 = LocalDate.of(2026, 11, 20)
    private val nov25 = LocalDate.of(2026, 11, 25)
    private val clock = TestClock(at(nov16), zone)
    private val plus = DebugEntitlements()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()
    private fun today(date: LocalDate) { clock.instant = at(date) }

    private suspend fun TestScope.setUp(opening: Long, withBill: Boolean = false) {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, plus)
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = opening, color = 0))
        if (withBill) {
            db.recurringDao().insert(
                RecurringEntity(
                    name = "النت", amount = 25_000, pocketId = cash, scheduleDay = 20, nextDue = nov20, kind = RecurringKind.BILL,
                ),
            )
        }
    }

    private suspend fun spend(amount: Long, date: LocalDate, categoryId: Long? = null) {
        f.entries.log(
            NewEntry(
                kind = EntryKind.SPENT, amount = amount, pocketId = cash, categoryId = categoryId,
                happenedAt = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant().toEpochMilli(),
            ),
        )
    }

    private suspend fun printed(date: LocalDate, amount: Long, status: TicketStatus = TicketStatus.CLOSED_AUTO, eom: Boolean = false) =
        db.dayTicketDao().insert(DayTicketEntity(date = date, amount = amount, base = amount, status = status, endOfMonth = eom))

    private suspend fun category(name: String, essential: Boolean) =
        db.categoryDao().insert(CategoryEntity(name = name, icon = "x", color = 0, essential = essential))

    @Test fun smallTicketIs540Over9DaysWithTheBillOutsideThePool() = runTest {
        setUp(79_000, withBill = true)
        val s = f.eom.state()
        assertEquals("Spendable - Reserved", 54_000L, s.pool)
        assertEquals(9, s.daysLeft)
        assertEquals(6_000L, s.ticket)
        assertEquals((0L..8L).map { nov16.plusDays(it) }, s.strip.map { it.date })
        assertTrue("every small ticket is equal", s.strip.all { it.amount == 6_000L })
        val billDay = s.strip.single { it.bills.isNotEmpty() }
        assertEquals(nov20, billDay.date)
        assertEquals(25_000L, billDay.bills.single().amount)
        assertEquals("النت", billDay.bills.single().name)
        assertEquals("one more no-spend day: 60 / 8 days = 7", 700L, s.boost)
        assertFalse(s.on)
        f.eom.switchOn()
        assertEquals(6_000L, f.tickets.printToday().amount)
    }

    @Test fun modePrintsTheSmallTicketAndTheModeBeatsTheBasketRaise() = runTest {
        setUp(79_000, withBill = true)
        f.settings.setTicketRaise(9_000, nov25.toEpochDay())
        assertEquals("raise lifts the normal ticket", 9_000L, f.tickets.printToday().amount)
        f.eom.switchOn()
        val on = db.dayTicketDao().forDate(nov16)!!
        assertEquals("mode: small ticket, no raise", Triple(6_000L, 6_000L, 0L), Triple(on.amount, on.base, on.carriedIn))
        f.eom.switchOff()
        assertEquals("off: the raise holds again", 9_000L, db.dayTicketDao().forDate(nov16)!!.amount)
        f.eom.switchOn()
        today(nov16.plusDays(1))
        // 54,000 / 8 days = 6,750 -> 6,700; the raise (90) does not lift it.
        assertEquals("next day's print in the mode", 6_700L, f.tickets.printToday().amount)
    }

    @Test fun leftoverStaysInThePoolAndLiftsTomorrow() = runTest {
        setUp(79_000, withBill = true)
        f.eom.switchOn()
        assertEquals(6_000L, f.tickets.printToday().amount)
        assertEquals("the mode's default destination keeps it in the pool", LeftoverDestination.TOMORROW, f.home.closeDayData(nov16).leftoverDefault)
        spend(1_000, nov16)
        val r = f.tickets.closeDay(nov16, LeftoverDestination.TOMORROW)
        assertEquals(5_000L, r.leftover)
        assertEquals("nothing moved to the jar", 0L, db.jarDao().totalNow(Pot.JAR))
        today(nov16.plusDays(1))
        val t = f.tickets.printToday()
        // (79,000 - 1,000 - 25,000) / 8 days = 6,625 -> 6,600; the leftover is in the pool, not carried on top.
        assertEquals(Pair(6_600L, 0L), Pair(t.amount, t.carriedIn))
        f.eom.switchOff()
        assertEquals("off: the jar is the default again", LeftoverDestination.JAR, f.home.closeDayData(nov16).leftoverDefault)
    }

    @Test fun anEndOfMonthRowAlwaysCountsAsInTheMode() = runTest {
        setUp(20_000, withBill = true) // 200 - 250 reserved
        val t = f.tickets.printToday()
        assertTrue(t.endOfMonth)
        assertEquals(-5_000L, t.eomPool)
        val s = f.eom.state()
        assertEquals(listOf(false, true, true), listOf(s.on, s.inMode, s.offer))
        assertTrue(f.eomMode.isOn.first())
        f.eom.switchOn()
        assertTrue("still an end-of-month row in the mode", db.dayTicketDao().forDate(nov16)!!.endOfMonth)
    }

    @Test fun isOnFollowsTheSwitch() = runTest {
        setUp(79_000, withBill = true)
        assertFalse(f.eomMode.isOn.first())
        f.eom.switchOn()
        assertTrue(f.eomMode.isOn.first())
        f.eom.switchOff()
        assertFalse(f.eomMode.isOn.first())
    }

    @Test fun offerNeedsThreePrintedDays() = runTest {
        today(nov12)
        setUp(390_000)
        printed(nov12.minusDays(1), 30_000)
        printed(nov12.minusDays(2), 30_000)
        printed(nov12, 10_000, TicketStatus.OPEN)
        assertFalse("two printed days are not enough", f.eom.state().offer)
        printed(nov12.minusDays(3), 30_000)
        assertTrue(f.eom.state().offer)
    }

    @Test fun offerIsUnderSixtyPercentOfTheAverage() = runTest {
        today(nov12)
        setUp(390_000)
        (1L..3L).forEach { printed(nov12.minusDays(it), 30_000) }
        val id = printed(nov12, 18_000, TicketStatus.OPEN)
        assertFalse("exactly 60% is not under", f.eom.state().offer)
        db.dayTicketDao().update(db.dayTicketDao().forDate(nov12)!!.copy(id = id, amount = 17_900))
        assertTrue(f.eom.state().offer)
    }

    @Test fun offerIsDismissedForTheCycleAndHiddenInTheMode() = runTest {
        today(nov12)
        setUp(390_000)
        printed(nov12, 0, TicketStatus.OPEN, eom = true)
        assertTrue(f.eom.state().offer)
        f.eom.dismissOffer()
        assertFalse(f.eom.state().offer)
        assertEquals(nov25.toEpochDay(), f.settings.current().eomOfferDismissedFor)
        today(nov25)
        db.dayTicketDao().update(db.dayTicketDao().forDate(nov12)!!.copy(status = TicketStatus.CLOSED_AUTO))
        printed(nov25, 0, TicketStatus.OPEN, eom = true)
        assertTrue("a new cycle offers again", f.eom.state().offer)
        f.eom.switchOn()
        assertFalse("nothing to offer once it is on", f.eom.state().offer)
    }

    @Test fun noSpendDaysCountAndTightFistIsEarnedOnTheSecondOnlyOnce() = runTest {
        setUp(79_000, withBill = true)
        f.eom.switchOn()
        f.tickets.printToday()
        val first = f.tickets.closeDay(nov16, LeftoverDestination.TOMORROW)
        assertEquals(1, f.eom.state().noSpendDays)
        assertFalse(StampKind.TIGHT_FIST in first.newStamps)
        today(nov16.plusDays(1))
        f.tickets.printToday()
        val second = f.tickets.closeDay(nov16.plusDays(1), LeftoverDestination.TOMORROW)
        assertEquals(2, f.eom.state().noSpendDays)
        assertTrue(StampKind.TIGHT_FIST in second.newStamps)
        today(nov16.plusDays(2))
        f.tickets.printToday()
        val third = f.tickets.closeDay(nov16.plusDays(2), LeftoverDestination.TOMORROW)
        assertFalse("the stamp is earned once", StampKind.TIGHT_FIST in third.newStamps)
        assertEquals(1, db.stampDao().kinds().count { it == StampKind.TIGHT_FIST })
    }

    @Test fun aDayWithASpendOrClosedOutsideTheModeIsNotANoSpendDay() = runTest {
        setUp(79_000, withBill = true)
        f.tickets.printToday()
        f.tickets.closeDay(nov16, LeftoverDestination.JAR) // the mode is off
        f.eom.switchOn()
        today(nov16.plusDays(1))
        f.tickets.printToday()
        spend(500, nov16.plusDays(1))
        f.tickets.closeDay(nov16.plusDays(1), LeftoverDestination.TOMORROW)
        assertEquals(0, f.eom.state().noSpendDays)
    }

    @Test fun habitsAreTheTwoBiggestNonEssentialCategoriesThisCycle() = runTest {
        today(nov12)
        setUp(390_000)
        val coffee = category("قهوة", essential = false)
        val food = category("أكل", essential = false)
        val other = category("حاجة تانية", essential = false)
        val groceries = category("بقالة", essential = true)
        spend(31_000, nov12, coffee)
        spend(28_000, nov12, food)
        spend(10_000, nov12, other)
        spend(90_000, nov12, groceries)
        spend(99_000, nov12.minusDays(30), coffee) // the cycle before: not counted
        val habits = f.eom.state().habits
        assertEquals(listOf(coffee, food), habits.map { it.categoryId })
        assertEquals(listOf(31_000L, 28_000L), habits.map { it.spent })
        assertEquals(listOf(false, false), habits.map { it.paused })
        f.eom.setPaused(coffee, true)
        assertEquals(listOf(true, false), f.eom.state().habits.map { it.paused })
        f.eom.setPaused(coffee, false)
        assertEquals(listOf(false, false), f.eom.state().habits.map { it.paused })
    }

    @Test fun rescueSourcesHideAtZeroAndNameTheBiggestDebtor() = runTest {
        today(nov12)
        setUp(390_000)
        assertEquals(Triple(null, null, null), f.eom.state().rescue.let { Triple(it.freeJar, it.owedToMe, it.emergency) })
        db.jarDao().insert(JarMovementEntity(pot = Pot.JAR, amount = 184_000, source = JarSource.MANUAL, at = 0))
        db.jarDao().insert(JarMovementEntity(pot = Pot.EMERGENCY, amount = 60_000, source = JarSource.MANUAL, at = 0))
        val ahmed = db.peopleDao().insertPerson(PersonEntity(name = "أحمد سمير"))
        val mona = db.peopleDao().insertPerson(PersonEntity(name = "منى"))
        db.peopleDao().insertDebt(DebtEntity(personId = ahmed, direction = DebtDirection.THEY_OWE, amount = 50_000, date = nov12))
        db.peopleDao().insertDebt(DebtEntity(personId = mona, direction = DebtDirection.THEY_OWE, amount = 20_000, date = nov12))
        db.peopleDao().insertDebt(DebtEntity(personId = mona, direction = DebtDirection.I_OWE, amount = 90_000, date = nov12))
        val r = f.eom.state().rescue
        assertEquals(184_000L, r.freeJar)
        assertEquals(60_000L, r.emergency)
        assertEquals(Pair("أحمد سمير", 50_000L), r.owedToMe!!.let { it.name to it.amount })
    }

    @Test fun theModeEndsByItselfAtPayday() = runTest {
        today(nov12)
        setUp(390_000)
        f.eom.switchOn()
        f.tickets.printToday()
        assertTrue(f.settings.current().eomOn)
        today(nov25)
        val t = f.tickets.printToday()
        assertFalse("payday: the mode is off", f.settings.current().eomOn)
        assertFalse(f.eomMode.isOn.first())
        assertNull(t.eomPool)
        assertTrue(f.settings.current().eomNoSpend.isEmpty())
    }
}
