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
        PersonEntity::class, DebtEntity::class, TabItemEntity::class, OccasionEntity::class, GiftEntity::class,
        GameyaEntity::class, GameyaMemberEntity::class, GameyaPaymentEntity::class, GameyaPlanEntity::class,
        WishEntity::class, TrackedItemEntity::class, ClientJobEntity::class,
        MonthStoryEntity::class, ChildEntity::class, ChoreEntity::class, ChoreTickEntity::class, ChildAdvanceEntity::class,
        ChildJarMoveEntity::class, ChildSpendEntity::class,
    ],
    version = 8,
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
    abstract fun seasonDao(): SeasonDao
    abstract fun peopleDao(): PeopleDao
    abstract fun dutyDao(): DutyDao
    abstract fun gameyaDao(): GameyaDao
    abstract fun wishDao(): WishDao
    abstract fun trackedItemDao(): TrackedItemDao
    abstract fun clientJobDao(): ClientJobDao
    abstract fun storyDao(): StoryDao
    abstract fun kidsDao(): KidsDao

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

/** v4 (Phase 7): people, debts, the tab, the duty ledger and gameyas; an entry can name its debt. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `person` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`phone` TEXT, `kind` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debt` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, " +
                "`direction` TEXT NOT NULL, `amount` INTEGER NOT NULL, `reason` TEXT, `date` INTEGER NOT NULL, `dueDate` INTEGER, " +
                "`settled` INTEGER NOT NULL, `sourceRecurringId` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tab_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL, `settled` INTEGER NOT NULL, `settledEntryId` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `occasion` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`date` INTEGER NOT NULL, `whose` TEXT NOT NULL, `personId` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gift` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `occasionId` INTEGER NOT NULL, " +
                "`personId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `direction` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gameya` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`monthly` INTEGER NOT NULL, `startMonth` INTEGER NOT NULL, `myTurn` INTEGER NOT NULL, `recurringId` INTEGER, " +
                "`archived` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gameya_member` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gameyaId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `turn` INTEGER NOT NULL, `phone` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gameya_payment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gameyaId` INTEGER NOT NULL, " +
                "`memberId` INTEGER, `month` INTEGER NOT NULL, `paid` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gameya_plan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gameyaId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, `amount` INTEGER NOT NULL, `seasonId` INTEGER, `goalId` INTEGER)",
        )
        db.execSQL("ALTER TABLE `entry` ADD COLUMN `debtId` INTEGER")
    }
}

/** v5 (Phase 8): the wishlist and the tracked items of the basket. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wish` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`price` INTEGER NOT NULL, `parkedAt` INTEGER NOT NULL, `answer` TEXT NOT NULL, `answeredAt` INTEGER, " +
                "`installmentMonthly` INTEGER, `installmentMonths` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tracked_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`normalisedName` TEXT NOT NULL, `unit` TEXT, `addedByUser` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
    }
}

/** v6 (Phase 8): what a changed-my-mind answer really moved to the jar. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `wish` ADD COLUMN `movedToJar` INTEGER")
    }
}

/** v7 (Phase 9a): installment plans know their length; the freelancer's client jobs. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `totalCount` INTEGER")
        db.execSQL("ALTER TABLE `recurring` ADD COLUMN `paidBefore` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `client_job` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client` TEXT NOT NULL, " +
                "`label` TEXT NOT NULL, `amount` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, `paidEntryId` INTEGER)",
        )
    }
}

/** v8 (Phase 9b): the monthly stories and the kids' allowance. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `month_story` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cycleStart` INTEGER NOT NULL, " +
                "`cycleEnd` INTEGER NOT NULL, `payload` TEXT NOT NULL, `shownAt` INTEGER)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_month_story_cycleStart` ON `month_story` (`cycleStart`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `child` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`birthYear` INTEGER NOT NULL, `color` INTEGER NOT NULL, `weekly` INTEGER NOT NULL, `jarLabel` TEXT, `jarTarget` INTEGER, " +
                "`matchPer` INTEGER, `matchAdd` INTEGER, `archived` INTEGER NOT NULL, `handedOver` INTEGER NOT NULL, `handedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chore` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, " +
                "`bonus` INTEGER NOT NULL, `childId` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chore_tick` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `choreId` INTEGER NOT NULL, " +
                "`childId` INTEGER NOT NULL, `weekStart` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chore_tick_weekStart` ON `chore_tick` (`weekStart`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `child_advance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `childId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, `requestedAt` INTEGER NOT NULL, `answer` TEXT NOT NULL, `weekStart` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_child_advance_childId` ON `child_advance` (`childId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `child_jar_move` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `childId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, `at` INTEGER NOT NULL, `kind` TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_child_jar_move_childId` ON `child_jar_move` (`childId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `child_spend` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `childId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, `at` INTEGER NOT NULL, `note` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_child_spend_childId` ON `child_spend` (`childId`)")
    }
}
