package com.masroufi.app.notify

import com.masroufi.app.core.formatPounds
import com.masroufi.app.domain.NotifKind
import java.time.LocalDateTime

/** At most this many notifications a day, whatever their kind (07-COPY "Never"). */
const val NOTIF_DAILY_CAP = 2

/** What an amount becomes while the lock or "hide amounts" is on (same dots as the screens). */
const val NOTIF_DOTS = "•••"

data class DueItem(val id: Long, val name: String, val amount: Long = 0)
data class TurnItem(val gameyaId: Long, val yearMonth: Int, val payout: Long)
data class OccasionItem(val id: Long, val name: String, val gaveMe: Long?)
data class LowPocket(val id: Long, val name: String, val balance: Long)

/** A snapshot of everything the rules look at; the loader fills it, so the rules need no database. */
data class NotifFacts(
    /** Today's ticket row exists and is still open, and what is left of it (piastres, can be <= 0). */
    val ticketOpen: Boolean = false,
    val ticketLeft: Long = 0,
    /** Today's ticket amount; null = no row printed today. */
    val ticketAmount: Long? = null,
    val streak: Int = 0,
    val billsTomorrow: List<DueItem> = emptyList(),
    val gameyaDueTomorrow: List<DueItem> = emptyList(),
    /** Gameyas whose payout month is this month. */
    val gameyaTurns: List<TurnItem> = emptyList(),
    /** Wishes parked 48 hours ago or more and not answered. */
    val wishesDue: List<DueItem> = emptyList(),
    /** Someone else's occasions exactly a week away. */
    val occasions: List<OccasionItem> = emptyList(),
    val plusTrialEndsTomorrow: Boolean = false,
    /** Subscription trials whose "remind me" day is today. */
    val subTrials: List<DueItem> = emptyList(),
    /** Pockets with the alert on whose balance is under their threshold right now. */
    val lowPockets: List<LowPocket> = emptyList(),
)

data class NotifConfig(val enabled: Set<NotifKind>, val reminderHour: Int?, val mask: Boolean)

/** What was already sent: stored keys, how many were posted today, and the pockets already told about being low. */
data class NotifState(val sent: Set<String> = emptySet(), val postedToday: Int = 0, val lowNotified: Set<Long> = emptySet())

/**
 * One notification to post. [key] is what is stored once it is posted; [arg] is what the tap needs (the day for the
 * evening and streak ones, the pocket for a low balance); [alt] picks the variant without the amount or second sentence.
 */
data class Notice(val kind: NotifKind, val key: String, val args: List<String>, val arg: Long? = null, val alt: Boolean = false)

/**
 * The rules. Windows: the evening reminder in its chosen hour and the next one, the morning one 08-10, the streak one at 23,
 * everything else 09-21. Priority = [NotifKind] order. While the evening reminder is still to come today (on, ticket open,
 * its hour not reached) the others may take at most one of the two slots, so a morning bill never starves it.
 */
object NotificationPlanner {
    private val DAYTIME = 9..21
    private val MORNING_WINDOW = 8..10

