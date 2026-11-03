package com.masroufi.app.data.repo

import com.masroufi.app.core.Money
import com.masroufi.app.core.startMillis
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.domain.PayCycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Today's number in the primary currency: what is safe to spend today, and what has been spent so far. */
data class TodayBudget(
    val currency: String,
    /** The daily number, in whole pounds rounded down so it never overstates what is safe. */
    val safeMinor: Long,
    val spentMinor: Long,
    val balanceMinor: Long,
    val days: Int,
) {
    val remainingMinor: Long get() = safeMinor - spentMinor

    /** The daily number once an income of [amountMinor] lands. */
    fun afterIncome(amountMinor: Long): Long = wholeUnits((balanceMinor + spentMinor + amountMinor) / days, currency)

    companion object {
        fun wholeUnits(minor: Long, currency: String): Long {
            var unit = 1L
            repeat(Money.fractionDigits(currency)) { unit *= 10 }
            return minor.coerceAtLeast(0) / unit * unit
        }
    }
}

@Singleton
class TodayRepository @Inject constructor(
    private val wallets: WalletRepository,
    private val transactions: TransactionRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    fun observe(): Flow<TodayBudget> {
        val today = LocalDate.now(clock)
        return combine(
            wallets.observeWallets(),
            transactions.observeTypeTotals(today.startMillis(clock.zone), today.plusDays(1).startMillis(clock.zone)),
            settings.settings,
        ) { w, totals, s ->
            val currency = s.primaryCurrency
            val balance = w.filter { !it.wallet.archived && it.wallet.currency == currency }.sumOf { it.balanceMinor }
            val spent = totals.filter { it.type == TransactionType.EXPENSE && it.currency == currency }.sumOf { it.total }
            TodayBudget(
                currency = currency,
                safeMinor = TodayBudget.wholeUnits(PayCycle.safeToday(balance, spent, today, s.paydayDay), currency),
                spentMinor = spent,
                balanceMinor = balance,
                days = PayCycle.daysUntilNextIncome(today, s.paydayDay),
            )
        }
    }
}
