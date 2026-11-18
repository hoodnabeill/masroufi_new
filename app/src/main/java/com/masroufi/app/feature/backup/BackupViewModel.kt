package com.masroufi.app.feature.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.masroufi.app.data.backup.BackupDamaged
import com.masroufi.app.data.backup.BackupFormat
import com.masroufi.app.data.backup.BackupSchedule
import com.masroufi.app.data.backup.BackupService
import com.masroufi.app.data.backup.RestoreResult
import com.masroufi.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject

enum class BackupSheet { SetPassword, Restore, Export }

/** Plain one-line results; shown in the open sheet, else under the status hero. */
enum class BackupMessage { SaveFailed, ExportSaved, NotABackup, WrongPassword, Damaged, TooNew, PasswordShort, PasswordMismatch }

/** A system picker the screen should open (once): [name] is the suggested file name for the two "create" pickers. */
data class BackupLaunch(val kind: Kind, val name: String = "") {
    enum class Kind { Create, Export, Open, Tree }
}

/** The facts on the restore sheet: the backup's day and how many tickets it holds. */
data class RestoreInfo(val day: LocalDate, val tickets: Int)

data class BackupUi(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.of(2000, 1, 1),
    /** The last good write; null = never backed up. */
    val lastDay: LocalDate? = null,
    val lastTime: LocalTime? = null,
    val tickets: Int = 0,
    val bytes: Long = 0,
    /** The last automatic run failed (quiet line; cleared by the next good write). */
    val failed: Boolean = false,
    val auto: Boolean = false,
    val folder: String? = null,
    val hasPassword: Boolean = false,
    /** 0..1 while a backup is being written; null otherwise. */
    val progress: Float? = null,
    val busy: Boolean = false,
    val sheet: BackupSheet? = null,
    val restore: RestoreInfo? = null,
    val message: BackupMessage? = null,
    val launch: BackupLaunch? = null,
    /** The restore replaced the files: the screen relaunches the app. */
    val restarted: Boolean = false,
)

/** What the screen can do; the view model implements it and the screen tests use a fake. */
interface BackupActions {
    fun backupNow()
    fun restoreTapped()
    fun setAuto(on: Boolean)
    fun pickFolder()
    fun changePassword()
    fun exportTapped()
    fun closeSheet()
    fun submitPassword(password: String, repeat: String)
    fun confirmRestore(password: String)
    fun confirmExport()
    fun launched()
    fun created(uri: Uri?)
    fun exported(uri: Uri?)
    fun opened(uri: Uri?)
    fun treePicked(uri: Uri?)
}

private enum class After { Backup, Auto }

private data class Local(
    val progress: Float? = null,
    val busy: Boolean = false,
    val sheet: BackupSheet? = null,
    val restore: RestoreInfo? = null,
    val message: BackupMessage? = null,
    val launch: BackupLaunch? = null,
    val restarted: Boolean = false,
    val after: After? = null,
)

private const val TAG = "Backup"
private const val MIN_PASSWORD = 6

