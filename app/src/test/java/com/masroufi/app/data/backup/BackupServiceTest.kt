package com.masroufi.app.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.PlainCipher
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** A real database file under Robolectric, a DataStore file in a temp folder and a low key-stretching count. */
@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = TestClock(Instant.parse("2026-11-18T19:12:00Z"))
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: TazkaraDatabase
    private val opened = mutableListOf<TazkaraDatabase>()
    private lateinit var settingsFile: File

    @Before fun open() {
        context.deleteDatabase(TazkaraDatabase.FILE_NAME)
        db = openDb()
        settingsFile = File(tmp.root, "s.preferences_pb")
    }

    @After fun close() = opened.forEach { runCatching { it.close() } }

    private fun openDb(): TazkaraDatabase =
        Room.databaseBuilder(context, TazkaraDatabase::class.java, TazkaraDatabase.FILE_NAME).allowMainThreadQueries().build().also { opened += it }

    private fun TestScope.settings() =
        SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { settingsFile }, clock)

    private fun service(settings: SettingsStore) = BackupService(context, db, settingsFile, settings, PlainCipher(), clock, "9.9.9", iterations = 1_000)

    private suspend fun addEntry(note: String, amount: Long = 2_500) {
        val pocket = db.pocketDao().all().first().firstOrNull()?.id
            ?: db.pocketDao().insert(PocketEntity(name = "كاش", kind = PocketKind.CASH, color = 0))
        val category = db.categoryDao().allNow().firstOrNull()?.id
            ?: db.categoryDao().insert(CategoryEntity(name = "مواصلات", icon = "transport", color = 0))
        db.entryDao().insert(
            EntryEntity(kind = EntryKind.SPENT, amount = amount, pocketId = pocket, categoryId = category, note = note, happenedAt = clock.millis(), loggedAt = clock.millis()),
        )
    }

    private suspend fun notes(d: TazkaraDatabase) = d.entryDao().allRows().first().mapNotNull { it.note }.sorted()

    @Test fun aBackupWithNoPasswordSetIsRefused() = runTest {
        val s = service(settings())
        assertFalse(s.hasKey())
        try {
            s.createBackup()
            fail("made a backup without a password")
        } catch (e: NoBackupKey) {
            // expected
        }
    }

    @Test fun theStoredKeyOpensWhatThePasswordOpens() = runTest {
        val store = settings()
        val s = service(store)
        s.setPassword("pw-123456")
        assertTrue(s.hasKey())
        addEntry("تاكسي")
        val file = s.createBackup()
        // The password alone opens it, and the header says what it holds.
        assertTrue(BackupFormat.unzip(BackupFormat.decrypt(file.bytes, "pw-123456")).containsKey(BackupFormat.DB_ENTRY))
        assertEquals(clock.millis(), file.header.createdAt)
        assertEquals("9.9.9", file.header.appVersion)
        assertNotNull(store.current().backupKey)
    }

    @Test fun restoreReplacesTheData() = runTest {
        val store = settings()
        val s = service(store)
        s.setPassword("pw-123456")
        store.setProfileName("يوسف")
        addEntry("قبل النسخة")
        val savedSettings = settingsFile.readBytes()
        val file = s.createBackup()
        addEntry("بعد النسخة")
        store.setProfileName("حد تاني")
        assertEquals(listOf("بعد النسخة", "قبل النسخة"), notes(db))

        assertEquals(RestoreResult.Done, s.restore(file.bytes, "pw-123456"))

        val reopened = openDb()
        assertEquals(listOf("قبل النسخة"), notes(reopened))
        assertArrayEquals(savedSettings, settingsFile.readBytes())
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("restore-") })
    }

    @Test fun aWrongPasswordLeavesEverythingAsItWas() = runTest {
        val store = settings()
        val s = service(store)
        s.setPassword("pw-123456")
        addEntry("قبل النسخة")
        val file = s.createBackup()
        addEntry("بعد النسخة")
        val settingsBefore = settingsFile.readBytes()

        assertEquals(RestoreResult.WrongPassword, s.restore(file.bytes, "not-it"))

        assertEquals(listOf("بعد النسخة", "قبل النسخة"), notes(db)) // the open database was not closed or replaced
        assertArrayEquals(settingsBefore, settingsFile.readBytes())
    }

    @Test fun aDamagedFileLeavesEverythingAsItWas() = runTest {
        val s = service(settings())
        s.setPassword("pw-123456")
        addEntry("باقي")
        assertEquals(RestoreResult.Damaged, s.restore("this is not a backup".toByteArray(), "pw-123456"))
        val file = s.createBackup().bytes
        file[file.size - 5] = (file[file.size - 5].toInt() xor 1).toByte()
        assertEquals(RestoreResult.WrongPassword, s.restore(file, "pw-123456")) // a changed byte fails the tag
        assertEquals(listOf("باقي"), notes(db))
    }

    @Test fun anEncryptedZipWithoutADatabaseIsDamaged() = runTest {
        val s = service(settings())
        val key = BackupFormat.newKey("pw-123456", 1_000)
        val file = BackupFormat.encrypt(BackupFormat.zip(listOf("other.txt" to byteArrayOf(1))), key, 1L, 0, "1")
        assertEquals(RestoreResult.Damaged, s.restore(file, "pw-123456"))
    }

    @Test fun aFileThatIsNotADatabaseIsDamaged() = runTest {
        val s = service(settings())
        val key = BackupFormat.newKey("pw-123456", 1_000)
        val file = BackupFormat.encrypt(BackupFormat.zip(listOf(BackupFormat.DB_ENTRY to "plain text".toByteArray())), key, 1L, 0, "1")
        addEntry("باقي")
        assertEquals(RestoreResult.Damaged, s.restore(file, "pw-123456"))
        assertEquals(listOf("باقي"), notes(db))
    }

    @Test fun theCsvStartsWithTheBomAndKeepsArabicIntact() = runTest {
        val s = service(settings())
        addEntry("تاكسي, للشغل")
        addEntry("=cmd", amount = 15_000)
        val bytes = s.exportCsv()
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        val lines = text.trim().split("\r\n")
        assertEquals("Date,Time,Type,Amount in pounds,Category,Pocket,Note,Fee", lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("2026-11-18,21:12,")) // Cairo time
        assertTrue(lines[1].endsWith("Spent,25.00,مواصلات,كاش,\"تاكسي, للشغل\",0.00"))
        assertTrue(lines[2].contains(",150.00,"))
        assertTrue(lines[2].contains("'=cmd")) // a note starting with = gets a mark so Excel never runs it
    }

    @Test fun aFailedAutomaticRunIsNotedAndAGoodWriteClearsIt() = runTest {
        val store = settings()
        val s = service(store)
        s.setPassword("pw-123456")
        // off: nothing to do and nothing noted
        assertTrue(s.runAuto())
        assertNull(store.current().backupFailedAt)
        // on, with a folder no provider serves: the write fails, the failure is noted and the last good backup stays
        store.recordBackup(1_000L, 5, 2_000L)
        store.setBackupAuto(true)
        store.setBackupFolder("content://com.example.nowhere/tree/primary%3ABackups")
        assertFalse(s.runAuto())
        val failed = store.current()
        assertEquals(clock.millis(), failed.backupFailedAt)
        assertEquals(1_000L, failed.backupLastAt)
        store.recordBackup(2_000L, 6, 3_000L)
        assertNull(store.current().backupFailedAt)
    }

    @Test fun theFileNameCarriesTheTimeSoTheNewestSortsLast() = runTest {
        val s = service(settings())
        assertEquals("masroufi-2026-11-18-2112.masroufi", s.fileName())
    }
}
