package com.masroufi.app.backup

import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.security.KeystoreCipher
import com.masroufi.app.security.PayloadCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncStatus { OK, NOT_CONFIGURED, AUTH_FAILED, WRONG_PASSPHRASE, NETWORK, INVALID_URL, REMOTE_CHANGED, SERVER_ERROR }

data class SyncReport(val status: SyncStatus, val conflicts: Int = 0)

/**
 * WebDAV sync: download the encrypted remote snapshot, merge it with local data, write the merge locally,
 * then upload. Local data is only modified after the remote file was fully downloaded and decrypted, and the
 * local write is a single database transaction, so a failed sync never loses local data.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val backup: BackupRepository,
    private val webDav: WebDavClient,
    private val settings: SettingsRepository,
    private val cipher: KeystoreCipher,
    private val clock: Clock,
) {
    private val lock = Mutex()

    suspend fun configure(url: String, username: String, password: String, passphrase: String): SyncStatus {
        try {
            webDav.fileUrl(url, FILE_NAME)
        } catch (e: WebDavException.InvalidUrl) {
            return SyncStatus.INVALID_URL
        }
        settings.saveSyncConfig(
            url = url.trim(),
            username = username.trim(),
            encryptedPassword = password.takeIf { it.isNotEmpty() }?.let(cipher::encrypt),
            encryptedPassphrase = cipher.encrypt(passphrase),
        )
        return SyncStatus.OK
    }

    suspend fun disconnect() = settings.clearSyncConfig()

    suspend fun sync(): SyncReport = lock.withLock {
        val config = settings.currentSync()
        if (!config.isConfigured) return@withLock SyncReport(SyncStatus.NOT_CONFIGURED)
        val report = try {
            val password = config.encryptedPassword?.let(cipher::decrypt).orEmpty()
            val passphrase = cipher.decrypt(config.encryptedPassphrase!!).toCharArray()
            val url = webDav.fileUrl(config.url, FILE_NAME)
            syncWithRetry(url, config.username, password, passphrase, config.lastSyncAt)
        } catch (e: WebDavException.Unauthorized) {
            SyncReport(SyncStatus.AUTH_FAILED)
        } catch (e: WebDavException.InvalidUrl) {
            SyncReport(SyncStatus.INVALID_URL)
        } catch (e: WebDavException.Http) {
            SyncReport(SyncStatus.SERVER_ERROR)
        } catch (e: PayloadCrypto.WrongPasswordException) {
            SyncReport(SyncStatus.WRONG_PASSPHRASE)
        } catch (e: PayloadCrypto.NotABackupException) {
            SyncReport(SyncStatus.WRONG_PASSPHRASE)
        } catch (e: IOException) {
            SyncReport(SyncStatus.NETWORK)
        }
        settings.recordSync(
            at = if (report.status == SyncStatus.OK) clock.millis() else null,
            status = report.status.name,
            conflicts = report.conflicts,
            etag = null,
        )
        report
    }

    private suspend fun syncWithRetry(url: okhttp3.HttpUrl, user: String, password: String, passphrase: CharArray, lastSync: Long?): SyncReport {
        repeat(MAX_ATTEMPTS) {
            try {
                return syncOnce(url, user, password, passphrase, lastSync)
            } catch (e: WebDavException.PreconditionFailed) {
                // Another device uploaded between our download and upload: merge again with its version.
            }
        }
        return SyncReport(SyncStatus.REMOTE_CHANGED)
    }

    private suspend fun syncOnce(url: okhttp3.HttpUrl, user: String, password: String, passphrase: CharArray, lastSync: Long?): SyncReport {
        val remote = webDav.get(url, user, password)
        val local = backup.snapshot()
        val now = clock.millis()
        val remotePayload = remote?.let { withContext(Dispatchers.Default) { backup.decode(it.bytes, passphrase) } }
        val outcome = if (remotePayload == null) {
            MergeOutcome(local, 0, changedLocally = false, changedRemotely = true)
        } else {
            SyncMerger.merge(local, remotePayload, lastSync, now)
        }
        if (outcome.changedLocally) backup.write(outcome.payload, replace = false)
        if (outcome.changedRemotely || remote == null) {
            val bytes = withContext(Dispatchers.Default) { backup.encode(outcome.payload.copy(exportedAt = now), passphrase) }
            webDav.put(url, user, password, bytes, expectedEtag = remote?.etag, remoteExists = remote != null)
        }
        return SyncReport(SyncStatus.OK, outcome.conflicts)
    }

    companion object {
        const val FILE_NAME = "masroufi-sync.msrf"
        private const val MAX_ATTEMPTS = 3
    }
}
