package com.masroufi.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class DebtDirection { I_OWE, OWED_TO_ME }

@Serializable
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey val id: String,
    val direction: DebtDirection,
    val personName: String,
    val originalMinor: Long,
    val currency: String,
    val dueEpochDay: Long? = null,
    val note: String = "",
    val remind: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
@Entity(tableName = "debt_payments", indices = [Index("debtId")])
data class DebtPayment(
    @PrimaryKey val id: String,
    val debtId: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val note: String = "",
    val updatedAt: Long,
    val deleted: Boolean = false,
)

data class DebtWithPaid(
    @Embedded val debt: Debt,
    val paidMinor: Long,
)
