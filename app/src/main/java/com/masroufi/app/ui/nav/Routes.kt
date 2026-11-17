package com.masroufi.app.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object Onboarding
@Serializable data object Home
@Serializable data object RollTab
@Serializable data object PlanTab
@Serializable data object PeopleTab
@Serializable data class CloseDay(val date: Long)
@Serializable data class ProverbReward(val date: Long)
@Serializable data object Gallery
@Serializable data object Pockets
@Serializable data class PocketDetail(val id: Long)
@Serializable data object NewPocket
@Serializable data class EnvelopeDetail(val id: Long)
@Serializable data object NewEnvelope
@Serializable data class EditEnvelope(val id: Long)
@Serializable data object NewGoal
@Serializable data object FixedCosts
/** [id] 0 = a new fixed cost. */
@Serializable data class FixedCostEdit(val id: Long)
@Serializable data object Subscriptions
/** [id] 0 = a new subscription; [name] pre-fills it (a one-tap chip). */
@Serializable data class SubscriptionEdit(val id: Long, val name: String = "")
/** [id] 0 = a new personal occasion. */
@Serializable data class SeasonEdit(val id: Long)
@Serializable data object Zakat
