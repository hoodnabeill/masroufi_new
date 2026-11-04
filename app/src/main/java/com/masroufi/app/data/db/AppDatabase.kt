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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Wallet::class, Category::class, Transaction::class, Budget::class, Goal::class, GoalEntry::class,
        RecurringRule::class, Debt::class, DebtPayment::class, Gameya::class, GameyaMember::class,
        GameyaPayment::class, GameyaPayout::class, SplitRule::class, Allocation::class,
    ],
    version = 4,
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

        /** v2: recurring rules can name the subscription service they pay for. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recurring ADD COLUMN serviceKey TEXT")
            }
        }

        /** v4: debts and gameya members can keep a phone number. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN phone TEXT")
                db.execSQL("ALTER TABLE gameya_members ADD COLUMN phone TEXT")
            }
        }

        /** v3: goals can save their monthly plan automatically. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN autoSave INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
