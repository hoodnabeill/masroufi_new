package com.masroufi.app.entitlements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface Entitlements {
    val isPlus: StateFlow<Boolean>
}

/** Debug builds only: the gallery toggle flips Plus. */
class DebugEntitlements(initial: Boolean = false) : Entitlements {
    private val state = MutableStateFlow(initial)
    override val isPlus: StateFlow<Boolean> = state.asStateFlow()
    fun setDebugOverride(on: Boolean) { state.value = on }
}

// shortcut: release is always free tier; real purchase state arrives with billing in Phase 10.
class ReleaseEntitlements : Entitlements {
    override val isPlus: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
}
