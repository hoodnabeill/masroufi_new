package com.masroufi.app.data.repo

import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.masroufi.app.R
import com.masroufi.app.core.PRIMARY_CURRENCY
import com.masroufi.app.core.PayCycle
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.EnvelopeCategoryEntity
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.db.EnvelopeDao
import com.masroufi.app.data.db.EnvelopeEntity
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.EnvelopeMoveEntity
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.SplitKind
import com.masroufi.app.data.db.SplitRuleEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.SplitResult
import com.masroufi.app.domain.SplitRule
import com.masroufi.app.domain.envelopeLeft
import com.masroufi.app.domain.runsOutDay
import com.masroufi.app.domain.splitIncome
import com.masroufi.app.entitlements.Entitlements
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Envelopes on Free (02-FEATURES "Free vs Plus"). */
const val FREE_ENVELOPES = 4

/** A Plus feature was asked for on Free; the UI shows the Plus card. */
class PlusRequired : Exception("Plus required")

/** The honest line under an envelope (11-DATA "Envelopes"). */
sealed interface EnvelopeLine {
    data class RunsOut(val date: LocalDate) : EnvelopeLine
    data object OnPace : EnvelopeLine
}

data class EnvelopeView(
    val id: Long,
    val name: String,
    val color: Int,
    val kind: EnvelopeKind,
    /** The saved monthly amount adjusted by this cycle's moves. */
    val monthly: Long,
    val spent: Long,
    val left: Long,
    /** Share of this cycle's monthly spent, 0..1. */
    val bar: Float,
    /** A fixed-bill envelope whose recurring items are all paid this cycle. */
    val sealed: Boolean,
    @StringRes val paidLabelRes: Int?,
    /** Null when sealed or when no honest line applies. */
    val line: EnvelopeLine?,
    /** Spent so far is below last cycle's spend by the same day of the cycle. */
    val betterThanLast: Boolean,
    val categoryIds: Set<Long>,
)

/** A starter envelope from the seeded categories; the user types the monthly amount. */
data class EnvelopeSuggestion(val name: String, val kind: EnvelopeKind, val categoryIds: Set<Long>, val monthly: Long = 0)

/** Suggested envelope name to the seeded category names it holds. */
private val SUGGESTED = listOf(
    Triple("الأكل والبقالة", EnvelopeKind.SPENDING, listOf("أكل", "بقالة")),
    Triple("المواصلات", EnvelopeKind.SPENDING, listOf("مواصلات")),
    Triple("الفواتير والنت", EnvelopeKind.FIXED, listOf("فواتير")),
    Triple("الخروجات", EnvelopeKind.SPENDING, listOf("قهوة")),
)

