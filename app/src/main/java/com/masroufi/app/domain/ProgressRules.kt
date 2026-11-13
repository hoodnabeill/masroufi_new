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
fun streak(days: List<DayResult>, today: LocalDate): Int {
    val byDate = days.associateBy { it.date } // duplicates: last wins
    var d = if (byDate[today]?.status == TicketStatus.CLOSED_BY_HAND) today else today.minusDays(1)
    var n = 0
    while (byDate[d]?.status == TicketStatus.CLOSED_BY_HAND) {
        n++
        d = d.minusDays(1)
    }
    return n
}

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
