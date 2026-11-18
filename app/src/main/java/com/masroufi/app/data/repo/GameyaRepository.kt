package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.db.GameyaDao
import com.masroufi.app.data.db.GameyaEntity
import com.masroufi.app.data.db.GameyaMemberEntity
import com.masroufi.app.data.db.GameyaPaymentEntity
import com.masroufi.app.data.db.GameyaPlanEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.entitlements.Entitlements
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

const val FREE_GAMEYAS = 1

enum class SlotState { PAID_OUT, THIS_MONTH, WAITING }

/** One seat of the wheel; [member] is null for the user. */
data class WheelSlot(val turn: Int, val member: GameyaMemberEntity?, val state: SlotState) {
    val isMe: Boolean get() = member == null
}

data class NewGameyaMember(val name: String, val phone: String? = null)

/**
 * [people] counts the user. [monthNumber] is this month's turn (0 before the start, [people] once it is over, with
 * [finished]); [thisMonth] is the calendar month clamped into the gameya's months.
 */
data class GameyaView(
    val gameya: GameyaEntity,
    val people: Int,
    val payout: Long,
    val myTurnMonth: YearMonth,
    val monthNumber: Int,
    val finished: Boolean,
    val thisMonth: YearMonth,
    val monthsUntil: Long,
    val myPaidSoFar: Long,
    val myPaidThisMonth: Boolean,
    val wheel: List<WheelSlot>,
    /** The others' installments this month; [paidCount] includes the user's own. */
    val paidMembers: List<GameyaMemberEntity>,
    val unpaidMembers: List<GameyaMemberEntity>,
    val paidCount: Int,
    val plan: List<GameyaPlanEntity>,
    val unplanned: Long,
)

internal fun Int.toYearMonth(): YearMonth = YearMonth.of(this / 100, this % 100)
internal fun YearMonth.toInt(): Int = year * 100 + monthValue

internal fun gameyaView(
    g: GameyaEntity,
    members: List<GameyaMemberEntity>,
    payments: List<GameyaPaymentEntity>,
    plan: List<GameyaPlanEntity>,
    today: LocalDate,
): GameyaView {
    val people = members.size + 1
    val start = g.startMonth.toYearMonth()
    val now = YearMonth.from(today)
    val elapsed = ChronoUnit.MONTHS.between(start, now).toInt() + 1
    val finished = elapsed > people
    val thisMonth = if (now.isBefore(start)) start else minOf(now, start.plusMonths(people - 1L))
    val turn = if (finished) people + 1 else elapsed.coerceAtLeast(0)
    val mine = payments.filter { it.gameyaId == g.id && it.memberId == null && it.paid }
    val paidIds = payments.filter { it.gameyaId == g.id && it.month == thisMonth.toInt() && it.paid }.mapNotNull { it.memberId }.toSet()
    val (paid, unpaid) = members.partition { it.id in paidIds }
    val myPaidNow = mine.any { it.month == thisMonth.toInt() }
    val myTurnMonth = start.plusMonths(g.myTurn - 1L)
    val seats = (members.map { WheelSlot(it.turn, it, SlotState.WAITING) } + WheelSlot(g.myTurn, null, SlotState.WAITING))
        .sortedBy { it.turn }
        .map { it.copy(state = if (it.turn < turn) SlotState.PAID_OUT else if (it.turn == turn) SlotState.THIS_MONTH else SlotState.WAITING) }
    return GameyaView(
        gameya = g,
        people = people,
        payout = g.monthly * people,
        myTurnMonth = myTurnMonth,
        monthNumber = turn.coerceIn(0, people),
        finished = finished,
        thisMonth = thisMonth,
        monthsUntil = ChronoUnit.MONTHS.between(now, myTurnMonth).coerceAtLeast(0),
        myPaidSoFar = g.monthly * mine.map { it.month }.distinct().count { it <= now.toInt() },
        myPaidThisMonth = myPaidNow,
        wheel = seats,
        paidMembers = paid,
        unpaidMembers = unpaid,
        paidCount = paid.size + if (myPaidNow) 1 else 0,
        plan = plan.filter { it.gameyaId == g.id },
        unplanned = g.monthly * people - plan.filter { it.gameyaId == g.id }.sumOf { it.amount },
    )
}

/** The plain Arabic text for the group chat: the month, who was paid out, whose turn it is and who has not paid yet. */
internal fun gameyaStatement(v: GameyaView): String {
    fun names(list: List<WheelSlot>) = list.joinToString("، ") { it.member?.name ?: "أنا" }
    val turn = v.wheel.firstOrNull { it.state == SlotState.THIS_MONTH }
    return buildList {
        add("كشف ${v.gameya.name}")
        add("القسط الشهري: ${formatPounds(v.gameya.monthly)} · القبض: ${formatPounds(v.payout)}")
        add("شهر ${v.monthNumber.coerceAtLeast(1)} من ${v.people}")
        v.wheel.filter { it.state == SlotState.PAID_OUT }.takeIf { it.isNotEmpty() }?.let { add("قبضوا: ${names(it)}") }
        turn?.let { add("دور الشهر ده: ${names(listOf(it))}") }
        add("دفعوا ${v.paidCount} من ${v.people}")
        v.unpaidMembers.takeIf { it.isNotEmpty() }?.let { add("لسه: " + it.joinToString("، ") { m -> m.name }) }
    }.joinToString("\n")
}

