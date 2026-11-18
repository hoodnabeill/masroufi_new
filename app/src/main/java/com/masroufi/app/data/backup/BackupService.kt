package com.masroufi.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.masroufi.app.R
import com.masroufi.app.core.SecretCipher
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.EntryKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a restore did. Anything but [Done] left every file as it was. */
enum class RestoreResult { Done, WrongPassword, Damaged, TooNew }

/** A finished backup file in memory, with its header. */
class BackupFile(val bytes: ByteArray, val header: BackupHeader)

/** No backup password was set on this phone yet. */
class NoBackupKey : IllegalStateException("no backup password set")

/**
 * Makes and opens backups (decision 4). The password is never stored: setting it keeps only the stretched key, sealed by
 * [cipher] (the Keystore), so the weekly backup can write without asking.
 */
class BackupService(
    private val context: Context,
    private val db: TazkaraDatabase,
    /** The settings DataStore's file; it goes into the backup and is replaced on restore. */
    private val settingsFile: File,
    private val settings: SettingsStore,
    private val cipher: SecretCipher,
    private val clock: Clock,
    private val appVersion: String,
    private val iterations: Int = BackupFormat.ITERATIONS,
) {
    suspend fun hasKey(): Boolean = loadKey() != null

    /** Stretches [password] with a fresh salt and keeps only the sealed result; older backups keep their own password. */
    suspend fun setPassword(password: String) = withContext(Dispatchers.IO) {
        settings.setBackupKey(seal(BackupFormat.newKey(password, iterations)))
    }

    /** The name a backup written now gets: the newest sorts last, which the keep-4 rule relies on. */
    fun fileName(): String =
        BackupFormat.FILE_PREFIX + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.US).format(ZonedDateTime.now(clock)) + BackupFormat.FILE_SUFFIX

    /** A consistent copy of the database and the settings file, encrypted under the stored key; [onProgress] gets 0..1. */
    suspend fun createBackup(onProgress: (Float) -> Unit = {}): BackupFile = withContext(Dispatchers.IO) {
        val key = loadKey() ?: throw NoBackupKey()
        onProgress(0.1f)
        val (dbBytes, walBytes) = snapshotDatabase()
        onProgress(0.4f)
        val tickets = db.dayTicketDao().allNow().size
        val entries = buildList {
            add(BackupFormat.DB_ENTRY to dbBytes)
            if (walBytes != null) add(BackupFormat.WAL_ENTRY to walBytes)
            if (settingsFile.exists()) add(BackupFormat.SETTINGS_ENTRY to settingsFile.readBytes())
        }
        val zip = BackupFormat.zip(entries)
        onProgress(0.6f)
        val bytes = BackupFormat.encrypt(zip, key, clock.millis(), tickets, appVersion)
        onProgress(0.9f)
        BackupFile(bytes, BackupFormat.readHeader(bytes))
    }

    /** Remembers a written backup for the screen and the Settings row. */
    suspend fun record(file: BackupFile) = settings.recordBackup(file.header.createdAt, file.header.tickets, file.bytes.size.toLong())

    /**
     * The weekly run: writes a backup into the chosen folder, keeps the newest 4 and notes success or failure. True when
     * there was nothing to do (off, or a wipe turned it off) or it worked.
     */
    suspend fun runAuto(): Boolean {
        val s = settings.current()
        val folder = s.backupFolder
        if (!s.backupAuto || folder == null) return true
        return try {
            val file = createBackup()
            withContext(Dispatchers.IO) { writeToFolder(Uri.parse(folder), fileName(), file.bytes) }
            record(file)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            settings.recordBackupFailed(clock.millis())
            false
        }
    }

    /**
     * Opens [file] with [password], checks the result in a temp folder, and only then closes the database and swaps the
     * files in. The caller relaunches the app afterwards (nothing in memory is trusted). Older schemas migrate when
     * Room opens the restored database.
     */
    suspend fun restore(file: ByteArray, password: String): RestoreResult = withContext(Dispatchers.IO) {
        val parts = try {
            BackupFormat.unzip(BackupFormat.decrypt(file, password))
        } catch (e: BackupWrongKey) {
            return@withContext RestoreResult.WrongPassword
        } catch (e: BackupDamaged) {
            return@withContext RestoreResult.Damaged
        }
        val dbBytes = parts[BackupFormat.DB_ENTRY] ?: return@withContext RestoreResult.Damaged
        val target = File(db.openHelper.writableDatabase.path ?: return@withContext RestoreResult.Damaged)
        val temp = File(context.cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            val tempDb = File(temp, BackupFormat.DB_ENTRY).apply { writeBytes(dbBytes) }
            val walBytes = parts[BackupFormat.WAL_ENTRY]
            if (walBytes != null) File(temp, BackupFormat.DB_ENTRY + "-wal").writeBytes(walBytes)
            val version = soundVersion(tempDb) ?: return@withContext RestoreResult.Damaged
            if (version > db.openHelper.writableDatabase.version) return@withContext RestoreResult.TooNew
            // Staged next to the real files first: a full disk fails here, before anything is replaced.
            val stagedDb = File(target.path + ".restore")
            tempDb.copyTo(stagedDb, overwrite = true)
            val stagedWal = walBytes?.let { File(target.path + "-wal.restore").also { f -> f.writeBytes(it) } }
            val stagedSettings = parts[BackupFormat.SETTINGS_ENTRY]?.let { bytes ->
                File(settingsFile.path + ".restore").also { settingsFile.parentFile?.mkdirs(); it.writeBytes(bytes) }
            }
            db.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
            Files.move(stagedDb.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            stagedWal?.let { Files.move(it.toPath(), File(target.path + "-wal").toPath(), StandardCopyOption.REPLACE_EXISTING) }
            stagedSettings?.let { Files.move(it.toPath(), settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            RestoreResult.Done
        } finally {
            temp.deleteRecursively()
        }
    }

    /** Every entry, oldest first, as a CSV (UTF-8 with a byte-order mark so Excel reads Arabic). Not encrypted. */
    suspend fun exportCsv(): ByteArray = withContext(Dispatchers.IO) {
        val rows = db.entryDao().allRows().first().asReversed()
        val zone = clock.zone
        val out = StringBuilder()
        out.append(
            listOf(R.string.backup_csv_date, R.string.backup_csv_time, R.string.backup_csv_type, R.string.backup_csv_amount,
                R.string.backup_csv_category, R.string.backup_csv_pocket, R.string.backup_csv_note, R.string.backup_csv_fee)
                .joinToString(",") { csv(context.getString(it)) },
        ).append("\r\n")
        rows.forEach { r ->
            val at = Instant.ofEpochMilli(r.happenedAt).atZone(zone)
            out.append(
                listOf(
                    at.toLocalDate().toString(), at.toLocalTime().withSecond(0).withNano(0).toString(),
                    csv(context.getString(kindLabel(r.kind))), pounds(r.amount), csv(r.categoryName.orEmpty()),
                    csv(pocketText(r)), csv(r.note.orEmpty()), pounds(r.fee),
                ).joinToString(","),
            ).append("\r\n")
        }
        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun kindLabel(k: EntryKind) = when (k) {
        EntryKind.SPENT -> R.string.backup_csv_spent
        EntryKind.RECEIVED -> R.string.backup_csv_received
        EntryKind.MOVED -> R.string.backup_csv_moved
        EntryKind.ADJUST -> R.string.backup_csv_adjust
        EntryKind.DEBT -> R.string.backup_csv_debt
    }

    private fun pocketText(r: EntryRow) = if (r.toPocketName != null) "${r.pocketName} > ${r.toPocketName}" else r.pocketName

    /** Pounds with two decimals and a dot, whatever the language. */
    private fun pounds(piastres: Long): String {
        val abs = Math.abs(piastres)
        return String.format(Locale.US, "%s%d.%02d", if (piastres < 0) "-" else "", abs / 100, abs % 100)
    }

    /** Quotes a text field; a leading = + - @ gets a quote mark so a spreadsheet never runs it as a formula. */
    private fun csv(text: String): String {
        val safe = if (text.isNotEmpty() && text[0] in "=+-@") "'$text" else text
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /**
     * The database file as it stands, copied while holding the write lock so nothing changes mid-copy. A checkpoint first
     * folds the WAL in; if a reader kept part of it back, the WAL file is copied too (restore replays it).
     */
    private fun snapshotDatabase(): Pair<ByteArray, ByteArray?> {
        val sdb = db.openHelper.writableDatabase
        sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val path = sdb.path ?: throw IOException("in-memory database")
        sdb.beginTransaction()
        try {
            val wal = File("$path-wal").takeIf { it.exists() && it.length() > 0 }
            return File(path).readBytes() to wal?.readBytes()
        } finally {
            sdb.endTransaction()
        }
    }

    /** The schema version of a readable, uncorrupt SQLite file; null when it is not one. */
    private fun soundVersion(file: File): Int? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            val ok = d.rawQuery("PRAGMA quick_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
            if (ok && d.version >= 1) d.version else null
        }
    } catch (e: SQLiteException) {
        null
    }

    private fun writeToFolder(tree: Uri, name: String, bytes: ByteArray) {
        val cr = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val doc = DocumentsContract.createDocument(cr, parent, "application/octet-stream", name) ?: throw IOException("cannot create $name")
        (cr.openOutputStream(doc, "wt") ?: throw IOException("cannot open $name")).use { it.write(bytes) }
        val found = mutableListOf<Pair<String, String>>()
        cr.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c -> while (c.moveToNext()) found += c.getString(0) to c.getString(1) }
        val gone = BackupFormat.toDelete(found.map { it.second }).toSet()
        found.filter { it.second in gone }.forEach { (id, _) ->
            // A file that cannot be deleted stays; the next run tries again.
            runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    private suspend fun loadKey(): BackupKey? {
        val sealed = settings.current().backupKey ?: return null
        return try {
            unseal(sealed)
        } catch (e: GeneralSecurityException) {
            null // the Keystore key is gone (a new phone): the password is asked for again
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun seal(key: BackupKey): String {
        val plain = ByteArrayOutputStream()
        DataOutputStream(plain).run {
            writeInt(key.iterations)
            writeByte(key.salt.size)
            write(key.salt)
            write(key.key)
        }
        return Base64.encodeToString(cipher.encrypt(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun unseal(text: String): BackupKey {
        val input = DataInputStream(ByteArrayInputStream(cipher.decrypt(Base64.decode(text, Base64.NO_WRAP))))
        val rounds = input.readInt()
        val salt = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        val key = ByteArray(32).also { input.readFully(it) }
        return BackupKey(salt, rounds, key)
    }
}