    fun plan(now: LocalDateTime, facts: NotifFacts, cfg: NotifConfig, state: NotifState): List<Notice> {
        val today = now.toLocalDate().toEpochDay()
        val h = now.hour
        fun on(k: NotifKind) = k in cfg.enabled
        fun money(p: Long) = if (cfg.mask) NOTIF_DOTS else formatPounds(p)
        fun daily(key: String) = "$key@$today"
        val rh = cfg.reminderHour
        val out = mutableListOf<Notice>()

        if (on(NotifKind.EVENING) && rh != null && h in rh..(rh + 1) && facts.ticketOpen) {
            out += if (facts.ticketLeft > 0) {
                Notice(NotifKind.EVENING, daily("evening"), listOf(money(facts.ticketLeft)), today)
            } else {
                Notice(NotifKind.EVENING, daily("evening"), emptyList(), today, alt = true)
            }
        }
        if (h in DAYTIME) {
            if (on(NotifKind.BILL)) {
                facts.billsTomorrow.forEach { out += Notice(NotifKind.BILL, daily("bill:${it.id}"), listOf(it.name, money(it.amount))) }
            }
            if (on(NotifKind.GAMEYA_DUE)) {
                facts.gameyaDueTomorrow.forEach { out += Notice(NotifKind.GAMEYA_DUE, daily("gdue:${it.id}"), listOf(money(it.amount))) }
            }
            if (on(NotifKind.TRIAL_SUB)) {
                facts.subTrials.forEach { out += Notice(NotifKind.TRIAL_SUB, "sub:${it.id}", listOf(it.name)) }
            }
            if (on(NotifKind.TRIAL_PLUS) && facts.plusTrialEndsTomorrow) out += Notice(NotifKind.TRIAL_PLUS, "plus", emptyList())
            if (on(NotifKind.LOW_BALANCE)) {
                facts.lowPockets.filter { it.id !in state.lowNotified }.forEach {
                    out += Notice(NotifKind.LOW_BALANCE, "low:${it.id}", listOf(it.name, money(it.balance)), it.id)
                }
            }
        }
        // An evening reminder at 22 or later is the same nudge as the streak line an hour on: only one of them.
        val streakClear = !(on(NotifKind.EVENING) && rh != null && rh >= 22)
        if (on(NotifKind.STREAK) && h == 23 && facts.ticketOpen && facts.streak >= 2 && streakClear) {
            out += Notice(NotifKind.STREAK, daily("streak"), listOf(facts.streak.toString()), today)
        }
        if (h in DAYTIME) {
            if (on(NotifKind.GAMEYA_TURN)) {
                facts.gameyaTurns.forEach {
                    out += Notice(NotifKind.GAMEYA_TURN, "turn:${it.gameyaId}:${it.yearMonth}", listOf(money(it.payout)))
                }
            }
            if (on(NotifKind.OCCASION)) {
                facts.occasions.forEach {
                    out += if (it.gaveMe != null) {
                        Notice(NotifKind.OCCASION, "occ:${it.id}", listOf(it.name, money(it.gaveMe)))
                    } else {
                        Notice(NotifKind.OCCASION, "occ:${it.id}", listOf(it.name), alt = true)
                    }
                }
            }
            if (on(NotifKind.WISH)) facts.wishesDue.forEach { out += Notice(NotifKind.WISH, "wish:${it.id}", listOf(it.name)) }
        }
        if (on(NotifKind.MORNING) && h in MORNING_WINDOW && facts.ticketAmount != null) {
            out += Notice(NotifKind.MORNING, daily("morning"), listOf(money(facts.ticketAmount)))
        }

        val fresh = out.filter { it.key !in state.sent }.sortedBy { it.kind.ordinal }
        val room = (NOTIF_DAILY_CAP - state.postedToday).coerceAtLeast(0)
        val eveningStillToCome = on(NotifKind.EVENING) && rh != null && h < rh && facts.ticketOpen && daily("evening") !in state.sent
        val others = if (eveningStillToCome) (room - 1).coerceAtLeast(0) else room
        val evening = fresh.filter { it.kind == NotifKind.EVENING }.take(room)
        return evening + fresh.filter { it.kind != NotifKind.EVENING }.take(minOf(room - evening.size, others))
    }

    /** The pockets still told about: those that were and still are under the line, plus the ones just posted. */
    fun lowAfter(state: NotifState, facts: NotifFacts, posted: List<Notice>): Set<Long> =
        state.lowNotified.intersect(facts.lowPockets.map { it.id }.toSet()) +
            posted.filter { it.kind == NotifKind.LOW_BALANCE }.mapNotNull { it.arg }

    /** Drops stored per-day keys older than yesterday; once-only keys stay. */
    fun prune(sent: Set<String>, today: Long): Set<String> =
        sent.filterTo(mutableSetOf()) { k -> k.substringAfterLast('@', "").toLongOrNull()?.let { it >= today - 1 } ?: true }
}
