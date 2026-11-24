package com.masroufi.app.widget

import android.util.Log
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.di.AppScope
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.HatTrick
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.buildMatch
import com.masroufi.app.domain.streak
import com.masroufi.app.entitlements.Entitlements
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws the home-screen widgets from the stored summary. */
interface WidgetRefresher {
    suspend fun update()
}

/** Repeat chips look at today and the 29 days before it, as on Home. */
private const val REPEAT_DAYS = 30L

/**
 * Writes the [DaySummary] and then redraws the widgets. Called after a ticket prints, after every punch, edit, delete
 * and close, and when the lock settings change. A failure here is logged and never fails the action that called it; the write is not awaited.
 */
@Singleton
class DaySummaryWriter @Inject constructor(
    private val ticketDao: DayTicketDao,
    private val entryDao: EntryDao,
    private val categoryDao: CategoryDao,
    private val settings: SettingsStore,
    private val store: DaySummaryStore,
    private val plus: Entitlements,
    private val clock: Clock,
    private val widgets: WidgetRefresher,
    @AppScope private val scope: CoroutineScope,
) {
    private val lock = Mutex()

    /**
     * Rewrites the summary in the background and returns at once: the caller (a punch, a print) never waits for the
     * DataStore, whose edits can be queued behind another edit that needs the caller's thread. Every run rebuilds from
     * the database, so runs that queue up (they take [lock] in turn) end with the latest state.
     */
    fun refresh() {
        scope.launch {
            try {
                lock.withLock {
                    store.write(build())
                    widgets.update()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DaySummaryWriter", "writing the day summary failed", e)
            }
        }
    }

    private suspend fun build(): DaySummary {
        val s = settings.current()
        val today = LocalDate.now(clock)
        val row = ticketDao.forDate(today)
        val results = ticketDao.allNow().map { DayResult(it.date, it.status, it.amount, it.spent) }
        val isPlus = plus.isPlus.value
        val match = buildMatch(results, today, isPlus)
        val names = categoryDao.allNow().associate { it.id to it.name }
        val since = today.minusDays(REPEAT_DAYS - 1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val chips = entryDao.repeatCandidates(since).mapNotNull { r ->
            names[r.categoryId]?.let { RepeatChip(r.categoryId, it, r.amount, r.pocketId) }
        }.take(MAX_CHIPS)
        return DaySummary(
            day = today.toEpochDay(),
            printed = row != null,
            open = row?.status == TicketStatus.OPEN,
            amount = row?.amount ?: 0,
            left = row?.let { it.amount - it.spent } ?: 0,
            punches = row?.entryCount ?: 0,
            streak = streak(results, today, isPlus),
            matchYou = match.goalsFor,
            matchLast = match.lastWeekGoalsFor,
            hatTrickNear = match.hatTrick == HatTrick.ONE_AWAY,
            chips = chips,
            lockOn = s.lockOn,
            hideAmounts = s.hideAmounts,
        )
    }
}
