package com.masroufi.app.data.repo

import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.ScheduleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One-tap add chips: names only, the price is typed. */
val COMMON_SUBSCRIPTIONS = listOf(
    "نت البيت", "نتفليكس", "شاهد", "أنغامي", "واتش ات", "سبوتيفاي", "يوتيوب بريميوم", "اشتراك الجيم", "باقة الموبايل",
    "تخزين سحابي", "حاجة تانية",
)

enum class SubscriptionAnswer { YES, NOT_REALLY, STOPPED }

data class SubscriptionView(
    val item: RecurringEntity,
    /** A yearly-scheduled item: its yearly price ÷ 12, floored. */
    val monthly: Long,
    val perYear: Long,
    /** Shared: the user's part (price ÷ people, floored); the others owe the rest. */
    val userShare: Long,
    val othersOwe: Long,
    /** Days to the trial's last day while it runs; null = no trial or it ended. The cost if not cancelled is item.amount. */
    val trialDaysLeft: Long?,
    /** Two "not really" in a row: monthly × 12. */
    val stopSaving: Long?,
    /** Paying yearly instead: monthly × 12 − yearly price, when cheaper. */
    val yearlySaving: Long?,
)

data class SubscriptionsView(
    val items: List<SubscriptionView>,
    val monthlyTotal: Long,
    val perYear: Long,
    /** Monthly total ÷ today's ticket, floored; 0 without a ticket. */
    val tickets: Long,
    /** The question of the month; null once one was asked this calendar month. */
    val question: SubscriptionView?,
)

internal fun RecurringEntity.monthly(): Long = when (scheduleType) {
    ScheduleType.YEARLY -> (yearlyPrice ?: amount) / 12
    ScheduleType.MONTHLY -> amount
    ScheduleType.WEEKLY -> amount * 52 / 12
}

internal fun RecurringEntity.answerList(): List<SubscriptionAnswer> =
    answers?.split(',')?.mapNotNull { a -> SubscriptionAnswer.entries.firstOrNull { it.name == a } } ?: emptyList()

internal fun subscriptionView(item: RecurringEntity, today: LocalDate): SubscriptionView {
    val monthly = item.monthly()
    val share = item.amount / item.sharedWith.coerceAtLeast(1)
    val last = item.answerList().takeLast(2)
    val yearly = item.yearlyPrice
    return SubscriptionView(
        item = item,
        monthly = monthly,
        perYear = if (item.scheduleType == ScheduleType.YEARLY) yearly ?: item.amount else item.amount * 12,
        userShare = share,
        othersOwe = item.amount - share,
        trialDaysLeft = item.trialEnd?.takeIf { !it.isBefore(today) }?.let { ChronoUnit.DAYS.between(today, it) },
        stopSaving = if (last.size == 2 && last.all { it == SubscriptionAnswer.NOT_REALLY }) monthly * 12 else null,
        yearlySaving = if (item.scheduleType == ScheduleType.MONTHLY && yearly != null && item.amount * 12 > yearly) {
            item.amount * 12 - yearly
        } else {
            null
        },
    )
}

@Singleton
class SubscriptionRepository @Inject constructor(
    private val recurringDao: RecurringDao,
    private val ticketDao: DayTicketDao,
    private val clock: Clock,
) {
    fun observe(): Flow<SubscriptionsView> = flow {
        val today = LocalDate.now(clock)
        emitAll(
            combine(recurringDao.all(), ticketDao.forDateFlow(today)) { all, ticket ->
                val subs = all.filter { it.kind == RecurringKind.SUBSCRIPTION }
                val views = subs.filter { !it.archived }.map { subscriptionView(it, today) }.sortedBy { it.item.nextDue }
                val total = views.sumOf { it.monthly }
                val ticketAmount = ticket?.amount ?: 0L
                SubscriptionsView(
                    items = views,
                    monthlyTotal = total,
                    perYear = views.sumOf { it.perYear },
                    tickets = if (ticketAmount > 0) total / ticketAmount else 0L,
                    question = question(subs, views, today),
                )
            },
        )
    }

    /** Longest since asked (never asked first); at most one question per calendar month (stopped ones count). */
    private fun question(all: List<RecurringEntity>, active: List<SubscriptionView>, today: LocalDate): SubscriptionView? {
        val month = YearMonth.from(today)
        if (all.any { at -> at.lastAskedAt?.let { YearMonth.from(clock.dayOf(it)) == month } == true }) return null
        return active.minWithOrNull(compareBy<SubscriptionView>({ it.item.lastAskedAt ?: Long.MIN_VALUE }, { it.item.id }))
    }

    suspend fun add(
        name: String,
        amount: Long,
        pocketId: Long,
        nextDue: LocalDate,
        scheduleType: ScheduleType = ScheduleType.MONTHLY,
        trialEnd: LocalDate? = null,
        yearlyPrice: Long? = null,
        sharedWith: Int = 1,
    ): Long {
        require(amount > 0) { "amount must be > 0" }
        require(yearlyPrice == null || yearlyPrice > 0) { "yearly price must be > 0" }
        require(sharedWith >= 1) { "sharedWith counts the user: >= 1" }
        return recurringDao.insert(
            RecurringEntity(
                name = name, amount = amount, pocketId = pocketId, scheduleDay = nextDue.dayOfMonth, nextDue = nextDue,
                kind = RecurringKind.SUBSCRIPTION, scheduleType = scheduleType, trialEnd = trialEnd, yearlyPrice = yearlyPrice,
                sharedWith = sharedWith, updatedAt = clock.millis(),
            ),
        )
    }

    /** Keeps the last three answers; STOPPED archives the subscription. */
    suspend fun answer(id: Long, answer: SubscriptionAnswer) {
        val item = checkNotNull(recurringDao.byId(id)) { "no subscription $id" }
        val now = clock.millis()
        recurringDao.update(
            item.copy(
                answers = (item.answerList() + answer).takeLast(3).joinToString(",") { it.name },
                lastAskedAt = now,
                archived = item.archived || answer == SubscriptionAnswer.STOPPED,
                updatedAt = now,
            ),
        )
    }
}
