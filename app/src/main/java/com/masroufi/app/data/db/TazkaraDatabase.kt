package com.masroufi.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Schema changes add a Migration; never destructive.
@Database(
    entities = [
        PocketEntity::class, EntryEntity::class, CategoryEntity::class, DayTicketEntity::class,
        EnvelopeEntity::class, EnvelopeCategoryEntity::class, RecurringEntity::class,
        JarMovementEntity::class, GoalEntity::class, ProverbCollectedEntity::class, StampEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TazkaraDatabase : RoomDatabase() {
    abstract fun pocketDao(): PocketDao
    abstract fun entryDao(): EntryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun dayTicketDao(): DayTicketDao
    abstract fun envelopeDao(): EnvelopeDao
    abstract fun recurringDao(): RecurringDao
    abstract fun jarDao(): JarDao
    abstract fun goalDao(): GoalDao
    abstract fun proverbDao(): ProverbDao
    abstract fun stampDao(): StampDao

    companion object {
        const val FILE_NAME = "tazkara.db"
    }
}

/** v2: a pocket's monthly limit, low-balance alert and last pasted SMS balance. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `pocket` ADD COLUMN `monthlyLimit` INTEGER")
        db.execSQL("ALTER TABLE `pocket` ADD COLUMN `lowBalanceThreshold` INTEGER")
        db.execSQL("ALTER TABLE `pocket` ADD COLUMN `lowBalanceAlert` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `pocket` ADD COLUMN `lastSmsBalance` INTEGER")
        db.execSQL("ALTER TABLE `pocket` ADD COLUMN `lastSmsAt` INTEGER")
    }
}
