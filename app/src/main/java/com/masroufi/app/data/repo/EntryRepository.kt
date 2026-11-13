package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.payCycleFor
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.roundUp
import com.masroufi.app.entitlements.Entitlements
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

const val NOTE_MAX_CHARS = 120

data class NewEntry(
    val kind: EntryKind,
    val amount: Long,
    val pocketId: Long,
    val toPocketId: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val happenedAt: Long,
    val recurringId: Long? = null,
    val fee: Long = 0,
) {
    init {
        require(amount > 0) { "amount must be > 0" }
        require(fee >= 0) { "fee must be >= 0" }
        require(kind != EntryKind.MOVED || (toPocketId != null && toPocketId != pocketId)) {
            "a move needs a different target pocket"
        }
        require(kind == EntryKind.MOVED || toPocketId == null) { "only a move has a target pocket" }
        require(note == null || note.length <= NOTE_MAX_CHARS) { "note must be at most $NOTE_MAX_CHARS chars" }
    }
}

@Singleton
class EntryRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val entryDao: EntryDao,
    private val pocketDao: PocketDao,
    private val jarDao: JarDao,
    private val recurringDao: RecurringDao,
    private val tickets: TicketRepository,
    private val settings: SettingsStore,
    private val entitlements: Entitlements,
    private val clock: Clock,
) {
    suspend fun log(entry: NewEntry): Long {
        requireNotFuture(entry)
        val step = activeStep()
        return db.withTransaction {
            checkInputs(entry)
            val now = clock.millis()
            val rounded = if (step != null && qualifies(entry)) roundUp(entry.amount, step) else 0L
            val fresh = EntryEntity(
                kind = entry.kind, amount = entry.amount, pocketId = entry.pocketId, happenedAt = entry.happenedAt, loggedAt = now,
            )
            val id = entryDao.insert(fresh.with(entry, rounded, now))
            if (rounded > 0) jarDao.insert(roundUpMovement(id, rounded, now))
            entry.recurringId?.let { recurringDao.setPaid(it, true) }
            tickets.recalcDay(clock.dayOf(entry.happenedAt))
            id
        }
    }

    suspend fun edit(id: Long, entry: NewEntry) {
        requireNotFuture(entry)
        val step = activeStep()
        db.withTransaction<Unit> {
            val old = checkNotNull(entryDao.byId(id)) { "no entry $id" }
            checkInputs(entry)
            val now = clock.millis()
            // Turning round-ups off keeps old ones (11-DATA), as long as the entry still punches the ticket.
            val rounded = when {
                !qualifies(entry) -> 0L
                step != null -> roundUp(entry.amount, step)
                else -> old.roundUp
            }
            entryDao.update(old.with(entry, rounded, now))
            val movement = jarDao.forEntry(id).firstOrNull { it.source == JarSource.ROUND_UP }
            jarDao.sync(movement, rounded, now) { roundUpMovement(id, rounded, now) }
            releaseBill(old)
            entry.recurringId?.let { recurringDao.setPaid(it, true) }
            val oldDay = clock.dayOf(old.happenedAt)
            val newDay = clock.dayOf(entry.happenedAt)
            tickets.recalcDay(newDay)
            if (oldDay != newDay) tickets.recalcDay(oldDay)
        }
    }

    suspend fun delete(id: Long) {
        db.withTransaction<Unit> {
            val old = entryDao.byId(id) ?: return@withTransaction // already gone (e.g. a double tap): nothing to undo
            jarDao.forEntry(id).filter { it.source == JarSource.ROUND_UP }.forEach { jarDao.delete(it.id) }
            entryDao.delete(id)
            releaseBill(old)
            tickets.recalcDay(clock.dayOf(old.happenedAt))
        }
    }

    /** Caller holds the transaction. */
    private suspend fun checkInputs(e: NewEntry) {
        val pocket = pocketDao.byId(e.pocketId)
        require(pocket != null && !pocket.archived) { "pocket ${e.pocketId} must exist and not be archived" }
        require(e.fee == 0L || e.kind == EntryKind.MOVED) { "only a move can carry a fee" }
        if (e.kind == EntryKind.MOVED) {
            val to = pocketDao.byId(checkNotNull(e.toPocketId))
            require(to != null && !to.archived) { "pocket ${e.toPocketId} must exist and not be archived" }
            require(to.currency == pocket.currency) { "a move needs two pockets in the same currency" }
        }
    }

    /**
     * An entry no longer pays its bill (deleted, or edited to another bill/none): the bill is unpaid again unless
     * another entry in the same pay cycle still pays it. Caller holds the transaction and has already stored the change.
     * shortcut: paidThisCycle is only reset here; resetting it at payday is Phase 6 work.
     */
    private suspend fun releaseBill(old: EntryEntity) {
        val rid = old.recurringId ?: return
        val day = clock.dayOf(old.happenedAt)
        val cycle = payCycleFor(day, settings.current().payday, entryDao.latestReceivedAt()?.let(clock::dayOf))
        fun startMillis(d: LocalDate) = d.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val still = entryDao.countForRecurring(rid, startMillis(cycle.start), startMillis(cycle.nextPayday))
        if (still == 0) recurringDao.setPaid(rid, false)
    }

    private fun requireNotFuture(entry: NewEntry) =
        require(!clock.dayOf(entry.happenedAt).isAfter(LocalDate.now(clock))) { "an entry cannot be dated after today" }

    /** The round-up step in pounds while round-ups are on (Plus and a step > 0), else null. */
    private suspend fun activeStep(): Int? =
        if (entitlements.isPlus.value) settings.current().roundUpStep.takeIf { it > 0 } else null

    /** Only an entry that punches the ticket rounds up. Caller holds the transaction. */
    private suspend fun qualifies(entry: NewEntry): Boolean =
        entry.kind == EntryKind.SPENT && entry.recurringId == null && pocketDao.byId(entry.pocketId)?.punchesTicket == true

    private fun EntryEntity.with(e: NewEntry, roundUp: Long, now: Long) = copy(
        kind = e.kind,
        amount = e.amount,
        pocketId = e.pocketId,
        toPocketId = e.toPocketId,
        categoryId = e.categoryId,
        note = e.note,
        happenedAt = e.happenedAt,
        recurringId = e.recurringId,
        fee = e.fee,
        roundUp = roundUp,
        updatedAt = now,
    )

    private fun roundUpMovement(entryId: Long, amount: Long, now: Long) = JarMovementEntity(
        pot = Pot.JAR, amount = amount, source = JarSource.ROUND_UP, entryId = entryId, at = now, updatedAt = now,
    )
}
