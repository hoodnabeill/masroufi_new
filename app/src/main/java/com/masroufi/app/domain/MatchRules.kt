package com.masroufi.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

enum class GoalKind { FOR, AGAINST, NONE }

/** [amount]: the leftover (FOR) or the overspend (AGAINST), piastres; 0 for NONE. */
data class MatchDay(val date: LocalDate, val goal: GoalKind, val amount: Long)

enum class WeekResult { WIN, DRAW, LOSS }

/** [result] is null for the current week and for the very first week (nothing to play against). */
data class LeagueRow(
    val start: LocalDate,
    val number: Int,
    val result: WeekResult?,
    val goalsFor: Int,
    val days: List<GoalKind>,
    val saved: Long,
    val current: Boolean,
)

/** [ONE_AWAY]: two goals in a row up to yesterday and today still open (the nudge). */
enum class HatTrick { NONE, ONE_AWAY, DONE }

data class MatchView(
    val today: LocalDate,
    val weekNumber: Int,
    val daysLeft: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val lastWeekGoalsFor: Int,
    val days: List<MatchDay>,
    /** The day with the biggest leftover this week; null when no goal-for day left anything. */
    val bestDay: LocalDate?,
    /** Today's ticket left so far while today is open; null otherwise. */
    val todayLeft: Long?,
    val hatTrick: HatTrick,
    /** Newest first; at most [FREE_LEAGUE_WEEKS] without Plus. */
    val league: List<LeagueRow>,
    val lockedWeeks: Int,
    val wonOf: Int,
    val ofLast: Int,
    val longestWinRun: Int,
    val firstWeek: Boolean,
)

const val FREE_LEAGUE_WEEKS = 4
private const val LAST_WEEKS = 8

/** A closed day (by hand or auto, as Home's stubs) under or on the ticket is a goal for, over it a goal against. */
fun matchDay(date: LocalDate, row: DayResult?): MatchDay = when {
    row == null || row.status == TicketStatus.OPEN -> MatchDay(date, GoalKind.NONE, 0)
    row.spent > row.ticket -> MatchDay(date, GoalKind.AGAINST, row.spent - row.ticket)
    else -> MatchDay(date, GoalKind.FOR, leftover(row.ticket, row.spent))
}

/** Week numbers follow ISO weeks of the Monday inside the Saturday-Friday week. */
fun weekNumber(start: LocalDate): Int = start.plusDays(2).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

private fun weekDays(start: LocalDate, byDate: Map<LocalDate, DayResult>) =
    (0L..6L).map { start.plusDays(it).let { d -> matchDay(d, byDate[d]) } }

private fun List<MatchDay>.saved() =
    maxOf(0L, filter { it.goal == GoalKind.FOR }.sumOf { it.amount } - filter { it.goal == GoalKind.AGAINST }.sumOf { it.amount })

fun buildMatch(rows: List<DayResult>, today: LocalDate, isPlus: Boolean): MatchView {
    val byDate = rows.associateBy { it.date }
    val thisStart = weekStart(today)
    val firstStart = rows.minOfOrNull { weekStart(it.date) }?.coerceAtMost(thisStart) ?: thisStart
    val starts = generateSequence(firstStart) { it.plusDays(7) }.takeWhile { !it.isAfter(thisStart) }.toList()
    val weeks = starts.map { weekDays(it, byDate) }
    val goals = weeks.map { w -> w.count { it.goal == GoalKind.FOR } }

    val all = starts.indices.map { i ->
        val current = starts[i] == thisStart
        LeagueRow(
            start = starts[i], number = weekNumber(starts[i]),
            result = if (current || i == 0) null else compareValues(goals[i], goals[i - 1]).let {
                when { it > 0 -> WeekResult.WIN; it < 0 -> WeekResult.LOSS; else -> WeekResult.DRAW }
            },
            goalsFor = goals[i], days = weeks[i].map { it.goal }, saved = weeks[i].saved(), current = current,
        )
    }.asReversed()

    val played = all.filter { it.result != null }.asReversed() // oldest first
    val lastPlayed = played.takeLast(LAST_WEEKS)
    var run = 0
    var longest = 0
    for (r in played) { run = if (r.result == WeekResult.WIN) run + 1 else 0; longest = maxOf(longest, run) }

    val days = weeks.last()
    val todayRow = byDate[today]
    val shown = if (isPlus) all else all.take(FREE_LEAGUE_WEEKS)
    val closedFor = { d: LocalDate -> matchDay(d, byDate[d]).goal == GoalKind.FOR }
    val todayOpen = todayRow != null && todayRow.status == TicketStatus.OPEN
    // Consecutive goals-for ending today (if scored) or yesterday.
    val end = if (matchDay(today, todayRow).goal != GoalKind.NONE) today else today.minusDays(1)
    var n = 0
    while (closedFor(end.minusDays(n.toLong()))) n++
    val hat = when {
        n >= 3 -> HatTrick.DONE
        n == 2 && end != today && (todayOpen || todayRow == null) -> HatTrick.ONE_AWAY
        else -> HatTrick.NONE
    }

    return MatchView(
        today = today, weekNumber = weekNumber(thisStart),
        daysLeft = (ChronoUnit.DAYS.between(today, thisStart.plusDays(6)) + 1).toInt(),
        goalsFor = goals.last(), goalsAgainst = days.count { it.goal == GoalKind.AGAINST },
        lastWeekGoalsFor = goals.getOrElse(goals.size - 2) { 0 },
        days = days,
        bestDay = days.filter { it.goal == GoalKind.FOR && it.amount > 0 }.maxByOrNull { it.amount }?.date,
        todayLeft = if (todayOpen) todayRow!!.ticket - todayRow.spent else null,
        hatTrick = hat, league = shown, lockedWeeks = all.size - shown.size,
        wonOf = lastPlayed.count { it.result == WeekResult.WIN }, ofLast = lastPlayed.size, longestWinRun = longest,
        firstWeek = starts.size == 1,
    )
}
