package com.masroufi.app.data.repo

import com.masroufi.app.core.PIASTRES
import com.masroufi.app.core.Payday
import com.masroufi.app.core.payCycleFor
import com.masroufi.app.data.db.ClientJobDao
import com.masroufi.app.data.db.ClientJobEntity
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.TicketCalc
import com.masroufi.app.domain.computeTicket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val VAULT_NAME = "الخزنة"
private val VAULT_COLOR = 0xFF35E0D0.toInt() // Tz.turquoise
private const val ROUND_TO = 500 * PIASTRES
private const val KEEP_MONTHS = 3L

/** One calendar month of work income into the vault. */
data class MonthIncome(val month: YearMonth, val amount: Long)

enum class SuggestionKind { RAISE, LOWER }

/** A change of the self-salary; it only ever happens when the user taps it. */
data class SalarySuggestion(val kind: SuggestionKind, val amount: Long)

/** What the last self-payday did, for Home: [paid] was moved (0 when the vault was empty), [short] = less than the salary. */
data class SelfPay(val month: YearMonth, val entryId: Long?, val paid: Long, val short: Boolean)

data class ClientJobView(val job: ClientJobEntity, val overdue: Boolean, val daysLate: Int) {
    val paid: Boolean get() = job.paidEntryId != null
}

data class FreelanceView(
    val on: Boolean,
    val vault: Long,
    /** Buffer months x 2 (vault / salary rounded down to the nearest half): 5 = 2.5 months. */
    val bufferHalves: Int,
    val safeUntil: LocalDate?,
    val salary: Long?,
    val selfPayday: Int?,
    val nextSelfPayday: LocalDate?,
    /** The daily ticket the self-salary gives its cycle; 0 until a salary is set. */
    val dailyTicket: Long,
    /** The last six months, oldest first, the current month last. */
    val income: List<MonthIncome>,
    val average: Long,
    val suggestion: SalarySuggestion?,
    val selfPay: SelfPay?,
    val jobs: List<ClientJobView>,
    /** Unpaid jobs, overdue or not. */
    val owed: Long,
)

/** Buffer months x 2: [vault] / [salary] rounded down to the nearest half; never negative. */
fun bufferHalves(vault: Long, salary: Long): Int {
    require(salary > 0) { "salary must be > 0" }
    return (2 * vault.coerceAtLeast(0) / salary).toInt()
}

/** What [now]'s self-payday did, read from [s] alone; null before it ran, when the mode is off, or when nothing was paid and the vault was not short. */
fun selfPayOf(s: Settings, now: YearMonth): SelfPay? {
    val month = s.selfPaidMonth ?: return null
    if (!s.freelanceOn || month != now.key() || (s.selfPayEntryId == null && !s.selfPayShort)) return null
    return SelfPay(now, s.selfPayEntryId, s.selfPaid ?: 0L, s.selfPayShort)
}

/** Today plus the buffer: one calendar month per whole month and 15 days for a half. */
fun safeUntil(today: LocalDate, halves: Int): LocalDate = today.plusMonths(halves / 2L).plusDays(if (halves % 2 == 1) 15L else 0L)

