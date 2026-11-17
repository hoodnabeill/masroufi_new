package com.masroufi.app.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object Home
@Serializable data object RollTab
@Serializable data object PlanTab
@Serializable data object PeopleTab
@Serializable data class CloseDay(val date: Long)
@Serializable data class ProverbReward(val date: Long)
@Serializable data object Gallery
