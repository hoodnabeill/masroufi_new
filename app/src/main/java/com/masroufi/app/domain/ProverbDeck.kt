package com.masroufi.app.domain

import kotlin.random.Random

fun nextProverb(collected: Set<Int>, seed: Long, size: Int): Int? {
    if (size <= 0) return null
    return (1..size).shuffled(Random(seed)).firstOrNull { it !in collected }
}

// shortcut: 50 drafted cards (14-PROVERBS.md); read the size from the proverb catalog when Phase 3 adds the texts.
const val PROVERB_SET_SIZE = 50
