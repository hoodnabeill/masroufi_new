package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.spentToday
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today 2026-11-17; cash holds 1,000 EGP. */
@RunWith(AndroidJUnit4::class)
class PeopleRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 100_000, color = 0))
    }

    private suspend fun balance() = db.pocketDao().balanceOf(cash)
    private suspend fun entries() = db.entryDao().forDay(0, Long.MAX_VALUE)
    private suspend fun punched() = spentToday(db.entryDao().forDayWithPocket(0, Long.MAX_VALUE))
    private suspend fun rejected(block: suspend () -> Unit) = runCatching { block() }.exceptionOrNull() is IllegalArgumentException

    @Test fun lendMovesThePocketButIsNeitherSpendingNorIncome() = runTest {
        setUp()
        val ahmed = f.people.addPerson("أحمد")
        val debt = f.people.lend(ahmed, 50_000, cash, "سلفة", LocalDate.of(2026, 11, 30))
        assertEquals(50_000L, balance())
        val e = entries().single()
        assertEquals(listOf<Any?>(EntryKind.DEBT, -50_000L, debt), listOf(e.kind, e.amount, e.debtId))
        assertEquals("no hole", 0L, punched())
        assertNull("never anchors the pay cycle", db.entryDao().latestReceivedAt())
        val summary = f.roll.month(YearMonth.of(2026, 11)).first().summary
        assertEquals("not received, not spent", 0L to 0L, summary.received to summary.spent)

        f.people.lend(ahmed, 10_000, null)
        assertEquals("no pocket: a record only", 1, entries().size)
        assertEquals(60_000L, f.people.observeTotals().first().theyOwe)
    }

    @Test fun borrowBringsMoneyIn() = runTest {
        setUp()
        val mama = f.people.addPerson("ماما")
        f.people.borrow(mama, 30_000, cash)
        assertEquals(130_000L, balance())
        assertNull("not income", db.entryDao().latestReceivedAt())
    }

    @Test fun totalsAndPerPersonBalances() = runTest {
        setUp()
        val ahmed = f.people.addPerson("أحمد")
        val mama = f.people.addPerson("ماما")
        val kiosk = f.people.addPerson("كشك عم حسن", kind = PersonKind.SHOP)
        f.people.lend(ahmed, 50_000, null)
        f.people.borrow(mama, 50_000, null)
        f.people.addTabItem(kiosk, "عيش", 7_000)
        f.people.addTabItem(kiosk, "لبن", 5_000)

        assertEquals(PeopleTotals(theyOwe = 50_000, theyOweCount = 1, iOwe = 62_000, onTab = 12_000), f.people.observeTotals().first())
        val all = f.people.observeList(PeopleFilter.ALL).first()
        assertEquals(
            listOf(50_000L, -50_000L, -12_000L),
            listOf(ahmed, mama, kiosk).map { id -> all.single { it.person.id == id }.balance },
        )
        assertEquals(setOf(ahmed, mama), f.people.observeList(PeopleFilter.LOANS).first().map { it.person.id }.toSet())
        assertEquals(listOf(kiosk), f.people.observeList(PeopleFilter.TAB).first().map { it.person.id })
        assertTrue(f.people.observeList(PeopleFilter.DUTY).first().isEmpty())
        f.duty.addGift(f.duty.addOccasion("فرح", today, OccasionOwner.MINE), ahmed, 50_000, GiftDirection.RECEIVED)
        assertEquals(listOf(ahmed), f.people.observeList(PeopleFilter.DUTY).first().map { it.person.id })
    }

    @Test fun repayInPartsBothWays() = runTest {
        setUp()
        val ahmed = f.people.addPerson("أحمد")
        val debt = f.people.lend(ahmed, 50_000, cash)
        f.people.repay(debt, 20_000, cash)
        assertEquals("they paid part back", 70_000L, balance())
        assertEquals(30_000L, checkNotNull(db.peopleDao().debtById(debt)).amount)
        assertEquals(30_000L, f.people.observeTotals().first().theyOwe)
        f.people.repay(debt, 30_000, null)
        assertTrue("settled", checkNotNull(db.peopleDao().debtById(debt)).settled)
        assertEquals("a record only", 70_000L, balance())
        assertEquals(0L, f.people.observeTotals().first().theyOwe)
        assertTrue("already settled", rejected { f.people.repay(debt, 1, cash) })

        val mama = f.people.addPerson("ماما")
        val owed = f.people.borrow(mama, 30_000, cash)
        assertTrue("more than is open", rejected { f.people.repay(owed, 30_001, cash) })
        f.people.repay(owed, 30_000, cash)
        assertEquals("I paid it back", 70_000L, balance())
        assertTrue(checkNotNull(db.peopleDao().debtById(owed)).settled)
        assertTrue(rejected { f.people.lend(ahmed, 0, cash) })
    }

    @Test fun splitPunchesOwnShareAndOwesTheRest() = runTest {
        setUp()
        val a = f.people.addPerson("كريم")
        val b = f.people.addPerson("وهبة")
        val r = f.people.split(90_000, cash, mapOf(a to 30_000L, b to 30_000L), note = "عشا")
        assertEquals(10_000L, balance())
        assertEquals("my 300 is one hole", 30_000L, punched())
        val kinds = entries().associateBy { it.kind }
        assertEquals(30_000L, checkNotNull(kinds[EntryKind.SPENT]).amount)
        assertEquals(-60_000L to null, checkNotNull(kinds[EntryKind.DEBT]).let { it.amount to it.debtId })
        val debts = r.debtIds.map { checkNotNull(db.peopleDao().debtById(it)) }
        assertEquals(listOf(30_000L, 30_000L), debts.map { it.amount })
        assertEquals(setOf(DebtDirection.THEY_OWE), debts.map { it.direction }.toSet())
        assertEquals(60_000L, f.people.observeTotals().first().theyOwe)

        val all = f.people.split(20_000, cash, mapOf(a to 20_000L))
        assertNull("I paid nothing of my own: no spend", all.ownEntryId)
        assertEquals(30_000L, punched())
        assertTrue("shares above the total", rejected { f.people.split(10_000, cash, mapOf(a to 10_001L)) })
    }

    @Test fun settlingTheTabIsOneSpendThatPunches() = runTest {
        setUp()
        val kiosk = f.people.addPerson("كشك", kind = PersonKind.SHOP)
        f.people.addTabItem(kiosk, "عيش", 7_000)
        f.people.addTabItem(kiosk, "لبن", 5_000)
        assertEquals("nothing moves while it is on the tab", 100_000L, balance())
        assertTrue("not a shop", rejected { f.people.addTabItem(f.people.addPerson("فلان"), "x", 1) })

        val id = f.people.settleTab(kiosk, cash)
        assertEquals(88_000L, balance())
        assertEquals(listOf(EntryKind.SPENT to 12_000L), entries().map { it.kind to it.amount })
        assertEquals("a spend today punches", 12_000L, punched())
        assertEquals(listOf(true, true), db.peopleDao().tabItems().first().map { it.settled })
        assertEquals(listOf<Long?>(id, id), db.peopleDao().tabItems().first().map { it.settledEntryId })
        assertEquals(0L, f.people.observeTotals().first().onTab)
        assertTrue("nothing left to settle", rejected { f.people.settleTab(kiosk, cash) })
    }

    @Test fun dueAndOverdueDebts() = runTest {
        setUp()
        val a = f.people.addPerson("أحمد")
        val late = f.people.lend(a, 1_000, null, dueDate = today.minusDays(3))
        val now = f.people.borrow(a, 2_000, null, dueDate = today)
        f.people.lend(a, 3_000, null, dueDate = today.plusDays(1))
        f.people.lend(a, 4_000, null)
        assertEquals(listOf(late, now), f.people.dueOrOverdue().map { it.debt.id })
        f.people.repay(late, 1_000, null)
        assertEquals("settled ones are not due", listOf(now), f.people.dueOrOverdue().map { it.debt.id })
        assertEquals("only what I owe", listOf(now), f.people.debtsIOwe().map { it.debt.id })
    }

    @Test fun sharedSubscriptionPaymentCreatesAndDeletingItRemovesTheDebt() = runTest {
        setUp()
        val net = f.subscriptions.add("نت البيت", 60_000, cash, today, sharedWith = 3)
        val solo = f.subscriptions.add("شاهد", 10_000, cash, today)
        f.recurring.markPaid(solo, cash)
        assertTrue("not shared: no debt", db.peopleDao().openDebts().isEmpty())

        val payment = f.recurring.markPaid(net, cash)
        val debt = db.peopleDao().openDebts().single()
        assertEquals(listOf<Any?>(DebtDirection.THEY_OWE, 40_000L, net, today), listOf(debt.direction, debt.amount, debt.sourceRecurringId, debt.date))
        assertEquals("نت البيت · 2 صحاب", checkNotNull(db.peopleDao().personById(debt.personId)).name)
        assertEquals("the others' total", 40_000L, f.people.observeTotals().first().theyOwe)

        f.entries.delete(payment)
        assertTrue("payment deleted: the unsettled debt goes", db.peopleDao().openDebts().isEmpty())

        val again = f.recurring.markPaid(net, cash)
        f.people.repay(db.peopleDao().openDebts().single().id, 40_000, null)
        f.entries.delete(again)
        assertEquals("settled stays", 1, db.peopleDao().debts().first().size)
    }

    @Test fun deletingAPersonTakesTheirDebtsAndTab() = runTest {
        setUp()
        val a = f.people.addPerson("أحمد", kind = PersonKind.SHOP)
        f.people.lend(a, 10_000, cash)
        f.people.addTabItem(a, "x", 100)
        f.people.deletePerson(a)
        assertTrue(db.peopleDao().debts().first().isEmpty() && db.peopleDao().tabItems().first().isEmpty())
        assertEquals("the money entry stays, unlinked", listOf<Long?>(null), entries().map { it.debtId })
        assertEquals(90_000L, balance())
    }
}
