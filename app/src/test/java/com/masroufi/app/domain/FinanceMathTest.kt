package com.masroufi.app.domain

import com.masroufi.app.core.Money
import com.masroufi.app.data.model.Gameya
import com.masroufi.app.data.model.GameyaMember
import com.masroufi.app.data.model.GameyaPayment
import com.masroufi.app.data.model.GameyaPayout
import com.masroufi.app.data.model.SplitItem
import com.masroufi.app.data.model.SplitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    @Test fun parsesWesternAndArabicDigits() {
        assertEquals(12_550L, Money.parse("125.5", "EGP"))
        assertEquals(12_550L, Money.parse("١٢٥٫٥", "EGP"))
        assertEquals(100_000L, Money.parse("1,000", "EGP"))
    }

    @Test fun rejectsTooManyDecimalsAndNegatives() {
        assertNull(Money.parse("1.234", "EGP"))
        assertNull(Money.parse("-5", "EGP"))
        assertNull(Money.parse("abc", "EGP"))
        assertNull(Money.parse("", "EGP"))
    }

    @Test fun respectsCurrencyPrecision() {
        assertEquals(1_234L, Money.parse("1.234", "KWD"))
        assertEquals("1.234", Money.toPlainString(1_234, "KWD"))
        assertEquals("10", Money.toPlainString(1_000, "EGP"))
        assertEquals("10.5", Money.toPlainString(1_050, "EGP"))
    }
}

class RecurrenceTest {
    private val jan31 = LocalDate.of(2026, 1, 31)

    @Test fun monthlyFromThe31stClampsAndRecovers() {
        val dates = Recurrence.due(jan31, Frequency.MONTHLY, 1, null, null, LocalDate.of(2026, 5, 31))
        assertEquals(
            listOf(jan31, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31)),
            dates,
        )
    }

    @Test fun dueSkipsAlreadyGeneratedAndRespectsEnd() {
        val start = LocalDate.of(2026, 1, 1)
        val due = Recurrence.due(start, Frequency.WEEKLY, 2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1))
        assertEquals(listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 29)), due)
    }

    @Test fun repeatedRunsProduceNothingNew() {
        val start = LocalDate.of(2026, 1, 1)
        val today = LocalDate.of(2026, 1, 10)
        val first = Recurrence.due(start, Frequency.DAILY, 1, null, null, today)
        val second = Recurrence.due(start, Frequency.DAILY, 1, null, first.last(), today)
        assertEquals(10, first.size)
        assertTrue(second.isEmpty())
    }

    @Test fun nextAfterStopsAtEnd() {
        assertNull(Recurrence.nextAfter(LocalDate.of(2026, 1, 1), Frequency.YEARLY, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)))
    }
}

class BudgetProgressTest {
    @Test fun statusThresholds() {
        assertEquals(BudgetStatus.ON_TRACK, BudgetProgress.of(10_000, 7_999, null, false).status)
        assertEquals(BudgetStatus.WARNING, BudgetProgress.of(10_000, 8_000, null, false).status)
        assertEquals(BudgetStatus.OVER, BudgetProgress.of(10_000, 10_001, null, false).status)
    }

    @Test fun rolloverCarriesUnspentAndOverspent() {
        val unspent = BudgetProgress.of(10_000, 2_000, previousSpentMinor = 6_000, rollover = true)
        assertEquals(4_000, unspent.carriedMinor)
        assertEquals(12_000, unspent.remainingMinor)
        val overspent = BudgetProgress.of(10_000, 2_000, previousSpentMinor = 13_000, rollover = true)
        assertEquals(-3_000, overspent.carriedMinor)
        assertEquals(5_000, overspent.remainingMinor)
        assertEquals(0, BudgetProgress.of(10_000, 0, 6_000, rollover = false).carriedMinor)
    }
}

class SplitCalculatorTest {
    private fun pct(label: String, percent: Long) = SplitItem(label, SplitKind.PERCENT, percent * 100)

    @Test fun fixedFirstThenPercentOfRemainder() {
        val result = SplitCalculator.calculate(
            1_000_000,
            listOf(SplitItem("Rent", SplitKind.FIXED, 400_000), pct("Savings", 50), pct("Fun", 25)),
        ) as SplitResult.Ok
        assertEquals(listOf(400_000L, 300_000L, 150_000L), result.lines.map { it.amountMinor })
        assertEquals(150_000L, result.unallocatedMinor)
    }

    @Test fun roundingNeverAllocatesMoreThanIncome() {
        val result = SplitCalculator.calculate(100, listOf(pct("A", 33), pct("B", 33), pct("C", 34))) as SplitResult.Ok
        assertEquals(100L, result.lines.sumOf { it.amountMinor } + result.unallocatedMinor)
        assertTrue(result.unallocatedMinor >= 0)
    }