@Singleton
class GameyaRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val dao: GameyaDao,
    private val recurring: RecurringRepository,
    private val entitlements: Entitlements,
    private val clock: Clock,
) {
    /** Active gameyas, oldest first. */
    fun observe(): Flow<List<GameyaView>> = combine(dao.all(), dao.members(), dao.payments(), dao.plans()) { all, members, payments, plans ->
        val today = LocalDate.now(clock)
        all.filter { !it.archived }.map { g -> gameyaView(g, members.filter { it.gameyaId == g.id }, payments, plans, today) }
    }

    /**
     * [others] are the other members in turn order; they fill the turns around the user's [myTurn] (1..others+1). The
     * user's installment becomes the gameya's recurring item, paid from [pocketId] on [dueDay]. Free: one active
     * gameya, else [PlusRequired].
     */
    suspend fun create(
        name: String,
        monthly: Long,
        startMonth: YearMonth,
        myTurn: Int,
        others: List<NewGameyaMember>,
        pocketId: Long,
        dueDay: Int = 1,
    ): Long {
        require(name.isNotBlank()) { "name must not be blank" }
        require(monthly > 0) { "monthly must be > 0" }
        require(others.isNotEmpty()) { "a gameya needs other members" }
        require(myTurn in 1..others.size + 1) { "my turn must be 1..${others.size + 1}" }
        return db.withTransaction {
            if (!entitlements.isPlus.value && dao.activeCount() >= FREE_GAMEYAS) throw PlusRequired()
            val today = LocalDate.now(clock)
            val first = maxOf(startMonth, YearMonth.from(today))
            val due = first.atDay(minOf(dueDay.coerceAtLeast(1), first.lengthOfMonth()))
            val recurringId = recurring.add(name.trim(), monthly, pocketId, due, kind = RecurringKind.GAMEYA)
            val id = dao.insert(GameyaEntity(name = name.trim(), monthly = monthly, startMonth = startMonth.toInt(), myTurn = myTurn, recurringId = recurringId))
            var turn = 0
            others.forEach {
                turn++
                if (turn == myTurn) turn++
                dao.insertMember(GameyaMemberEntity(gameyaId = id, name = it.name.trim(), turn = turn, phone = it.phone?.trim()?.ifEmpty { null }))
            }
            id
        }
    }

    suspend fun get(id: Long): GameyaEntity? = dao.byId(id)

    /** Renames the gameya or changes its monthly amount; the installment item follows. */
    suspend fun update(id: Long, name: String, monthly: Long) {
        require(name.isNotBlank()) { "name must not be blank" }
        require(monthly > 0) { "monthly must be > 0" }
        db.withTransaction {
            val g = checkNotNull(dao.byId(id)) { "no gameya $id" }
            dao.update(g.copy(name = name.trim(), monthly = monthly))
            g.recurringId?.let { rid -> recurring.get(rid)?.let { recurring.update(it.copy(name = name.trim(), amount = monthly)) } }
        }
    }

    /** The gameya leaves the list, and so does its installment (kept for the entries that paid it). */
    suspend fun archive(id: Long) = db.withTransaction {
        val g = checkNotNull(dao.byId(id)) { "no gameya $id" }
        dao.update(g.copy(archived = true))
        g.recurringId?.let { recurring.delete(it) }
    }

    /** "بدّل دورك مع حد": the user takes [memberId]'s turn and that member takes the user's. */
    suspend fun swapTurn(gameyaId: Long, memberId: Long) = db.withTransaction {
        val g = checkNotNull(dao.byId(gameyaId)) { "no gameya $gameyaId" }
        val m = checkNotNull(dao.memberById(memberId)) { "no member $memberId" }
        require(m.gameyaId == gameyaId) { "member $memberId is not in gameya $gameyaId" }
        dao.update(g.copy(myTurn = m.turn))
        dao.updateMember(m.copy(turn = g.myTurn))
    }

    /** Records that a member ([memberId] null = the user) paid or has not paid [month]. */
    suspend fun setPaid(gameyaId: Long, memberId: Long?, month: YearMonth, paid: Boolean) = db.withTransaction {
        val old = dao.payment(gameyaId, memberId, month.toInt())
        if (old == null) {
            dao.insertPayment(GameyaPaymentEntity(gameyaId = gameyaId, memberId = memberId, month = month.toInt(), paid = paid))
        } else {
            dao.updatePayment(old.copy(paid = paid))
        }
    }

    /** The user's installment: logs the recurring payment (like any fixed cost) and marks this month paid. */
    suspend fun payMine(gameyaId: Long, pocketId: Long) = db.withTransaction {
        val g = checkNotNull(dao.byId(gameyaId)) { "no gameya $gameyaId" }
        recurring.markPaid(checkNotNull(g.recurringId) { "gameya $gameyaId has no installment item" }, pocketId)
        val view = gameyaView(g, dao.membersOf(gameyaId), emptyList(), emptyList(), LocalDate.now(clock))
        setPaid(gameyaId, null, view.thisMonth, true)
    }

    suspend fun addPlanRow(gameyaId: Long, label: String, amount: Long, seasonId: Long? = null, goalId: Long? = null): Long {
        require(amount > 0) { "amount must be > 0" }
        require(label.isNotBlank()) { "label must not be blank" }
        return dao.insertPlan(GameyaPlanEntity(gameyaId = gameyaId, label = label.trim(), amount = amount, seasonId = seasonId, goalId = goalId))
    }

    suspend fun updatePlanRow(row: GameyaPlanEntity) {
        require(row.amount > 0) { "amount must be > 0" }
        dao.updatePlan(row)
    }

    suspend fun deletePlanRow(id: Long) = dao.deletePlan(id)

    /** The statement to share to the group chat. */
    suspend fun statement(gameyaId: Long): String =
        gameyaStatement(checkNotNull(observe().first().firstOrNull { it.gameya.id == gameyaId }) { "no active gameya $gameyaId" })
}
