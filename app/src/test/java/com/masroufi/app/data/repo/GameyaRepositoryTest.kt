package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.punches
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Gameya mock: 10 people, 2,000 a month, started in August, today 2026-11-17 (month 4); the user is turn 8 (March).
 */
@RunWith(AndroidJUnit4::class)
class GameyaRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(LocalDate.of(2026, 11, 17)), zone)
    private val plus = DebugEntitlements()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L
    private val names = listOf("منى", "طارق", "سلمى", "هدى", "عمرو", "نهى", "حسام", "دينا", "شريف")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun at(date: LocalDate): Instant = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant()

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, plus)
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 1_000_000, color = 0))
    }

    private suspend fun create(name: String = "جمعية الشغل") =
        f.gameyas.create(name, 200_000, YearMonth.of(2026, 8), 8, names.map { NewGameyaMember(it) }, cash, dueDay = 5)

    private suspend fun view(id: Long) = f.gameyas.observe().first().single { it.gameya.id == id }

    @Test fun createdGameyaHasTheNumbersOfTheMock() = runTest {
        setUp()
        val id = create()
        val v = view(id)
        assertEquals("monthly × members", 2_000_000L, v.payout)
        assertEquals("start + turn − 1", YearMonth.of(2027, 3), v.myTurnMonth)
        assertEquals("شهر 4 من 10", listOf(4, 10), listOf(v.monthNumber, v.people))
        assertEquals("بعد 4 شهور", 4L, v.monthsUntil)
        assertEquals("turns around mine", listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), v.wheel.map { it.turn })
        assertEquals(listOf(8), v.wheel.filter { it.isMe }.map { it.turn })
        assertEquals(
            listOf(SlotState.PAID_OUT, SlotState.PAID_OUT, SlotState.PAID_OUT, SlotState.THIS_MONTH, SlotState.WAITING),
            v.wheel.take(5).map { it.state },
        )
        assertEquals("the member after my turn skips it", listOf("دينا", "شريف"), v.wheel.takeLast(2).map { it.member?.name })

        val item = checkNotNull(db.recurringDao().byId(checkNotNull(v.gameya.recurringId)))
        assertEquals(
            listOf<Any?>(RecurringKind.GAMEYA, 200_000L, cash, LocalDate.of(2026, 11, 5), "جمعية الشغل"),
            listOf(item.kind, item.amount, item.pocketId, item.nextDue, item.name),
        )
    }

    @Test fun paidSoFarAndThisMonthsPayments() = runTest {
        setUp()
        val id = create()
        val members = db.gameyaDao().membersOf(id).associateBy { it.name }
        listOf(8, 9, 10, 11).forEach { f.gameyas.setPaid(id, null, YearMonth.of(2026, it), true) }
        listOf("منى", "طارق", "سلمى", "هدى", "نهى", "حسام").forEach { f.gameyas.setPaid(id, members.getValue(it).id, YearMonth.of(2026, 11), true) }
        f.gameyas.setPaid(id, members.getValue("عمرو").id, YearMonth.of(2026, 10), true) // last month's does not count now
        f.gameyas.setPaid(id, members.getValue("نهى").id, YearMonth.of(2026, 11), false)
        f.gameyas.setPaid(id, members.getValue("نهى").id, YearMonth.of(2026, 11), true)

        val v = view(id)
        assertEquals("200,000 × the 4 months I paid", 800_000L, v.myPaidSoFar)
        assertTrue(v.myPaidThisMonth)
        assertEquals(listOf("عمرو", "دينا", "شريف"), v.unpaidMembers.map { it.name })
        assertEquals("6 of the others and me", 7, v.paidCount)
    }

    @Test fun payingMyInstallmentLogsTheRecurringPayment() = runTest {
        setUp()
        val id = create()
        f.gameyas.payMine(id, cash)
        assertEquals(800_000L, db.pocketDao().balanceOf(cash))
        val v = view(id)
        assertTrue(v.myPaidThisMonth)
        assertEquals("the item moved on a month", LocalDate.of(2026, 12, 5), db.recurringDao().byId(checkNotNull(v.gameya.recurringId))?.nextDue)
        assertEquals("a fixed cost never punches", 0, db.entryDao().forDayWithPocket(0, Long.MAX_VALUE).count { it.punches })
    }

    @Test fun swapTurnsWithAMember() = runTest {
        setUp()
        val id = create()
        val hoda = db.gameyaDao().membersOf(id).first { it.name == "هدى" }
        f.gameyas.swapTurn(id, hoda.id)
        val v = view(id)
        assertEquals(YearMonth.of(2026, 11), v.myTurnMonth)
        assertEquals(0L, v.monthsUntil)
        assertTrue(v.wheel.single { it.turn == 4 }.isMe)
        assertEquals("هدى", v.wheel.single { it.turn == 8 }.member?.name)
    }

    @Test fun planRowsAndWhatIsLeftUnplanned() = runTest {
        setUp()
        val id = create()
        f.gameyas.addPlanRow(id, "العيد الكبير", 900_000)
        val phone = f.gameyas.addPlanRow(id, "موبايل جديد", 800_000)
        f.gameyas.addPlanRow(id, "الحصّالة", 300_000, goalId = 3)
        assertEquals(0L, view(id).unplanned)
        f.gameyas.deletePlanRow(phone)
        val v = view(id)
        assertEquals(800_000L, v.unplanned)
        assertEquals(listOf<Long?>(null, 3L), v.plan.map { it.goalId })
    }

    @Test fun statementIsPlainArabic() = runTest {
        setUp()
        val id = create()
        val members = db.gameyaDao().membersOf(id).associateBy { it.name }
        listOf("منى", "طارق", "سلمى", "هدى", "نهى", "حسام").forEach { f.gameyas.setPaid(id, members.getValue(it).id, YearMonth.of(2026, 11), true) }
        f.gameyas.setPaid(id, null, YearMonth.of(2026, 11), true)
        assertEquals(
            listOf(
                "كشف جمعية الشغل",
                "القسط الشهري: 2,000 · القبض: 20,000",
                "شهر 4 من 10",
                "قبضوا: منى، طارق، سلمى",
                "دور الشهر ده: هدى",
                "دفعوا 7 من 10",
                "لسه: عمرو، دينا، شريف",
            ).joinToString("\n"),
            f.gameyas.statement(id),
        )
    }

    @Test fun updateRenamesAndTheInstallmentItemFollows() = runTest {
        setUp()
        val id = create()
        f.gameyas.update(id, " جمعية البيت ", 300_000)
        val v = view(id)
        assertEquals(listOf<Any?>("جمعية البيت", 300_000L, 3_000_000L), listOf(v.gameya.name, v.gameya.monthly, v.payout))
        val item = checkNotNull(db.recurringDao().byId(checkNotNull(v.gameya.recurringId)))
        assertEquals(listOf<Any?>("جمعية البيت", 300_000L), listOf(item.name, item.amount))
    }

    @Test fun beforeTheStartAndAfterTheEnd() = runTest {
        setUp()
        val id = create()
        clock.instant = at(LocalDate.of(2026, 7, 15))
        var v = view(id)
        assertEquals(listOf(0, 8L, YearMonth.of(2026, 8)), listOf(v.monthNumber, v.monthsUntil, v.thisMonth))
        assertTrue("nobody's turn yet", v.wheel.all { it.state == SlotState.WAITING })
        clock.instant = at(LocalDate.of(2027, 9, 1))
        v = view(id)
        assertTrue(v.finished)
        assertTrue("everyone was paid out", v.wheel.all { it.state == SlotState.PAID_OUT })
        assertEquals(YearMonth.of(2027, 5), v.thisMonth)
    }

    @Test fun freeHasOneActiveGameya() = runTest {
        setUp()
        val first = create()
        assertTrue(runCatching { create("تانية") }.exceptionOrNull() is PlusRequired)
        plus.setDebugOverride(true)
        create("تانية")
        assertEquals(2, f.gameyas.observe().first().size)
        plus.setDebugOverride(false)
        f.gameyas.archive(first)
        assertEquals("archived: out of the list", 1, f.gameyas.observe().first().size)
        assertNull("its installment goes too", db.recurringDao().byId(1))
        assertTrue(runCatching { create("تالتة") }.exceptionOrNull() is PlusRequired)
    }
}