/** Days of spending before an envelope says when it runs out. */
private const val MIN_DAYS_OF_DATA = 5

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EnvelopeRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val envelopeDao: EnvelopeDao,
    private val entryDao: EntryDao,
    private val recurringDao: RecurringDao,
    private val categoryDao: CategoryDao,
    private val settings: SettingsStore,
    private val entitlements: Entitlements,
    private val clock: Clock,
) {
    /** Active envelopes for today's pay cycle. */
    fun observe(): Flow<List<EnvelopeView>> =
        combine(envelopeDao.all(), envelopeDao.links(), envelopeDao.moves(), recurringDao.all(), entryDao.countFlow()) { e, l, m, r, _ ->
            Snapshot(e, l, m, r)
        }.mapLatest { views(it) }

    private data class Snapshot(
        val envelopes: List<EnvelopeEntity>,
        val links: List<EnvelopeCategoryEntity>,
        val moves: List<EnvelopeMoveEntity>,
        val recurring: List<RecurringEntity>,
    )

    private suspend fun views(snap: Snapshot): List<EnvelopeView> {
        val s = settings.current()
        val today = LocalDate.now(clock)
        val cycle = currentCycle()
        val last = entryDao.payCycleOn(cycle.start.minusDays(1), s.payday, clock)
        val elapsed = ChronoUnit.DAYS.between(cycle.start, today) + 1
        val cycleDays = ChronoUnit.DAYS.between(cycle.start, cycle.nextPayday)
        // Days the app has seen this cycle: from the cycle start or the first open, whichever is later.
        val firstDay = s.firstOpenDay?.let(LocalDate::ofEpochDay)?.takeIf { it.isAfter(cycle.start) } ?: cycle.start
        val daysWithData = (ChronoUnit.DAYS.between(firstDay, today) + 1).toInt()
        val now = spentByCategory(cycle.start, today.plusDays(1))
        val lastSoFar = spentByCategory(last.start, minOf(last.start.plusDays(elapsed), cycle.start))
        val moves = snap.moves.filter { it.cycleStart == cycle.start }
        val categoriesOf = snap.links.groupBy({ it.envelopeId }, { it.categoryId })

        return snap.envelopes.filter { !it.archived }.map { env ->
            val cats = categoriesOf[env.id].orEmpty().toSet()
            val monthly = env.monthly + moves.filter { it.toId == env.id }.sumOf { it.amount } -
                moves.filter { it.fromId == env.id }.sumOf { it.amount }
            val spent = cats.sumOf { now[it] ?: 0L }
            val left = envelopeLeft(monthly, spent)
            val bills = snap.recurring.filter { it.envelopeId == env.id && !it.archived }
            val sealed = env.kind == EnvelopeKind.FIXED && bills.isNotEmpty() && bills.all { !it.nextDue.isBefore(cycle.nextPayday) }
            // OnPace: spent / elapsed <= monthly / cycleDays, without fractions.
            val line = if (sealed) null else runsOutDay(left, spent, daysWithData, today, cycle.nextPayday)?.let { EnvelopeLine.RunsOut(it) }
                ?: EnvelopeLine.OnPace.takeIf { spent * cycleDays <= monthly * elapsed }
            EnvelopeView(
                id = env.id, name = env.name, color = env.color, kind = env.kind, monthly = monthly, spent = spent, left = left,
                bar = when {
                    monthly > 0 -> (spent.toFloat() / monthly).coerceIn(0f, 1f)
                    spent > 0 -> 1f
                    else -> 0f
                },
                sealed = sealed,
                paidLabelRes = if (sealed) R.string.paper_sealed else null,
                line = line,
                betterThanLast = spent < cats.sumOf { lastSoFar[it] ?: 0L },
                categoryIds = cats,
            )
        }
    }

    /** The last applied split: (income, unassigned), or null before the first one. */
    suspend fun lastSplit(): Pair<Long, Long>? {
        val s = settings.current()
        return s.lastSplitIncome?.let { income -> income to (s.lastSplitUnassigned ?: 0L) }
    }

    /** The last applied split as it changes: (income, unassigned), or null before the first one. */
    fun observeSplit(): Flow<Pair<Long, Long>?> =
        settings.settings.map { s -> s.lastSplitIncome?.let { it to (s.lastSplitUnassigned ?: 0L) } }

    /** Refuses a 5th active envelope on Free with [PlusRequired]. */
    suspend fun create(name: String, color: Int, monthly: Long, kind: EnvelopeKind, categoryIds: Set<Long>): Long {
        require(monthly >= 0) { "monthly must be >= 0" }
        val plus = entitlements.isPlus.value
        return db.withTransaction {
            if (!plus && envelopeDao.activeCount() >= FREE_ENVELOPES) throw PlusRequired()
            val now = clock.millis()
            val id = envelopeDao.insert(EnvelopeEntity(name = name, color = color, monthly = monthly, kind = kind, updatedAt = now))
            categoryIds.forEach { envelopeDao.link(EnvelopeCategoryEntity(id, it, now)) }
            id
        }
    }

    suspend fun update(envelope: EnvelopeEntity, categoryIds: Set<Long>) {
        require(envelope.monthly >= 0) { "monthly must be >= 0" }
        db.withTransaction {
            val now = clock.millis()
            envelopeDao.update(envelope.copy(updatedAt = now))
            envelopeDao.unlinkAll(envelope.id)
            categoryIds.forEach { envelopeDao.link(EnvelopeCategoryEntity(envelope.id, it, now)) }
        }
    }

    suspend fun archive(id: Long) {
        val env = checkNotNull(envelopeDao.byId(id)) { "no envelope $id" }
        envelopeDao.update(env.copy(archived = true, updatedAt = clock.millis()))
    }

    /** Decision 2: moves up to what [from] has left, for this pay cycle only. */
    suspend fun move(from: Long, to: Long, amount: Long) {
        require(amount > 0) { "amount must be > 0" }
        require(from != to) { "a move needs two envelopes" }
        val views = observe().first().associateBy { it.id }
        val source = requireNotNull(views[from]) { "envelope $from must exist and not be archived" }
        requireNotNull(views[to]) { "envelope $to must exist and not be archived" }
        require(amount <= source.left) { "cannot move more than what is left" }
        val now = clock.millis()
        envelopeDao.insertMove(EnvelopeMoveEntity(cycleStart = currentCycle().start, fromId = from, toId = to, amount = amount, at = now))
    }

    suspend fun saveSplit(rules: List<SplitRule>) {
        splitIncome(0, rules) // validates the rules (amounts, percent range and total) before anything is stored
        db.withTransaction {
            envelopeDao.clearSplit()
            envelopeDao.insertSplit(
                rules.mapIndexed { i, r ->
                    when (r) {
                        is SplitRule.Fixed -> SplitRuleEntity(envelopeId = r.envelopeId, kind = SplitKind.FIXED, value = r.amount, sortOrder = i)
                        is SplitRule.Percent ->
                            SplitRuleEntity(envelopeId = r.envelopeId, kind = SplitKind.PERCENT, value = r.percent.toLong(), sortOrder = i)
                    }
                },
            )
        }
    }

    suspend fun savedSplit(): List<SplitRule> = envelopeDao.splitRules().map {
        when (it.kind) {
            SplitKind.FIXED -> SplitRule.Fixed(it.envelopeId, it.value)
            SplitKind.PERCENT -> SplitRule.Percent(it.envelopeId, it.value.toInt())
        }
    }

    /** Splits [income] by the saved split and stores the summary (income and unassigned). */
    suspend fun applySplit(income: Long): SplitResult {
        val result = splitIncome(income, savedSplit())
        settings.setLastSplit(income, result.unassigned)
        return result
    }

    /**
     * The four starter envelopes from the seeded categories. Monthly amounts stay 0 for the user to fill (no invented
     * amounts), so [householdSize] does not change anything yet.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun suggestDefaults(householdSize: Int?): List<EnvelopeSuggestion> {
        val byName = categoryDao.all().first().associateBy({ it.name }, { it.id })
        return SUGGESTED.map { (name, kind, cats) -> EnvelopeSuggestion(name, kind, cats.mapNotNull(byName::get).toSet()) }
    }

    /** Today's pay cycle. */
    suspend fun cycle(): PayCycle = currentCycle()

    /** The stored envelope (its saved monthly, not this cycle's) with its category ids; null when missing. */
    suspend fun saved(id: Long): Pair<EnvelopeEntity, Set<Long>>? {
        val env = envelopeDao.byId(id) ?: return null
        return env to envelopeDao.links().first().filter { it.envelopeId == id }.map { it.categoryId }.toSet()
    }

    /** This cycle's EGP spending in [categoryIds], newest first. */
    fun cycleEntries(categoryIds: Set<Long>): Flow<List<EntryRow>> =
        flow { emit(currentCycle()) }.flatMapLatest { c ->
            fun millis(d: LocalDate) = d.atStartOfDay(clock.zone).toInstant().toEpochMilli()
            entryDao.rows(millis(c.start), millis(c.nextPayday)).map { rows ->
                rows.filter { it.kind == EntryKind.SPENT && it.currency == PRIMARY_CURRENCY && it.categoryId in categoryIds }
            }
        }

    private suspend fun currentCycle(): PayCycle = entryDao.payCycleOn(LocalDate.now(clock), settings.current().payday, clock)

    /** [from, to) as local days. */
    private suspend fun spentByCategory(from: LocalDate, to: LocalDate): Map<Long, Long> {
        fun millis(d: LocalDate) = d.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return entryDao.spentByCategory(millis(from), millis(to)).associate { it.categoryId to it.total }
    }
}
