package com.masroufi.app.domain

/** The person a shared subscription's debts go to: "{name} · {N−1} صحاب" (decision 4). */
fun sharedGroupName(subscription: String, others: Int): String = "$subscription · $others صحاب"

/** [total] split into [count] shares that add up to it exactly: the extra piastres go one each to the first shares. */
fun splitEqually(total: Long, count: Int): List<Long> {
    require(total >= 0 && count >= 1) { "total >= 0 and count >= 1" }
    val base = total / count
    val extra = (total % count).toInt()
    return List(count) { if (it < extra) base + 1 else base }
}

/**
 * Quick split: [total] over [participants] (the user included). Amounts in [overrides] are fixed, the rest is split
 * equally over the others, so the shares always add up to [total]. Null when they cannot (a fixed share too big, or
 * every share fixed and not adding up).
 */
fun computeShares(total: Long, participants: List<Long>, overrides: Map<Long, Long>): Map<Long, Long>? {
    if (participants.isEmpty() || total <= 0) return null
    val free = participants.filter { it !in overrides }
    val rest = total - participants.filter { it in overrides }.sumOf { overrides.getValue(it) }
    if (rest < 0 || (free.isEmpty() && rest != 0L)) return null
    val parts = if (free.isEmpty()) emptyList() else splitEqually(rest, free.size)
    return participants.associateWith { overrides[it] ?: parts[free.indexOf(it)] }
}
