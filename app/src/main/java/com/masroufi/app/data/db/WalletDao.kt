package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.masroufi.app.data.model.Wallet
import com.masroufi.app.data.model.WalletWithBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query(
        """
        SELECT w.*, w.initialBalanceMinor
          + COALESCE((SELECT SUM(t.amountMinor) FROM transactions t WHERE t.walletId = w.id AND t.type = 'INCOME' AND t.deleted = 0), 0)
          - COALESCE((SELECT SUM(t.amountMinor) FROM transactions t WHERE t.walletId = w.id AND t.type IN ('EXPENSE', 'TRANSFER') AND t.deleted = 0), 0)
          + COALESCE((SELECT SUM(COALESCE(t.toAmountMinor, t.amountMinor)) FROM transactions t WHERE t.toWalletId = w.id AND t.type = 'TRANSFER' AND t.deleted = 0), 0)
          AS balanceMinor
        FROM wallets w
        WHERE w.deleted = 0
        ORDER BY w.archived, w.sortOrder, w.createdAt
        """,
    )
    fun observeWithBalances(): Flow<List<WalletWithBalance>>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun get(id: String): Wallet?

    @Query("SELECT * FROM wallets WHERE id = :id")
    fun observe(id: String): Flow<Wallet?>

    @Query("SELECT COUNT(*) FROM wallets WHERE deleted = 0")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(wallet: Wallet)

    @Query("SELECT * FROM wallets")
    suspend fun all(): List<Wallet>

    @Upsert
    suspend fun upsertAll(items: List<Wallet>)

    @Query("DELETE FROM wallets")
    suspend fun clear()
}
