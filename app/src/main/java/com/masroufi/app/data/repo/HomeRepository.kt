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
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.streak
import com.masroufi.app.domain.weekStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class RepeatChip(val categoryId: Long?, val categoryName: String, val amount: Long, val pocketId: Long)

data class HomeData(
    val today: DayTicketEntity?,
    val week: List<DayTicketEntity>,
    val jarTotal: Long,
    val streak: Int,
    val collected: Int,
    val spendable: Long,
    val cycle: PayCycle,
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
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    /**
     * Cold. Today is re-read from the clock on every day_ticket change, so a collector that crosses midnight follows
     * the new day once Home re-prints at midnight (Task 17) and writes the new row.
     */
    fun observeHome(): Flow<HomeData> = ticketDao.countFlow()
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
        val settingsAndCategories = combine(settings.settings, categoryDao.all(), ::Pair)
        // Spendable re-emits on every entry change, which also refreshes the cycle anchor and the repeat chips.
        return combine(
            days, jarDao.total(Pot.JAR), proverbDao.all(), pocketDao.countingBalanceTotalFlow(), settingsAndCategories,
        ) { d, jar, proverbs, spendable, (s, categories) ->
            val names = categories.associate { it.id to it.name }
            val since = today.minusDays(REPEAT_DAYS - 1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
            // A chip whose category is gone (or never set) has no name to show, so it is left out.
            val repeats = entryDao.repeatCandidates(since).mapNotNull { r ->
                r.categoryId?.let { names[it] }?.let { RepeatChip(r.categoryId, it, r.amount, r.pocketId) }
            }
            HomeData(
                today = d.today,
                week = d.week,
                jarTotal = jar,
                streak = d.streak,
                collected = proverbs.size,
                spendable = spendable,
                cycle = entryDao.payCycleOn(today, s.payday, clock),
                repeats = repeats,
            )
        }
    }
}
