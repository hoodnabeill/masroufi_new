package com.masroufi.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class WalletType { CASH, BANK, DIGITAL, CUSTOM }

@Serializable
@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey val id: String,
    val name: String,
    val type: WalletType,
    val currency: String,
    val initialBalanceMinor: Long,
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

data class WalletWithBalance(
    @Embedded val wallet: Wallet,
    val balanceMinor: Long,
)
