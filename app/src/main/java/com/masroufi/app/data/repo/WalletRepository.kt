package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.Wallet
import com.masroufi.app.data.model.WalletWithBalance
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.walletDao()

    fun observeWallets(): Flow<List<WalletWithBalance>> = dao.observeWithBalances()

    fun observe(id: String): Flow<Wallet?> = dao.observe(id)

    suspend fun get(id: String): Wallet? = dao.get(id)

    suspend fun hasAnyWallet(): Boolean = dao.count() > 0

    suspend fun save(wallet: Wallet) {
        ensure(wallet.name.isNotBlank(), FinanceError.NAME_REQUIRED)
        dao.upsert(wallet.copy(name = wallet.name.trim(), updatedAt = clock.millis()))
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        val wallet = dao.get(id) ?: throw FinanceException(FinanceError.NOT_FOUND)
        dao.upsert(wallet.copy(archived = archived, updatedAt = clock.millis()))
    }

    /** A wallet with history is archived rather than deleted so past balances and reports stay correct. */
    suspend fun delete(id: String) = db.withTransaction {
        ensure(db.transactionDao().countForWallet(id) == 0, FinanceError.WALLET_HAS_TRANSACTIONS)
        val wallet = dao.get(id) ?: throw FinanceException(FinanceError.NOT_FOUND)
        dao.upsert(wallet.copy(deleted = true, updatedAt = clock.millis()))
    }
}

@Singleton
class CategoryRepository @Inject constructor(
    db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.categoryDao()

    fun observeAll(): Flow<List<Category>> = dao.observeAll()
    fun observeByRecentUse(): Flow<List<Category>> = dao.observeByRecentUse()

    suspend fun save(category: Category) {
        ensure(category.name.isNotBlank() || category.systemKey != null, FinanceError.NAME_REQUIRED)
        dao.upsert(category.copy(name = category.name.trim(), updatedAt = clock.millis()))
    }

    suspend fun delete(id: String) {
        val category = dao.get(id) ?: return
        dao.upsert(category.copy(deleted = true, updatedAt = clock.millis()))
    }
}
