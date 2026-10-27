package com.masroufi.app.domain

import com.masroufi.app.data.model.SplitItem
import com.masroufi.app.data.model.SplitKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class BudgetStatus { ON_TRACK, WARNING, OVER }

data class BudgetProgress(
    val limitMinor: Long,
    val spentMinor: Long,
    val carriedMinor: Long,
) {
    val effectiveLimitMinor: Long get() = limitMinor + carriedMinor
    val remainingMinor: Long get() = effectiveLimitMinor - spentMinor
    val fraction: Float get() = if (effectiveLimitMinor <= 0) (if (spentMinor > 0) 1f else 0f) else (spentMinor.toDouble() / effectiveLimitMinor).toFloat()
    val status: BudgetStatus
        get() = when {
            spentMinor > effectiveLimitMinor -> BudgetStatus.OVER
            spentMinor * 100 >= effectiveLimitMinor * WARNING_PERCENT -> BudgetStatus.WARNING
            else -> BudgetStatus.ON_TRACK
        }

    companion object {
        const val WARNING_PERCENT = 80

        /** Rollover carries last period's unspent (or overspent, as a negative) amount into this one. */
        fun of(limitMinor: Long, spentMinor: Long, previousSpentMinor: Long?, rollover: Boolean) = BudgetProgress(
            limitMinor = limitMinor,
            spentMinor = spentMinor,
            carriedMinor = if (rollover && previousSpentMinor != null) limitMinor - previousSpentMinor else 0,
        )
    }
}

sealed interface SplitResult {
    data class Ok(val lines: List<Line>, val unallocatedMinor: Long) : SplitResult
    data class FixedExceedsIncome(val fixedTotalMinor: Long) : SplitResult
    data class PercentOver100(val totalBasisPoints: Long) : SplitResult
    data object Empty : SplitResult

    data class Line(val item: SplitItem, val amountMinor: Long)
}

object SplitCalculator {
    /** Fixed amounts are taken first; percentages apply to what remains. Shares round down, the leftover stays unallocated. */
    fun calculate(incomeMinor: Long, items: List<SplitItem>): SplitResult {
        if (items.isEmpty() || incomeMinor <= 0) return SplitResult.Empty
        val fixedTotal = items.filter { it.kind == SplitKind.FIXED }.sumOf { it.value }
        if (fixedTotal > incomeMinor) return SplitResult.FixedExceedsIncome(fixedTotal)
        val percentTotal = items.filter { it.kind == SplitKind.PERCENT }.sumOf { it.value }
        if (percentTotal > 10_000) return SplitResult.PercentOver100(percentTotal)
        val base = incomeMinor - fixedTotal
        val lines = items.map { item ->
            val amount = when (item.kind) {
                SplitKind.FIXED -> item.value
                SplitKind.PERCENT -> Math.multiplyExact(base, item.value) / 10_000
            }
            SplitResult.Line(item, amount)
        }
        return SplitResult.Ok(lines, incomeMinor - lines.sumOf { it.amountMinor })
    }
}

enum class DebtStatus { OPEN, OVERDUE, SETTLED }

object DebtMath {
    fun remaining(originalMinor: Long, paidMinor: Long): Long = (originalMinor - paidMinor).coerceAtLeast(0)

    fun status(originalMinor: Long, paidMinor: Long, dueEpochDay: Long?, today: LocalDate): DebtStatus = when {
        paidMinor >= originalMinor -> DebtStatus.SETTLED
        dueEpochDay != null && LocalDate.ofEpochDay(dueEpochDay).isBefore(today) -> DebtStatus.OVERDUE
        else -> DebtStatus.OPEN
    }

    fun isValidPayment(amountMinor: Long, originalMinor: Long, paidMinor: Long): Boolean =
        amountMinor > 0 && amountMinor <= remaining(originalMinor, paidMinor)
}

data class GoalEstimate(val estimatedDate: LocalDate, val monthlyRateMinor: Long)

object GoalMath {
    private const val MIN_HISTORY_DAYS = 30L

    fun fraction(savedMinor: Long, targetMinor: Long): Float =
        if (targetMinor <= 0) 0f else (savedMinor.toDouble() / targetMinor).toFloat().coerceIn(0f, 1f)

    /**
     * Projects from the actual saving pace. Returns null when history is under 30 days or the pace is not
     * positive: a projection from less data would be a guess, not an estimate.
     */
    fun estimate(entries: List<Pair<LocalDate, Long>>, targetMinor: Long, today: LocalDate): GoalEstimate? {
        if (entries.isEmpty()) return null
        val saved = entries.sumOf { it.second }
        if (saved >= targetMinor) return null
        val first = entries.minOf { it.first }
        val days = ChronoUnit.DAYS.between(first, today)
        if (days < MIN_HISTORY_DAYS || saved <= 0) return null
        val perDay = saved.toDouble() / days
        val daysLeft = Math.ceil((targetMinor - saved) / perDay).toLong()
        return GoalEstimate(today.plusDays(daysLeft), (perDay * 30).toLong())
    }
}
