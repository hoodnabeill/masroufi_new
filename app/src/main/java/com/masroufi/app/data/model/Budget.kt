package com.masroufi.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class BudgetPeriod { MONTHLY, CUSTOM }

/** [categoryId] null means the budget covers all expenses in [currency]. Dates are epoch days. */
@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String? = null,
    val limitMinor: Long,
    val currency: String,
    val period: BudgetPeriod,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val rollover: Boolean = false,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
