package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.masroufi.app.data.model.Debt
import com.masroufi.app.data.model.DebtPayment
import com.masroufi.app.data.model.DebtWithPaid
import com.masroufi.app.data.model.Gameya
import com.masroufi.app.data.model.GameyaMember
import com.masroufi.app.data.model.GameyaPayment
import com.masroufi.app.data.model.GameyaPayout
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query(
        """
        SELECT d.*, COALESCE((SELECT SUM(p.amountMinor) FROM debt_payments p WHERE p.debtId = d.id AND p.deleted = 0), 0) AS paidMinor
        FROM debts d WHERE d.deleted = 0 ORDER BY d.dueEpochDay IS NULL, d.dueEpochDay, d.createdAt DESC
        """,
    )
    fun observeWithPaid(): Flow<List<DebtWithPaid>>

    @Query(
        """
        SELECT d.*, COALESCE((SELECT SUM(p.amountMinor) FROM debt_payments p WHERE p.debtId = d.id AND p.deleted = 0), 0) AS paidMinor
        FROM debts d WHERE d.id = :id
        """,
    )
    fun observeOne(id: String): Flow<DebtWithPaid?>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM debt_payments WHERE debtId = :debtId AND deleted = 0")
    suspend fun paid(debtId: String): Long

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId AND deleted = 0 ORDER BY occurredAt DESC")
    fun observePayments(debtId: String): Flow<List<DebtPayment>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun get(id: String): Debt?

    @Upsert
    suspend fun upsert(debt: Debt)

    @Upsert
    suspend fun upsertPayment(payment: DebtPayment)

    @Query("SELECT * FROM debts")
    suspend fun all(): List<Debt>

    @Query("SELECT * FROM debt_payments")
    suspend fun allPayments(): List<DebtPayment>

    @Upsert
    suspend fun upsertAll(items: List<Debt>)

    @Upsert
    suspend fun upsertAllPayments(items: List<DebtPayment>)

    @Query("DELETE FROM debts")
    suspend fun clear()

    @Query("DELETE FROM debt_payments")
    suspend fun clearPayments()
}

@Dao
interface GameyaDao {
    @Query("SELECT * FROM gameyas WHERE deleted = 0 ORDER BY startEpochDay DESC")
    fun observeAll(): Flow<List<Gameya>>

    @Query("SELECT * FROM gameyas WHERE id = :id")
    fun observe(id: String): Flow<Gameya?>

    @Query("SELECT * FROM gameyas WHERE id = :id")
    suspend fun get(id: String): Gameya?

    @Query("SELECT * FROM gameya_members WHERE gameyaId = :gameyaId AND deleted = 0 ORDER BY payoutOrder")
    fun observeMembers(gameyaId: String): Flow<List<GameyaMember>>

    @Query("SELECT * FROM gameya_members WHERE gameyaId = :gameyaId AND deleted = 0 ORDER BY payoutOrder")
    suspend fun members(gameyaId: String): List<GameyaMember>

    @Query("SELECT * FROM gameya_members WHERE deleted = 0")
    fun observeAllMembers(): Flow<List<GameyaMember>>

    @Query("SELECT * FROM gameya_payments WHERE gameyaId = :gameyaId AND deleted = 0")
    fun observePayments(gameyaId: String): Flow<List<GameyaPayment>>

    @Query("SELECT * FROM gameya_payments WHERE deleted = 0")
    fun observeAllPayments(): Flow<List<GameyaPayment>>

    @Query("SELECT * FROM gameya_payouts WHERE gameyaId = :gameyaId AND deleted = 0")
    fun observePayouts(gameyaId: String): Flow<List<GameyaPayout>>

    @Query("SELECT * FROM gameya_payouts WHERE deleted = 0")
    fun observeAllPayouts(): Flow<List<GameyaPayout>>

    @Query("SELECT * FROM gameya_payments WHERE id = :id")
    suspend fun payment(id: String): GameyaPayment?

    @Query("SELECT * FROM gameya_payouts WHERE id = :id")
    suspend fun payout(id: String): GameyaPayout?

    @Upsert
    suspend fun upsert(gameya: Gameya)

    @Upsert
    suspend fun upsertMembers(members: List<GameyaMember>)

    @Upsert
    suspend fun upsertPayment(payment: GameyaPayment)

    @Upsert
    suspend fun upsertPayout(payout: GameyaPayout)

    @Query("SELECT * FROM gameyas")
    suspend fun all(): List<Gameya>

    @Query("SELECT * FROM gameya_members")
    suspend fun allMembers(): List<GameyaMember>

    @Query("SELECT * FROM gameya_payments")
    suspend fun allPayments(): List<GameyaPayment>

    @Query("SELECT * FROM gameya_payouts")
    suspend fun allPayouts(): List<GameyaPayout>

    @Upsert
    suspend fun upsertAll(items: List<Gameya>)

    @Upsert
    suspend fun upsertAllPayments(items: List<GameyaPayment>)

    @Upsert
    suspend fun upsertAllPayouts(items: List<GameyaPayout>)

    @Query("DELETE FROM gameyas")
    suspend fun clear()

    @Query("DELETE FROM gameya_members")
    suspend fun clearMembers()

    @Query("DELETE FROM gameya_payments")
    suspend fun clearPayments()

    @Query("DELETE FROM gameya_payouts")
    suspend fun clearPayouts()
}
