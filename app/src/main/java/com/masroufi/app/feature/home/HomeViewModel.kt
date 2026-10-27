package com.masroufi.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.DateWindow
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.DebtDirection
import com.masroufi.app.data.model.GoalWithSaved
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.WalletWithBalance
import com.masroufi.app.data.prefs.AppSettings
import com.masroufi.app.data.prefs.DashboardSection
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.BudgetRepository
import com.masroufi.app.data.repo.BudgetWithProgress
import com.masroufi.app.data.repo.CategoryRepository
import com.masroufi.app.data.repo.DebtRepository
import com.masroufi.app.data.repo.GoalRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.data.repo.TransactionFilter
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.domain.DebtMath
import com.masroufi.app.domain.Insight
import com.masroufi.app.domain.InsightEngine
import com.masroufi.app.domain.InsightInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class CurrencyTotal(val currency: String, val minor: Long)

data class HomeUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val wallets: List<WalletWithBalance> = emptyList(),
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val spending: List<Pair<Category?, Long>> = emptyList(),
    val budgets: List<BudgetWithProgress> = emptyList(),
    val goals: List<GoalWithSaved> = emptyList(),
    val upcoming: List<RecurringRule> = emptyList(),
    val iOweMinor: Long = 0,
    val owedToMeMinor: Long = 0,
    val recent: List<TransactionRow> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
) {
    val currency: String get() = settings.primaryCurrency

    /** Balances are totalled per currency; different currencies are never added together. */
    val balances: List<CurrencyTotal>
        get() = wallets.filter { !it.wallet.archived }
            .groupBy { it.wallet.currency }
            .map { (currency, list) -> CurrencyTotal(currency, list.sumOf { it.balanceMinor }) }
            .sortedByDescending { it.currency == settings.primaryCurrency }

    val primaryBalance: Long get() = balances.firstOrNull { it.currency == currency }?.minor ?: 0
    val otherBalances: List<CurrencyTotal> get() = balances.filter { it.currency != currency }
    fun visible(section: DashboardSection) = section !in settings.hiddenSections
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    wallets: WalletRepository,
    transactions: TransactionRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    goals: GoalRepository,
    recurring: RecurringRepository,
    debts: DebtRepository,
    private val settings: SettingsRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val month = DateWindow.month(YearMonth.from(today), clock.zone)
    private val previousSameDays = DateWindow.days(
        YearMonth.from(today).minusMonths(1).atDay(1),
        today.minusMonths(1),
        clock.zone,
    )

    private val core = combine(
        settings.settings,
        wallets.observeWallets(),
        transactions.observeTypeTotals(month.startMillis, month.endMillis),
        transactions.observeExpenseByCategory(month.startMillis, month.endMillis),
        categories.observeAll(),
    ) { s, w, totals, byCategory, cats ->
        val catMap = cats.associateBy { it.id }
        val primary = s.primaryCurrency
        HomeUiState(
            loading = false,
            settings = s,
            wallets = w,
            incomeMinor = totals.filter { it.type == TransactionType.INCOME && it.currency == primary }.sumOf { it.total },
            expenseMinor = totals.filter { it.type == TransactionType.EXPENSE && it.currency == primary }.sumOf { it.total },
            spending = byCategory.filter { it.currency == primary }
                .groupBy { id -> id.categoryId?.let { catMap[it]?.parentId ?: it } }
                .map { (id, list) -> (id?.let(catMap::get)) to list.sumOf { it.total } }
                .sortedByDescending { it.second },
            categories = catMap,
        )
    }

    private val planning = combine(
        budgets.observeWithProgress(),
        goals.observeGoals(),
        recurring.observeAll(),
        debts.observeDebts(),
        transactions.observeRows(TransactionFilter(limit = 6)),
    ) { b, g, r, d, recent ->
        val horizon = today.plusDays(14).toEpochDay()
        Planning(
            budgets = b,
            goals = g.filter { !it.goal.archived },
            upcoming = r.filter { it.active && (it.nextEpochDay ?: Long.MAX_VALUE) <= horizon }.sortedBy { it.nextEpochDay },
            debts = d.map { it.debt.direction to (it.debt.currency to DebtMath.remaining(it.debt.originalMinor, it.paidMinor)) },
            recent = recent,
        )
    }

    private val insightData = combine(
        transactions.observeExpenseByCategory(previousSameDays.startMillis, previousSameDays.endMillis),
        transactions.observeFirstOccurredAt(),
    ) { previous, first -> previous to first }

    val state: StateFlow<HomeUiState> = combine(core, planning, insightData) { c, p, (previous, first) ->
        val primary = c.currency
        val current = c.spending.associate { (cat, total) -> cat?.id to total }
        val previousByCategory = previous.filter { it.currency == primary }
            .groupBy { row -> row.categoryId?.let { c.categories[it]?.parentId ?: it } }
            .mapValues { (_, rows) -> rows.sumOf { it.total } }
        val months = first?.let { ChronoUnit.MONTHS.between(YearMonth.from(it.toLocalDate()), YearMonth.from(today)).toInt() } ?: 0
        val insights = InsightEngine.generate(
            InsightInput(
                today = today,
                currency = primary,
                currentByCategory = current,
                previousSameDaysByCategory = previousByCategory,
                monthsWithHistory = months,
                upcoming = p.upcoming.take(3).mapNotNull { rule ->
                    rule.nextEpochDay?.let { Insight.UpcomingRecurring(rule.note, rule.amountMinor, rule.currency, LocalDate.ofEpochDay(it)) }
                },
                budgetAlerts = p.budgets.map { Insight.BudgetAlert(it.budget.name, it.progress.status, it.progress.remainingMinor, it.budget.currency) },
            ),
        )
        c.copy(
            budgets = p.budgets,
            goals = p.goals,
            upcoming = p.upcoming,
            iOweMinor = p.debts.filter { it.first == DebtDirection.I_OWE && it.second.first == primary }.sumOf { it.second.second },
            owedToMeMinor = p.debts.filter { it.first == DebtDirection.OWED_TO_ME && it.second.first == primary }.sumOf { it.second.second },
            recent = p.recent,
            insights = insights,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setSectionVisible(section: DashboardSection, visible: Boolean) {
        viewModelScope.launch {
            val hidden = state.value.settings.hiddenSections
            settings.setHiddenSections(if (visible) hidden - section else hidden + section)
        }
    }

    private data class Planning(
        val budgets: List<BudgetWithProgress>,
        val goals: List<GoalWithSaved>,
        val upcoming: List<RecurringRule>,
        val debts: List<Pair<DebtDirection, Pair<String, Long>>>,
        val recent: List<TransactionRow>,
    )
}
