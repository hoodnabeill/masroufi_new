package com.masroufi.app.domain

import java.time.LocalDate

/** Wording patterns of Match.dc.html, one per kind. */
enum class EventKind { GOAL, EXACT, BEST, LAST_MINUTE, AGAINST, OPEN }

/** [amount]: leftover (goal, best, last minute), overspend (against), or what is left of today's ticket (open). [fresh]: scored since the last visit. */
data class MatchEvent(val date: LocalDate, val kind: EventKind, val amount: Long, val fresh: Boolean)

/** A leftover up to 10 pounds is "in the last minute" (builder's threshold; the mock shows 5 as the small one and 18 as a plain goal). */
const val SMALL_LEFTOVER = 1_000L

/** The score the match screen last showed: the week (its Saturday, epoch day) and the goals counted then. */
data class MatchSeen(val weekStart: Long, val goalsFor: Int, val goalsAgainst: Int)

/** Goals that appeared since the last visit. [from] is the "for" score to start the flip from; [dates] are the days whose event rows slide in. */
data class FreshGoals(val from: Int, val forGoals: Int, val againstGoals: Int, val dates: Set<LocalDate>)

/** No visit yet, or a visit in an earlier week, counts from 0:0. The fresh days are the latest closed days of the week. */
fun freshGoals(seen: MatchSeen?, view: MatchView): FreshGoals {
    val same = seen != null && seen.weekStart == weekStart(view.today).toEpochDay()
    val oldFor = if (same) seen!!.goalsFor else 0
    val oldAgainst = if (same) seen!!.goalsAgainst else 0
    val newFor = (view.goalsFor - oldFor).coerceAtLeast(0)
    val newAgainst = (view.goalsAgainst - oldAgainst).coerceAtLeast(0)
    val dates = view.days.filter { it.goal != GoalKind.NONE }.takeLast(newFor + newAgainst).map { it.date }.toSet()
    return FreshGoals(from = minOf(oldFor, view.goalsFor), forGoals = newFor, againstGoals = newAgainst, dates = dates)
}

/**
 * This week's event rows, Saturday first: a closed day, and today while it is open. "Best day" needs two goals with a
 * leftover (a lone goal is not a best of anything); a leftover of exactly 0 is "on the limit".
 */
fun matchEvents(view: MatchView, fresh: Set<LocalDate>): List<MatchEvent> {
    val bestCounts = view.days.count { it.goal == GoalKind.FOR && it.amount > 0 } >= 2
    return view.days.mapNotNull { d ->
        val kind = when (d.goal) {
            GoalKind.AGAINST -> EventKind.AGAINST
            GoalKind.FOR -> when {
                d.amount == 0L -> EventKind.EXACT
                bestCounts && d.date == view.bestDay -> EventKind.BEST
                d.amount <= SMALL_LEFTOVER -> EventKind.LAST_MINUTE
                else -> EventKind.GOAL
            }
            GoalKind.NONE -> if (d.date == view.today && view.todayLeft != null) EventKind.OPEN else null
        }
        kind?.let {
            val amount = if (it == EventKind.OPEN) maxOf(0L, view.todayLeft ?: 0L) else d.amount
            MatchEvent(d.date, it, amount, d.date in fresh)
        }
    }
}
