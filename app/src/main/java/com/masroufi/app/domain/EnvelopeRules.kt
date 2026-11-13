package com.masroufi.app.domain

import com.masroufi.app.core.ceilDiv
import com.masroufi.app.core.floorToPound
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun envelopeLeft(monthly: Long, spentThisCycle: Long): Long = monthly - spentThisCycle

/** Day the envelope hits zero at the current average pace, or null when unknown or after payday. */
fun runsOutDay(
    left: Long,
    spentThisCycle: Long,
    daysWithData: Int,
    today: LocalDate,
    nextPayday: LocalDate,
): LocalDate? {
    if (daysWithData < 5 || spentThisCycle <= 0 || left <= 0) return null
    // ceil(left / (spent / days)) without fractions
    val days = Math.multiplyExact(left, daysWithData.toLong()).ceilDiv(spentThisCycle)
    if (days >= ChronoUnit.DAYS.between(today, nextPayday)) return null
    return today.plusDays(days)
}

sealed interface SplitRule {
    data class Fixed(val envelopeId: Long, val amount: Long) : SplitRule
    data class Percent(val envelopeId: Long, val percent: Int) : SplitRule
}

data class SplitResult(val shares: Map<Long, Long>, val unassigned: Long)

fun splitIncome(income: Long, rules: List<SplitRule>): SplitResult {
    require(income >= 0) { "income must be >= 0" }
    var percentTotal = 0
    for (r in rules) when (r) {
        is SplitRule.Fixed -> require(r.amount >= 0) { "fixed amount must be >= 0" }
        is SplitRule.Percent -> {
            require(r.percent in 0..100) { "percent must be in 0..100" }
            percentTotal += r.percent
        }
    }
    require(percentTotal <= 100) { "percent total must be <= 100" }

    val shares = LinkedHashMap<Long, Long>()
    rules.forEach { shares.putIfAbsent(it.envelopeId, 0L) }
    var remaining = income
    for (r in rules) if (r is SplitRule.Fixed) {
        val share = minOf(r.amount, remaining)
        remaining -= share
        shares.merge(r.envelopeId, share, Long::plus)
    }
    val base = remaining
    for (r in rules) if (r is SplitRule.Percent) {
        val share = Math.floorDiv(base * r.percent, 100L).floorToPound()
        shares.merge(r.envelopeId, share, Long::plus)
    }
    return SplitResult(shares, income - shares.values.sum())
}
