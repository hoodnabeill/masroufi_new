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