@Singleton
class FreelanceRepository @Inject constructor(
    private val pocketDao: PocketDao,
    private val entryDao: EntryDao,
    private val recurringDao: RecurringDao,
    private val jobDao: ClientJobDao,
    // Provider: EntryRepository -> TicketRepository -> this would be a cycle.
    private val entries: Provider<EntryRepository>,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun view(): FreelanceView {
        val s = settings.current()
        val today = LocalDate.now(clock)
        val vaultPocket = pocketDao.vault()
        val vault = vaultPocket?.let { pocketDao.balanceOf(it.id) } ?: 0L
        val salary = s.selfSalary?.takeIf { s.freelanceOn }
        val halves = salary?.let { bufferHalves(vault, it) } ?: 0
        val income = incomeByMonth(vaultPocket)
        val average = income.sumOf { it.amount } / MONTHS
        val jobs = jobs(today)
        return FreelanceView(
            on = s.freelanceOn,
            vault = vault,
            bufferHalves = halves,
            safeUntil = salary?.let { safeUntil(today, halves) },
            salary = salary,
            selfPayday = s.selfPayday,
            nextSelfPayday = nextSelfPayday(today),
            dailyTicket = dailyTicket(),
            income = income,
            average = average,
            suggestion = suggestion(),
            selfPay = selfPay(),
            jobs = jobs,
            owed = jobs.filter { !it.paid }.sumOf { it.job.amount },
        )
    }

    /** Re-reads on every entry, job and settings change. */
    fun observe(): Flow<FreelanceView> = combine(settings.settings, entryDao.countFlow(), jobDao.all()) { _, _, _ -> view() }

    /**
     * On: creates the vault pocket (it never counts in the ticket) when there is none and puts the pay cycle on the
     * self-payday. The payday before is kept for [turnOff]. Calling it again edits the salary and the day. When this
     * month's self-payday is already past, this month is treated as done: the first payment is next month's.
     */
    suspend fun turnOn(salary: Long, selfPayday: Int) {
        require(salary > 0) { "self-salary must be > 0" }
        require(selfPayday in 1..31) { "self-payday must be 1..31" }
        ensureVault()
        val s = settings.current()
        settings.freelanceSwitchOn(salary, selfPayday, restoreTo = if (s.freelanceOn) null else s.payday)
        val today = LocalDate.now(clock)
        if (!today.isBefore(paydayIn(YearMonth.from(today), selfPayday)) && s.selfPaidMonth != YearMonth.from(today).key()) {
            settings.markSelfPaid(YearMonth.from(today).key(), null, 0, false)
        }
    }

    /** Off: the payday that was set before comes back. The vault and its money stay. */
    suspend fun turnOff() = settings.freelanceSwitchOff()

    private suspend fun ensureVault(): PocketEntity = pocketDao.vault() ?: PocketEntity(
        name = VAULT_NAME, kind = PocketKind.VAULT, color = VAULT_COLOR, countsInTicket = false, updatedAt = clock.millis(),
    ).let { it.copy(id = pocketDao.insert(it)) }

    /** The next self-payday on or after [today]; this month's counts until its payment ran. Null when the mode is off. */
    suspend fun nextSelfPayday(today: LocalDate = LocalDate.now(clock)): LocalDate? {
        val s = settings.current()
        val day = s.selfPayday?.takeIf { s.freelanceOn } ?: return null
        val thisMonth = YearMonth.from(today)
        val date = paydayIn(thisMonth, day)
        return if (date.isBefore(today) || s.selfPaidMonth == thisMonth.key()) paydayIn(thisMonth.plusMonths(1), day) else date
    }

    /**
     * The daily ticket the self-salary gives its cycle: the salary less the monthly fixed costs (installments included)
     * over the days of the cycle that starts at the next self-payday, by the ordinary ticket rule (floored to a pound).
     */
    suspend fun dailyTicket(): Long {
        val s = settings.current()
        val salary = s.selfSalary?.takeIf { s.freelanceOn } ?: return 0L
        val start = nextSelfPayday() ?: return 0L
        val cycle = payCycleFor(start, Payday.Day(s.selfPayday!!), null)
        val calc = computeTicket(salary, recurringDao.monthlyFixedTotal(), 0, cycle.daysLeft(cycle.start))
        return (calc as? TicketCalc.Printed)?.amount ?: 0L
    }

    /**
     * Called by printToday: on the first open on or after the self-payday (once a month) moves min(self-salary, vault)
     * from the vault to the lowest-id counting pocket (a MOVED entry, no fee). A short vault pays what is there, never
     * a negative amount; an empty one makes no entry. The result is kept for Home ([selfPay]) and the ritual.
     * [s] is the settings the caller already read (one read per print). Returns true when money was moved.
     */
    suspend fun runSelfPayday(today: LocalDate, s: Settings): Boolean {
        val salary = s.selfSalary?.takeIf { s.freelanceOn } ?: return false
        val day = s.selfPayday ?: return false
        val month = YearMonth.from(today)
        if (s.selfPaidMonth == month.key() || today.isBefore(paydayIn(month, day))) return false
        val vault = pocketDao.vault() ?: return false
        val to = pocketDao.firstCounting() ?: return false
        val paid = minOf(salary, pocketDao.balanceOf(vault.id)).coerceAtLeast(0)
        val entryId = if (paid > 0) {
            entries.get().log(NewEntry(kind = EntryKind.MOVED, amount = paid, pocketId = vault.id, toPocketId = to.id, happenedAt = clock.millis()))
        } else {
            null
        }
        settings.markSelfPaid(month.key(), entryId, paid, short = paid < salary)
        return entryId != null
    }

    /** This month's self-payday, null before it ran or when the mode is off. */
    suspend fun selfPay(): SelfPay? = selfPayOf(settings.current(), YearMonth.now(clock))

    /** Income into the vault by calendar month for the six months up to and including this one. */
    private suspend fun incomeByMonth(vault: PocketEntity?): List<MonthIncome> {
        val last = YearMonth.now(clock)
        val months = (MONTHS - 1 downTo 0L).map { last.minusMonths(it) }
        fun start(m: YearMonth) = m.atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val received = if (vault == null) emptyList() else entryDao.forDay(start(months.first()), start(last.plusMonths(1)))
            .filter { it.pocketId == vault.id && it.kind == EntryKind.RECEIVED }
        return months.map { m -> MonthIncome(m, received.filter { YearMonth.from(clock.dayOf(it.happenedAt)) == m }.sumOf { it.amount }) }
    }

    /**
     * Raise: the six-month average (empty months count as zero) is at least 10% above the salary and the buffer is two
     * months or more; the amount is the average rounded down to 500. Lower: the buffer is under one month; the amount is
     * the same rounding of the average and only offered when that is below the salary (the spec names no amount).
     * At most once a month; "keep it" silences three months. Null while the mode is off.
     */
    suspend fun suggestion(): SalarySuggestion? {
        val s = settings.current()
        val salary = s.selfSalary?.takeIf { s.freelanceOn } ?: return null
        val now = YearMonth.now(clock)
        if (s.selfSuggestMonth == now.key()) return null
        val kept = s.selfKeptMonth
        if (kept != null && now.isBefore(YearMonth.of((kept / 100).toInt(), (kept % 100).toInt()).plusMonths(KEEP_MONTHS))) return null
        val vault = pocketDao.vault()
        val halves = bufferHalves(vault?.let { pocketDao.balanceOf(it.id) } ?: 0L, salary)
        val average = incomeByMonth(vault).sumOf { it.amount } / MONTHS
        val rounded = average / ROUND_TO * ROUND_TO
        return when {
            average * 10 >= salary * 11 && halves >= 4 && rounded > salary -> SalarySuggestion(SuggestionKind.RAISE, rounded)
            halves < 2 && rounded in 1L until salary -> SalarySuggestion(SuggestionKind.LOWER, rounded)
            else -> null
        }
    }

    /** The user tapped the suggestion: the self-salary changes, and no new one shows this month. */
    suspend fun applySuggestion(suggestion: SalarySuggestion) {
        settings.setSelfSalary(suggestion.amount)
        settings.setSelfSuggestMonth(YearMonth.now(clock).key())
    }

    /** "خلّيه زي ما هو": nothing changes and no suggestion shows for three months. */
    suspend fun keepSalary() = settings.setSelfKeptMonth(YearMonth.now(clock).key())

    /** "جالي شغل": work income lands in the vault (created when missing). */
    suspend fun workCame(amount: Long, note: String? = null): Long = receiveIntoVault(amount, note)

    private suspend fun receiveIntoVault(amount: Long, note: String?): Long {
        require(amount > 0) { "amount must be > 0" }
        val vault = ensureVault()
        return entries.get().log(NewEntry(kind = EntryKind.RECEIVED, amount = amount, pocketId = vault.id, note = note, happenedAt = clock.millis()))
    }

    suspend fun jobs(today: LocalDate = LocalDate.now(clock)): List<ClientJobView> = jobDao.allNow().map {
        val late = if (it.paidEntryId == null) ChronoUnit.DAYS.between(it.dueDate, today).toInt() else 0
        ClientJobView(it, overdue = late >= 1, daysLate = late.coerceAtLeast(0))
    }

    suspend fun addJob(client: String, label: String, amount: Long, dueDate: LocalDate): Long {
        require(client.isNotBlank()) { "a job needs a client" }
        require(amount > 0) { "amount must be > 0" }
        return jobDao.insert(ClientJobEntity(client = client.trim(), label = label.trim(), amount = amount, dueDate = dueDate))
    }

    suspend fun editJob(job: ClientJobEntity) {
        require(job.client.isNotBlank()) { "a job needs a client" }
        require(job.amount > 0) { "amount must be > 0" }
        jobDao.update(job)
    }

    suspend fun deleteJob(id: Long) = jobDao.delete(id)

    /** The client paid: a RECEIVED entry into the vault for the job's amount; deleting that entry makes the job unpaid again. */
    suspend fun markPaid(jobId: Long) {
        val job = checkNotNull(jobDao.byId(jobId)) { "no job $jobId" }
        if (job.paidEntryId != null) return
        val entryId = receiveIntoVault(job.amount, listOf(job.client, job.label).filter { it.isNotBlank() }.joinToString(" · ").take(NOTE_MAX_CHARS))
        jobDao.update(job.copy(paidEntryId = entryId))
    }

    private fun paydayIn(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))

    private companion object {
        const val MONTHS = 6L
    }
}
