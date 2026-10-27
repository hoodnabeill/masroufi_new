package com.masroufi.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Goal money is an earmark, not a wallet: entries never change wallet balances,
 * so the same pound is never counted twice.
 */
@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val targetEpochDay: Long? = null,
    val iconKey: String = "savings",
    val colorIndex: Int = 0,
    val plannedMonthlyMinor: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Positive amount = contribution, negative = withdrawal. */
@Serializable
@Entity(tableName = "goal_entries", indices = [Index("goalId")])
data class GoalEntry(
    @PrimaryKey val id: String,
    val goalId: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val note: String = "",
    val updatedAt: Long,
    val deleted: Boolean = false,
)

data class GoalWithSaved(
    @androidx.room.Embedded val goal: Goal,
    val savedMinor: Long,
)
