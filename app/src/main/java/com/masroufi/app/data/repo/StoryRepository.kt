package com.masroufi.app.data.repo

import com.masroufi.app.core.PayCycle
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.EnvelopeDao
import com.masroufi.app.data.db.EnvelopeEntity
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.MonthStoryEntity
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.ProverbDao
import com.masroufi.app.data.db.StoryDao
import com.masroufi.app.data.db.StorySpend
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.longestStreak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A cycle with fewer torn days than this gets the short two-slide story (11-DATA "Monthly story"). */
const val STORY_THIN_BELOW = 7

/** Which suggestion line the story shows; the numbers are in [StorySuggestion], the words are the UI's (strings.xml). */
enum class StoryRule { ENVELOPE_RAN_OUT, HABIT_GREW, WEEKDAY_OVER, NEUTRAL }

@Serializable
data class StoryBestDay(val epochDay: Long, val leftover: Long)

/** Earlier cycles only (null fields = no such cycle). [isBest]: strictly more torn days than every earlier cycle. */
@Serializable
data class StoryComparison(
    val prevStart: Long? = null,
    val prevEnd: Long? = null,
    val prevTorn: Int? = null,
    val bestStart: Long? = null,
    val bestEnd: Long? = null,
    val bestTorn: Int? = null,
    val isBest: Boolean = false,
)

@Serializable
data class StoryEnvelope(val envelopeId: Long, val name: String, val spent: Long)

@Serializable
data class StoryLargest(val amount: Long, val epochDay: Long, val category: String? = null, val note: String? = null)

/** ENVELOPE_RAN_OUT: a = cycle day it ran out (1-based), b = cycle days, c = its limit. HABIT_GREW: a = this cycle, b = the one before. WEEKDAY_OVER: a = ISO weekday, b = over days on it, c = all over days. */
@Serializable
data class StorySuggestion(val rule: StoryRule, val subject: String? = null, val a: Long = 0, val b: Long = 0, val c: Long = 0)

/** Everything a story shows, as stored. Money is piastres, dates are epoch days; [grid] has one char per cycle day: U under, O over, N never torn. */
@Serializable
data class StoryPayload(
    val version: Int = 1,
    val cycleDays: Int,
    val torn: Int,
    val under: Int,
    val over: Int,
    val never: Int,
    val grid: String,
    val thin: Boolean,
    val bestDay: StoryBestDay? = null,
    val comparison: StoryComparison? = null,
    val topEnvelopes: List<StoryEnvelope> = emptyList(),
    val totalSpent: Long = 0,
    val largestEntry: StoryLargest? = null,
    val jarMoved: Long = 0,
    val cardsWon: Int = 0,
    val longestStreak: Int = 0,
    val suggestion: StorySuggestion = StorySuggestion(StoryRule.NEUTRAL),
)

data class MonthStory(val id: Long, val cycleStart: LocalDate, val cycleEnd: LocalDate, val shownAt: Long?, val payload: StoryPayload)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private const val RAN_OUT_MIN_DAYS_BEFORE_END = 5
private const val HABIT_MIN_GROWTH = 10_000L // 100 pounds
private const val WEEKDAY_MIN_OVER_DAYS = 3

