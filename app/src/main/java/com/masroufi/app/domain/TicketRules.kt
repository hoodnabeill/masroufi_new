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

/**
 * What tomorrow's ticket is built on: tomorrow's own pay cycle (on the day before payday tomorrow starts a new one), so
 * [daysLeft] counts from tomorrow and [unpaidBills] are those still unpaid and due before that cycle's next payday.
 */
data class TomorrowBasis(val daysLeft: Int, val unpaidBills: Long) {
    /** Tomorrow's ticket amount (0 when it would be an end-of-month row), with [carry] of today's leftover added. */
    fun preview(spendable: Long, jar: Long, emergency: Long, carry: Long): Long =
        when (val c = computeTicket(spendable, unpaidBills + jar.coerceAtLeast(0) + emergency.coerceAtLeast(0), carry, daysLeft)) {
            is TicketCalc.Printed -> c.amount
            is TicketCalc.EndOfMonth -> 0L
        }
}

data class EntryForDay(
    val kind: EntryKind,
    val amount: Long,
    val roundUp: Long,
    val fee: Long,
    val countsInTicket: Boolean,
    val recurringId: Long?,
)

/**
 * Punches the ticket (one hole): from a counting pocket, either spent and not a fixed bill, or a move that paid a fee
 * (owner decision: the fee punches; the moved amount is a planned move, not spending).
 */
val EntryForDay.punches: Boolean get() = countsInTicket && when (kind) {
    EntryKind.SPENT -> recurringId == null
    EntryKind.MOVED -> fee > 0
    EntryKind.RECEIVED -> false
}

/** What a punch takes from the ticket: a spend's amount + round-up, a move's fee. */
val EntryForDay.punched: Long get() = when (kind) {
    EntryKind.SPENT -> amount + roundUp
    EntryKind.MOVED -> fee
    EntryKind.RECEIVED -> 0
}

fun spentToday(entries: List<EntryForDay>): Long = entries.filter { it.punches }.sumOf { it.punched }

/**
 * What stays with the user at payday if they spend exactly the base ticket every remaining day (today's carry too).
 * Jar and emergency money stay theirs; only unpaid bills leave. `morningSpendable` = current Spendable + today's
 * punched spent. May be negative (the card hides it then).
 */
fun projectedAtPayday(morningSpendable: Long, unpaidBillsBeforePayday: Long, base: Long, carry: Long, daysLeft: Int): Long =
    morningSpendable - unpaidBillsBeforePayday - carry - base * daysLeft

fun overspendPerDay(overspend: Long, daysLeft: Int): Long {
    require(daysLeft >= 1) { "daysLeft must be >= 1" }
    return if (overspend > 0) overspend.ceilDiv(daysLeft.toLong()).ceilToPound() else 0L
}
