package com.masroufi.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.TicketStatus
import java.time.LocalDate

enum class PocketKind { CASH, MOBILE_WALLET, BANK, UNDER_TILE, VAULT }
enum class EnvelopeKind { SPENDING, FIXED, EMERGENCY }
enum class RecurringKind { BILL, INSTALLMENT, SUBSCRIPTION, GAMEYA }
enum class Pot { JAR, EMERGENCY }
enum class JarSource { TICKET_LEFTOVER, ROUND_UP, MANUAL, CHANGED_MIND }

// Money is Long piastres. updatedAt is epoch millis, set by callers from the injected Clock.

@Entity(tableName = "pocket")
data class PocketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: PocketKind,
    val currency: String = "EGP",
    val openingBalance: Long = 0,
    val color: Int,
    val archived: Boolean = false,
    val countsInTicket: Boolean = true,
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "entry",
    indices = [Index("pocketId"), Index("toPocketId"), Index("happenedAt"), Index("recurringId")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EntryKind,
    val amount: Long,
    val pocketId: Long,
    val toPocketId: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null, // at most 120 chars, enforced by the caller
    val happenedAt: Long,
    val loggedAt: Long,
    val roundUp: Long = 0,
    val recurringId: Long? = null,
    val fee: Long = 0,
    val isFee: Boolean = false,
    val trackedItemId: Long? = null,
    val unitPrice: Long? = null,
    val updatedAt: Long = 0,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int,
    val parentId: Long? = null,
    val essential: Boolean = false,
    val updatedAt: Long = 0,
)

@Entity(tableName = "day_ticket", indices = [Index("date", unique = true)])
data class DayTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val amount: Long,
    val base: Long,
    val carriedIn: Long = 0,
    val spent: Long = 0,
    val entryCount: Int = 0,
    val status: TicketStatus = TicketStatus.OPEN,
    val leftover: Long = 0,
    val destination: LeftoverDestination? = null,
    val proverbNumber: Int? = null,
    val endOfMonth: Boolean = false,
    val updatedAt: Long = 0,
)

@Entity(tableName = "envelope")
data class EnvelopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val monthly: Long,
    val kind: EnvelopeKind,
    val updatedAt: Long = 0,
)

@Entity(tableName = "envelope_category", primaryKeys = ["envelopeId", "categoryId"])
data class EnvelopeCategoryEntity(
    val envelopeId: Long,
    val categoryId: Long,
    val updatedAt: Long = 0,
)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val pocketId: Long,
    val scheduleDay: Int,
    val nextDue: LocalDate,
    val envelopeId: Long? = null,
    val paidThisCycle: Boolean = false,
    val kind: RecurringKind,
    val updatedAt: Long = 0,
)

@Entity(
    tableName = "jar_movement",
    indices = [Index("pot"), Index("dayDate"), Index("entryId")],
)
data class JarMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pot: Pot,
    val amount: Long, // signed: deposits positive, withdrawals negative
    val source: JarSource,
    val goalId: Long? = null,
    val dayDate: LocalDate? = null,
    val entryId: Long? = null,
    val at: Long,
    val updatedAt: Long = 0,
)

@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    val date: LocalDate? = null,
    val updatedAt: Long = 0,
)

@Entity(tableName = "proverb_collected")
data class ProverbCollectedEntity(
    @PrimaryKey val number: Int,
    val collectedOn: LocalDate,
    val updatedAt: Long = 0,
)

@Entity(tableName = "stamp")
data class StampEntity(
    @PrimaryKey val kind: StampKind,
    val earnedOn: LocalDate,
    val updatedAt: Long = 0,
)
