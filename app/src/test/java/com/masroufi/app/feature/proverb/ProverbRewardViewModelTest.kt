package com.masroufi.app.feature.proverb

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.ProverbCollectedEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.TicketStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

/** Saturday 2026-11-07 starts the week; the days below are Tue 10, Wed 11 and Thu 12 November. */
@RunWith(AndroidJUnit4::class)
class ProverbRewardViewModelTest {
    private val main = StandardTestDispatcher()
    private val nov12 = LocalDate.of(2026, 11, 12)
    private lateinit var db: TazkaraDatabase

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun day(date: LocalDate, status: TicketStatus = TicketStatus.CLOSED_BY_HAND, card: Int? = 5) = runBlocking {
        db.dayTicketDao().insert(
            DayTicketEntity(
                date = date, amount = 30_000, base = 30_000, spent = 20_000, entryCount = 3, status = status,
                leftover = 10_000, destination = LeftoverDestination.JAR, proverbNumber = card,
            ),
        )
    }

    private fun vm(date: LocalDate = nov12) =
        ProverbRewardViewModel(db.dayTicketDao(), db.proverbDao(), SavedStateHandle(mapOf("date" to date.toEpochDay())))

    private fun ProverbRewardViewModel.settled(): ProverbRewardUi {
        val end = System.nanoTime() + 10_000_000_000L
        while (state.value == ProverbRewardUi.Loading) {
            main.scheduler.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            check(System.nanoTime() < end) { "timed out waiting for the reward" }
            Thread.sleep(5)
        }
        return state.value
    }

    @Test fun countsTicketStreakAndWeek() {
        day(nov12.minusDays(2)); day(nov12.minusDays(1)); day(nov12)
        val s = vm().settled() as ProverbRewardUi.Ready
        assertEquals("3 hand closes: ticket number 3", 3, s.ticketNumber)
        assertEquals("three days in a row", 3, s.streak)
        assertEquals("k of 7 this week", 3, s.weekDays)
        assertEquals(5, s.proverb.number)
        assertEquals(10_000L, s.leftover)
    }

    @Test fun autoClosedDayHasNothingToReward() {
        day(nov12, status = TicketStatus.CLOSED_AUTO, card = null)
        assertEquals(ProverbRewardUi.NoCard, vm().settled())
    }

    @Test fun noCardAndEmptyAlbumHasNothingToReward() {
        day(nov12, card = null)
        assertEquals(ProverbRewardUi.NoCard, vm().settled())
    }

    @Test fun fullAlbumShowsAFavourite() {
        day(nov12, card = null)
        runBlocking { db.proverbDao().insert(ProverbCollectedEntity(number = 7, collectedOn = nov12.minusDays(3))) }
        val s = vm().settled() as ProverbRewardUi.Ready
        assertTrue(s.allCollected)
        assertEquals(7, s.proverb.number)
    }
}
