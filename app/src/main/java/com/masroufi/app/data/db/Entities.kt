package com.masroufi.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.masroufi.app.core.PRIMARY_CURRENCY
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
enum class ScheduleType { MONTHLY, YEARLY }
enum class SeasonKind { RAMADAN, EID_AL_FITR, EID_AL_ADHA, SUMMER, SCHOOL, PERSONAL }
enum class SplitKind { FIXED, PERCENT }
enum class PersonKind { PERSON, SHOP }
enum class DebtDirection { THEY_OWE, I_OWE }
enum class OccasionOwner { MINE, THEIRS }
enum class GiftDirection { RECEIVED, GIVEN }
enum class WishAnswer { WAITING, BOUGHT, CHANGED_MIND }

// Money is Long piastres. updatedAt is epoch millis, set by callers from the injected Clock.

@Entity(tableName = "pocket")
data class PocketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: PocketKind,
    val currency: String = PRIMARY_CURRENCY,
    val openingBalance: Long = 0,
    val color: Int,
    val archived: Boolean = false,
    val countsInTicket: Boolean = true,
    val updatedAt: Long = 0,
    /** Typed by the user from their provider; null = none. */
    val monthlyLimit: Long? = null,
    val lowBalanceThreshold: Long? = null,
    @ColumnInfo(defaultValue = "0") val lowBalanceAlert: Boolean = false,
    /** The balance parsed from the last pasted SMS and when (epoch millis); the message text is never stored. */
    val lastSmsBalance: Long? = null,
    val lastSmsAt: Long? = null,
)

@Entity(
    tableName = "entry",
    indices = [Index("pocketId"), Index("toPocketId"), Index("happenedAt"), Index("recurringId")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EntryKind,
    val amount: Long, // signed only for ADJUST and DEBT
    val pocketId: Long,
    val toPocketId: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null, // at most 120 chars, enforced by the caller
    val happenedAt: Long,
    val loggedAt: Long,
    val roundUp: Long = 0,
    val recurringId: Long? = null,
    /**
     * A transfer's or cash-out's fee is stored in `fee` on that entry and deducted from `pocketId`; never also log a
     * separate SPENT entry for it. `isFee` marks an entry whose whole amount is a fee (e.g. a standalone bank charge).
     */
    val fee: Long = 0,
    val isFee: Boolean = false,
    val trackedItemId: Long? = null,
    val unitPrice: Long? = null,
    val updatedAt: Long = 0,
    /** A DEBT entry's loan, repayment or borrow; null for a split's single DEBT entry (it covers several debts). */
    val debtId: Long? = null,
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
    /** End-of-month rows only: Spendable - Reserved and the days left when it printed. */
    val eomPool: Long? = null,
    val eomDaysLeft: Int? = null,
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
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
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
    /** Legacy, unused since v6: "paid" is derived (nextDue >= the next payday). Kept to avoid a migration. */
    val paidThisCycle: Boolean = false,
    val kind: RecurringKind,
    /** "onboarding" for items the first-run flow created (it replaces them on a redo); null otherwise. */
    val source: String? = null,
    val updatedAt: Long = 0,
    // No @ColumnInfo default: MIGRATION_2_3 adds DEFAULT 'MONTHLY' for old rows; Room always writes the column.
    val scheduleType: ScheduleType = ScheduleType.MONTHLY,
    /** Subscriptions: the free trial's last day; null = none. */
    val trialEnd: LocalDate? = null,
    val yearlyPrice: Long? = null,
    /** People paying it, the user included. */
    @ColumnInfo(defaultValue = "1") val sharedWith: Int = 1,
    /** The last three answers to the question of the month. */
    val answers: String? = null,
    val lastAskedAt: Long? = null,
    /** Deleted while entries still pay it: kept for them, hidden everywhere else. */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
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
    /** Jar money earmarked for a season (decision 3); moves nothing out of the jar. */
    val seasonId: Long? = null,
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

@Entity(tableName = "season")
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SeasonKind,
    val name: String,
    val hijriMonth: Int? = null,
    val hijriDay: Int? = null,
    val gregorianDate: LocalDate? = null,
    val target: Long,
    val color: Int,
    val updatedAt: Long = 0,
)

@Entity(tableName = "season_item")
data class SeasonItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seasonId: Long,
    val label: String,
    val amount: Long,
)

/** Decision 2: money moved between envelopes for the pay cycle starting `cycleStart` only. */
@Entity(tableName = "envelope_move")
data class EnvelopeMoveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleStart: LocalDate,
    val fromId: Long,
    val toId: Long,
    val amount: Long,
    val at: Long,
)

/** One line of the saved income split; value = piastres (FIXED) or percent (PERCENT). */
@Entity(tableName = "split_rule")
data class SplitRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val envelopeId: Long,
    val kind: SplitKind,
    val value: Long,
    val sortOrder: Int,
)

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val kind: PersonKind = PersonKind.PERSON,
    val updatedAt: Long = 0,
)

/** [amount] is what is still open: a partial repayment lowers it; a full one sets [settled]. */
@Entity(tableName = "debt")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val direction: DebtDirection,
    val amount: Long,
    val reason: String? = null,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val settled: Boolean = false,
    /** A shared subscription's payment that created this debt (decision 4). */
    val sourceRecurringId: Long? = null,
)

@Entity(tableName = "tab_item")
data class TabItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val label: String,
    val amount: Long,
    val date: LocalDate,
    val settled: Boolean = false,
    val settledEntryId: Long? = null,
)

@Entity(tableName = "occasion")
data class OccasionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: LocalDate,
    val whose: OccasionOwner,
    val personId: Long? = null,
)

@Entity(tableName = "gift")
data class GiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occasionId: Long,
    val personId: Long,
    val amount: Long,
    val direction: GiftDirection,
)

/** [startMonth] and the payment months are yyyymm; the user is turn [myTurn] and is not in gameya_member. */
@Entity(tableName = "gameya")
data class GameyaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthly: Long,
    val startMonth: Int,
    val myTurn: Int,
    val recurringId: Long? = null,
    val archived: Boolean = false,
)

@Entity(tableName = "gameya_member")
data class GameyaMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameyaId: Long,
    val name: String,
    val turn: Int,
    val phone: String? = null,
)

/** memberId null = the user's own installment. */
@Entity(tableName = "gameya_payment")
data class GameyaPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameyaId: Long,
    val memberId: Long? = null,
    val month: Int,
    val paid: Boolean,
)

@Entity(tableName = "gameya_plan")
data class GameyaPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameyaId: Long,
    val label: String,
    val amount: Long,
    val seasonId: Long? = null,
    val goalId: Long? = null,
)

/** "نِفسي فيه": something parked for 48 hours; [installmentMonthly]/[installmentMonths] are what the shop said, as typed. */
@Entity(tableName = "wish")
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    val parkedAt: Long,
    val answer: WishAnswer = WishAnswer.WAITING,
    val answeredAt: Long? = null,
    val installmentMonthly: Long? = null,
    val installmentMonths: Int? = null,
    /** What "صرفت نظر" really moved to the jar (capped at spendable); null = nothing moved. */
    val movedToJar: Long? = null,
)

/** A thing bought again and again; [normalisedName] is what entries are matched on. [unit] is not captured yet. */
@Entity(tableName = "tracked_item")
data class TrackedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalisedName: String,
    val unit: String? = null,
    val addedByUser: Boolean = false,
    val createdAt: Long,
)
