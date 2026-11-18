package com.masroufi.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

val LEVEL_NAMES = listOf("مبتدئ", "ماشي", "منظّم", "مدبّر", "مقتصد", "أسطى", "معلّم", "حوت")

private val LEVEL_THRESHOLDS = listOf(0, 7, 20, 40, 60, 100, 180, 365)

enum class StampKind { FIRST_TICKET, FULL_WEEK, HAT_TRICK, TIGHT_FIST, FIRST_GRAM, SETTLED }

fun weekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

/** Counts back from today (if hand-closed) or yesterday; an open today never breaks it. */
fun streak(days: List<DayResult>, today: LocalDate, plusSafety: Boolean = false): Int =
    streakResult(days, today, plusSafety).days

/** [bridged] = the missed days the safety ticket let pass; they are not streak days. */
data class StreakResult(val days: Int, val bridged: List<LocalDate>)

/**
 * With [plusSafety] (the safety ticket, Plus) one missed day (not closed by hand) per Saturday-Friday week does not
 * break the streak and is not counted; a second miss in the same week breaks it.
 */
fun streakResult(days: List<DayResult>, today: LocalDate, plusSafety: Boolean): StreakResult {
    val byDate = days.associateBy { it.date } // duplicates: last wins
    val first = byDate.keys.minOrNull() ?: return StreakResult(0, emptyList())
    var d = if (byDate[today]?.status == TicketStatus.CLOSED_BY_HAND) today else today.minusDays(1)
    var n = 0
    val bridges = mutableListOf<Pair<LocalDate, Int>>() // the day and the streak length when it was passed
    val missedWeeks = mutableSetOf<LocalDate>()
    while (!d.isBefore(first)) {
        if (byDate[d]?.status == TicketStatus.CLOSED_BY_HAND) {
            n++
        } else if (plusSafety && missedWeeks.add(weekStart(d))) {
            bridges += d to n
        } else {
            break
        }
        d = d.minusDays(1)
    }
    // A bridge with no streak day older than it protects nothing (it is not part of the streak).
    return StreakResult(n, bridges.filter { it.second < n }.map { it.first })
}

/** Streak days the safety ticket kept: the streak with it minus the streak without. */
fun protectedDays(days: List<DayResult>, today: LocalDate): Int =
    streak(days, today, plusSafety = true) - streak(days, today)

fun level(handClosed: Int): Int {
    require(handClosed >= 0) { "handClosed must be >= 0" }
    return LEVEL_THRESHOLDS.indexOfLast { handClosed >= it } + 1
}

fun nextLevelAt(handClosed: Int): Int? {
    require(handClosed >= 0) { "handClosed must be >= 0" }
    return LEVEL_THRESHOLDS.firstOrNull { it > handClosed }
}

fun earnedStamps(days: List<DayResult>): Set<StampKind> {
    val hand = days.filter { it.status == TicketStatus.CLOSED_BY_HAND }.associateBy { it.date }
    val out = mutableSetOf<StampKind>()
    if (hand.isNotEmpty()) out += StampKind.FIRST_TICKET
    if (hand.keys.any { d -> weekStart(d).let { s -> (0L..6L).all { s.plusDays(it) in hand } } }) {
        out += StampKind.FULL_WEEK
    }
    val under = hand.filterValues { it.spent <= it.ticket }.keys
    if (under.any { d -> d.plusDays(1) in under && d.plusDays(2) in under }) out += StampKind.HAT_TRICK
    return out
}

/** The longest run of consecutive days closed by hand. */
fun longestStreak(days: List<DayResult>): Int {
    val hand = days.filter { it.status == TicketStatus.CLOSED_BY_HAND }.map { it.date }.toSortedSet()
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in hand) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    return best
}

/** Days closed by hand on or under the ticket. */
fun daysUnder(days: List<DayResult>): Int = days.count { it.status == TicketStatus.CLOSED_BY_HAND && it.spent <= it.ticket }

/** How many times a stamp was earned: full weeks, hat-tricks (every full run of three under days), else 1. */
fun stampCount(kind: StampKind, days: List<DayResult>): Int {
    val hand = days.filter { it.status == TicketStatus.CLOSED_BY_HAND }.associateBy { it.date }
    return when (kind) {
        StampKind.FULL_WEEK ->
            hand.keys.map(::weekStart).distinct().count { s -> (0L..6L).all { s.plusDays(it) in hand } }.coerceAtLeast(1)
        StampKind.HAT_TRICK -> {
            val under = hand.filterValues { it.spent <= it.ticket }.keys.toSortedSet()
            var total = 0
            var run = 0
            var prev: LocalDate? = null
            for (d in under) {
                run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
                if (run % 3 == 0) total++
                prev = d
            }
            total.coerceAtLeast(1)
        }
        else -> 1
    }
}

/** 0..1 through the current level toward the next; 1 at the top level. */
fun levelProgress(handClosed: Int): Float {
    val next = nextLevelAt(handClosed) ?: return 1f
    val start = LEVEL_THRESHOLDS[level(handClosed) - 1]
    return (handClosed - start).toFloat() / (next - start)
}

/** What a locked card in the album says it needs: the next ordinary card, the first full week (card 45), or any other rare one. */
enum class LockHint { TOMORROW, FULL_WEEK, RARE }

/** Up to one placeholder per hint that still applies, in the mock's order. */
fun lockHints(collected: Set<Int>): List<LockHint> = buildList {
    if (ProverbCatalog.all.any { it.number !in collected && !it.rare }) add(LockHint.TOMORROW)
    if (45 !in collected) add(LockHint.FULL_WEEK)
    if (ProverbCatalog.rare.any { it != 45 && it !in collected }) add(LockHint.RARE)
}
