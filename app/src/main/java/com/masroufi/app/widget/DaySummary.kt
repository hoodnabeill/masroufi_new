package com.masroufi.app.widget

import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.ui.components.HIDDEN_AMOUNT
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * The stored day summary (decision 8): the only thing the widgets read. It is rewritten whenever the day changes
 * (print, punch, edit, delete, close) and when the lock or hide-amounts setting changes; nothing here is calculated by a widget.
 * All amounts are piastres.
 */
data class DaySummary(
    /** Epoch day the ticket belongs to. */
    val day: Long,
    /** False while no ticket is printed for [day] (before onboarding ends or after a wipe). */
    val printed: Boolean,
    val open: Boolean,
    val amount: Long,
    /** Ticket minus spent; negative when the day went over. */
    val left: Long,
    val punches: Int,
    val streak: Int,
    /** This week's score against last week's (goals for). */
    val matchYou: Int,
    val matchLast: Int,
    /** One goal more today makes a hat-trick. */
    val hatTrickNear: Boolean,
    /** Up to [MAX_CHIPS] repeat chips, the same ones Home shows. */
    val chips: List<RepeatChip>,
    val lockOn: Boolean,
    val hideAmounts: Boolean,
)

const val MAX_CHIPS = 3

/** The small ticket shows this many holes; filled ones are the punches so far. */
const val HOLE_SLOTS = 6

fun DaySummary.toJson(): String = JSONObject()
    .put("day", day).put("printed", printed).put("open", open).put("amount", amount).put("left", left)
    .put("punches", punches).put("streak", streak).put("you", matchYou).put("last", matchLast).put("hat", hatTrickNear)
    .put("lock", lockOn).put("hide", hideAmounts)
    .put(
        "chips",
        JSONArray(chips.map { JSONObject().put("c", it.categoryId).put("n", it.categoryName).put("a", it.amount).put("p", it.pocketId) }),
    )
    .toString()

/** Null for text that is not a summary (nothing stored yet, or damaged). */
fun daySummaryFromJson(text: String?): DaySummary? = try {
    text?.let {
        val o = JSONObject(it)
        val chips = o.getJSONArray("chips")
        DaySummary(
            day = o.getLong("day"), printed = o.getBoolean("printed"), open = o.getBoolean("open"), amount = o.getLong("amount"),
            left = o.getLong("left"), punches = o.getInt("punches"), streak = o.getInt("streak"), matchYou = o.getInt("you"),
            matchLast = o.getInt("last"), hatTrickNear = o.getBoolean("hat"),
            chips = (0 until chips.length()).map { i ->
                chips.getJSONObject(i).let { RepeatChip(it.getLong("c"), it.getString("n"), it.getLong("a"), it.getLong("p")) }
            },
            lockOn = o.getBoolean("lock"), hideAmounts = o.getBoolean("hide"),
        )
    }
} catch (e: org.json.JSONException) {
    null
}

enum class TicketMode { EMPTY, OPEN, CLOSED }

/** What the ticket widgets draw. [number] and [of] are already dots while amounts are hidden. */
data class TicketView(
    val mode: TicketMode,
    val number: String,
    val of: String,
    val holes: Int,
    val filled: Int,
    val over: Boolean,
)

data class ChipView(val chip: RepeatChip, val amount: String)

data class WideView(val ticket: TicketView, val chips: List<ChipView>, val locked: Boolean)

data class MatchWidgetView(val you: String, val last: String, val hatNear: Boolean)

private const val NO_NUMBER = "-"

/** Amounts are dots with the app lock on or hide amounts on. */
private fun DaySummary?.hidden() = this != null && (lockOn || hideAmounts)

private fun money(s: DaySummary?, piastres: Long) = if (s.hidden()) HIDDEN_AMOUNT else formatPounds(piastres)

/** A summary of another day (the midnight print has not run yet) counts as no ticket. */
private fun DaySummary?.freshFor(today: LocalDate) = this != null && printed && day == today.toEpochDay()

fun ticketView(s: DaySummary?, today: LocalDate): TicketView {
    if (s == null || !s.freshFor(today)) return TicketView(TicketMode.EMPTY, NO_NUMBER, NO_NUMBER, 0, 0, false)
    return TicketView(
        mode = if (s.open) TicketMode.OPEN else TicketMode.CLOSED,
        number = money(s, s.left.coerceAtLeast(0)),
        of = money(s, s.amount),
        holes = s.punches,
        filled = s.punches.coerceAtMost(HOLE_SLOTS),
        over = s.left < 0,
    )
}

/** The repeat buttons only while today's ticket is open and the lock is off (amounts and actions stay behind the lock). */
fun wideView(s: DaySummary?, today: LocalDate): WideView {
    val ticket = ticketView(s, today)
    val chips = if (s != null && ticket.mode == TicketMode.OPEN && !s.lockOn) s.chips.take(MAX_CHIPS).map { ChipView(it, money(s, it.amount)) } else emptyList()
    return WideView(ticket, chips, locked = s?.lockOn == true)
}

/** Scores are counts, not money: they show even with the lock on. */
fun matchView(s: DaySummary?): MatchWidgetView =
    MatchWidgetView(you = (s?.matchYou ?: 0).toString(), last = (s?.matchLast ?: 0).toString(), hatNear = s?.hatTrickNear == true)
