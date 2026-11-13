package com.masroufi.app.core

const val PIASTRES = 100L

/** Largest multiple of 100 piastres <= this (toward negative infinity). */
fun Long.floorToPound(): Long = Math.floorDiv(this, PIASTRES) * PIASTRES

/** Smallest multiple of 100 piastres >= this. */
fun Long.ceilToPound(): Long = -Math.floorDiv(-this, PIASTRES) * PIASTRES

/** Ceiling division by a positive divisor. */
fun Long.ceilDiv(d: Long): Long {
    require(d > 0) { "divisor must be positive" }
    return -Math.floorDiv(-this, d)
}
