package com.masroufi.app.entitlements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface Entitlements {
    val isPlus: StateFlow<Boolean>
}

/**
 * Debug builds: the gallery toggle flips Plus. Built with [plus] (the real sources) the toggle is an override on top of
 * the subscription and trial; with none (tests) it is the whole state.
 */
class DebugEntitlements(initial: Boolean = false, private val plus: PlusRepository? = null) : Entitlements {
    private val state = MutableStateFlow(initial)
    override val isPlus: StateFlow<Boolean> = plus?.isPlus ?: state.asStateFlow()

    init { plus?.setDebugOverride(initial) }

    fun setDebugOverride(on: Boolean) {
        if (plus != null) plus.setDebugOverride(on) else state.value = on
    }
}

/** Release: Plus is a subscription or the trial, nothing else. */
class PlusEntitlements(plus: PlusRepository) : Entitlements {
    override val isPlus: StateFlow<Boolean> = plus.isPlus
}
