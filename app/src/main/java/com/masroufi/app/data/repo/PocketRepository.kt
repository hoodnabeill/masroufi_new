package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class PocketView(val pocket: PocketEntity, val balance: Long, val lastActivityAt: Long?)

/** This month's move fees from a pocket: count to total per kind. */
data class FeesSummary(val total: Long, val cashOut: Pair<Int, Long>, val transfer: Pair<Int, Long>)

/** Piastres in hand from the note counter: 200s, 100s and 50s plus small change (piastres). */
fun cashCount(n200: Int, n100: Int, n50: Int, changePiastres: Long): Long {
    require(n200 >= 0 && n100 >= 0 && n50 >= 0 && changePiastres >= 0) { "counts must be >= 0" }
    return (200L * n200 + 100L * n100 + 50L * n50) * 100 + changePiastres
}

@Singleton
class PocketRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val pocketDao: PocketDao,
    private val entryDao: EntryDao,
    private val recurringDao: RecurringDao,
    private val entries: EntryRepository,
    private val clock: Clock,
) {
    /** Pockets not archived, in creation order. */
    fun observePockets(): Flow<List<PocketView>> =
        combine(pocketDao.all(), pocketDao.balances(), pocketDao.lastActivity()) { pockets, balances, activity ->
            val bal = balances.associate { it.pocketId to it.balance }
            val last = activity.associate { it.pocketId to it.at }
            pockets.filter { !it.archived }.sortedBy { it.id }.map { PocketView(it, bal[it.id] ?: it.openingBalance, last[it.id]) }
        }

    suspend fun addPocket(name: String, kind: PocketKind, opening: Long, countsInTicket: Boolean, color: Int): Long {
        require(name.isNotBlank()) { "a pocket needs a name" }
        return pocketDao.insert(
            PocketEntity(
                name = name.trim(), kind = kind, openingBalance = opening, countsInTicket = countsInTicket, color = color,
                updatedAt = clock.millis(),
            ),
        )
    }

    suspend fun update(pocket: PocketEntity) {
        require(pocket.name.isNotBlank()) { "a pocket needs a name" }
        pocketDao.update(pocket.copy(updatedAt = clock.millis()))
    }

    suspend fun archive(pocketId: Long) {
        val p = checkNotNull(pocketDao.byId(pocketId)) { "no pocket $pocketId" }
        pocketDao.update(p.copy(archived = true, updatedAt = clock.millis()))
    }

    /** Records an ADJUST of counted - balance (decision 2); null when they already match. */
    suspend fun reconcile(pocketId: Long, counted: Long): Long? = db.withTransaction<Long?> {
        val diff = counted - pocketDao.balanceOf(pocketId)
        if (diff == 0L) null else entries.log(NewEntry(kind = EntryKind.ADJUST, amount = diff, pocketId = pocketId, happenedAt = clock.millis()))
    }

    /** Everything leaving or entering the pocket this calendar month, fees included, adjustments not. */
    suspend fun movedThisMonth(pocketId: Long): Long {
        val (start, end) = thisMonth()
        return entryDao.movedBetween(pocketId, start, end)
    }

    suspend fun feesThisMonth(pocketId: Long): FeesSummary {
        val (start, end) = thisMonth()
        val (cashOut, transfer) = entryDao.moveFees(pocketId, start, end).partition { it.cashOut }
        return FeesSummary(
            total = cashOut.sumOf { it.fee } + transfer.sumOf { it.fee },
            cashOut = cashOut.size to cashOut.sumOf { it.fee },
            transfer = transfer.size to transfer.sumOf { it.fee },
        )
    }

    suspend fun recordSms(pocketId: Long, balance: Long) = pocketDao.recordSms(pocketId, balance, clock.millis())

    /**
     * The room left under the typed monthly limit (limit - moved, may be negative) when moved + unpaid recurring
     * payments from this pocket due by the month's end would pass it; null when they fit or there is no limit.
     * Overdue unpaid payments count too: they still have to leave this month.
     */
    suspend fun limitWarning(pocketId: Long): Long? {
        val limit = pocketDao.byId(pocketId)?.monthlyLimit ?: return null
        val moved = movedThisMonth(pocketId)
        val due = recurringDao.unpaidFromPocketDueBefore(pocketId, YearMonth.now(clock).plusMonths(1).atDay(1))
        return if (moved + due > limit) limit - moved else null
    }

    private fun thisMonth(): Pair<Long, Long> {
        val m = YearMonth.now(clock)
        fun start(ym: YearMonth) = ym.atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return start(m) to start(m.plusMonths(1))
    }
}
