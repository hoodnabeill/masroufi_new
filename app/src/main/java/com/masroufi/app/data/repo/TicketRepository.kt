package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.payCycleFor
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.ProverbCollectedEntity
import com.masroufi.app.data.db.ProverbDao
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.StampDao
import com.masroufi.app.data.db.StampEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.PROVERB_SET_SIZE
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.TicketCalc
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.computeTicket
import com.masroufi.app.domain.earnedStamps
import com.masroufi.app.domain.leftover
import com.masroufi.app.domain.nextProverb
import com.masroufi.app.domain.punches
import com.masroufi.app.domain.spentToday
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class CloseResult(val leftover: Long, val proverbNumber: Int?, val newStamps: Set<StampKind>)

/** The local day an entry belongs to. */
internal fun Clock.dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** Same predicate as countingBalanceTotal / forDayWithPocket: the pocket's spending punches the EGP ticket. */
internal val PocketEntity.punchesTicket: Boolean get() = countsInTicket && !archived && currency == "EGP"

internal fun potOf(destination: LeftoverDestination?): Pot? = when (destination) {
    LeftoverDestination.JAR -> Pot.JAR
    LeftoverDestination.EMERGENCY -> Pot.EMERGENCY
    LeftoverDestination.TOMORROW, null -> null
}

/** Makes `existing` hold `amount`: updates it, deletes it at 0, or inserts `create()` when missing and amount > 0. */
internal suspend fun JarDao.sync(existing: JarMovementEntity?, amount: Long, now: Long, create: () -> JarMovementEntity) {
    when {
        existing == null -> if (amount > 0) insert(create())
        amount <= 0 -> delete(existing.id)
        existing.amount != amount -> update(existing.copy(amount = amount, updatedAt = now))
    }
}