    @Test fun rejectsInvalidRules() {
        assertTrue(SplitCalculator.calculate(100, listOf(SplitItem("x", SplitKind.FIXED, 101))) is SplitResult.FixedExceedsIncome)
        assertTrue(SplitCalculator.calculate(100, listOf(pct("a", 60), pct("b", 50))) is SplitResult.PercentOver100)
        assertEquals(SplitResult.Empty, SplitCalculator.calculate(100, emptyList()))
    }
}

class DebtAndGoalMathTest {
    private val today = LocalDate.of(2026, 3, 10)

    @Test fun partialRepaymentsAndStatus() {
        assertEquals(7_000, DebtMath.remaining(10_000, 3_000))
        assertTrue(DebtMath.isValidPayment(7_000, 10_000, 3_000))
        assertFalse(DebtMath.isValidPayment(7_001, 10_000, 3_000))
        assertFalse(DebtMath.isValidPayment(0, 10_000, 3_000))
        assertEquals(DebtStatus.OVERDUE, DebtMath.status(10_000, 3_000, today.minusDays(1).toEpochDay(), today))
        assertEquals(DebtStatus.SETTLED, DebtMath.status(10_000, 10_000, today.minusDays(1).toEpochDay(), today))
        assertEquals(DebtStatus.OPEN, DebtMath.status(10_000, 0, null, today))
    }

    @Test fun goalEstimateNeedsHistory() {
        assertNull(GoalMath.estimate(listOf(today.minusDays(10) to 5_000L), 100_000, today))
        val estimate = GoalMath.estimate(listOf(today.minusDays(60) to 30_000L, today.minusDays(30) to 30_000L), 120_000, today)!!
        assertEquals(today.plusDays(60), estimate.estimatedDate)
        assertEquals(1f, GoalMath.fraction(200, 100))
    }
}

class GameyaMathTest {
    private val gameya = Gameya("g", "Family", 100_000, "EGP", monthly = true, startEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(), updatedAt = 0)
    private val members = listOf(
        GameyaMember("a", "g", "Aya", 1, updatedAt = 0),
        GameyaMember("me", "g", "Me", 2, isMe = true, updatedAt = 0),
        GameyaMember("c", "g", "Omar", 3, updatedAt = 0),
    )

    private fun paid(member: String, round: Int) = GameyaPayment("g:$member:$round", "g", member, round, 100_000, 0, 0)

    @Test fun tracksObligationsPayoutAndMissed() {
        val today = LocalDate.of(2026, 2, 15)
        val summary = GameyaMath.summarize(
            gameya,
            members,
            listOf(paid("a", 1), paid("me", 1), paid("c", 1), paid("me", 2)),
            listOf(GameyaPayout("g:1", "g", 1, 300_000, 0, 0)),
            today,
        )
        assertEquals(300_000, summary.potMinor)
        assertEquals(300_000, summary.myTotalObligationMinor)
        assertEquals(200_000, summary.myPaidMinor)
        assertEquals(100_000, summary.myRemainingMinor)
        assertEquals(2, summary.myPayoutRound?.round)
        assertEquals(LocalDate.of(2026, 2, 1), summary.rounds[1].date)
        assertEquals(2, summary.missedContributions)
        assertEquals(1, summary.completedRounds)
        assertEquals(ContributionStatus.PAID, summary.rounds[1].myStatus)
        assertEquals(ContributionStatus.SCHEDULED, summary.rounds[2].myStatus)
    }

    @Test fun removedMembersPaymentsDoNotCount() {
        val summary = GameyaMath.summarize(gameya, members, listOf(paid("ghost", 1)), emptyList(), LocalDate.of(2026, 1, 1))
        assertTrue(summary.rounds[0].paidMemberIds.isEmpty())
    }
}

class InsightEngineTest {
    @Test fun noProjectionWithoutHistory() {
        val insights = InsightEngine.generate(
            InsightInput(LocalDate.of(2026, 3, 20), "EGP", mapOf("food" to 50_000L), mapOf("food" to 20_000L), 0, emptyList(), emptyList()),
        )
        assertTrue(insights.none { it is Insight.MonthEndProjection })
        val rising = insights.filterIsInstance<Insight.CategoryRising>().single()
        assertEquals(150, rising.percent)
    }

    @Test fun projectionOnlyWithEnoughData() {
        val insights = InsightEngine.generate(
            InsightInput(LocalDate.of(2026, 3, 10), "EGP", mapOf(null to 10_000L), emptyMap(), 3, emptyList(), emptyList()),
        )
        assertEquals(31_000L, insights.filterIsInstance<Insight.MonthEndProjection>().single().projectedMinor)
    }
}
