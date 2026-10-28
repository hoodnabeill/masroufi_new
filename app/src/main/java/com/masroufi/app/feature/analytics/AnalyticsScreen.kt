package com.masroufi.app.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.core.DateWindow
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.DebtDirection
import com.masroufi.app.data.model.MonthTotal
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.BudgetRepository
import com.masroufi.app.data.repo.BudgetWithProgress
import com.masroufi.app.data.repo.CategoryRepository
import com.masroufi.app.data.repo.DebtRepository
import com.masroufi.app.data.repo.GoalRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.domain.BudgetStatus
import com.masroufi.app.domain.DebtMath
import com.masroufi.app.domain.Frequency
import com.masroufi.app.feature.home.debtLabel
import com.masroufi.app.ui.components.BarGroup
import com.masroufi.app.ui.components.ChartLegend
import com.masroufi.app.ui.components.ChartSlice
import com.masroufi.app.ui.components.DonutChart
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.GroupedBarChart
import com.masroufi.app.ui.components.LabeledRow
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AnalyticsState(
    val loading: Boolean = true,
    val months: Int = 6,
    val currency: String = "EGP",
    val monthly: List<MonthTotal> = emptyList(),
    val byCategory: List<Pair<Category?, Long>> = emptyList(),
    val budgets: List<BudgetWithProgress> = emptyList(),
    val savedMinor: Long = 0,
    val targetMinor: Long = 0,
    val monthlyRecurringExpense: Long = 0,
    val monthlyRecurringIncome: Long = 0,
    val iOwe: Long = 0,
    val owedToMe: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    goals: GoalRepository,
    recurring: RecurringRepository,
    debts: DebtRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val months = MutableStateFlow(6)

    private val ranged = months.flatMapLatest { m ->
        val now = YearMonth.now(clock)
        val start = DateWindow.month(now.minusMonths((m - 1).toLong()), clock.zone)
        val end = DateWindow.month(now, clock.zone)
        combine(
            transactions.observeMonthlyTotals(start.startMillis),
            transactions.observeExpenseByCategory(start.startMillis, end.endMillis),
        ) { monthly, byCat -> Triple(m, monthly, byCat) }
    }

    private val planning = combine(budgets.observeWithProgress(), goals.observeGoals(), recurring.observeAll(), debts.observeDebts()) { b, g, r, d ->
        Plan(b, g, r, d)
    }

    val state: StateFlow<AnalyticsState> = combine(ranged, planning, categories.observeAll(), settings.settings) { (m, monthly, byCat), (b, g, r, d), cats, s ->
        val currency = s.primaryCurrency
        val catMap = cats.associateBy { it.id }
        val activeRules = r.filter { it.active && it.currency == currency }
        AnalyticsState(
            loading = false,
            months = m,
            currency = currency,
            monthly = monthly.filter { it.currency == currency },
            byCategory = byCat.filter { it.currency == currency }
                .groupBy { row -> row.categoryId?.let { catMap[it]?.parentId ?: it } }
                .map { (id, rows) -> (id?.let(catMap::get)) to rows.sumOf { it.total } }
                .sortedByDescending { it.second },
            budgets = b,
            savedMinor = g.filter { it.goal.currency == currency }.sumOf { it.savedMinor },
            targetMinor = g.filter { it.goal.currency == currency }.sumOf { it.goal.targetMinor },
            monthlyRecurringExpense = activeRules.filter { it.type == TransactionType.EXPENSE }.sumOf { monthlyEquivalent(it.amountMinor, it.frequency, it.interval) },
            monthlyRecurringIncome = activeRules.filter { it.type == TransactionType.INCOME }.sumOf { monthlyEquivalent(it.amountMinor, it.frequency, it.interval) },
            iOwe = d.filter { it.debt.direction == DebtDirection.I_OWE && it.debt.currency == currency }.sumOf { DebtMath.remaining(it.debt.originalMinor, it.paidMinor) },
            owedToMe = d.filter { it.debt.direction == DebtDirection.OWED_TO_ME && it.debt.currency == currency }.sumOf { DebtMath.remaining(it.debt.originalMinor, it.paidMinor) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun setMonths(value: Int) {
        months.value = value
    }

    companion object {
        /** Approximation used only for the "about per month" figure; labelled as an estimate in the UI. */
        fun monthlyEquivalent(amount: Long, frequency: Frequency, interval: Int): Long {
            val i = interval.coerceAtLeast(1)
            return when (frequency) {
                Frequency.DAILY -> amount * 30 / i
                Frequency.WEEKLY -> amount * 52 / 12 / i
                Frequency.MONTHLY -> amount / i
                Frequency.YEARLY -> amount / 12 / i
            }
        }
    }
}

@Composable
fun AnalyticsScreen(onBack: () -> Unit, viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = LocalMoneyFormatter.current
    val finance = LocalFinanceColors.current
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("MMM", locale)

    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.analytics_title), onBack = onBack) }) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SegmentedChoice(listOf(1, 3, 6, 12), state.months, viewModel::setMonths, { stringResource(R.string.analytics_months, it) })
            }
            val months = state.monthly.map { it.month }.distinct().sorted()
            if (months.isEmpty()) {
                item { EmptyState(MIcons.Insights, stringResource(R.string.analytics_empty_title), stringResource(R.string.analytics_empty_body)) }
                return@LazyColumn
            }
            item {
                val income = state.monthly.filter { it.type == TransactionType.INCOME }.sumOf { it.total }
                val expense = state.monthly.filter { it.type == TransactionType.EXPENSE }.sumOf { it.total }
                PressableCard(onClick = null) {
                    Text(stringResource(R.string.analytics_income_vs_expense), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    GroupedBarChart(
                        groups = months.map { key ->
                            BarGroup(
                                YearMonth.parse(key).format(monthFormat),
                                listOf(
                                    state.monthly.filter { it.month == key && it.type == TransactionType.INCOME }.sumOf { it.total },
                                    state.monthly.filter { it.month == key && it.type == TransactionType.EXPENSE }.sumOf { it.total },
                                ),
                            )
                        },
                        colors = listOf(finance.income, finance.expense),
                        description = stringResource(R.string.analytics_income_vs_expense),
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledRow(stringResource(R.string.type_income)) { MoneyText(income, state.currency, color = finance.income) }
                    LabeledRow(stringResource(R.string.type_expense)) { MoneyText(expense, state.currency, color = finance.expense) }
                    LabeledRow(stringResource(R.string.analytics_net_cash_flow)) {
                        MoneyText(income - expense, state.currency, color = if (income >= expense) finance.income else finance.expense, signed = true)
                    }
                    LabeledRow(stringResource(R.string.analytics_avg_monthly_expense)) { MoneyText(expense / months.size, state.currency) }
                }
            }
            if (state.byCategory.isNotEmpty()) {
                item {
                    val slices = state.byCategory.take(8).map { (cat, total) -> ChartSlice(categoryLabel(cat), total, categoryColor(cat?.colorIndex)) }
                    PressableCard(onClick = null) {
                        Text(stringResource(R.string.section_spending), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            DonutChart(slices, Modifier.size(180.dp), description = slices.joinToString { it.label })
                        }
                        Spacer(Modifier.height(12.dp))
                        ChartLegend(slices, valueText = { money.format(it.value, state.currency) })
                    }
                }
            }
            if (state.budgets.isNotEmpty()) {
                item {
                    PressableCard(onClick = null) {
                        Text(stringResource(R.string.analytics_budget_adherence), style = MaterialTheme.typography.titleMedium)
                        val within = state.budgets.count { it.progress.status != BudgetStatus.OVER }
                        Text(stringResource(R.string.analytics_budgets_within, within, state.budgets.size), style = MaterialTheme.typography.bodyMedium)
                        state.budgets.forEach { b ->
                            LabeledRow(b.budget.name) {
                                Text("${(b.progress.fraction * 100).toInt()}%", color = if (b.progress.status == BudgetStatus.OVER) finance.expense else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            item {
                PressableCard(onClick = null) {
                    Text(stringResource(R.string.analytics_obligations), style = MaterialTheme.typography.titleMedium)
                    LabeledRow(stringResource(R.string.analytics_savings_progress)) {
                        Text("${money.format(state.savedMinor, state.currency)} / ${money.format(state.targetMinor, state.currency)}")
                    }
                    LabeledRow(stringResource(R.string.analytics_recurring_expense)) { MoneyText(state.monthlyRecurringExpense, state.currency) }
                    LabeledRow(stringResource(R.string.analytics_recurring_income)) { MoneyText(state.monthlyRecurringIncome, state.currency) }
                    LabeledRow(stringResource(debtLabel(DebtDirection.I_OWE))) { MoneyText(state.iOwe, state.currency, color = finance.expense) }
                    LabeledRow(stringResource(debtLabel(DebtDirection.OWED_TO_ME))) { MoneyText(state.owedToMe, state.currency, color = finance.income) }
                    Text(stringResource(R.string.analytics_estimate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class Plan(
    val budgets: List<BudgetWithProgress>,
    val goals: List<com.masroufi.app.data.model.GoalWithSaved>,
    val recurring: List<com.masroufi.app.data.model.RecurringRule>,
    val debts: List<com.masroufi.app.data.model.DebtWithPaid>,
)
