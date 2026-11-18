package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.AdvanceAnswer
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.ChildAdvanceEntity
import com.masroufi.app.data.db.ChildEntity
import com.masroufi.app.data.db.ChildJarKind
import com.masroufi.app.data.db.ChildJarMoveEntity
import com.masroufi.app.data.db.ChildSpendEntity
import com.masroufi.app.data.db.ChoreEntity
import com.masroufi.app.data.db.ChoreTickEntity
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.EnvelopeCategoryEntity
import com.masroufi.app.data.db.EnvelopeDao
import com.masroufi.app.data.db.EnvelopeEntity
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.KidsDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.entitlements.Entitlements
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** Children on Free (09-PREMIUM); more are Plus, and extras are kept read-only when Plus ends. */
const val FREE_CHILDREN = 1

/** The kids' envelope is the one with this name; the first child creates [KIDS_NEW_ENVELOPE] when there is none (builder's wording). */
const val KIDS_ENVELOPE = "العيال"
const val KIDS_NEW_ENVELOPE = "ظرف العيال"
const val KIDS_ITEM_NAME = "مصروف العيال"
private const val KIDS_CATEGORY_ICON = "people"
/** The note a jar saving carries in child_spend: it takes money out of "left" but is not a punch. */
const val JAR_SPEND_NOTE = "jar"
private val KIDS_COLOR = 0xFFE7A6FF.toInt() // Tz.lilac, as the data layer's other copies of Tz colours

/** The handover day until the parent picks one ("المصروف بيتسلّم كل سبت"). */
val DEFAULT_HANDOVER_DAY: DayOfWeek = DayOfWeek.SATURDAY

/** What the child asked to spend is more than what is left until handover; [left] is what is there. Nothing is recorded. */
class AboveLeft(val left: Long) : Exception("above what is left until handover ($left)")

/** A jar withdrawal above what the jar holds. */
class AboveChildJar(val saved: Long) : Exception("above the child's jar ($saved)")

/** An advance that is not allowed: one is open already, or it is above [limit] (half of the allowance, less what was taken). */
class AdvanceRefused(val reason: AdvanceRefusal, val limit: Long) : Exception("advance refused: $reason")

enum class AdvanceRefusal { OPEN_EXISTS, ABOVE_LIMIT }

enum class TickResult { TICKED, UNTICKED, TAKEN }

data class ChildJar(
    val label: String?,
    val target: Long?,
    val saved: Long,
    /** Saved / target as a whole percent, 0..100 (floored); 0 without a target. */
    val percent: Int,
    val goalReached: Boolean,
    val matchPer: Long?,
    val matchAdd: Long?,
)

/** What one child gets at the next handover: weekly + ticked chore bonuses + the match earned this week - approved advances (never below 0). */
data class ChildHandover(val childId: Long, val weekly: Long, val chores: Long, val match: Long, val advances: Long) {
    val amount: Long get() = (weekly + chores + match - advances).coerceAtLeast(0)
}

data class ChildView(
    val child: ChildEntity,
    /** False for a child beyond the first when Plus is off: shown, never changed. */
    val editable: Boolean,
    val age: Int,
    /** What the last handover gave plus advances approved since, less what the child spent; never below 0. */
    val left: Long,
    val handedOver: Long,
    /** Days since the week opened, 0..7 (the punches row). */
    val daysIntoWeek: Int,
    val jar: ChildJar?,
    val waitingAdvance: ChildAdvanceEntity?,
    val handover: ChildHandover,
    /** Spends since the last handover that were not jar savings: the punches row. */
    val punches: Int = 0,
    /** When the latest of them happened (epoch millis); null = none. */
    val lastSpendAt: Long? = null,
)

/** [tickedBy] is the child who did the chore this week; null = not yet. */
data class ChoreView(val chore: ChoreEntity, val tickedBy: Long?)

data class KidsView(
    val children: List<ChildView>,
    val chores: List<ChoreView>,
    val handoverDay: DayOfWeek,
    /** The next handover's date; null while there is no child. */
    val nextHandover: LocalDate?,
    /** The handover button's sum for all children. */
    val total: Long,
    /** Weekly total x 4, the mock's "880 في الشهر". */
    val monthEstimate: Long,
    val envelopeName: String?,
    val canAddChild: Boolean,
)

