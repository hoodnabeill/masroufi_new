package com.masroufi.app.data.repo

import android.util.Log
import androidx.room.withTransaction
import com.masroufi.app.BuildConfig
import com.masroufi.app.core.Payday
import com.masroufi.app.core.payCycleFor
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.di.AppScope
import com.masroufi.app.domain.TicketStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val SAMPLE_BILL = "Sample bill"

/** Debug gallery only: the mock's numbers (5,870 cash, payday 25, a 2,750 bill on the 20th), then today's ticket again. */
class DebugSampleData @Inject constructor(
    private val db: TazkaraDatabase,
    private val pocketDao: PocketDao,
    private val recurringDao: RecurringDao,
    private val ticketDao: DayTicketDao,
    private val settings: SettingsStore,
    private val tickets: TicketRepository,
    @AppScope private val scope: CoroutineScope,
    private val clock: Clock,
) {
    fun load() {
        check(BuildConfig.DEBUG) { "sample data is a debug tool" }
        scope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DebugSampleData", "sample data failed", e)
            }
        }
    }

    private suspend fun write() {
        val payday = Payday.Day(25)
        settings.setPayday(payday)
        val today = LocalDate.now(clock)
        val nextPayday = payCycleFor(today, payday, null).nextPayday
        val cash = checkNotNull(pocketDao.all().first().firstOrNull { it.name == "الكاش" }) { "no الكاش pocket" }
        val hasBill = recurringDao.all().first().any { it.name == SAMPLE_BILL }
        db.withTransaction {
            pocketDao.update(cash.copy(openingBalance = 587_000, updatedAt = clock.millis()))
            if (!hasBill) {
                recurringDao.insert(
                    RecurringEntity(
                        name = SAMPLE_BILL, amount = 275_000, pocketId = cash.id, scheduleDay = 20,
                        nextDue = nextPayday.withDayOfMonth(20), kind = RecurringKind.BILL, updatedAt = clock.millis(),
                    ),
                )
            }
            // printToday keeps an existing row, so an untouched open one goes first to re-print with the new numbers.
            ticketDao.forDate(today)?.takeIf { it.status == TicketStatus.OPEN && it.entryCount == 0 }?.let { ticketDao.delete(it.id) }
        }
        tickets.printToday()
    }
}
