package com.masroufi.app.feature.installments

import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.roll.PendingDelete
import com.masroufi.app.feature.roll.DeleteKind
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.AboveFreeJar
import com.masroufi.app.data.repo.EarlyPayoff
import com.masroufi.app.data.repo.InstallmentPlan
import com.masroufi.app.data.repo.InstallmentRepository
import com.masroufi.app.data.repo.InstallmentsView
import com.masroufi.app.data.repo.installmentPerDay
import com.masroufi.app.data.repo.ticketGrowth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** The grey line under a plan's name (Installments.dc.html): the mock shortens it for the last two payments. */
sealed interface PlanLine {
    data class OneLeft(val end: YearMonth) : PlanLine
    data class TwoLeft(val end: YearMonth) : PlanLine
    data class Left(val left: Int, val total: Int, val day: Int, val end: YearMonth) : PlanLine
    /** No total: nothing to count down. */
    data class Open(val day: Int) : PlanLine
    data object Done : PlanLine
}

fun planLine(plan: InstallmentPlan): PlanLine {
    val left = plan.left
    val total = plan.total
    val end = plan.endMonth
    return when {
        plan.finished -> PlanLine.Done
        left == null || total == null || end == null -> PlanLine.Open(plan.item.scheduleDay)
        left == 1 -> PlanLine.OneLeft(end)
        left == 2 -> PlanLine.TwoLeft(end)
        else -> PlanLine.Left(left, total, plan.item.scheduleDay, end)
    }
}

/** [ending]: finished, and its end motion has not played yet (the row folds away after it). */
data class PlanRowUi(val plan: InstallmentPlan, val line: PlanLine, val ending: Boolean)

enum class BarKind { NOW, MONTH, FREE }

/** One bar of the freedom timeline: [weight] months wide, [fraction] of today's monthly total still paid. */
data class TimelineBar(val kind: BarKind, val month: YearMonth?, val weight: Int, val fraction: Float)

/** "+6 في يناير": [withYear] when the month is a year or more away. */
data class GrowthStep(val growth: Long, val month: YearMonth, val withYear: Boolean)

data class EarlyUi(val planId: Long, val name: String, val amount: Long, val growth: Long)

data class InstallmentsUi(
    val loading: Boolean = true,
    val rows: List<PlanRowUi> = emptyList(),
    /** What the hero shows: it still holds a plan whose end motion has not played, so the number rolls down when it does. */
    val monthly: Long = 0,
    val perDay: Long = 0,
    val remaining: Long = 0,
    val freedom: YearMonth? = null,
    val bars: List<TimelineBar> = emptyList(),
    val growth: List<GrowthStep> = emptyList(),
    val early: EarlyUi? = null,
    /** The last "سدّد" found the free jar short of the typed amount; cleared by the next answer. */
    val payOffFailed: Boolean = false,
    val today: YearMonth = YearMonth.of(1970, 1),
) {
    val empty: Boolean get() = !loading && rows.isEmpty()
}

private const val TAG = "Installments"
private const val FREE_WEIGHT = 3
private const val FREE_FRACTION = 0.06f
private const val YEAR_AHEAD_MONTHS = 12L

private fun seenKey(id: Long) = "plan:$id"

/** The bars of the freedom card: today, then one bar from each plan's end month, then "free". */
fun timelineBars(view: InstallmentsView, today: YearMonth): List<TimelineBar> {
    val steps = view.timeline
    if (steps.isEmpty()) return emptyList()
    val monthlyOf = view.plans.associate { it.item.id to it.monthly }
    val total = view.monthly.coerceAtLeast(1)
    val points = listOf(today) + steps.map { it.month }
    var left = total
    val bars = steps.indices.map { i ->
        if (i > 0) left -= monthlyOf.getValue(steps[i - 1].planId)
        TimelineBar(
            if (i == 0) BarKind.NOW else BarKind.MONTH,
            steps.getOrNull(i - 1)?.month,
            ChronoUnit.MONTHS.between(points[i], points[i + 1]).toInt().coerceAtLeast(1),
            left.toFloat() / total,
        )
    }
    left -= monthlyOf.getValue(steps.last().planId)
    return bars + TimelineBar(BarKind.FREE, null, FREE_WEIGHT, (left.toFloat() / total).coerceAtLeast(FREE_FRACTION))
}

/** أقساطي: every number is the repository's; this only orders and words it. */
@HiltViewModel
class InstallmentsViewModel @Inject constructor(
    private val repo: InstallmentRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    deletes: PendingDeletes,
) : ViewModel() {
    private val payOffFailed = MutableStateFlow(false)

    val state: StateFlow<InstallmentsUi> = combine(repo.observe(), settings.settings, payOffFailed, deletes.hidden(DeleteKind.INSTALLMENT)) { all, s, failed, hidden ->
        // Only the rows leave; the totals and the chart come from storage until the delete is written.
        build(all, s, repo.earlyPayoff(), failed).let { ui -> if (hidden.isEmpty()) ui else ui.copy(rows = ui.rows.filter { it.plan.item.id !in hidden }) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InstallmentsUi())

    private fun build(v: InstallmentsView, s: Settings, early: EarlyPayoff?, failed: Boolean): InstallmentsUi {
        val today = YearMonth.now(clock)
        // A finished plan stays (with its end motion) until the screen has seen it; then it is gone for good.
        val rows = v.plans.filter { !it.finished || seenKey(it.item.id) !in s.milestonesSeen }
            .map { PlanRowUi(it, planLine(it), ending = it.finished) }
        val monthly = v.monthly + rows.filter { it.ending }.sumOf { it.plan.monthly }
        return InstallmentsUi(
            loading = false,
            rows = rows,
            monthly = monthly,
            perDay = installmentPerDay(monthly),
            remaining = v.remaining,
            freedom = v.freedom,
            bars = timelineBars(v, today),
            growth = v.timeline.map { GrowthStep(it.growth, it.month, ChronoUnit.MONTHS.between(today, it.month) >= YEAR_AHEAD_MONTHS) },
            early = early?.let { EarlyUi(it.plan.item.id, it.plan.item.name, it.amount, ticketGrowth(it.plan.monthly)) },
            payOffFailed = failed,
            today = today,
        )
    }

    /** The end motion of a finished plan has played (and its Milestone haptic fired): it is not shown again. */
    fun finishedSeen(planId: Long) = launch { settings.markMilestoneSeen(seenKey(planId)) }

    /** "كمّل عادي". */
    fun keepGoing(planId: Long) = launch { payOffFailed.value = false; repo.dismissEarlyPayoff(planId) }

    /** "سدّد": [amount] is what the user confirmed in the keypad sheet; a free jar that does not cover it moves nothing and shows a line. */
    fun payOff(planId: Long, amount: Long) = launch {
        try {
            repo.payOff(planId, amount)
            payOffFailed.value = false
        } catch (e: AboveFreeJar) {
            payOffFailed.value = true
        }
    }

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
