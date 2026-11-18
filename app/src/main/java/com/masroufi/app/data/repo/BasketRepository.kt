package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.floorToPound
import com.masroufi.app.core.payCycleFor
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.SpendRow
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.db.TrackedItemDao
import com.masroufi.app.data.db.TrackedItemEntity
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.normalize
import com.masroufi.app.entitlements.Entitlements
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CheapestPlace(val place: String, val price: Long)

/**
 * One tracked item. [changePercent] = latest vs first price in the last 3 months (null with fewer than two entries
 * there); [lastPlace] is the latest entry's note; [cheapest] only when two places were noted.
 */
data class BasketItemView(
    val item: TrackedItemEntity,
    val lastPrice: Long?,
    val changePercent: Int?,
    val lastPlace: String?,
    val cheapest: CheapestPlace?,
    val timesPerMonth: Int?,
)

/** The usual monthly quantities at the first price in the window ([then]) and at today's ([now]), piastres. */
data class BasketChange(val then: Long, val now: Long, val percent: Int)

data class SuggestedTicket(val current: Long, val suggested: Long)
data class WeekdayFact(val day: DayOfWeek, val average: Long, val restAverage: Long)
data class LateNightFact(val count: Int, val sum: Long)

/** [cashVsWalletPercent] is the average cash spend relative to the wallet's (-18 = 18% less in cash). */
data class HabitFacts(val weekday: WeekdayFact?, val lateNight: LateNightFact?, val cashVsWalletPercent: Int?)

data class BasketData(
    val items: List<BasketItemView>,
    val change: BasketChange?,
    val suggestion: SuggestedTicket?,
    val habits: HabitFacts,
)

sealed interface BasketResult {
    data object Locked : BasketResult
    data class Ready(val data: BasketData) : BasketResult
}

private const val WINDOW_MONTHS = 3L
private const val LATE_HOUR = 21
private const val MIN_WEEKDAY_SPAN = 14
private const val MIN_SIDE_ENTRIES = 3

@Singleton
class BasketRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val trackedDao: TrackedItemDao,
    private val entryDao: EntryDao,
    private val ticketDao: DayTicketDao,
    private val settings: SettingsStore,
    private val entitlements: Entitlements,
    private val clock: Clock,
) {
    /**
     * The tracked item the add sheet's [name] belongs to, made on first sight (not shown until it has two priced
     * entries). Everyone's entries are tracked quietly; only reading the basket is Plus.
     */
    suspend fun itemIdFor(name: String): Long = db.withTransaction {
        val n = normalizeItem(name)
        require(n.isNotEmpty()) { "an item needs a name" }
        trackedDao.byNormalised(n)?.id
            ?: trackedDao.insert(TrackedItemEntity(name = name.trim(), normalisedName = n, createdAt = clock.millis()))
    }

    /** "تابع سعر حاجة تانية": tracks [name] now, even before it has two entries. */
    suspend fun track(name: String): Long = db.withTransaction {
        val id = itemIdFor(name)
        val item = trackedDao.allNow().first { it.id == id }
        if (!item.addedByUser) trackedDao.update(item.copy(addedByUser = true))
        id
    }

    /** Locked without Plus; never throws for a read. */
    fun observe(): Flow<BasketResult> =
        combine(entitlements.isPlus, entryDao.countFlow(), trackedDao.all()) { plus, _, _ -> plus }.map { plus ->
            if (plus) BasketResult.Ready(load()) else BasketResult.Locked
        }

    suspend fun read(): BasketResult = if (entitlements.isPlus.value) BasketResult.Ready(load()) else BasketResult.Locked

    /**
     * Offered, never silent: the ticket is computed from the money, so there is no stored daily amount to edit. This
     * stores the raise (honoured by the ticket until the next payday) and lifts today's open ticket to it. Returns the
     * new amount, or null when locked or there is no suggestion.
     */
    suspend fun applySuggestedTicket(): Long? {
        val ready = read() as? BasketResult.Ready ?: return null
        val suggested = ready.data.suggestion?.suggested ?: return null
        val s = settings.current()
        val today = LocalDate.now(clock)
        val cycle = payCycleFor(today, s.payday, entryDao.latestReceivedAt()?.let(clock::dayOf))
        db.withTransaction {
            settings.setTicketRaise(suggested, cycle.nextPayday.toEpochDay())
            ticketDao.forDate(today)?.takeIf { !it.endOfMonth && it.status == TicketStatus.OPEN && it.base < suggested }?.let {
                ticketDao.update(it.copy(base = suggested, amount = suggested + it.carriedIn, updatedAt = clock.millis()))
            }
        }
        return suggested
    }

    private suspend fun load(): BasketData {
        val today = LocalDate.now(clock)
        val windowStart = today.minusMonths(WINDOW_MONTHS).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val tomorrow = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val byItem = entryDao.pricedEntries().groupBy { it.trackedItemId }
        val stats = trackedDao.allNow()
            .filter { it.addedByUser || (byItem[it.id]?.size ?: 0) >= 2 }
            .map { it to ItemStats(byItem[it.id].orEmpty(), windowStart, today, clock) }
        val items = stats.map { (item, s) ->
            BasketItemView(item, s.lastPrice, s.changePercent, s.lastPlace, s.cheapest, s.timesPerMonth)
        }
        val change = basketChange(stats.map { it.second })
        val row = ticketDao.forDate(today)?.takeIf { !it.endOfMonth && it.base > 0 }
        val suggestion = if (change != null && row != null && change.now > change.then) {
            (row.base * change.now / change.then).floorToPound().takeIf { it > row.base }?.let { SuggestedTicket(row.base, it) }
        } else {
            null
        }
        val month = today.withDayOfMonth(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val windowSpends = entryDao.habitSpends(windowStart, tomorrow)
        val habits = HabitFacts(
            weekday = weekdayFact(windowSpends, today, clock),
            lateNight = lateNightFact(entryDao.habitSpends(month, tomorrow), clock),
            cashVsWalletPercent = cashVsWallet(windowSpends),
        )
        return BasketData(items, change, suggestion, habits)
    }
}

internal fun normalizeItem(name: String): String = normalize(name).trim().replace(Regex("\\s+"), " ").lowercase()

/** One item's priced entries, oldest first, read against the 3-month window. */
internal class ItemStats(all: List<EntryEntity>, windowStart: Long, today: LocalDate, clock: Clock) {
    private val inWindow = all.filter { it.happenedAt >= windowStart }
    val lastPrice: Long? = all.lastOrNull()?.unitPrice
    val firstInWindow: Long? = inWindow.firstOrNull()?.unitPrice
    val lastInWindow: Long? = inWindow.lastOrNull()?.unitPrice
    val comparable: Boolean = inWindow.size >= 2
    val changePercent: Int? = if (comparable && firstInWindow!! > 0) {
        Math.round((lastInWindow!! - firstInWindow).toDouble() * 100.0 / firstInWindow).toInt()
    } else {
        null
    }
    val lastPlace: String? = all.lastOrNull()?.note?.trim()?.takeIf { it.isNotEmpty() }
    val cheapest: CheapestPlace? = inWindow
        .mapNotNull { e -> e.note?.trim()?.takeIf { it.isNotEmpty() }?.let { it to e.unitPrice!! } }
        .takeIf { p -> p.map { it.first }.distinct().size >= 2 }
        ?.minByOrNull { it.second }
        ?.let { CheapestPlace(it.first, it.second) }

    /** How many months the window really covers for this item: 1..3 (a young item is not diluted). */
    private val months: Double = inWindow.firstOrNull()?.let {
        val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.happenedAt).atZone(clock.zone).toLocalDate(), today) + 1
        (days / 30.0).coerceIn(1.0, WINDOW_MONTHS.toDouble())
    } ?: 1.0
    val timesPerMonth: Int? = if (comparable) Math.round(inWindow.size / months).toInt() else null

    /** Usual monthly quantity = Σ amount ÷ unit price over the window, per month. */
    val monthlyQuantity: Double = inWindow.sumOf { it.amount.toDouble() / it.unitPrice!! } / months
}

