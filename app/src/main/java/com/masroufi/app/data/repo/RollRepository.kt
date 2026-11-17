package com.masroufi.app.data.repo

import com.masroufi.app.core.PRIMARY_CURRENCY
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.domain.EntryForDay
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.normalize
import com.masroufi.app.domain.punched
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

enum class RollFilter { ALL, SPENT, INCOME, RECURRING }

enum class DayKind { TICKET, NON_TICKET }

/** spent = everything punched-shaped (spends + round-ups, move fees); received = income into EGP pockets ("جالك"). */
data class RollSummary(val spent: Long, val received: Long, val ticketsUnder: Int, val ticketsClosed: Int)

data class RollDay(val date: LocalDate, val ticket: DayTicketEntity?, val entries: List<EntryRow>, val kind: DayKind)

data class RollMonth(val month: YearMonth, val summary: RollSummary, val days: List<RollDay>)

private const val SEARCH_LIMIT = 200

fun RollFilter.matches(e: EntryRow): Boolean = when (this) {
    RollFilter.ALL -> true
    RollFilter.SPENT -> e.kind == EntryKind.SPENT || (e.kind == EntryKind.MOVED && e.fee > 0)
    RollFilter.INCOME -> e.kind == EntryKind.RECEIVED
    RollFilter.RECURRING -> e.recurringId != null
}

internal val EntryRow.punched: Long get() = EntryForDay(kind, amount, roundUp, fee, countsInTicket, recurringId).punched

/** The roll (الدفتر): calendar months (decision 1), newest first. Every Flow re-emits on entry, pocket, category or ticket changes. */
@Singleton
class RollRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val ticketDao: DayTicketDao,
    private val clock: Clock,
) {
    fun month(month: YearMonth, filter: RollFilter = RollFilter.ALL): Flow<RollMonth> {
        val first = month.atDay(1)
        val next = month.plusMonths(1).atDay(1)
        return combine(entryDao.rows(startMillis(first), startMillis(next)), ticketDao.range(first, month.atEndOfMonth())) { rows, tickets ->
            val egp = rows.filter { it.currency == PRIMARY_CURRENCY }
            val closed = tickets.filter { it.status != TicketStatus.OPEN }
            val summary = RollSummary(
                spent = egp.sumOf { it.punched },
                received = egp.filter { it.kind == EntryKind.RECEIVED }.sumOf { it.amount },
                ticketsUnder = closed.count { it.spent <= it.amount },
                ticketsClosed = closed.size,
            )
            val byDay = rows.filter { filter.matches(it) }.groupBy { clock.dayOf(it.happenedAt) }
            val ticketByDay = tickets.associateBy { it.date }
            val dates = if (filter == RollFilter.ALL) byDay.keys + ticketByDay.keys else byDay.keys
            val days = dates.sortedDescending().map { d ->
                val t = ticketByDay[d]
                RollDay(d, t, byDay[d].orEmpty(), if (t != null) DayKind.TICKET else DayKind.NON_TICKET)
            }
            RollMonth(month, summary, days)
        }
    }

    /**
     * Entries whose note or category name contains [query] after the quick-log Arabic normalising, newest first.
     * shortcut: filters every entry in memory (SQL cannot normalise Arabic); add a normalised column if rolls grow large.
     */
    fun search(query: String): Flow<List<EntryRow>> {
        val q = normalize(query.trim()).lowercase()
        if (q.isEmpty()) return flowOf(emptyList())
        fun String?.hit() = this != null && q in normalize(this).lowercase()
        return entryDao.allRows().map { rows -> rows.asSequence().filter { it.note.hit() || it.categoryName.hit() }.take(SEARCH_LIMIT).toList() }
    }

    /** Months that hold an entry or a ticket row, newest first. */
    fun months(): Flow<List<YearMonth>> = combine(entryDao.allHappenedAt(), ticketDao.dates()) { times, dates ->
        (times.map { YearMonth.from(clock.dayOf(it)) } + dates.map(YearMonth::from)).distinct().sortedDescending()
    }

    private fun startMillis(d: LocalDate) = d.atStartOfDay(clock.zone).toInstant().toEpochMilli()
}
