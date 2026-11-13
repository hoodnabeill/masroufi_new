package com.masroufi.app.domain

import kotlin.random.Random

fun nextProverb(collected: Set<Int>, seed: Long, size: Int): Int? {
    if (size <= 0) return null
    return (1..size).shuffled(Random(seed)).firstOrNull { it !in collected }
}
