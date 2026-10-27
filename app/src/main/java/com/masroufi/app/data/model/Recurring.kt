package com.masroufi.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.masroufi.app.domain.Frequency
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "recurring")
data class RecurringRule(
    @PrimaryKey val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val walletId: String,
    val toWalletId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val frequency: Frequency,
    val interval: Int = 1,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    /** Last occurrence date already materialised; occurrences after it are still pending. */
    val lastGeneratedEpochDay: Long? = null,
    val nextEpochDay: Long? = null,
    val active: Boolean = true,
    val remind: Boolean = true,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