data class HandoverResult(val entryId: Long, val total: Long, val perChild: List<ChildHandover>)

/** Money the match adds: [add] for every whole [per] saved this week (saved less withdrawn, never below 0). */
internal fun matchEarned(child: ChildEntity, moves: List<ChildJarMoveEntity>): Long {
    val per = child.matchPer ?: return 0
    val add = child.matchAdd ?: return 0
    if (per <= 0) return 0
    val week = moves.filter { it.at >= child.handedAt }
    val net = week.filter { it.kind == ChildJarKind.SAVE }.sumOf { it.amount } - week.filter { it.kind == ChildJarKind.WITHDRAW }.sumOf { it.amount }
    return net.coerceAtLeast(0) / per * add
}

private fun List<ChildJarMoveEntity>.saved(): Long = sumOf { if (it.kind == ChildJarKind.WITHDRAW) -it.amount else it.amount }

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class KidsRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val kidsDao: KidsDao,
    private val recurringDao: RecurringDao,
    private val pocketDao: PocketDao,
    private val entryDao: EntryDao,
    private val envelopeDao: EnvelopeDao,
    private val categoryDao: CategoryDao,
    private val entries: EntryRepository,
    private val entitlements: Entitlements,
    private val clock: Clock,
) {
    fun observe(): Flow<KidsView> = combine(
        listOf<Flow<Any?>>(
            kidsDao.children(), kidsDao.chores(), kidsDao.ticks(), kidsDao.advances(), kidsDao.jarMoves(), kidsDao.spends(),
            recurringDao.all(), entitlements.isPlus,
        ),
    ) { }.mapLatest { view() }

    suspend fun view(): KidsView {
        val today = LocalDate.now(clock)
        val children = kidsDao.childrenNow()
        val item = kidsItem()
        val w = week(item)
        val editable = editableIds(children)
        val views = children.map { c ->
            val moves = kidsDao.jarMovesOf(c.id)
            val handover = handoverOf(c, w, moves)
            val approved = w.advances.filter { it.childId == c.id && it.answer == AdvanceAnswer.APPROVED }.sumOf { it.amount }
            val spent = kidsDao.spendsSince(c.id, c.handedAt)
            val punched = spent.filter { it.note != JAR_SPEND_NOTE }
            ChildView(
                child = c,
                editable = c.id in editable,
                age = today.year - c.birthYear,
                left = (c.handedOver + approved - spent.sumOf { it.amount }).coerceAtLeast(0),
                handedOver = c.handedOver,
                daysIntoWeek = w.key?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceIn(0, 7) } ?: 0,
                jar = jarOf(c, moves),
                waitingAdvance = w.advances.firstOrNull { it.childId == c.id && it.answer == AdvanceAnswer.WAITING },
                handover = handover,
                punches = punched.size,
                lastSpendAt = punched.maxOfOrNull { it.at },
            )
        }
        val total = views.sumOf { it.handover.amount }
        return KidsView(
            children = views,
            chores = w.chores.map { ch -> ChoreView(ch, w.ticks.firstOrNull { it.choreId == ch.id }?.childId) },
            handoverDay = item?.let { DayOfWeek.of(it.scheduleDay) } ?: DEFAULT_HANDOVER_DAY,
            nextHandover = item?.nextDue,
            total = total,
            monthEstimate = total * 4,
            envelopeName = item?.envelopeId?.let { envelopeDao.byId(it)?.name },
            canAddChild = entitlements.isPlus.value || children.size < FREE_CHILDREN,
        )
    }

    // ---- children

    /** Refuses a second child on Free with [PlusRequired]. The new child starts with this week's allowance handed over. */
    suspend fun addChild(
        name: String,
        birthYear: Int,
        color: Int,
        weekly: Long,
        jarLabel: String? = null,
        jarTarget: Long? = null,
        matchPer: Long? = null,
        matchAdd: Long? = null,
    ): Long {
        validate(name, birthYear, weekly, jarTarget, matchPer, matchAdd)
        return db.withTransaction {
            if (!entitlements.isPlus.value && kidsDao.childrenNow().size >= FREE_CHILDREN) throw PlusRequired()
            val now = clock.millis()
            val id = kidsDao.insertChild(
                ChildEntity(
                    name = name.trim(), birthYear = birthYear, color = color, weekly = weekly, jarLabel = jarLabel?.trim()?.ifEmpty { null },
                    jarTarget = jarTarget, matchPer = matchPer, matchAdd = matchAdd, handedOver = weekly, handedAt = now,
                ),
            )
            syncItem()
            id
        }
    }

    /** Takes the editable fields of [child] (not the handover record); [PlusRequired] for a read-only child. */
    suspend fun updateChild(child: ChildEntity) {
        validate(child.name, child.birthYear, child.weekly, child.jarTarget, child.matchPer, child.matchAdd)
        db.withTransaction {
            val old = editable(child.id)
            kidsDao.updateChild(
                old.copy(
                    name = child.name.trim(), birthYear = child.birthYear, color = child.color, weekly = child.weekly,
                    jarLabel = child.jarLabel?.trim()?.ifEmpty { null }, jarTarget = child.jarTarget, matchPer = child.matchPer, matchAdd = child.matchAdd,
                ),
            )
            syncItem()
        }
    }

    /** Hides the child (its history stays); the handover item goes with the last one. */
    suspend fun deleteChild(id: Long) {
        db.withTransaction {
            kidsDao.updateChild(editable(id).copy(archived = true))
            syncItem()
        }
    }

    // ---- handover day and the handover

    /** The day of the week the handover falls on, as chosen (Saturday until then). */
    suspend fun handoverDay(): DayOfWeek = kidsItem()?.let { DayOfWeek.of(it.scheduleDay) } ?: DEFAULT_HANDOVER_DAY

    /** Moves the handover to the next [day] (today counts); the open week's ticks and advances follow it. */
    suspend fun setHandoverDay(day: DayOfWeek) {
        db.withTransaction {
            val item = checkNotNull(kidsItem()) { "no child yet" }
            val due = LocalDate.now(clock).with(TemporalAdjusters.nextOrSame(day))
            if (due == item.nextDue) return@withTransaction
            recurringDao.update(item.copy(scheduleDay = day.value, nextDue = due, updatedAt = clock.millis()))
            kidsDao.rekeyTicks(item.nextDue.minusDays(7), due.minusDays(7))
            kidsDao.rekeyAdvances(item.nextDue.minusDays(7), due.minusDays(7))
        }
    }

    /**
     * One tap: one SPENT entry of the sum for all children, paid from the kids' envelope's category, which is one payment of
     * the weekly item (so its due date moves a week on). Each child's [ChildEntity.handedOver] becomes their part, their
     * match is added to their jar, and the week's ticks, advances and match start over (a waiting advance moves to the new week).
     */
    suspend fun handOver(): HandoverResult = db.withTransaction {
        val item = checkNotNull(kidsItem()) { "no child to hand over to" }
        val children = kidsDao.childrenNow()
        val w = week(item)
        val per = children.map { handoverOf(it, w, kidsDao.jarMovesOf(it.id)) }
        val total = per.sumOf { it.amount }
        require(total > 0) { "nothing to hand over" }
        val (_, categoryId) = kidsEnvelope(total)
        val pocket = pocketDao.byId(item.pocketId)?.takeIf { !it.archived } ?: pocketDao.firstCounting() ?: checkNotNull(pocketDao.firstCash()) { "no pocket" }
        val now = clock.millis()
        val entryId = entries.log(
            NewEntry(kind = EntryKind.SPENT, amount = total, pocketId = pocket.id, categoryId = categoryId, happenedAt = now, recurringId = item.id),
        )
        per.forEach { h ->
            val child = children.first { it.id == h.childId }
            if (h.match > 0) kidsDao.insertJarMove(ChildJarMoveEntity(childId = child.id, amount = h.match, at = now, kind = ChildJarKind.MATCH))
            kidsDao.updateChild(child.copy(handedOver = h.amount, handedAt = now))
        }
        kidsDao.rekeyWaiting(w.key!!, w.key.plusDays(7))
        syncItem()
        HandoverResult(entryId, total, per)
    }

    // ---- chores

    suspend fun addChore(label: String, bonus: Long, childId: Long? = null): Long {
        require(label.isNotBlank()) { "a chore needs a label" }
        require(bonus > 0) { "bonus must be > 0" }
        return db.withTransaction {
            if (childId != null) requireNotNull(kidsDao.childById(childId)) { "no child $childId" }
            kidsDao.insertChore(ChoreEntity(label = label.trim(), bonus = bonus, childId = childId)).also { syncItem() }
        }
    }

    suspend fun updateChore(chore: ChoreEntity) {
        require(chore.label.isNotBlank()) { "a chore needs a label" }
        require(chore.bonus > 0) { "bonus must be > 0" }
        db.withTransaction {
            checkNotNull(kidsDao.choreById(chore.id)) { "no chore ${chore.id}" }
            kidsDao.updateChore(chore.copy(label = chore.label.trim()))
            syncItem()
        }
    }

    suspend fun deleteChore(id: Long) {
        db.withTransaction {
            kidsDao.deleteTicksOfChore(id)
            kidsDao.deleteChore(id)
            syncItem()
        }
    }

    /**
     * Ticks or unticks a chore for a child this week. A chore is done once a week: one for "any child" that another child
     * already did gives [TickResult.TAKEN] and changes nothing. The bonus goes into the next handover.
     */
    suspend fun toggleChore(choreId: Long, childId: Long): TickResult = db.withTransaction {
        val child = editable(childId)
        val chore = checkNotNull(kidsDao.choreById(choreId)) { "no chore $choreId" }
        require(chore.childId == null || chore.childId == child.id) { "chore $choreId is for another child" }
        val key = checkNotNull(kidsItem()) { "no child yet" }.nextDue.minusDays(7)
        val ticks = kidsDao.ticksIn(key).filter { it.choreId == choreId }
        val mine = ticks.firstOrNull { it.childId == child.id }
        val result = when {
            mine != null -> { kidsDao.deleteTick(mine.id); TickResult.UNTICKED }
            ticks.isNotEmpty() -> TickResult.TAKEN
            else -> { kidsDao.insertTick(ChoreTickEntity(choreId = choreId, childId = child.id, weekStart = key)); TickResult.TICKED }
        }
        syncItem()
        result
    }

    // ---- advances

    /**
     * Asks for [amount] of next week's allowance. One advance is open per child ([AdvanceRefused] OPEN_EXISTS), and what is
     * asked for plus what was approved this week is at most half of the weekly allowance (ABOVE_LIMIT).
     */
    suspend fun requestAdvance(childId: Long, amount: Long): Long {
        require(amount > 0) { "amount must be > 0" }
        return db.withTransaction {
            val child = editable(childId)
            val key = checkNotNull(kidsItem()) { "no child yet" }.nextDue.minusDays(7)
            val mine = kidsDao.advancesIn(key).filter { it.childId == childId }
            if (mine.any { it.answer == AdvanceAnswer.WAITING }) throw AdvanceRefused(AdvanceRefusal.OPEN_EXISTS, 0)
            val limit = (child.weekly / 2 - mine.filter { it.answer == AdvanceAnswer.APPROVED }.sumOf { it.amount }).coerceAtLeast(0)
            if (amount > limit) throw AdvanceRefused(AdvanceRefusal.ABOVE_LIMIT, limit)
            kidsDao.insertAdvance(ChildAdvanceEntity(childId = childId, amount = amount, requestedAt = clock.millis(), weekStart = key))
        }
    }

    /** "ماشي، تتخصم" ([approve]) takes it off the next handover; "يستنى السبت" declines it. */
    suspend fun answerAdvance(advanceId: Long, approve: Boolean) {
        db.withTransaction {
            val advance = checkNotNull(kidsDao.advanceById(advanceId)) { "no advance $advanceId" }
            require(advance.answer == AdvanceAnswer.WAITING) { "advance $advanceId was answered already" }
            editable(advance.childId)
            kidsDao.updateAdvance(advance.copy(answer = if (approve) AdvanceAnswer.APPROVED else AdvanceAnswer.DECLINED))
            syncItem()
        }
    }

    // ---- the child's jar and spending

    suspend fun save(childId: Long, amount: Long) = jarMove(childId, amount, ChildJarKind.SAVE)

    /** At most what the jar holds ([AboveChildJar] otherwise). */
    suspend fun withdraw(childId: Long, amount: Long) = jarMove(childId, amount, ChildJarKind.WITHDRAW)

    private suspend fun jarMove(childId: Long, amount: Long, kind: ChildJarKind) {
        require(amount > 0) { "amount must be > 0" }
        db.withTransaction {
            editable(childId)
            val saved = kidsDao.jarMovesOf(childId).saved()
            if (kind == ChildJarKind.WITHDRAW && amount > saved) throw AboveChildJar(saved)
            kidsDao.insertJarMove(ChildJarMoveEntity(childId = childId, amount = amount, at = clock.millis(), kind = kind))
            syncItem() // the match follows what was saved
        }
    }

    /** What the child spent out of the handover. Above what is left nothing is recorded ([AboveLeft]): the parent logs that as their own entry. */
    suspend fun spend(childId: Long, amount: Long, note: String? = null): Long {
        require(amount > 0) { "amount must be > 0" }
        require(note == null || note.length <= NOTE_MAX_CHARS) { "note must be at most $NOTE_MAX_CHARS chars" }
        return db.withTransaction {
            val child = editable(childId)
            val key = kidsItem()?.nextDue?.minusDays(7)
            val approved = key?.let { k -> kidsDao.advancesIn(k).filter { it.childId == childId && it.answer == AdvanceAnswer.APPROVED }.sumOf { it.amount } } ?: 0L
            val left = (child.handedOver + approved - kidsDao.spendsSince(childId, child.handedAt).sumOf { it.amount }).coerceAtLeast(0)
            if (amount > left) throw AboveLeft(left)
            kidsDao.insertSpend(ChildSpendEntity(childId = childId, amount = amount, at = clock.millis(), note = note?.trim()?.ifEmpty { null }))
        }
    }

    /**
     * The child puts [amount] of what is left into their jar: one jar saving (so the match follows) and one spend marked
     * [JAR_SPEND_NOTE], so the money is not counted twice. Above what is left: [AboveLeft], nothing recorded.
     */
    suspend fun saveFromLeft(childId: Long, amount: Long) {
        require(amount > 0) { "amount must be > 0" }
        db.withTransaction {
            val child = editable(childId)
            val key = kidsItem()?.nextDue?.minusDays(7)
            val approved = key?.let { k -> kidsDao.advancesIn(k).filter { it.childId == childId && it.answer == AdvanceAnswer.APPROVED }.sumOf { it.amount } } ?: 0L
            val left = (child.handedOver + approved - kidsDao.spendsSince(childId, child.handedAt).sumOf { it.amount }).coerceAtLeast(0)
            if (amount > left) throw AboveLeft(left)
            val now = clock.millis()
            kidsDao.insertSpend(ChildSpendEntity(childId = childId, amount = amount, at = now, note = JAR_SPEND_NOTE))
            kidsDao.insertJarMove(ChildJarMoveEntity(childId = childId, amount = amount, at = now, kind = ChildJarKind.SAVE))
            syncItem()
        }
    }

    // ---- internals

    private class Week(val key: LocalDate?, val chores: List<ChoreEntity>, val ticks: List<ChoreTickEntity>, val advances: List<ChildAdvanceEntity>)

    private suspend fun week(item: RecurringEntity?): Week {
        val key = item?.nextDue?.minusDays(7)
        return Week(
            key, kidsDao.choresNow(),
            key?.let { kidsDao.ticksIn(it) } ?: emptyList(), key?.let { kidsDao.advancesIn(it) } ?: emptyList(),
        )
    }

    private fun handoverOf(child: ChildEntity, w: Week, moves: List<ChildJarMoveEntity>) = ChildHandover(
        childId = child.id,
        weekly = child.weekly,
        chores = w.ticks.filter { it.childId == child.id }.sumOf { t -> w.chores.firstOrNull { it.id == t.choreId }?.bonus ?: 0L },
        match = matchEarned(child, moves),
        advances = w.advances.filter { it.childId == child.id && it.answer == AdvanceAnswer.APPROVED }.sumOf { it.amount },
    )

    private fun jarOf(child: ChildEntity, moves: List<ChildJarMoveEntity>): ChildJar? {
        if (child.jarTarget == null && child.jarLabel == null) return null
        val saved = moves.saved()
        val target = child.jarTarget
        return ChildJar(
            label = child.jarLabel, target = target, saved = saved,
            percent = if (target != null && target > 0) (saved * 100 / target).toInt().coerceIn(0, 100) else 0,
            goalReached = target != null && saved >= target,
            matchPer = child.matchPer, matchAdd = child.matchAdd,
        )
    }

    /** The lowest-id children are editable on Free (extras are read-only); every child on Plus. */
    private fun editableIds(children: List<ChildEntity>): Set<Long> =
        if (entitlements.isPlus.value) children.map { it.id }.toSet() else children.take(FREE_CHILDREN).map { it.id }.toSet()

    private suspend fun editable(childId: Long): ChildEntity {
        val children = kidsDao.childrenNow()
        val child = checkNotNull(children.firstOrNull { it.id == childId }) { "no child $childId" }
        if (childId !in editableIds(children)) throw PlusRequired()
        return child
    }

    private suspend fun kidsItem(): RecurringEntity? = recurringDao.allNow().firstOrNull { it.kind == RecurringKind.KIDS && !it.archived }

    /**
     * Keeps the weekly item true: made with the first child, its amount the sum of what the next handover will give, gone
     * with the last child. Caller holds the transaction.
     */
    private suspend fun syncItem() {
        val children = kidsDao.childrenNow()
        val item = kidsItem()
        if (children.isEmpty()) {
            if (item != null) archiveOrDelete(item)
            return
        }
        val current = item ?: createItem(children)
        val w = week(current)
        val total = children.sumOf { handoverOf(it, w, kidsDao.jarMovesOf(it.id)).amount }
        if (total > 0 && total != current.amount) recurringDao.update(current.copy(amount = total, updatedAt = clock.millis()))
    }

    private suspend fun archiveOrDelete(item: RecurringEntity) {
        if (entryDao.countAllForRecurring(item.id) == 0) recurringDao.delete(item.id) else recurringDao.update(item.copy(archived = true, updatedAt = clock.millis()))
    }

    private suspend fun createItem(children: List<ChildEntity>): RecurringEntity {
        val pocket = pocketDao.firstCounting() ?: checkNotNull(pocketDao.firstCash()) { "no pocket to hand the allowance over from" }
        val weekly = children.sumOf { it.weekly }
        val (envelope, _) = kidsEnvelope(weekly)
        val due = LocalDate.now(clock).with(TemporalAdjusters.next(DEFAULT_HANDOVER_DAY))
        val item = RecurringEntity(
            name = KIDS_ITEM_NAME, amount = weekly, pocketId = pocket.id, scheduleDay = DEFAULT_HANDOVER_DAY.value, nextDue = due,
            envelopeId = envelope.id, kind = RecurringKind.KIDS, scheduleType = ScheduleType.WEEKLY, updatedAt = clock.millis(),
        )
        return item.copy(id = recurringDao.insert(item))
    }

    /** The kids' envelope (named "العيال", else made, with no Free limit) and the category its handovers are filed under. Caller holds the transaction. */
    private suspend fun kidsEnvelope(weekly: Long): Pair<EnvelopeEntity, Long> {
        val now = clock.millis()
        val envelope = envelopeDao.allNow().firstOrNull { !it.archived && it.name.trim() in setOf(KIDS_ENVELOPE, KIDS_NEW_ENVELOPE) }
            ?: EnvelopeEntity(name = KIDS_NEW_ENVELOPE, color = KIDS_COLOR, monthly = weekly * 4, kind = EnvelopeKind.SPENDING, updatedAt = now)
                .let { it.copy(id = envelopeDao.insert(it)) }
        val category = envelopeDao.linksNow().filter { it.envelopeId == envelope.id }.minOfOrNull { it.categoryId }
            ?: categoryDao.insert(CategoryEntity(name = KIDS_ENVELOPE, icon = KIDS_CATEGORY_ICON, color = KIDS_COLOR, updatedAt = now))
                .also { envelopeDao.link(EnvelopeCategoryEntity(envelope.id, it, now)) }
        return envelope to category
    }

    private fun validate(name: String, birthYear: Int, weekly: Long, jarTarget: Long?, matchPer: Long?, matchAdd: Long?) {
        require(name.isNotBlank()) { "a child needs a name" }
        require(birthYear in 1900..LocalDate.now(clock).year) { "birth year out of range" }
        require(weekly > 0) { "the weekly allowance must be > 0" }
        require(jarTarget == null || jarTarget > 0) { "jar target must be > 0" }
        require((matchPer == null) == (matchAdd == null)) { "a match rule needs both the step and the amount" }
        require(matchPer == null || (matchPer > 0 && matchAdd!! > 0)) { "match step and amount must be > 0" }
    }
}
