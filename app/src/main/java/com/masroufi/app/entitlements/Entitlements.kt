package com.masroufi.app.entitlements

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class PremiumFeature { ADVANCED_ANALYTICS, AUTO_SPLIT, WEBDAV_SYNC, RECURRING_AUTOMATION, GAMEYA_PLUS, CUSTOMIZATION }

enum class EntitlementSource {
    /** No billing integration exists yet; every feature is free during early access. */
    EARLY_ACCESS,
    SUBSCRIPTION,
}

data class EntitlementState(val source: EntitlementSource, val unlocked: Set<PremiumFeature>)

/**
 * The single place feature access is decided. UI asks [isUnlocked] instead of checking subscription state,
 * so a future billing implementation only replaces this class.
 */
@Singleton
class Entitlements @Inject constructor() {
    private val _state = MutableStateFlow(EntitlementState(EntitlementSource.EARLY_ACCESS, PremiumFeature.entries.toSet()))
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    fun isUnlocked(feature: PremiumFeature): Flow<Boolean> = state.map { feature in it.unlocked }
}