/** Only when some item has two prices to compare; items with one entry count at that price on both sides. */
internal fun basketChange(stats: List<ItemStats>): BasketChange? {
    if (stats.none { it.comparable }) return null
    val used = stats.filter { it.firstInWindow != null && it.lastInWindow != null }
    val thenD: Double = used.sumOf { it.monthlyQuantity * it.firstInWindow!! }
    val nowD: Double = used.sumOf { it.monthlyQuantity * it.lastInWindow!! }
    val then = Math.round(thenD)
    val now = Math.round(nowD)
    if (then <= 0) return null
    return BasketChange(then, now, Math.round((now - then).toDouble() * 100.0 / then).toInt())
}

private fun SpendRow.date(clock: Clock): LocalDate = Instant.ofEpochMilli(happenedAt).atZone(clock.zone).toLocalDate()

/** The weekday with the highest average day spend against the other days' average; needs two weeks of data. */
internal fun weekdayFact(rows: List<SpendRow>, today: LocalDate, clock: Clock): WeekdayFact? {
    val first = rows.minOfOrNull { it.date(clock) } ?: return null
    val span = ChronoUnit.DAYS.between(first, today).toInt() + 1
    if (span < MIN_WEEKDAY_SPAN) return null
    val perDay = rows.groupBy { it.date(clock) }.mapValues { (_, r) -> r.sumOf { it.amount } }
    val days = (0 until span).map { first.plusDays(it.toLong()) }
    val total = perDay.values.sum()
    var best: Triple<DayOfWeek, Int, Long>? = null
    for (dow in DayOfWeek.values()) {
        val mine = days.filter { it.dayOfWeek == dow }
        if (mine.size < 2) continue
        val sum = mine.sumOf { perDay[it] ?: 0L }
        val b = best
        if (b == null || sum.toDouble() / mine.size > b.third.toDouble() / b.second) best = Triple(dow, mine.size, sum)
    }
    val (dow, n, sum) = best ?: return null
    val restDays = span - n
    if (restDays <= 0) return null
    val average = sum / n
    val rest = (total - sum) / restDays
    return if (average > rest) WeekdayFact(dow, average, rest) else null
}

/** Spends at or after 21:00 in [rows] (this month's); null when none. */
internal fun lateNightFact(rows: List<SpendRow>, clock: Clock): LateNightFact? {
    val late = rows.filter { Instant.ofEpochMilli(it.happenedAt).atZone(clock.zone).hour >= LATE_HOUR }
    return if (late.isEmpty()) null else LateNightFact(late.size, late.sumOf { it.amount })
}

/** Average cash spend against the average wallet spend, in percent; needs three of each. */
internal fun cashVsWallet(rows: List<SpendRow>): Int? {
    val cash = rows.filter { it.pocketKind == PocketKind.CASH }
    val wallet = rows.filter { it.pocketKind == PocketKind.MOBILE_WALLET }
    if (cash.size < MIN_SIDE_ENTRIES || wallet.size < MIN_SIDE_ENTRIES) return null
    val cashAvg = cash.sumOf { it.amount }.toDouble() / cash.size
    val walletAvg = wallet.sumOf { it.amount }.toDouble() / wallet.size
    return Math.round((cashAvg - walletAvg) * 100.0 / walletAvg).toInt()
}
