package com.masroufi.app.domain

import com.masroufi.app.core.ceilDiv
import com.masroufi.app.core.ceilToPound
import com.masroufi.app.core.floorToPound

enum class EntryKind { SPENT, RECEIVED, MOVED }

sealed interface TicketCalc {
    data class Printed(val amount: Long, val base: Long, val carriedIn: Long) : TicketCalc
    data class EndOfMonth(val pool: Long, val daysLeft: Int) : TicketCalc
}

/**
 * `reserved` excludes `carriedIn`. The carry (capped at Spendable - Reserved) leaves the pool before dividing and is
 * added back, so a carried-in leftover still prints when the divided base floors to zero.
 */
fun computeTicket(spendable: Long, reserved: Long, carriedIn: Long, daysLeft: Int): TicketCalc {
    require(daysLeft >= 1) { "daysLeft must be >= 1" }
    require(carriedIn >= 0) { "carriedIn must be >= 0" }
    val sr = spendable - reserved
    val endOfMonth = TicketCalc.EndOfMonth(sr, daysLeft)
    if (sr <= 0) return endOfMonth
    val carry = minOf(carriedIn, sr)
    val pool = sr - carry
    val base = if (pool > 0) Math.floorDiv(pool, daysLeft.toLong()).floorToPound() else 0L
    if (base + carry == 0L) return endOfMonth // never print a zero ticket
    return TicketCalc.Printed(amount = base + carry, base = base, carriedIn = carry)
}

data class EntryForDay(
    val kind: EntryKind,
    val amount: Long,
    val roundUp: Long,
    val countsInTicket: Boolean,
    val recurringId: Long?,
)

/** Punches the ticket: spent, from a counting pocket, not a fixed bill. */
val EntryForDay.punches: Boolean get() = kind == EntryKind.SPENT && countsInTicket && recurringId == null

fun spentToday(entries: List<EntryForDay>): Long = entries.filter { it.punches }.sumOf { it.amount + it.roundUp }

fun overspendPerDay(overspend: Long, daysLeft: Int): Long {
    require(daysLeft >= 1) { "daysLeft must be >= 1" }
    return if (overspend > 0) overspend.ceilDiv(daysLeft.toLong()).ceilToPound() else 0L
}
