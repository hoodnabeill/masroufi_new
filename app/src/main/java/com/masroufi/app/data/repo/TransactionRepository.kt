package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.model.CategoryTotal
import com.masroufi.app.data.model.MonthTotal
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.TypeTotal
import com.masroufi.app.receipts.ReceiptStore
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class TransactionSort { NEWEST, OLDEST, AMOUNT_DESC, AMOUNT_ASC }

data class TransactionFilter(
    val type: TransactionType? = null,
    val walletId: String? = null,
    val categoryId: String? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
    val sort: TransactionSort = TransactionSort.NEWEST,
    val limit: Int = 500,
) {
    val isActive: Boolean
        get() = type != null || walletId != null || categoryId != null || fromMillis != null ||
            toMillis != null || minMinor != null || maxMinor != null
}

@Singleton
class TransactionRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val receipts: ReceiptStore,
) {
    private val dao = db.transactionDao()

    fun observeRows(filter: TransactionFilter): Flow<List<TransactionRow>> = dao.observeRows(
        type = filter.type?.name,
        walletId = filter.walletId,
        categoryId = filter.categoryId,
        fromMillis = filter.fromMillis,
        toMillis = filter.toMillis,
        minMinor = filter.minMinor,
        maxMinor = filter.maxMinor,
        sort = filter.sort.name,
        limit = filter.limit,
    )

    fun observeRow(id: String): Flow<TransactionRow?> = dao.observeRow(id)
    suspend fun get(id: String): Transaction? = dao.get(id)
    fun observeTypeTotals(from: Long, to: Long): Flow<List<TypeTotal>> = dao.observeTypeTotals(from, to)
    fun observeExpenseByCategory(from: Long, to: Long): Flow<List<CategoryTotal>> = dao.observeExpenseByCategory(from, to)
    suspend fun expenseByCategory(from: Long, to: Long): List<CategoryTotal> = dao.expenseByCategory(from, to)
    fun observeMonthlyTotals(from: Long): Flow<List<MonthTotal>> = dao.observeMonthlyTotals(from)
    fun observeFirstOccurredAt(): Flow<Long?> = dao.observeFirstOccurredAt()
    suspend fun lastUsedExpenseWallet(): String? = dao.lastUsedExpenseWallet()

    /**
     * Validates and writes a transaction. The row's currency always follows its source wallet so totals
     * are never mixed across currencies; a transfer is one row, so both wallet balances move together.
     */
    suspend fun save(input: Transaction) = db.withTransaction {
        ensure(input.amountMinor > 0, FinanceError.AMOUNT_REQUIRED)
        val wallet = db.walletDao().get(input.walletId)
        ensure(wallet != null && !wallet.deleted, FinanceError.WALLET_REQUIRED)
        var toAmount: Long? = null
        if (input.type == TransactionType.TRANSFER) {
            val target = input.toWalletId?.let { db.walletDao().get(it) }
            ensure(target != null && !target.deleted, FinanceError.WALLET_REQUIRED)
            ensure(target!!.id != wallet!!.id, FinanceError.SAME_WALLET_TRANSFER)
            if (target.currency != wallet.currency) {
                ensure((input.toAmountMinor ?: 0) > 0, FinanceError.TARGET_AMOUNT_REQUIRED)
                toAmount = input.toAmountMinor
            }
        }
        val now = clock.millis()
        val existing = dao.get(input.id)
        if (existing?.receiptPath != null && existing.receiptPath != input.receiptPath) {
            receipts.delete(existing.receiptPath)
        }
        dao.upsert(
            input.copy(
                currency = wallet!!.currency,
                toWalletId = if (input.type == TransactionType.TRANSFER) input.toWalletId else null,
                toAmountMinor = toAmount,
                categoryId = if (input.type == TransactionType.TRANSFER) null else input.categoryId,
                note = input.note.trim(),
                tags = input.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(","),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun delete(id: String) {
        val tx = dao.get(id) ?: return
        tx.receiptPath?.let { receipts.delete(it) }
        dao.upsert(tx.copy(deleted = true, receiptPath = null, updatedAt = clock.millis()))
    }

    suspend fun removeReceipt(id: String) {
        val tx = dao.get(id) ?: return
        tx.receiptPath?.let { receipts.delete(it) }
        dao.upsert(tx.copy(receiptPath = null, updatedAt = clock.millis()))
    }
}
