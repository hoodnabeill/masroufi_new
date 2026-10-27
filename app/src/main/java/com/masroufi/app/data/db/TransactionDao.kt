package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.masroufi.app.data.model.CategoryTotal
import com.masroufi.app.data.model.MonthTotal
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.data.model.TypeTotal
import kotlinx.coroutines.flow.Flow

private const val ROW_SELECT = """
    SELECT t.*, c.name AS categoryName, c.systemKey AS categorySystemKey, c.iconKey AS categoryIcon,
           c.colorIndex AS categoryColor, w.name AS walletName, tw.name AS toWalletName, tw.currency AS toWalletCurrency
    FROM transactions t
    LEFT JOIN categories c ON c.id = t.categoryId
    LEFT JOIN wallets w ON w.id = t.walletId
    LEFT JOIN wallets tw ON tw.id = t.toWalletId
"""

@Dao
interface TransactionDao {
    @Query(
        ROW_SELECT + """
        WHERE t.deleted = 0
          AND (:type IS NULL OR t.type = :type)
          AND (:walletId IS NULL OR t.walletId = :walletId OR t.toWalletId = :walletId)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId)
          AND (:fromMillis IS NULL OR t.occurredAt >= :fromMillis)
          AND (:toMillis IS NULL OR t.occurredAt < :toMillis)
          AND (:minMinor IS NULL OR t.amountMinor >= :minMinor)
          AND (:maxMinor IS NULL OR t.amountMinor <= :maxMinor)
        ORDER BY
          CASE WHEN :sort = 'AMOUNT_DESC' THEN t.amountMinor END DESC,
          CASE WHEN :sort = 'AMOUNT_ASC' THEN t.amountMinor END ASC,
          CASE WHEN :sort = 'OLDEST' THEN t.occurredAt END ASC,
          t.occurredAt DESC
        LIMIT :limit
        """,
    )
    fun observeRows(
        type: String?,
        walletId: String?,
        categoryId: String?,
        fromMillis: Long?,
        toMillis: Long?,
        minMinor: Long?,
        maxMinor: Long?,
        sort: String,
        limit: Int,
    ): Flow<List<TransactionRow>>

    @Query(ROW_SELECT + " WHERE t.id = :id")
    fun observeRow(id: String): Flow<TransactionRow?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): Transaction?

    @Query(
        """
        SELECT type, currency, SUM(amountMinor) AS total FROM transactions
        WHERE deleted = 0 AND type != 'TRANSFER' AND occurredAt >= :fromMillis AND occurredAt < :toMillis
        GROUP BY type, currency
        """,
    )
    fun observeTypeTotals(fromMillis: Long, toMillis: Long): Flow<List<TypeTotal>>

    @Query(
        """
        SELECT categoryId, currency, SUM(amountMinor) AS total FROM transactions
        WHERE deleted = 0 AND type = 'EXPENSE' AND occurredAt >= :fromMillis AND occurredAt < :toMillis
        GROUP BY categoryId, currency
        """,
    )
    fun observeExpenseByCategory(fromMillis: Long, toMillis: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT categoryId, currency, SUM(amountMinor) AS total FROM transactions
        WHERE deleted = 0 AND type = 'EXPENSE' AND occurredAt >= :fromMillis AND occurredAt < :toMillis
        GROUP BY categoryId, currency
        """,
    )
    suspend fun expenseByCategory(fromMillis: Long, toMillis: Long): List<CategoryTotal>

    /** Month keys are computed in local time so totals match what the user sees on the calendar. */
    @Query(
        """
        SELECT strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime') AS month, type, currency, SUM(amountMinor) AS total
        FROM transactions
        WHERE deleted = 0 AND type != 'TRANSFER' AND occurredAt >= :fromMillis
        GROUP BY month, type, currency
        ORDER BY month
        """,
    )
    fun observeMonthlyTotals(fromMillis: Long): Flow<List<MonthTotal>>

    @Query(
        """
        SELECT COALESCE(SUM(t.amountMinor), 0) FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.deleted = 0 AND t.type = 'EXPENSE' AND t.currency = :currency
          AND t.occurredAt >= :fromMillis AND t.occurredAt < :toMillis
          AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId)
        """,
    )
    fun observeSpent(categoryId: String?, currency: String, fromMillis: Long, toMillis: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(t.amountMinor), 0) FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.deleted = 0 AND t.type = 'EXPENSE' AND t.currency = :currency
          AND t.occurredAt >= :fromMillis AND t.occurredAt < :toMillis
          AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId)
        """,
    )
    suspend fun spent(categoryId: String?, currency: String, fromMillis: Long, toMillis: Long): Long

    @Query("SELECT MIN(occurredAt) FROM transactions WHERE deleted = 0")
    fun observeFirstOccurredAt(): Flow<Long?>

    @Query("SELECT walletId FROM transactions WHERE deleted = 0 AND type = 'EXPENSE' ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastUsedExpenseWallet(): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: Transaction): Long

    @Upsert
    suspend fun upsert(transaction: Transaction)

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0 AND (walletId = :walletId OR toWalletId = :walletId)")
    suspend fun countForWallet(walletId: String): Int

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<Transaction>

    @Upsert
    suspend fun upsertAll(items: List<Transaction>)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
