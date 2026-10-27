package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.startMillis
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.domain.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.recurringDao()
    private val processLock = Mutex()

    fun observeAll(): Flow<List<RecurringRule>> = dao.observeAll()
    suspend fun get(id: String) = dao.get(id)

    suspend fun save(rule: RecurringRule) {
        ensure(rule.amountMinor > 0, FinanceError.AMOUNT_REQUIRED)
        val wallet = db.walletDao().get(rule.walletId)
        ensure(wallet != null, FinanceError.WALLET_REQUIRED)
        if (rule.type == TransactionType.TRANSFER) {
            val target = rule.toWalletId?.let { db.walletDao().get(it) }
            ensure(target != null, FinanceError.WALLET_REQUIRED)
            ensure(target!!.id != wallet!!.id, FinanceError.SAME_WALLET_TRANSFER)
            // A recurring transfer has no per-occurrence exchange rate, so it must stay in one currency.
            ensure(target.currency == wallet.currency, FinanceError.TARGET_AMOUNT_REQUIRED)
        }
        val today = LocalDate.now(clock)
        val start = LocalDate.ofEpochDay(rule.startEpochDay)
        val end = rule.endEpochDay?.let(LocalDate::ofEpochDay)
        val next = Recurrence.nextAfter(start, rule.frequency, rule.interval, end, today.minusDays(1))
        dao.upsert(rule.copy(currency = wallet!!.currency, note = rule.note.trim(), nextEpochDay = next?.toEpochDay(), updatedAt = clock.millis()))
    }

    /** Pausing skips occurrences that fall inside the pause instead of back-filling them on resume. */
    suspend fun setActive(id: String, active: Boolean) {
        val rule = dao.get(id) ?: return
        val yesterday = LocalDate.now(clock).minusDays(1).toEpochDay()
        val last = if (active) maxOf(rule.lastGeneratedEpochDay ?: Long.MIN_VALUE, yesterday) else rule.lastGeneratedEpochDay
        dao.upsert(rule.copy(active = active, lastGeneratedEpochDay = last, updatedAt = clock.millis()))
    }

    suspend fun delete(id: String) {
        val rule = dao.get(id) ?: return
        dao.upsert(rule.copy(deleted = true, active = false, updatedAt = clock.millis()))
    }

    /**
     * Materialises every due occurrence up to today. Safe to run any number of times: each occurrence has a
     * deterministic id and a unique (recurringId, occurrenceKey) index, so a repeat run inserts nothing.
     */
    suspend fun processDue(): Int = processLock.withLock {
        val today = LocalDate.now(clock)
        var created = 0
        for (rule in dao.active()) {
            db.withTransaction {
                val start = LocalDate.ofEpochDay(rule.startEpochDay)
                val end = rule.endEpochDay?.let(LocalDate::ofEpochDay)
                val after = rule.lastGeneratedEpochDay?.let(LocalDate::ofEpochDay)
                val due = Recurrence.due(start, rule.frequency, rule.interval, end, after, today)
                val wallet = db.walletDao().get(rule.walletId)
                val target = rule.toWalletId?.let { db.walletDao().get(it) }
                val walletsOk = wallet != null && !wallet.deleted &&
                    (rule.type != TransactionType.TRANSFER || (target != null && !target.deleted))
                if (walletsOk) {
                    val now = clock.millis()
                    for (date in due) {
                        val inserted = db.transactionDao().insertIgnore(
                            Transaction(
                                id = "${rule.id}:${date.toEpochDay()}",
                                type = rule.type,
                                amountMinor = rule.amountMinor,
                                currency = wallet!!.currency,
                                walletId = rule.walletId,
                                toWalletId = if (rule.type == TransactionType.TRANSFER) rule.toWalletId else null,
                                categoryId = if (rule.type == TransactionType.TRANSFER) null else rule.categoryId,
                                note = rule.note,
                                occurredAt = date.startMillis(clock.zone) + NOON_OFFSET_MILLIS,
                                recurringId = rule.id,
                                occurrenceKey = date.toEpochDay(),
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        if (inserted != -1L) created++
                    }
                }
                val last = due.lastOrNull()?.toEpochDay() ?: rule.lastGeneratedEpochDay
                val next = Recurrence.nextAfter(start, rule.frequency, rule.interval, end, today)
                dao.upsert(
                    rule.copy(
                        lastGeneratedEpochDay = if (walletsOk) last else rule.lastGeneratedEpochDay,
                        nextEpochDay = next?.toEpochDay(),
                        active = rule.active && next != null,
                    ),
                )
            }
        }
        created
    }

    private companion object {
        const val NOON_OFFSET_MILLIS = 12 * 60 * 60 * 1000L
    }
}
