package com.masroufi.app.notify

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.NotifKind
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private class FakeSink : NoticeSink {
    var allowed = true
    val posted = mutableListOf<Notice>()
    override fun canPost() = allowed
    override fun post(notice: Notice): Boolean { posted += notice; return true }
}

/** Now is Tuesday 2026-11-17 10:00 Cairo; one bill (النت, 250) is due tomorrow. */
@RunWith(AndroidJUnit4::class)
class NotificationServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(10, 0), zone).toInstant(), zone)
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private val sink = FakeSink()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private suspend fun TestScope.setUp(onboarded: Boolean = true): NotificationService {
        f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        f.settings.setOnboarded(onboarded)
        val pocket = f.pockets.addPocket("cash", PocketKind.CASH, 390_000, countsInTicket = true, color = 0)
        f.recurring.add("النت", 25_000, pocket, today.plusDays(1), kind = RecurringKind.BILL)
        return NotificationService(f.settings, db.dayTicketDao(), db.recurringDao(), f.gameyas, f.wishes, f.duty, f.pockets, f.plusRepo, sink, clock)
    }

    @Test fun aDueBillIsPostedOnceAndCounted() = runTest {
        val service = setUp()
        assertEquals(1, service.run())
        assertEquals(listOf("النت", "250"), sink.posted.single().args)
        assertEquals(NotifKind.BILL, sink.posted.single().kind)
        assertEquals(0, service.run()) // the next hourly check finds it sent
        val s = f.settings.current()
        assertEquals(1, s.notifCount)
        assertEquals(today.toEpochDay(), s.notifDay)
    }

    @Test fun theLockTurnsTheAmountIntoDots() = runTest {
        val service = setUp()
        f.settings.setLockOn(true)
        service.run()
        assertEquals(listOf("النت", NOTIF_DOTS), sink.posted.single().args)
    }

    @Test fun nothingIsPostedOrCountedWhileThePermissionIsMissing() = runTest {
        val service = setUp()
        sink.allowed = false
        assertEquals(0, service.run())
        assertTrue(sink.posted.isEmpty())
        assertEquals(0, f.settings.current().notifCount)
        sink.allowed = true
        assertEquals(1, service.run()) // once it is granted, what was due still goes out
    }

    @Test fun nothingIsPostedBeforeOnboarding() = runTest {
        val service = setUp(onboarded = false)
        assertEquals(0, service.run())
    }

    @Test fun aSwitchedOffKindIsNotPosted() = runTest {
        val service = setUp()
        f.settings.setNotifKind(NotifKind.BILL, false)
        assertEquals(0, service.run())
    }
}
