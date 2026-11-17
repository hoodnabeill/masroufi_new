package com.masroufi.app.data.repo

import com.masroufi.app.core.PayCycle
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.ProverbDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.streak
import com.masroufi.app.domain.weekStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class RepeatChip(val categoryId: Long, val categoryName: String, val amount: Long, val pocketId: Long)

data class HomeData(
    val today: DayTicketEntity?,
    val week: List<DayTicketEntity>,
    val jarTotal: Long,
    val streak: Int,
    val collected: Int,
    val spendable: Long,
    val cycle: PayCycle,
    /** Unpaid bills due before the next payday (for projectedAtPayday). */
    val unpaidBills: Long,
    val repeats: List<RepeatChip>,
)

/** The streak counts back over at most this many days. */
private const val STREAK_DAYS = 400L

/** Repeat chips look at today and the 29 days before it. */
private const val REPEAT_DAYS = 30L

private data class Days(val today: DayTicketEntity?, val week: List<DayTicketEntity>, val streak: Int)

@Singleton
class HomeRepository @Inject constructor(
    private val pocketDao: PocketDao,
    private val entryDao: EntryDao,
    private val categoryDao: CategoryDao,
    private val ticketDao: DayTicketDao,
    private val jarDao: JarDao,
    private val proverbDao: ProverbDao,
    private val recurringDao: RecurringDao,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    /**
     * Cold. Today is re-read from the clock whenever any source can emit, so a collector that crosses midnight
     * re-keys to the new day on its next change; Home also re-prints at midnight (Task 17).
     */
    fun observeHome(): Flow<HomeData> = merge<Any>(
        ticketDao.countFlow(), entryDao.countFlow(), pocketDao.countingBalanceTotalFlow(), jarDao.total(Pot.JAR),
        proverbDao.countFlow(), categoryDao.all(), recurringDao.all(), settings.settings,
    )
        .map { LocalDate.now(clock) }
        .distinctUntilChanged()
        .flatMapLatest(::homeOn)

    private fun homeOn(today: LocalDate): Flow<HomeData> {
        val week = weekStart(today)
        val days = combine(
            ticketDao.forDateFlow(today),
            ticketDao.range(week, week.plusDays(6)),
            ticketDao.range(today.minusDays(STREAK_DAYS), today),
        ) { row, weekRows, recent ->
            Days(row, weekRows, streak(recent.map { DayResult(it.date, it.status, it.amount, it.spent) }, today))
        }
        // recurring rows only trigger: unpaid bills are read below once the cycle is known.
        val settingsAndCategories = combine(settings.settings, categoryDao.all(), recurringDao.all()) { s, c, _ -> s to c }
        // Spendable re-emits on every entry change, which also refreshes the cycle anchor and the repeat chips.
        return combine(
            days, jarDao.total(Pot.JAR), proverbDao.countFlow(), pocketDao.countingBalanceTotalFlow(), settingsAndCategories,
        ) { d, jar, collected, spendable, (s, categories) ->
            val names = categories.associate { it.id to it.name }
            val since = today.minusDays(REPEAT_DAYS - 1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
            // The query already skips missing categories; this only covers a delete racing the read.
            val repeats = entryDao.repeatCandidates(since).mapNotNull { r ->
                names[r.categoryId]?.let { RepeatChip(r.categoryId, it, r.amount, r.pocketId) }
            }
            val cycle = entryDao.payCycleOn(today, s.payday, clock)
            HomeData(
                today = d.today,
                week = d.week,
                jarTotal = jar,
                streak = d.streak,
                collected = collected,
                spendable = spendable,
                cycle = cycle,
                unpaidBills = recurringDao.unpaidDueBefore(cycle.nextPayday),
                repeats = repeats,
            )
        }
    }
}
