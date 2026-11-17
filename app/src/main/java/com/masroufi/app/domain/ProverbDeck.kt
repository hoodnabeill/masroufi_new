package com.masroufi.app.domain

import kotlin.random.Random

/** The shuffle order depends only on `seed` and `size`; `exclude` (the rare cards) and `collected` are skipped. */
fun nextProverb(collected: Set<Int>, seed: Long, size: Int, exclude: Set<Int> = emptySet()): Int? {
    if (size <= 0) return null
    return (1..size).shuffled(Random(seed)).firstOrNull { it !in collected && it !in exclude }
}

val PROVERB_SET_SIZE: Int get() = ProverbCatalog.all.size
