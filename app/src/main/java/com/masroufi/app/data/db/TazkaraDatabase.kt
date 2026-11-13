package com.masroufi.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// shortcut: schema may change freely until Phase 3 ships the first usable build; from then every change adds a Migration.
@Database(
    entities = [
        PocketEntity::class, EntryEntity::class, CategoryEntity::class, DayTicketEntity::class,
        EnvelopeEntity::class, EnvelopeCategoryEntity::class, RecurringEntity::class,
        JarMovementEntity::class, GoalEntity::class, ProverbCollectedEntity::class, StampEntity::class,
    ],
    version = 1,
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
