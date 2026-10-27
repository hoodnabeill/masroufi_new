package com.masroufi.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * Amounts are always positive; [type] gives the direction. A transfer is a single row so both
 * wallets change in one write. [toAmountMinor] is set only for cross-currency transfers.
 */
@Serializable
@Entity(
    tableName = "transactions",
    indices = [
        Index("walletId"),
        Index("toWalletId"),
        Index("categoryId"),
        Index("occurredAt"),
        Index(value = ["recurringId", "occurrenceKey"], unique = true),
    ],
)
data class Transaction(
    @PrimaryKey val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val walletId: String,
    val toWalletId: String? = null,
    val toAmountMinor: Long? = null,
    val categoryId: String? = null,
    val note: String = "",
    val tags: String = "",
    val occurredAt: Long,
    val receiptPath: String? = null,
    val recurringId: String? = null,
    val occurrenceKey: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

data class TransactionRow(
    @Embedded val transaction: Transaction,
    val categoryName: String?,
    val categorySystemKey: String?,
    val categoryIcon: String?,
    val categoryColor: Int?,
    val walletName: String?,
    val toWalletName: String?,
    val toWalletCurrency: String?,
)

data class TypeTotal(val type: TransactionType, val currency: String, val total: Long)

data class CategoryTotal(val categoryId: String?, val currency: String, val total: Long)

data class MonthTotal(val month: String, val type: TransactionType, val currency: String, val total: Long)