@Singleton
class StoryRepository @Inject constructor(
    private val storyDao: StoryDao,
    private val ticketDao: DayTicketDao,
    private val entryDao: EntryDao,
    private val categoryDao: CategoryDao,
    private val envelopeDao: EnvelopeDao,
    private val jarDao: JarDao,
    private val proverbDao: ProverbDao,
    private val clock: Clock,
) {
    /** The newest story never opened (Home shows it once). */
    fun unseen(): Flow<MonthStory?> = storyDao.unseen().map { it?.let(::decode) }

    /** Every saved story, newest cycle first (the album's list). */
    fun all(): Flow<List<MonthStory>> = storyDao.all().map { rows -> rows.mapNotNull(::decode) }

    suspend fun get(id: Long): MonthStory? = storyDao.byId(id)?.let(::decode)

    /** Stamps the story as opened; only the first time counts. */
    suspend fun markShown(id: Long) = storyDao.markShown(id, clock.millis())

    /**
     * Called by printToday inside its transaction, after the earlier days closed and before today's row exists: builds
     * the story of the cycle that just ended, once. It is the cycle's first print when the last printed day is not in
     * the cycle of [today] (an upgrade in the middle of a cycle builds nothing); a cycle with no printed day has none.
     */
    internal suspend fun buildIfDue(today: LocalDate, s: Settings) {
        val current = entryDao.payCycleOn(today, s.payday, clock)
        val last = ticketDao.latestBefore(today) ?: return
        if (!last.date.isBefore(current.start)) return
        val cycle = entryDao.payCycleOn(current.start.minusDays(1), s.payday, clock)
        if (storyDao.byCycleStart(cycle.start) != null) return
        val days = ticketDao.allNow()
        if (days.none { cycle.contains(it.date) }) return
        val payload = payload(cycle, days, s)
        storyDao.insert(MonthStoryEntity(cycleStart = cycle.start, cycleEnd = cycle.nextPayday, payload = json.encodeToString(StoryPayload.serializer(), payload)))
    }

    private fun decode(row: MonthStoryEntity): MonthStory? = runCatching {
        MonthStory(row.id, row.cycleStart, row.cycleEnd, row.shownAt, json.decodeFromString(StoryPayload.serializer(), row.payload))
    }.getOrNull()

    private suspend fun payload(cycle: PayCycle, days: List<DayTicketEntity>, s: Settings): StoryPayload {
        val inCycle = days.filter { cycle.contains(it.date) }
        val byDate = inCycle.associateBy { it.date }
        val cycleDays = ChronoUnit.DAYS.between(cycle.start, cycle.nextPayday).toInt()
        val grid = (0 until cycleDays).map { i ->
            val row = byDate[cycle.start.plusDays(i.toLong())]
            when {
                row == null || row.status != TicketStatus.CLOSED_BY_HAND -> 'N'
                row.spent <= row.amount -> 'U'
                else -> 'O'
            }
        }.joinToString("")
        val under = grid.count { it == 'U' }
        val over = grid.count { it == 'O' }
        val torn = under + over
        val thin = torn < STORY_THIN_BELOW

        val startMs = cycle.start.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val endMs = cycle.nextPayday.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val spends = entryDao.storySpends(startMs, endMs)
        val categories = categoryDao.allNow().associateBy { it.id }
        val envelopes = envelopeDao.allNow()
        val links = envelopeDao.linksNow()
        // A category in two envelopes counts for the first (lowest id) only.
        val envelopeOf = links.sortedBy { it.envelopeId }.groupBy { it.categoryId }.mapValues { it.value.first().envelopeId }
        val perEnvelope = spends.filter { it.categoryId != null }.groupBy { envelopeOf[it.categoryId] }.filterKeys { it != null }

        val top = envelopes.mapNotNull { e -> perEnvelope[e.id]?.let { StoryEnvelope(e.id, e.name, it.sumOf { x -> x.amount }) } }
            .sortedWith(compareByDescending<StoryEnvelope> { it.spent }.thenBy { it.envelopeId }).take(3)
        val largest = spends.maxWithOrNull(compareBy<StorySpend> { it.amount }.thenByDescending { it.happenedAt })?.let {
            StoryLargest(it.amount, clock.dayOf(it.happenedAt).toEpochDay(), it.categoryId?.let(categories::get)?.name, it.note)
        }
        val jarWindow = jarDao.rowsNow(Pot.JAR).filter { it.at in startMs until endMs }.sources()

        return StoryPayload(
            cycleDays = cycleDays, torn = torn, under = under, over = over, never = cycleDays - torn, grid = grid, thin = thin,
            bestDay = inCycle.filter { it.status == TicketStatus.CLOSED_BY_HAND && it.spent <= it.amount }
                .maxWithOrNull(compareBy<DayTicketEntity> { it.amount - it.spent }.thenByDescending { it.date })
                ?.let { StoryBestDay(it.date.toEpochDay(), it.amount - it.spent) },
            comparison = if (thin) null else comparison(cycle, days, torn, s),
            topEnvelopes = top,
            totalSpent = spends.sumOf { it.amount },
            largestEntry = largest,
            jarMoved = (jarWindow.leftoverSum + jarWindow.roundUpSum + jarWindow.manualSum).coerceAtLeast(0),
            cardsWon = proverbDao.countBetween(cycle.start, cycle.nextPayday),
            longestStreak = longestStreak(inCycle.map { DayResult(it.date, it.status, it.amount, it.spent) }),
            suggestion = if (thin) StorySuggestion(StoryRule.NEUTRAL) else suggestion(cycle, grid, spends, envelopes, links.groupBy({ it.envelopeId }, { it.categoryId }), categories.mapValues { it.value.name }, s),
        )
    }

    /** Torn days of the cycle before and of the best earlier cycle; walks back to the first printed day. */
    private suspend fun comparison(cycle: PayCycle, days: List<DayTicketEntity>, torn: Int, s: Settings): StoryComparison {
        val first = days.first().date
        var c = entryDao.payCycleOn(cycle.start.minusDays(1), s.payday, clock)
        val immediate = c
        val earlier = mutableListOf<Pair<PayCycle, Int>>()
        var guard = 0
        while (c.nextPayday.isAfter(first) && guard++ < 240) {
            val rows = days.filter { c.contains(it.date) }
            if (rows.isNotEmpty()) earlier += c to rows.count { it.status == TicketStatus.CLOSED_BY_HAND }
            val before = entryDao.payCycleOn(c.start.minusDays(1), s.payday, clock)
            if (!before.start.isBefore(c.start)) break
            c = before
        }
        val prev = earlier.firstOrNull { it.first == immediate }
        val best = earlier.maxByOrNull { it.second } // the most recent of equals
        return StoryComparison(
            prevStart = prev?.first?.start?.toEpochDay(), prevEnd = prev?.first?.nextPayday?.toEpochDay(), prevTorn = prev?.second,
            bestStart = best?.first?.start?.toEpochDay(), bestEnd = best?.first?.nextPayday?.toEpochDay(), bestTorn = best?.second,
            isBest = best != null && torn > best.second,
        )
    }

    /**
     * One line, the first rule that applies (07-COPY has no text for these; the UI words each rule):
     * 1. ENVELOPE_RAN_OUT: a spending envelope with a limit whose spend reached it at least 5 days before the cycle ended; the earliest one.
     * 2. HABIT_GREW: a category (spends that pay no recurring item) that grew by at least 100 pounds and by half over the cycle before; the biggest growth.
     * 3. WEEKDAY_OVER: 3 or more days over the ticket, one weekday with 2 or more of them and more than any other weekday.
     * Otherwise NEUTRAL.
     */
    private suspend fun suggestion(
        cycle: PayCycle,
        grid: String,
        spends: List<StorySpend>,
        envelopes: List<EnvelopeEntity>,
        categoriesOf: Map<Long, List<Long>>,
        categoryNames: Map<Long, String>,
        s: Settings,
    ): StorySuggestion {
        val cycleDays = grid.length
        val moves = envelopeDao.movesNow().filter { it.cycleStart == cycle.start }
        val ranOut = envelopes.filter { !it.archived && it.kind == EnvelopeKind.SPENDING }.mapNotNull { e ->
            val limit = e.monthly + moves.filter { it.toId == e.id }.sumOf { it.amount } - moves.filter { it.fromId == e.id }.sumOf { it.amount }
            if (limit <= 0) return@mapNotNull null
            val cats = categoriesOf[e.id].orEmpty().toSet()
            var total = 0L
            for (x in spends.filter { it.categoryId in cats }) {
                total += x.amount
                if (total >= limit) {
                    val day = ChronoUnit.DAYS.between(cycle.start, clock.dayOf(x.happenedAt)).toInt() + 1
                    return@mapNotNull if (cycleDays - day >= RAN_OUT_MIN_DAYS_BEFORE_END) Triple(e, day, limit) else null
                }
            }
            null
        }.minWithOrNull(compareBy<Triple<EnvelopeEntity, Int, Long>> { it.second }.thenBy { it.first.id })
        if (ranOut != null) return StorySuggestion(StoryRule.ENVELOPE_RAN_OUT, ranOut.first.name, ranOut.second.toLong(), cycleDays.toLong(), ranOut.third)

        val prev = entryDao.payCycleOn(cycle.start.minusDays(1), s.payday, clock)
        val prevSpends = entryDao.storySpends(
            prev.start.atStartOfDay(clock.zone).toInstant().toEpochMilli(), cycle.start.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
        )
        fun habits(list: List<StorySpend>) = list.filter { it.recurringId == null && it.categoryId != null }
            .groupBy { it.categoryId!! }.mapValues { it.value.sumOf { x -> x.amount } }
        val now = habits(spends)
        val before = habits(prevSpends)
        val grew = now.mapNotNull { (cat, total) ->
            val was = before[cat] ?: return@mapNotNull null
            if (was > 0 && total - was >= HABIT_MIN_GROWTH && total * 2 >= was * 3) Triple(cat, total, was) else null
        }.maxWithOrNull(compareBy<Triple<Long, Long, Long>> { it.second - it.third }.thenByDescending { it.first })
        if (grew != null) return StorySuggestion(StoryRule.HABIT_GREW, categoryNames[grew.first], grew.second, grew.third)

        val overDays = grid.indices.filter { grid[it] == 'O' }.map { cycle.start.plusDays(it.toLong()).dayOfWeek }
        val perDay = overDays.groupingBy { it }.eachCount()
        val topDay = perDay.maxByOrNull { it.value }
        if (overDays.size >= WEEKDAY_MIN_OVER_DAYS && topDay != null && topDay.value >= 2 && perDay.count { it.value == topDay.value } == 1) {
            return StorySuggestion(StoryRule.WEEKDAY_OVER, null, topDay.key.value.toLong(), topDay.value.toLong(), overDays.size.toLong())
        }
        return StorySuggestion(StoryRule.NEUTRAL)
    }
}
