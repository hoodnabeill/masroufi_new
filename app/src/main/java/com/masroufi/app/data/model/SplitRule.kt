package com.masroufi.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class SplitKind { PERCENT, FIXED }

/** [value] is basis points (10000 = 100%) for PERCENT, minor units for FIXED. */
@Serializable
data class SplitItem(
    val label: String,
    val kind: SplitKind,
    val value: Long,
    val goalId: String? = null,
)

@Serializable
@Entity(tableName = "split_rules")
data class SplitRule(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val itemsJson: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
@Entity(tableName = "allocations")
data class Allocation(
    @PrimaryKey val id: String,
    val ruleId: String,
    val ruleName: String,
    val incomeMinor: Long,
    val currency: String,
    val detailJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
