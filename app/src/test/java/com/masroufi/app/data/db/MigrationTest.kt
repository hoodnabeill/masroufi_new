package com.masroufi.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The v1 `pocket` table exactly as Room 2.7 created it from the v1 PocketEntity. */
private const val V1_POCKET = "CREATE TABLE IF NOT EXISTS `pocket` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
    "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `currency` TEXT NOT NULL, `openingBalance` INTEGER NOT NULL, " +
    "`color` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `countsInTicket` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before @After fun clean() {
        context.deleteDatabase(name)
    }

    /**
     * v1 file database: only `pocket` differs between v1 and v2, so Room creates the other tables, then the v1 pocket
     * table replaces its own and user_version goes back to 1. Opening with Room v2 runs MIGRATION_1_2 and Room's
     * schema validation over every table.
     */
    @Test fun migration1to2KeepsData() = runTest {
        Room.databaseBuilder(context, TazkaraDatabase::class.java, name).build().apply { openHelper.writableDatabase }.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { v1 ->
            v1.execSQL("DROP TABLE `pocket`")
            v1.execSQL(V1_POCKET)
            v1.execSQL(
                "INSERT INTO pocket (id, name, kind, currency, openingBalance, color, archived, countsInTicket, updatedAt) " +
                    "VALUES (1, 'cash', 'CASH', 'EGP', 100000, 7, 0, 1, 5)",
            )
            v1.execSQL(
                "INSERT INTO entry (kind, amount, pocketId, happenedAt, loggedAt, roundUp, fee, isFee, updatedAt) " +
                    "VALUES ('SPENT', 2000, 1, 0, 0, 0, 0, 0, 0)",
            )
            v1.version = 1
        }

        val db = Room.databaseBuilder(context, TazkaraDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val p = checkNotNull(db.pocketDao().byId(1)) { "pocket lost in the migration" }
            assertEquals(
                "old columns kept",
                PocketEntity(id = 1, name = "cash", kind = PocketKind.CASH, openingBalance = 100_000, color = 7, updatedAt = 5),
                p,
            )
            assertEquals("new columns: no limit, alert off, no SMS", listOf(null, null, false, null, null),
                listOf(p.monthlyLimit, p.lowBalanceThreshold, p.lowBalanceAlert, p.lastSmsBalance, p.lastSmsAt))
            assertEquals("the entry survives and still counts", 98_000L, db.pocketDao().balanceOf(1))
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
