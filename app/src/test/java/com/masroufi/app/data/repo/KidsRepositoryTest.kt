package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EnvelopeCategoryEntity
import com.masroufi.app.data.db.EnvelopeEntity
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today Wednesday 2026-11-18: the handover is Saturday 11-21, so the open week is keyed 11-14. */
@RunWith(AndroidJUnit4::class)
class KidsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(LocalDate.of(2026, 11, 18)), zone)
    private val plus = DebugEntitlements(true)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    private val sat21 = LocalDate.of(2026, 11, 21)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()
    private fun later() { clock.instant = clock.instant.plusSeconds(60) }

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, plus)
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 5_000_000, color = 0))
    }

    private suspend fun child(
        name: String,
        weekly: Long,
        jarTarget: Long? = null,
        matchPer: Long? = null,
        matchAdd: Long? = null,
    ) = f.kids.addChild(name, 2017, 3, weekly, jarLabel = if (jarTarget != null) "العجلة" else null, jarTarget = jarTarget, matchPer = matchPer, matchAdd = matchAdd)

    /** The mock: Malak 70 a week, Omar 150. */
    private suspend fun two(): Pair<Long, Long> = child("ملك", 7_000, jarTarget = 150_000, matchPer = 1_000, matchAdd = 500) to child("عمر", 15_000)

    private suspend fun item() = db.recurringDao().allNow().single { it.kind == RecurringKind.KIDS && !it.archived }
    private suspend fun view(): KidsView = f.kids.view()
    private suspend fun kid(id: Long): ChildView = view().children.single { it.child.id == id }

    private inline fun <reified T : Throwable> failsWith(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("expected ${T::class.simpleName}")
        throw AssertionError()
    }

    @Test fun firstChildMakesTheEnvelopeItsCategoryAndTheWeeklyItem() = runTest {
        setUp()
        child("ملك", 7_000)
        val item = item()
        assertEquals(listOf<Any?>(KIDS_ITEM_NAME, 7_000L, ScheduleType.WEEKLY, 6, sat21), listOf(item.name, item.amount, item.scheduleType, item.scheduleDay, item.nextDue))
        val env = db.envelopeDao().allNow().single()
        assertEquals(listOf<Any?>(KIDS_NEW_ENVELOPE, env.id), listOf(env.name, item.envelopeId))
        val link = db.envelopeDao().linksNow().single()
        assertEquals(KIDS_ENVELOPE, db.categoryDao().allNow().single { it.id == link.categoryId }.name)
        child("عمر", 15_000)
        f.kids.handOver()
        assertEquals("one item and one envelope however many children and handovers", 1 to 1, db.recurringDao().allNow().size to db.envelopeDao().allNow().size)
    }

    @Test fun anEnvelopeNamedAlAyalIsUsedAsIs() = runTest {
        setUp()
        val cat = db.categoryDao().insert(CategoryEntity(name = "مصروف", icon = "grid", color = 0))
        val env = db.envelopeDao().insert(EnvelopeEntity(name = KIDS_ENVELOPE, color = 0, monthly = 88_000, kind = EnvelopeKind.SPENDING))
        db.envelopeDao().link(EnvelopeCategoryEntity(env, cat))
        child("ملك", 7_000)
        assertEquals(1, db.envelopeDao().allNow().size)
        assertEquals(env, item().envelopeId)
        val handover = f.kids.handOver()
        assertEquals("the handover is filed under the envelope's own category", cat, db.entryDao().byId(handover.entryId)!!.categoryId)
    }

    @Test fun handoverIsTheMocks70Plus150Is220AsOneEntryAndMovesTheWeekOn() = runTest {
        setUp()
        val (malak, omar) = two()
        f.tickets.printToday()
        assertEquals(22_000L, view().total)
        assertEquals(88_000L, view().monthEstimate)
        assertEquals("the item holds the week's total", 22_000L, item().amount)

        val result = f.kids.handOver()
        assertEquals(listOf(22_000L, listOf(7_000L, 15_000L)), listOf(result.total, result.perChild.map { it.amount }))
        val entry = db.entryDao().byId(result.entryId)!!
        assertEquals(listOf<Any?>(EntryKind.SPENT, 22_000L, cash, item().id), listOf(entry.kind, entry.amount, entry.pocketId, entry.recurringId))
        assertEquals(1, db.entryDao().countAllForRecurring(item().id))
        assertEquals("a week on", LocalDate.of(2026, 11, 28), item().nextDue)
        assertEquals(5_000_000L - 22_000, db.pocketDao().balanceOf(cash))
        assertEquals("it is reserved, not spent: the ticket has no hole", listOf<Any>(0L, 0), db.dayTicketDao().forDate(LocalDate.of(2026, 11, 18))!!.let { listOf(it.spent, it.entryCount) })
        assertEquals(listOf(7_000L, 15_000L), listOf(kid(malak).handedOver, kid(omar).handedOver))
    }

    @Test fun handoverNeedsAChildAndMoneyToHandOver() = runTest {
        setUp()
        failsWith<IllegalStateException> { f.kids.handOver() }
    }

    @Test fun choreBonusesAddToTheNextHandoverOnceAWeek() = runTest {
        setUp()
        val (malak, omar) = two()
        val room = f.kids.addChore("ملك رتّبت أوضتها", 1_000, malak)
        val bread = f.kids.addChore("عمر جاب العيش", 2_500)
        assertEquals(TickResult.TICKED, f.kids.toggleChore(room, malak))
        assertEquals(23_000L, view().total)
        assertEquals("the item follows", 23_000L, item().amount)
        assertEquals(TickResult.TICKED, f.kids.toggleChore(bread, omar))
        assertEquals(25_500L, view().total)
        assertEquals("a chore for any child is done once a week", TickResult.TAKEN, f.kids.toggleChore(bread, malak))
        assertEquals(25_500L, view().total)
        assertEquals(listOf<Long?>(malak, omar), view().chores.map { it.tickedBy })
        failsWith<IllegalArgumentException> { f.kids.toggleChore(room, omar) } // malak's chore
        assertEquals(TickResult.UNTICKED, f.kids.toggleChore(bread, omar))
        assertEquals(23_000L, view().total)
        f.kids.updateChore(view().chores.first().chore.copy(bonus = 4_000))
        assertEquals(26_000L, view().total)
        f.kids.deleteChore(room)
        assertEquals(22_000L, view().total)
    }

    @Test fun matchIsCountedInWholeStepsOnMoneySavedThisWeek() = runTest {
        setUp()
        val (malak, _) = two() // per 10, adds 5
        f.kids.save(malak, 900)
        assertEquals("under one step", 0L, kid(malak).handover.match)
        f.kids.save(malak, 1_600) // 25 saved
        assertEquals("25 saved at 10 -> 2 steps -> 10", 1_000L, kid(malak).handover.match)
        assertEquals(8_000L, kid(malak).handover.amount)
        f.kids.withdraw(malak, 1_000) // 15 left in the week
        assertEquals(500L, kid(malak).handover.match)
        failsWith<AboveChildJar> { f.kids.withdraw(malak, 9_999) }
    }

    @Test fun theMatchEntersTheJarAtHandoverAndTheNextWeekStartsAtZero() = runTest {
        setUp()
        val (malak, omar) = two()
        f.kids.save(malak, 2_500)
        later()
        f.kids.handOver()
        val jar = kid(malak).jar!!
        assertEquals("25 saved + 10 matched", 3_500L, jar.saved)
        assertEquals(listOf(2, false), listOf(jar.percent, jar.goalReached)) // 3,500 of 150,000
        assertEquals(0L, kid(malak).handover.match)
        assertEquals("what the handover gave: 70 + 10", 8_000L, kid(malak).handedOver)
        assertNull("no rule, no jar", kid(omar).jar)
        later()
        f.kids.save(malak, 1_000)
        assertEquals("a fresh week counts only its own savings", 500L, kid(malak).handover.match)
    }

    @Test fun jarGoalReachedFlagAndPercent() = runTest {
        setUp()
        val c = child("ملك", 7_000, jarTarget = 12_000)
        f.kids.save(c, 1_200)
        assertEquals(listOf(10, false), kid(c).jar!!.let { listOf(it.percent, it.goalReached) })
        f.kids.save(c, 11_000)
        assertEquals(listOf(100, true), kid(c).jar!!.let { listOf(it.percent, it.goalReached) })
    }

    @Test fun anAdvanceIsAtMostHalfTheAllowanceOneOpenAtATime() = runTest {
        setUp()
        val (malak, _) = two() // weekly 70 -> up to 35
        assertEquals(AdvanceRefusal.ABOVE_LIMIT to 3_500L, failsWith<AdvanceRefused> { f.kids.requestAdvance(malak, 3_600) }.let { it.reason to it.limit })
        val id = f.kids.requestAdvance(malak, 2_000)
        assertEquals(id, kid(malak).waitingAdvance?.id)
        assertEquals(AdvanceRefusal.OPEN_EXISTS, failsWith<AdvanceRefused> { f.kids.requestAdvance(malak, 100) }.reason)
        assertEquals("waiting takes nothing off yet", 22_000L, view().total)
        f.kids.answerAdvance(id, approve = true)
        assertEquals("ماشي، تتخصم", 20_000L, view().total)
        assertEquals(20_000L, item().amount)
        assertNull(kid(malak).waitingAdvance)
        // 20 of the 35 are taken: 15 is the most that is left to ask for.
        assertEquals(1_500L, failsWith<AdvanceRefused> { f.kids.requestAdvance(malak, 1_600) }.limit)
        val second = f.kids.requestAdvance(malak, 1_500)
        f.kids.answerAdvance(second, approve = true)
        assertEquals(18_500L, view().total)
        assertEquals(AdvanceRefusal.ABOVE_LIMIT, failsWith<AdvanceRefused> { f.kids.requestAdvance(malak, 1) }.reason)
        failsWith<IllegalArgumentException> { f.kids.answerAdvance(second, approve = false) } // answered already
    }

    @Test fun aDeclinedAdvanceTakesNothingOff() = runTest {
        setUp()
        val (malak, _) = two()
        val id = f.kids.requestAdvance(malak, 3_500)
        f.kids.answerAdvance(id, approve = false)
        assertEquals(22_000L, view().total)
        f.kids.requestAdvance(malak, 3_500) // a new one may be asked
    }

    @Test fun leftUntilHandoverIsHandedOverLessSpendingAndNeverBelowZero() = runTest {
        setUp()
        val (malak, _) = two()
        assertEquals("a new child starts with this week's allowance", 7_000L, kid(malak).left)
        f.kids.spend(malak, 2_500, "عصير")
        assertEquals(4_500L, kid(malak).left)
        val over = failsWith<AboveLeft> { f.kids.spend(malak, 5_000) }
        assertEquals(4_500L, over.left)
        assertEquals("nothing was recorded for it", 4_500L, kid(malak).left)
        assertEquals(1, db.kidsDao().spendsSince(malak, 0).size)
        f.kids.spend(malak, 4_500)
        assertEquals(0L, kid(malak).left)
        failsWith<AboveLeft> { f.kids.spend(malak, 1) }
        // An approved advance is cash in hand: it is spendable until the handover.
        val id = f.kids.requestAdvance(malak, 2_000)
        f.kids.answerAdvance(id, approve = true)
        assertEquals(2_000L, kid(malak).left)
    }

    @Test fun handoverStartsTheWeekOver() = runTest {
        setUp()
        val (malak, omar) = two()
        val bread = f.kids.addChore("عمر جاب العيش", 2_500)
        f.kids.toggleChore(bread, omar)
        f.kids.save(malak, 2_500)
        val approved = f.kids.requestAdvance(malak, 2_000)
        f.kids.answerAdvance(approved, approve = true)
        val waiting = f.kids.requestAdvance(omar, 5_000)
        f.kids.spend(malak, 3_000)
        assertEquals(7_000L + 1_000 - 2_000 + 17_500, view().total)
        later()
        val result = f.kids.handOver()
        assertEquals(listOf(6_000L, 17_500L), result.perChild.map { it.amount })
        // The new week: no ticks, no advances taken, no match, the item back to the plain allowance.
        assertEquals(listOf<Long?>(null), view().chores.map { it.tickedBy })
        assertEquals(listOf(7_000L, 15_000L), view().children.map { it.handover.amount })
        assertEquals(22_000L, item().amount)
        assertEquals("handed over 60 (70 + 10 match - 20 advance); the 30 spent are last week's", 6_000L, kid(malak).left)
        assertEquals(listOf(true, 0L), listOf(db.kidsDao().spendsSince(malak, 0).size == 1, view().children.first().handover.advances))
        // The advance nobody answered yet moves to the new week and can still be answered.
        assertEquals(waiting, kid(omar).waitingAdvance?.id)
        f.kids.answerAdvance(waiting, approve = true)
        assertEquals(17_000L, view().total)
    }

    @Test fun theItemIsReservedForEveryHandoverBeforePayday() = runTest {
        setUp()
        two()
        val today = LocalDate.of(2026, 11, 18)
        val s = f.settings.current() // payday 25: only Saturday 21 falls before it
        assertEquals(22_000L, f.tickets.poolToday(s, today).reserved)
        f.settings.setPayday(Payday.Day(8)) // the cycle runs to 12-08: Saturdays 21, 28 and 12-05
        assertEquals(66_000L, f.tickets.poolToday(f.settings.current(), today).reserved)

        val dao = db.recurringDao()
        assertEquals(0L, dao.unpaidDueBefore(sat21))
        assertEquals(22_000L, dao.unpaidDueBefore(sat21.plusDays(1)))
        assertEquals(22_000L, dao.unpaidDueBefore(sat21.plusDays(7)))
        assertEquals(44_000L, dao.unpaidDueBefore(sat21.plusDays(8)))
        assertEquals(22_000L, dao.unpaidFromPocketDueBefore(cash, sat21.plusDays(1)))
        assertEquals(44_000L, dao.unpaidFromPocketDueBefore(cash, sat21.plusDays(8)))

        f.kids.handOver() // 21 is paid: 28 and 12-05 are left before 12-08
        assertEquals(44_000L, f.tickets.poolToday(f.settings.current(), today).reserved)
    }

    @Test fun weeklyItemsAdvanceAndGoBackSevenDays() {
        val item = RecurringEntity(
            name = "x", amount = 1, pocketId = 1, scheduleDay = 6, nextDue = sat21, kind = RecurringKind.KIDS, scheduleType = ScheduleType.WEEKLY,
        )
        assertEquals(LocalDate.of(2026, 11, 28), item.shiftedDue(1))
        assertEquals(LocalDate.of(2026, 11, 14), item.shiftedDue(-1))
        assertEquals(LocalDate.of(2026, 12, 5), item.shiftedDue(2))
    }

    @Test fun deletingTheHandoverEntryBringsTheDueDateBack() = runTest {
        setUp()
        two()
        val entry = f.kids.handOver().entryId
        assertEquals(LocalDate.of(2026, 11, 28), item().nextDue)
        f.entries.delete(entry)
        assertEquals(sat21, item().nextDue)
    }

    @Test fun freeAllowsOneChildAndExtrasAreReadOnlyWhenPlusEnds() = runTest {
        setUp()
        plus.setDebugOverride(false)
        val first = child("ملك", 7_000)
        assertFalse(view().canAddChild)
        failsWith<PlusRequired> { child("عمر", 15_000) }
        assertEquals(1, view().children.size)

        plus.setDebugOverride(true)
        val second = child("عمر", 15_000)
        val bread = f.kids.addChore("خبز", 1_000)
        plus.setDebugOverride(false)
        assertEquals(listOf(true, false), view().children.map { it.editable })
        failsWith<PlusRequired> { f.kids.toggleChore(bread, second) }
        failsWith<PlusRequired> { f.kids.spend(second, 100) }
        failsWith<PlusRequired> { f.kids.save(second, 100) }
        failsWith<PlusRequired> { f.kids.requestAdvance(second, 100) }
        failsWith<PlusRequired> { f.kids.updateChild(view().children.last().child.copy(name = "x")) }
        failsWith<PlusRequired> { f.kids.deleteChild(second) }
        assertEquals("the first stays usable", TickResult.TICKED, f.kids.toggleChore(bread, first))
        f.kids.spend(first, 100)
        assertEquals("both are still handed their allowance", 23_000L, f.kids.handOver().total)
        assertEquals(15_000L, kid(second).handedOver)
    }

    @Test fun handoverDayMovesTheWeekAndKeepsItsTicks() = runTest {
        setUp()
        val (malak, _) = two()
        assertEquals(DayOfWeek.SATURDAY, f.kids.handoverDay())
        val room = f.kids.addChore("غرفة", 1_000, malak)
        f.kids.toggleChore(room, malak)
        f.kids.setHandoverDay(DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, f.kids.handoverDay())
        assertEquals(LocalDate.of(2026, 11, 22), item().nextDue)
        assertEquals("the tick followed the week", 8_000L, kid(malak).handover.amount)
        assertEquals(DayOfWeek.SUNDAY, view().handoverDay)
    }

    @Test fun editingAndDeletingChildrenKeepsTheItemTrue() = runTest {
        setUp()
        val (malak, omar) = two()
        f.kids.updateChild(kid(malak).child.copy(name = "ملك 2", weekly = 8_000, jarTarget = null, jarLabel = null, matchPer = null, matchAdd = null))
        assertEquals(listOf("ملك 2", 8_000L), kid(malak).child.let { listOf(it.name, it.weekly) })
        assertEquals(23_000L, item().amount)
        assertNull(kid(malak).jar)
        f.kids.deleteChild(malak)
        assertEquals(listOf(omar), view().children.map { it.child.id })
        assertEquals(15_000L, item().amount)
        f.kids.deleteChild(omar)
        assertTrue("the last child takes the weekly item with it", db.recurringDao().allNow().none { it.kind == RecurringKind.KIDS })
        val again = child("ياسين", 5_000)
        assertEquals(5_000L, item().amount)
        f.kids.handOver()
        f.kids.deleteChild(again)
        assertTrue("an item that has paid is archived, not deleted", db.recurringDao().allNow().single { it.kind == RecurringKind.KIDS }.archived)
    }

    @Test fun inputsAreChecked() = runTest {
        setUp()
        failsWith<IllegalArgumentException> { child("", 7_000) }
        failsWith<IllegalArgumentException> { child("ملك", 0) }
        failsWith<IllegalArgumentException> { f.kids.addChild("ملك", 2099, 1, 7_000) }
        failsWith<IllegalArgumentException> { f.kids.addChild("ملك", 2017, 1, 7_000, matchPer = 1_000) }
        failsWith<IllegalArgumentException> { f.kids.addChild("ملك", 2017, 1, 7_000, jarTarget = 0) }
        assertTrue(db.kidsDao().childrenNow().isEmpty())
        failsWith<IllegalArgumentException> { f.kids.addChore("x", 0) }
    }

    @Test fun observeEmitsTheView() = runTest {
        setUp()
        two()
        assertEquals(view(), f.kids.observe().first())
    }
}
