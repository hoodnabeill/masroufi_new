package com.masroufi.app.feature.subscriptions

import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.domain.Frequency
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyEquivalentTest {
    private fun rule(amount: Long, frequency: Frequency, interval: Int = 1) = RecurringRule(
        id = "r", type = TransactionType.EXPENSE, amountMinor = amount, currency = "EGP", walletId = "w",
        frequency = frequency, interval = interval, startEpochDay = 0, updatedAt = 0,
    )

    @Test fun monthlyStaysAsIs() = assertEquals(150_00L, monthlyEquivalent(rule(150_00, Frequency.MONTHLY)))

    @Test fun yearlyIsSplitOverTwelveMonths() = assertEquals(100_00L, monthlyEquivalent(rule(1_200_00, Frequency.YEARLY)))

    @Test fun everyTwoMonthsHalves() = assertEquals(50_00L, monthlyEquivalent(rule(100_00, Frequency.MONTHLY, interval = 2)))

    @Test fun weeklyScalesToAMonth() = assertEquals(434_81L, monthlyEquivalent(rule(100_00, Frequency.WEEKLY)))
}
