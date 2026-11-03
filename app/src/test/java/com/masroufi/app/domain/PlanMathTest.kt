package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanMathTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test fun limitsRoundUpToFiftiesThenHundreds() {
        assertEquals(35_000L, BudgetPlan.niceLimit(32_000, 100))
        assertEquals(60_000L, BudgetPlan.niceLimit(58_000, 100))
        assertEquals(130_000L, BudgetPlan.niceLimit(123_000, 100))
        assertEquals(0L, BudgetPlan.niceLimit(0, 100))
    }

    @Test fun suggestsTopUnbudgetedCategories() {
        val recent = mapOf("food" to 120_000L, "mobile" to 58_000L, "transport" to 90_000L, "fun" to 5_000L)
        val month = mapOf("food" to 32_000L, "mobile" to 58_000L)
        val s = BudgetPlan.suggest(recent, month, budgeted = setOf("transport"), unit = 100)
        assertEquals(listOf("food", "mobile", "fun"), s.map { it.categoryId })
        assertEquals(32_000L, s.first().spentMinor)
    }

    @Test fun boostPullsTheFinishDateIn() {
        // 11,250 left at 2,000 a month; +20 a day makes it 2,600.
        val base = GoalBoost.finish(1_125_000, 200_000, 0, today)!!
        val boosted = GoalBoost.finish(1_125_000, 200_000, 2_000, today)!!
        assertEquals(today.plusDays(172), base)
        assertEquals(today.plusDays(132), boosted)
        assertNull(GoalBoost.finish(1_000, 0, 0, today))
    }

    @Test fun monthlyNeededAndPace() {
        // 181 days is a little under six months, so a bit over 2,000 a month.
        assertTrue(GoalBoost.monthlyFor(1_200_000, today, today.plusMonths(6))!! in 200_000L..205_000L)
        assertNull(GoalBoost.monthlyFor(1_000, today, today))
        assertEquals(true, GoalBoost.onTrack(3_750, 15_000, today.minusDays(30), today.plusDays(200), today))
        assertEquals(false, GoalBoost.onTrack(0, 15_000, today.minusDays(30), today.plusDays(30), today))
        assertNull(GoalBoost.onTrack(0, 15_000, today, null, today))
    }
}
