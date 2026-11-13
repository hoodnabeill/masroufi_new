package com.masroufi.app.domain

import com.masroufi.app.core.PIASTRES

fun roundUp(amount: Long, stepPounds: Int): Long {
    require(stepPounds >= 0) { "stepPounds must be >= 0" }
    if (stepPounds == 0 || amount <= 0) return 0L
    val step = stepPounds * PIASTRES
    val rem = Math.floorMod(amount, step)
    return if (rem == 0L) 0L else step - rem
}