/** النسخة الاحتياطية: backup now, restore, the weekly automatic backup, the Excel export. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: BackupService,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel(), BackupActions {
    private val local = MutableStateFlow(Local())
    private val hasPassword = MutableStateFlow(false)

    /** The file the restore sheet is about, kept off the screen state. */
    private var restoreBytes: ByteArray? = null

    val state: StateFlow<BackupUi> = combine(settings.settings, local, hasPassword) { s, l, pw ->
        val last = s.backupLastAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone) }
        BackupUi(
            loading = false, today = LocalDate.now(clock),
            lastDay = last?.toLocalDate(), lastTime = last?.toLocalTime(),
            tickets = s.backupLastTickets ?: 0, bytes = s.backupLastBytes ?: 0, failed = s.backupFailedAt != null,
            auto = s.backupAuto, folder = s.backupFolder?.let(::folderLabel), hasPassword = pw || s.backupKey != null,
            progress = l.progress, busy = l.busy, sheet = l.sheet, restore = l.restore, message = l.message,
            launch = l.launch, restarted = l.restarted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUi())

    override fun backupNow() = act {
        if (service.hasKey()) open(BackupLaunch(BackupLaunch.Kind.Create, service.fileName()))
        else local.update { it.copy(sheet = BackupSheet.SetPassword, after = After.Backup, message = null) }
    }

    override fun created(uri: Uri?) {
        if (uri == null) return
        act {
            local.update { it.copy(progress = 0f, message = null) }
            try {
                val file = service.createBackup { p -> local.update { it.copy(progress = p) } }
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("cannot open")).use { it.write(file.bytes) }
                }
                service.record(file)
                local.update { it.copy(progress = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "backup failed", e)
                local.update { it.copy(progress = null, message = BackupMessage.SaveFailed) }
            }
        }
    }

    override fun restoreTapped() = open(BackupLaunch(BackupLaunch.Kind.Open))

    override fun opened(uri: Uri?) {
        if (uri == null) return
        act {
            try {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: throw java.io.IOException("cannot read")
                val header = BackupFormat.readHeader(bytes)
                restoreBytes = bytes
                val day = Instant.ofEpochMilli(header.createdAt).atZone(clock.zone).toLocalDate()
                local.update { it.copy(sheet = BackupSheet.Restore, restore = RestoreInfo(day, header.tickets), message = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupDamaged) {
                local.update { it.copy(message = BackupMessage.NotABackup) }
            } catch (e: java.io.IOException) {
                local.update { it.copy(message = BackupMessage.NotABackup) }
            }
        }
    }

    override fun confirmRestore(password: String) {
        val bytes = restoreBytes ?: return
        if (local.value.busy) return
        act {
            local.update { it.copy(busy = true, message = null) }
            try {
                when (service.restore(bytes, password)) {
                    RestoreResult.Done -> local.update { it.copy(busy = false, sheet = null, restarted = true) }
                    RestoreResult.WrongPassword -> local.update { it.copy(busy = false, message = BackupMessage.WrongPassword) }
                    RestoreResult.Damaged -> local.update { it.copy(busy = false, message = BackupMessage.Damaged) }
                    RestoreResult.TooNew -> local.update { it.copy(busy = false, message = BackupMessage.TooNew) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "restore failed", e)
                local.update { it.copy(busy = false, message = BackupMessage.Damaged) }
            }
        }
    }

    override fun setAuto(on: Boolean) = act {
        if (!on) {
            settings.setBackupAuto(false)
            schedule(false)
        } else {
            enableAuto()
        }
    }

    private suspend fun enableAuto() {
        when {
            !service.hasKey() -> local.update { it.copy(sheet = BackupSheet.SetPassword, after = After.Auto, message = null) }
            settings.current().backupFolder == null -> {
                local.update { it.copy(after = After.Auto) }
                open(BackupLaunch(BackupLaunch.Kind.Tree))
            }
            else -> {
                settings.setBackupAuto(true)
                schedule(true)
            }
        }
    }

    override fun pickFolder() = open(BackupLaunch(BackupLaunch.Kind.Tree))

    override fun treePicked(uri: Uri?) {
        if (uri == null) {
            local.update { it.copy(after = null) }
            return
        }
        act {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                settings.setBackupFolder(uri.toString())
                if (local.value.after == After.Auto) enableAuto()
            } catch (e: SecurityException) {
                Log.e(TAG, "folder permission", e)
                local.update { it.copy(message = BackupMessage.SaveFailed) }
            } finally {
                local.update { it.copy(after = null) }
            }
        }
    }

    override fun changePassword() = local.update { it.copy(sheet = BackupSheet.SetPassword, after = null, message = null) }

    override fun submitPassword(password: String, repeat: String) {
        when {
            password.length < MIN_PASSWORD -> local.update { it.copy(message = BackupMessage.PasswordShort) }
            password != repeat -> local.update { it.copy(message = BackupMessage.PasswordMismatch) }
            else -> act {
                local.update { it.copy(busy = true, message = null) }
                try {
                    service.setPassword(password)
                    hasPassword.value = true
                    val next = local.value.after
                    local.update { it.copy(busy = false, sheet = null, after = null) }
                    when (next) {
                        After.Backup -> open(BackupLaunch(BackupLaunch.Kind.Create, service.fileName()))
                        After.Auto -> enableAuto()
                        null -> {}
                    }
                } finally {
                    local.update { it.copy(busy = false) }
                }
            }
        }
    }

    override fun exportTapped() = local.update { it.copy(sheet = BackupSheet.Export, message = null) }

    override fun confirmExport() {
        local.update { it.copy(sheet = null) }
        open(BackupLaunch(BackupLaunch.Kind.Export, "masroufi-${LocalDate.now(clock)}.csv"))
    }

    override fun exported(uri: Uri?) {
        if (uri == null) return
        act {
            try {
                val bytes = service.exportCsv()
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("cannot open")).use { it.write(bytes) }
                }
                local.update { it.copy(message = BackupMessage.ExportSaved) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "export failed", e)
                local.update { it.copy(message = BackupMessage.SaveFailed) }
            }
        }
    }

    override fun closeSheet() {
        restoreBytes = null
        local.update { it.copy(sheet = null, restore = null, message = null, after = null, busy = false) }
    }

    override fun launched() = local.update { it.copy(launch = null) }

    private fun open(launch: BackupLaunch) = local.update { it.copy(launch = launch) }

    private fun schedule(on: Boolean) = BackupSchedule.setWeekly(WorkManager.getInstance(context), ZonedDateTime.now(clock), on)

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "backup action failed", e)
            }
        }
    }
}

/** "Documents/Backups" out of a tree uri's id ("primary:Documents/Backups"); the uri text when it has no such id. */
internal fun folderLabel(uri: String): String =
    runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfter(':').ifBlank { null } }.getOrNull() ?: uri
