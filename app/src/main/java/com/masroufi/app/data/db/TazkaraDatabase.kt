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
        SeasonEntity::class, SeasonItemEntity::class, EnvelopeMoveEntity::class, SplitRuleEntity::class,
    ],
    version = 3,
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

/** v3 (Phase 6): seasons, envelope moves, the saved split; subscription and archive columns. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `season` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `hijriMonth` INTEGER, `hijriDay` INTEGER, `gregorianDate` INTEGER, `target` INTEGER NOT NULL, " +
                "`color` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `season_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `seasonId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, `amount` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `envelope_move` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycleStart` INTEGER NOT NULL, " +
                "`fromId` INTEGER NOT NULL, `toId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `split_rule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `envelopeId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `value` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
        )
        db.execSQL("ALTER TABLE `jar_movement` ADD COLUMN `seasonId` INTEGER")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `scheduleType` TEXT NOT NULL DEFAULT 'MONTHLY'")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `trialEnd` INTEGER")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `yearlyPrice` INTEGER")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `sharedWith` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `answers` TEXT")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `lastAskedAt` INTEGER")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `envelope` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
    }
}
