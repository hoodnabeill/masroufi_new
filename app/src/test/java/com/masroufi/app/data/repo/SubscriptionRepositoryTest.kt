package com.masroufi.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.data.db.TazkaraDatabase
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
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today 2026-11-17, one cash pocket. */
@RunWith(AndroidJUnit4::class)
class SubscriptionRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val clock = TestClock(at(d(11, 17), 9), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private var cash = 0L

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()

    private suspend fun TestScope.setUp() {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, color = 0))
    }

    private suspend fun view() = f.subscriptions.observe().first()

    @Test fun totalsTicketsAndShares() = runTest {
        setUp()
        f.subscriptions.add("نتفليكس", 15_000, cash, d(12, 1))
        f.subscriptions.add("اشتراك الجيم", 360_000, cash, LocalDate.of(2027, 3, 1), scheduleType = ScheduleType.YEARLY, yearlyPrice = 360_000)
        val net = f.subscriptions.add("نت البيت", 60_000, cash, d(11, 20), sharedWith = 3)
        val old = f.subscriptions.add("شاهد", 10_000, cash, d(11, 25))
        db.recurringDao().update(checkNotNull(db.recurringDao().byId(old)).copy(archived = true))
        f.recurring.add("كهربا", 40_000, cash, d(11, 20), kind = RecurringKind.BILL)

        var v = view()
        assertEquals("monthly + yearly ÷ 12 + shared at full price", 105_000L, v.monthlyTotal)
        assertEquals(1_260_000L, v.perYear)
        assertEquals("no ticket today", 0L, v.tickets)
        val shared = v.items.single { it.item.id == net }
        assertEquals(20_000L to 40_000L, shared.userShare to shared.othersOwe)
        assertEquals("by next renewal", listOf("نت البيت", "نتفليكس", "اشتراك الجيم"), v.items.map { it.item.name })

        db.dayTicketDao().insert(DayTicketEntity(date = d(11, 17), amount = 20_000, base = 20_000))
        v = view()
        assertEquals("105,000 ÷ 20,000 tickets, floored", 5L, v.tickets)
    }

    @Test fun yearlyHintAndTrialCountdown() = runTest {
        setUp()
        val monthly = f.subscriptions.add("سبوتيفاي", 15_000, cash, d(12, 1), yearlyPrice = 150_000, trialEnd = d(11, 22))
        val ended = f.subscriptions.add("أنغامي", 5_000, cash, d(12, 1), trialEnd = d(11, 10))
        val v = view()
        val s = v.items.single { it.item.id == monthly }
        assertEquals("15,000 × 12 − 150,000", 30_000L, s.yearlySaving)
        assertEquals(5L, s.trialDaysLeft)
        assertEquals("cost if not cancelled", 15_000L, s.item.amount)
        assertNull("trial over", v.items.single { it.item.id == ended }.trialDaysLeft)
        assertNull("no yearly price: no hint", v.items.single { it.item.id == ended }.yearlySaving)
    }

    @Test fun questionOfTheMonthOncePerMonth() = runTest {
        setUp()
        fun ms(date: LocalDate) = at(date, 12).toEpochMilli()
        val a = f.subscriptions.add("أ", 15_000, cash, d(12, 1))
        val b = f.subscriptions.add("ب", 15_000, cash, d(12, 2))
        val c = f.subscriptions.add("ج", 15_000, cash, d(12, 3))
        db.recurringDao().update(checkNotNull(db.recurringDao().byId(a)).copy(lastAskedAt = ms(d(9, 3))))
        db.recurringDao().update(checkNotNull(db.recurringDao().byId(c)).copy(lastAskedAt = ms(d(10, 3))))
        assertEquals("never asked first", b, view().question?.item?.id)

        f.subscriptions.answer(b, SubscriptionAnswer.NOT_REALLY)
        assertNull("asked this month: no more questions", view().question)

        clock.instant = at(d(12, 2), 9)
        assertEquals("next month: the longest since asked", a, view().question?.item?.id)
        f.subscriptions.answer(b, SubscriptionAnswer.NOT_REALLY)
        val sb = view().items.single { it.item.id == b }
        assertEquals("NOT_REALLY,NOT_REALLY", sb.item.answers)
        assertEquals("two 'not really' in a row: monthly × 12", 180_000L, sb.stopSaving)
        assertNull(view().items.single { it.item.id == a }.stopSaving)
    }

    @Test fun answersKeepLastThreeAndStoppedArchives() = runTest {
        setUp()
        val id = f.subscriptions.add("يوتيوب بريميوم", 6_000, cash, d(12, 1))
        for (ans in listOf(SubscriptionAnswer.YES, SubscriptionAnswer.NOT_REALLY, SubscriptionAnswer.YES)) f.subscriptions.answer(id, ans)
        assertEquals("YES,NOT_REALLY,YES", db.recurringDao().byId(id)?.answers)
        f.subscriptions.answer(id, SubscriptionAnswer.STOPPED)
        val item = checkNotNull(db.recurringDao().byId(id))
        assertEquals("NOT_REALLY,YES,STOPPED", item.answers)
        assertEquals(clock.millis(), item.lastAskedAt)
        assertTrue("stopped: archived", item.archived)
        assertEquals(emptyList<RecurringEntity>(), view().items.map { it.item })
    }

    @Test fun commonNamesList() {
        assertEquals(11, COMMON_SUBSCRIPTIONS.size)
        assertEquals("نت البيت", COMMON_SUBSCRIPTIONS.first())
        assertEquals("حاجة تانية", COMMON_SUBSCRIPTIONS.last())
    }
}
