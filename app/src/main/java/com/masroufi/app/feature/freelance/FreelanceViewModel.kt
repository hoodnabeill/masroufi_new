package com.masroufi.app.feature.freelance

import kotlinx.coroutines.flow.combine
import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.roll.PendingDelete
import com.masroufi.app.feature.roll.DeleteKind
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.ClientJobEntity
import com.masroufi.app.data.repo.ClientJobView
import com.masroufi.app.data.repo.FreelanceRepository
import com.masroufi.app.data.repo.FreelanceView
import com.masroufi.app.data.repo.NOTE_MAX_CHARS
import com.masroufi.app.data.repo.SalarySuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** A month's bar against the dashed salary line: under the line it is quiet, a month with nothing is a carnelian sliver. */
enum class BarTone { OVER, UNDER, EMPTY }

/** [fraction] of the chart's height (0 for an empty month: the screen draws its sliver). */
data class IncomeBarUi(val month: YearMonth, val amount: Long, val fraction: Float, val tone: BarTone)

data class FreelanceUi(
    val loading: Boolean = true,
    val on: Boolean = false,
    val vault: Long = 0,
    /** The buffer in whole months and whether a half follows: 2 and true = "شهرين ونص". */
    val bufferMonths: Int = 0,
    val bufferHalf: Boolean = false,
    /** How full the drawn tank is (0..1). */
    val tank: Float = 0f,
    val safeUntil: LocalDate? = null,
    val salary: Long? = null,
    val selfPayday: Int? = null,
    val daysToPayday: Int? = null,
    val dailyTicket: Long = 0,
    val bars: List<IncomeBarUi> = emptyList(),
    /** The salary line's height in the chart (0..1); null without a salary. */
    val line: Float? = null,
    val average: Long = 0,
    /** The most recent of the six months that had no income at all. */
    val emptyMonth: YearMonth? = null,
    val suggestion: SalarySuggestion? = null,
    /** What clients still owe: the unpaid jobs, the oldest due date first. */
    val jobs: List<ClientJobView> = emptyList(),
    val owed: Long = 0,
) {
    val buffer: Int get() = bufferMonths * 2 + if (bufferHalf) 1 else 0

    /** On, but nothing has come yet and nothing is owed: the empty state of 04-SCREENS. */
    val empty: Boolean get() = on && bars.all { it.amount == 0L } && jobs.isEmpty()
}

private const val TAG = "Freelance"
/** The drawn tank holds this many months of salary. */
private const val TANK_MONTHS = 4f
private const val CHART_HEADROOM = 1.1f
const val DEFAULT_DUE_DAYS = 30

/** مرتب لنفسك: the repository's numbers, shaped for the screen. The sheets keep what is typed and call these with the result. */
@HiltViewModel
class FreelanceViewModel @Inject constructor(
    private val repo: FreelanceRepository,
    private val clock: Clock,
    private val deletes: PendingDeletes,
) : ViewModel() {
    val state: StateFlow<FreelanceUi> = combine(repo.observe(), deletes.hidden(DeleteKind.JOB)) { v, hidden -> build(v, hidden) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FreelanceUi())

    private fun build(v: FreelanceView, hidden: Set<Long>): FreelanceUi {
        val today = LocalDate.now(clock)
        val top = maxOf(v.income.maxOfOrNull { it.amount } ?: 0L, v.salary ?: 0L) * CHART_HEADROOM
        val bars = v.income.map {
            val tone = when {
                it.amount <= 0 -> BarTone.EMPTY
                v.salary != null && it.amount < v.salary -> BarTone.UNDER
                else -> BarTone.OVER
            }
            IncomeBarUi(it.month, it.amount, if (top > 0) it.amount / top else 0f, tone)
        }
        return FreelanceUi(
            loading = false,
            on = v.on,
            vault = v.vault,
            bufferMonths = v.bufferHalves / 2,
            bufferHalf = v.bufferHalves % 2 == 1,
            tank = (v.bufferHalves / 2f / TANK_MONTHS).coerceIn(0f, 1f),
            safeUntil = v.safeUntil,
            salary = v.salary,
            selfPayday = v.selfPayday,
            daysToPayday = v.nextSelfPayday?.let { ChronoUnit.DAYS.between(today, it).toInt() },
            dailyTicket = v.dailyTicket,
            bars = bars,
            line = v.salary?.let { if (top > 0) it / top else null },
            average = v.average,
            emptyMonth = bars.lastOrNull { it.tone == BarTone.EMPTY }?.month,
            suggestion = v.suggestion,
            jobs = v.jobs.filter { !it.paid && it.job.id !in hidden },
            owed = v.owed,
        )
    }

    /** First visit (the mode is off) or "عدّل": the self-salary and the day it is paid. */
    fun turnOn(salary: Long, day: Int) = launch { repo.turnOn(salary, day) }

    /** "خلّيه {amount}": the user's tap changes the salary. */
    fun applySuggestion(suggestion: SalarySuggestion) = launch { repo.applySuggestion(suggestion) }

    /** "خلّيه زي ما هو". */
    fun keepSalary() = launch { repo.keepSalary() }

    /** "جالي شغل": [amount] lands in the vault; the client and the label are optional notes. */
    fun workCame(amount: Long, client: String, label: String) = launch { repo.workCame(amount, note(client, label)) }

    /** [dueInDays] counts from today (negative = already overdue). */
    fun addJob(client: String, label: String, amount: Long, dueInDays: Int) =
        launch { repo.addJob(client, label, amount, LocalDate.now(clock).plusDays(dueInDays.toLong())) }

    /** [dueInDays] null keeps the job's date. */
    fun editJob(job: ClientJobEntity, client: String, label: String, amount: Long, dueInDays: Int?) = launch {
        val due = dueInDays?.let { LocalDate.now(clock).plusDays(it.toLong()) } ?: job.dueDate
        repo.editJob(job.copy(client = client.trim(), label = label.trim(), amount = amount, dueDate = due))
    }

    /** The client paid: an income into the vault. */
    fun markPaid(jobId: Long) = launch { repo.markPaid(jobId) }

    fun deleteJob(jobId: Long) = deletes.schedule(PendingDelete(jobId, kind = DeleteKind.JOB)) { repo.deleteJob(jobId) }

    private fun note(client: String, label: String): String? =
        listOf(client.trim(), label.trim()).filter { it.isNotEmpty() }.joinToString(" · ").take(NOTE_MAX_CHARS).ifEmpty { null }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "action failed", e)
            }
        }
    }
}