@Singleton
class TicketRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val pocketDao: PocketDao,
    private val entryDao: EntryDao,
    private val ticketDao: DayTicketDao,
    private val recurringDao: RecurringDao,
    private val jarDao: JarDao,
    private val proverbDao: ProverbDao,
    private val stampDao: StampDao,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    /**
     * Today's ticket: printed once, after auto-closing every earlier open day. Days in a gap get no row (design
     * call 10). Entries already logged today are added back to Spendable, so the ticket is the morning's.
     */
    suspend fun printToday(): DayTicketEntity {
        val s = settings.current()
        return db.withTransaction {
            val today = LocalDate.now(clock)
            ticketDao.forDate(today)?.let { return@withTransaction it }

            // Normally one row (yesterday or the last printed day); more only if the clock went backwards.
            for (open in ticketDao.openBefore(today)) {
                // A carry is only for the next day; an older day's leftover goes to the jar instead.
                val dest = if (s.leftoverDefault == LeftoverDestination.TOMORROW && open.date != today.minusDays(1)) {
                    LeftoverDestination.JAR
                } else {
                    s.leftoverDefault
                }
                close(open, TicketStatus.CLOSED_AUTO, dest, proverbNumber = null)
            }

            val carriedIn = ticketDao.forDate(today.minusDays(1))
                ?.takeIf { it.status != TicketStatus.OPEN && it.destination == LeftoverDestination.TOMORROW }
                ?.leftover ?: 0L
            val cycle = payCycleFor(today, s.payday, entryDao.latestReceivedAt()?.let(clock::dayOf))
            val reserved = recurringDao.unpaidDueBefore(cycle.nextPayday) +
                jarDao.totalNow(Pot.JAR).coerceAtLeast(0) + jarDao.totalNow(Pot.EMERGENCY).coerceAtLeast(0)
            val punches = punchesOn(today)
            val spent = spentToday(punches)
            // A round-up is in both spent and the reserved jar, so adding it back here nets to zero.
            val spendable = pocketDao.countingBalanceTotal() + spent
            val now = clock.millis()
            val calc = computeTicket(spendable, reserved, carriedIn, cycle.daysLeft(today))
            val row = when (calc) {
                is TicketCalc.Printed ->
                    DayTicketEntity(date = today, amount = calc.amount, base = calc.base, carriedIn = calc.carriedIn, updatedAt = now)
                is TicketCalc.EndOfMonth ->
                    DayTicketEntity(date = today, amount = 0, base = 0, endOfMonth = true, updatedAt = now)
            }.copy(spent = spent, entryCount = punches.size)
            row.copy(id = ticketDao.insert(row))
        }
    }

    /** Closes an OPEN day by hand; a day already closed returns its stored leftover and nothing new. */
    suspend fun closeDay(date: LocalDate, destination: LeftoverDestination): CloseResult {
        val s = settings.current()
        return db.withTransaction {
            val row = checkNotNull(ticketDao.forDate(date)) { "no ticket printed for $date" }
            if (row.status != TicketStatus.OPEN) return@withTransaction CloseResult(row.leftover, null, emptySet())

            val now = clock.millis()
            val proverb = nextProverb(proverbDao.numbers().toSet(), s.proverbSeed, PROVERB_SET_SIZE)
            proverb?.let { proverbDao.insert(ProverbCollectedEntity(number = it, collectedOn = date, updatedAt = now)) }
            val closed = close(row, TicketStatus.CLOSED_BY_HAND, destination, proverb)

            val days = ticketDao.allNow().map { DayResult(it.date, it.status, it.amount, it.spent) }
            val newStamps = earnedStamps(days) - stampDao.kinds().toSet()
            newStamps.forEach { stampDao.insert(StampEntity(kind = it, earnedOn = date, updatedAt = now)) }
            CloseResult(closed.leftover, proverb, newStamps)
        }
    }

    /**
     * Recomputes a printed day's spent and holes after an entry changes. The ticket amount never changes; a closed
     * day's leftover and its jar movement follow the new spent.
     *
     * Known limit: a later edit to a TOMORROW-closed day updates that day's leftover but not the next day's stored
     * carriedIn. No money is lost: the pockets carry the difference into the following tickets.
     */
    suspend fun recalcDay(date: LocalDate) {
        db.withTransaction<Unit> { ticketDao.forDate(date)?.let { settle(it) } }
    }

    /** Caller holds the transaction. */
    private suspend fun close(
        row: DayTicketEntity,
        status: TicketStatus,
        destination: LeftoverDestination,
        proverbNumber: Int?,
    ): DayTicketEntity = settle(row.copy(status = status, destination = destination, proverbNumber = proverbNumber))

    /** Writes spent, holes and (once closed) leftover, and syncs the leftover movement. Caller holds the transaction. */
    private suspend fun settle(row: DayTicketEntity): DayTicketEntity {
        val punches = punchesOn(row.date)
        val spent = spentToday(punches)
        val closed = row.status != TicketStatus.OPEN
        val now = clock.millis()
        val updated = row.copy(
            spent = spent,
            entryCount = punches.size,
            leftover = if (closed) leftover(row.amount, spent) else row.leftover,
            updatedAt = now,
        )
        ticketDao.update(updated)
        val pot = potOf(row.destination)
        if (closed && pot != null) {
            val movement = jarDao.forDay(row.date).firstOrNull { it.source == JarSource.TICKET_LEFTOVER }
            jarDao.sync(movement, updated.leftover, now) { leftoverMovement(pot, updated.leftover, row.date, now) }
        }
        return updated
    }

    private suspend fun punchesOn(date: LocalDate) = entryDao.forDayWithPocket(
        date.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
        date.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli(),
    ).filter { it.punches }

    private fun leftoverMovement(pot: Pot, amount: Long, date: LocalDate, now: Long) = JarMovementEntity(
        pot = pot, amount = amount, source = JarSource.TICKET_LEFTOVER, dayDate = date, at = now, updatedAt = now,
    )
}
