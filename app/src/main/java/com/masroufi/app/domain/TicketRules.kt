package com.masroufi.app.domain

import com.masroufi.app.core.ceilDiv
import com.masroufi.app.core.ceilToPound
import com.masroufi.app.core.floorToPound

enum class EntryKind { SPENT, RECEIVED, MOVED }

sealed interface TicketCalc {
    data class Printed(val amount: Long, val base: Long, val carriedIn: Long) : TicketCalc
    data class EndOfMonth(val pool: Long, val daysLeft: Int) : TicketCalc
}

/** `reserved` excludes `carriedIn`; the carry leaves the pool before dividing, then is added back. */
fun computeTicket(spendable: Long, reserved: Long, carriedIn: Long, daysLeft: Int): TicketCalc {
    require(daysLeft >= 1) { "daysLeft must be >= 1" }
    require(carriedIn >= 0) { "carriedIn must be >= 0" }
    val pool = spendable - reserved - carriedIn
    val endOfMonth = TicketCalc.EndOfMonth(spendable - reserved, daysLeft)
    if (pool <= 0) return endOfMonth
    val base = Math.floorDiv(pool, daysLeft.toLong()).floorToPound()
    if (base == 0L) return endOfMonth // never print a zero ticket
    return TicketCalc.Printed(amount = base + carriedIn, base = base, carriedIn = carriedIn)
}

data class EntryForDay(
    val kind: EntryKind,
    val amount: Long,
    val roundUp: Long,
    val countsInTicket: Boolean,
    val recurringId: Long?,
)

fun spentToday(entries: List<EntryForDay>): Long =
    entries.filter { it.kind == EntryKind.SPENT && it.countsInTicket && it.recurringId == null }
        .sumOf { it.amount + it.roundUp }

fun overspendPerDay(overspend: Long, daysLeft: Int): Long {
    require(daysLeft >= 1) { "daysLeft must be >= 1" }
    return if (overspend > 0) overspend.ceilDiv(daysLeft.toLong()).ceilToPound() else 0L
}
