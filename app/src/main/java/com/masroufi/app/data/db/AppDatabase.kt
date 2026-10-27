package com.masroufi.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.masroufi.app.data.model.Allocation
import com.masroufi.app.data.model.Budget
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.Debt
import com.masroufi.app.data.model.DebtPayment
import com.masroufi.app.data.model.Gameya
import com.masroufi.app.data.model.GameyaMember
import com.masroufi.app.data.model.GameyaPayment
import com.masroufi.app.data.model.GameyaPayout
import com.masroufi.app.data.model.Goal
import com.masroufi.app.data.model.GoalEntry
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.SplitRule
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.data.model.Wallet

@Database(
    entities = [
        Wallet::class, Category::class, Transaction::class, Budget::class, Goal::class, GoalEntry::class,
        RecurringRule::class, Debt::class, DebtPayment::class, Gameya::class, GameyaMember::class,
        GameyaPayment::class, GameyaPayout::class, SplitRule::class, Allocation::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun recurringDao(): RecurringDao
    abstract fun debtDao(): DebtDao
    abstract fun gameyaDao(): GameyaDao
    abstract fun splitDao(): SplitDao

    companion object {
        const val NAME = "masroufi.db"
    }
}
