package com.masroufi.app.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.security.PayloadCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

class UnsupportedBackupException : Exception()

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val json: Json,
    private val clock: Clock,
    private val settings: SettingsRepository,
) {
    suspend fun snapshot(): BackupPayload = db.withTransaction {
        BackupPayload(
            exportedAt = clock.millis(),
            wallets = db.walletDao().all(),
            categories = db.categoryDao().all(),
            transactions = db.transactionDao().all(),
            budgets = db.budgetDao().all(),
            goals = db.goalDao().all(),
            goalEntries = db.goalDao().allEntries(),
            recurring = db.recurringDao().all(),
            debts = db.debtDao().all(),
            debtPayments = db.debtDao().allPayments(),
            gameyas = db.gameyaDao().all(),
            gameyaMembers = db.gameyaDao().allMembers(),
            gameyaPayments = db.gameyaDao().allPayments(),
            gameyaPayouts = db.gameyaDao().allPayouts(),
            splitRules = db.splitDao().all(),
            allocations = db.splitDao().allAllocations(),
        )
    }

    /** Writes [payload] in one transaction; with [replace] the existing data is cleared first, so a failure leaves it intact. */
    suspend fun write(payload: BackupPayload, replace: Boolean) = db.withTransaction {
        if (replace) {
            db.transactionDao().clear()
            db.walletDao().clear()
            db.categoryDao().clear()
            db.budgetDao().clear()
            db.goalDao().clearEntries()
            db.goalDao().clear()
            db.recurringDao().clear()
            db.debtDao().clearPayments()
            db.debtDao().clear()
            db.gameyaDao().clearPayments()
            db.gameyaDao().clearPayouts()
            db.gameyaDao().clearMembers()
            db.gameyaDao().clear()
            db.splitDao().clearAllocations()
            db.splitDao().clear()
        }
        db.walletDao().upsertAll(payload.wallets)
        db.categoryDao().upsertAll(payload.categories)
        db.transactionDao().upsertAll(payload.transactions)
        db.budgetDao().upsertAll(payload.budgets)
        db.goalDao().upsertAll(payload.goals)
        db.goalDao().upsertAllEntries(payload.goalEntries)
        db.recurringDao().upsertAll(payload.recurring)
        db.debtDao().upsertAll(payload.debts)
        db.debtDao().upsertAllPayments(payload.debtPayments)
        db.gameyaDao().upsertAll(payload.gameyas)
        db.gameyaDao().upsertMembers(payload.gameyaMembers)
        db.gameyaDao().upsertAllPayments(payload.gameyaPayments)
        db.gameyaDao().upsertAllPayouts(payload.gameyaPayouts)
        db.splitDao().upsertAll(payload.splitRules)
        db.splitDao().upsertAllAllocations(payload.allocations)
    }

    fun encode(payload: BackupPayload, password: CharArray): ByteArray =
        PayloadCrypto.encrypt(json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8), password)

    fun decode(bytes: ByteArray, password: CharArray): BackupPayload {
        val plain = PayloadCrypto.decrypt(bytes, password)
        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), plain.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw PayloadCrypto.NotABackupException()
        } catch (e: IllegalArgumentException) {
            throw PayloadCrypto.NotABackupException()
        }
        if (payload.formatVersion > BackupPayload.FORMAT_VERSION) throw UnsupportedBackupException()
        return payload
    }

    suspend fun exportTo(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val bytes = encode(snapshot(), password)
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open destination")
        stream.use { it.write(bytes) }
        settings.setLastBackup(clock.millis())
    }

    suspend fun readFrom(uri: Uri, password: CharArray): BackupPayload = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
        val bytes = stream.use { it.readBytes() }
        withContext(Dispatchers.Default) { decode(bytes, password) }
    }
}
