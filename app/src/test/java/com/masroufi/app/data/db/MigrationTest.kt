package com.masroufi.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The v1 `pocket` table exactly as Room 2.7 created it from the v1 PocketEntity. */
private const val V1_POCKET = "CREATE TABLE IF NOT EXISTS `pocket` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
    "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `currency` TEXT NOT NULL, `openingBalance` INTEGER NOT NULL, " +
    "`color` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `countsInTicket` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"

/** The v2 tables v3 changed, exactly as Room 2.7 created them from the v2 entities. */
private val V2_TABLES = listOf(
    "CREATE TABLE IF NOT EXISTS `envelope` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`color` INTEGER NOT NULL, `monthly` INTEGER NOT NULL, `kind` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`amount` INTEGER NOT NULL, `pocketId` INTEGER NOT NULL, `scheduleDay` INTEGER NOT NULL, `nextDue` INTEGER NOT NULL, " +
        "`envelopeId` INTEGER, `paidThisCycle` INTEGER NOT NULL, `kind` TEXT NOT NULL, `source` TEXT, `updatedAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `jar_movement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pot` TEXT NOT NULL, " +
        "`amount` INTEGER NOT NULL, `source` TEXT NOT NULL, `goalId` INTEGER, `dayDate` INTEGER, `entryId` INTEGER, " +
        "`at` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_jar_movement_pot` ON `jar_movement` (`pot`)",
    "CREATE INDEX IF NOT EXISTS `index_jar_movement_dayDate` ON `jar_movement` (`dayDate`)",
    "CREATE INDEX IF NOT EXISTS `index_jar_movement_entryId` ON `jar_movement` (`entryId`)",
)

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before @After fun clean() {
        context.deleteDatabase(name)
    }

    /** Room creates the current file; the tables v3 added or changed go back to their v2 shapes and user_version to 2. */
    private fun v2File(): SQLiteDatabase {
        Room.databaseBuilder(context, TazkaraDatabase::class.java, name).build().apply { openHelper.writableDatabase }.close()
        val v2 = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        listOf("season", "season_item", "envelope_move", "split_rule", "envelope", "recurring", "jar_movement")
            .forEach { v2.execSQL("DROP TABLE `$it`") }
        V2_TABLES.forEach(v2::execSQL)
        v2.version = 2
        return v2
    }

    /** Opening runs the migrations and then Room's schema validation over every table. */
    private fun open() =
        Room.databaseBuilder(context, TazkaraDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    /** v1 file database: only `pocket` differs between v1 and v2, so the v2 file gets the v1 pocket table and version 1. */
    @Test fun migration1to2KeepsData() = runTest {
        v2File().use { v1 ->
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

        val db = open()
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
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test fun migration2to3KeepsDataWithDefaults() = runTest {
        val due = LocalDate.of(2026, 11, 24)
        v2File().use { v2 ->
            v2.execSQL(
                "INSERT INTO pocket (id, name, kind, currency, openingBalance, color, archived, countsInTicket, updatedAt, lowBalanceAlert) " +
                    "VALUES (1, 'cash', 'CASH', 'EGP', 100000, 7, 0, 1, 5, 0)",
            )
            v2.execSQL("INSERT INTO envelope (id, name, color, monthly, kind, updatedAt) VALUES (1, 'food', 3, 200000, 'SPENDING', 5)")
            v2.execSQL(
                "INSERT INTO recurring (id, name, amount, pocketId, scheduleDay, nextDue, envelopeId, paidThisCycle, kind, source, updatedAt) " +
                    "VALUES (1, 'rent', 300000, 1, 24, ${due.toEpochDay()}, 1, 0, 'BILL', 'onboarding', 5)",
            )
            v2.execSQL("INSERT INTO jar_movement (id, pot, amount, source, at, updatedAt) VALUES (1, 'JAR', 5000, 'MANUAL', 9, 9)")
        }

        val db = open()
        try {
            assertEquals(
                "old recurring columns kept; new ones at their defaults",
                RecurringEntity(
                    id = 1, name = "rent", amount = 300_000, pocketId = 1, scheduleDay = 24, nextDue = due, envelopeId = 1,
                    kind = RecurringKind.BILL, source = "onboarding", updatedAt = 5,
                ),
                db.recurringDao().byId(1),
            )
            val r = checkNotNull(db.recurringDao().byId(1))
            assertEquals(
                "defaults spelled out",
                listOf(ScheduleType.MONTHLY, null, null, 1, null, null, false),
                listOf(r.scheduleType, r.trialEnd, r.yearlyPrice, r.sharedWith, r.answers, r.lastAskedAt, r.archived),
            )
            assertEquals(
                "envelope kept, not archived",
                listOf(EnvelopeEntity(id = 1, name = "food", color = 3, monthly = 200_000, kind = EnvelopeKind.SPENDING, updatedAt = 5)),
                db.envelopeDao().all().first(),
            )
            assertEquals("jar money kept", 5_000L, db.jarDao().totalNow(Pot.JAR))
            db.openHelper.readableDatabase.query("SELECT seasonId FROM jar_movement").use {
                it.moveToFirst()
                assertEquals("no season earmark", true, it.isNull(0))
            }
            assertEquals("the unpaid bill is still reserved", 300_000L, db.recurringDao().unpaidDueBefore(due.plusDays(1)))
            db.envelopeDao().insertMove(EnvelopeMoveEntity(cycleStart = due, fromId = 1, toId = 1, amount = 1, at = 0))
            db.envelopeDao().insertSplit(listOf(SplitRuleEntity(envelopeId = 1, kind = SplitKind.PERCENT, value = 10, sortOrder = 0)))
            assertEquals("new tables usable", 1 to 1, db.envelopeDao().moves().first().size to db.envelopeDao().splitRules().size)
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
