package com.masroufi.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A rotating savings group (جمعية). Round n is paid out to the member whose payoutOrder is n. */
@Serializable
@Entity(tableName = "gameyas")
data class Gameya(
    @PrimaryKey val id: String,
    val name: String,
    val contributionMinor: Long,
    val currency: String,
    /** Days between rounds when [monthly] is false. */
    val intervalDays: Int = 30,
    val monthly: Boolean = true,
    val startEpochDay: Long,
    val organizer: String = "",
    val note: String = "",
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
@Entity(tableName = "gameya_members", indices = [Index("gameyaId")])
data class GameyaMember(
    @PrimaryKey val id: String,
    val gameyaId: String,
    val name: String,
    val payoutOrder: Int,
    val isMe: Boolean = false,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** id is "gameyaId:memberId:round" so marking a payment twice can never create two rows. */
@Serializable
@Entity(tableName = "gameya_payments", indices = [Index("gameyaId")])
data class GameyaPayment(
    @PrimaryKey val id: String,
    val gameyaId: String,
    val memberId: String,
    val round: Int,
    val amountMinor: Long,
    val paidAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** id is "gameyaId:round". */
@Serializable
@Entity(tableName = "gameya_payouts", indices = [Index("gameyaId")])
data class GameyaPayout(
    @PrimaryKey val id: String,
    val gameyaId: String,
    val round: Int,
    val amountMinor: Long,
    val receivedAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
