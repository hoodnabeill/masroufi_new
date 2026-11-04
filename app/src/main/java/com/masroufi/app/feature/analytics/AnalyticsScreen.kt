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
import java.time.LocalDate
import com.masroufi.app.ui.theme.AmountStyle
import com.masroufi.app.ui.components.staggerIn
import com.masroufi.app.ui.components.glassInset
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.FitAmount
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
    /** Last 12 weeks of spending per day in the primary currency. */
    val daily: Map<java.time.LocalDate, Long> = emptyMap(),
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

    val state: StateFlow<AnalyticsState> = combine(
        ranged,
        planning,
        categories.observeAll(),
        settings.settings,
        transactions.observeDailyExpense(java.time.LocalDate.now(clock).minusDays(HEATMAP_DAYS.toLong()).atStartOfDay(clock.zone).toInstant().toEpochMilli()),
    ) { (m, monthly, byCat), (b, g, r, d), cats, s, daily ->
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
            daily = daily.filter { it.currency == currency }.associate { java.time.LocalDate.parse(it.day) to it.total },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun setMonths(value: Int) {
        months.value = value
    }

    companion object {
        const val HEATMAP_DAYS = 84

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
fun AnalyticsScreen(
    onBack: () -> Unit,
    onEmergencyGoal: () -> Unit = {},
    onLog: () -> Unit = {},
    onBudgets: () -> Unit = {},
    onRecap: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel(),
    insightsViewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val insights by insightsViewModel.state.collectAsStateWithLifecycle()
    val money = LocalMoneyFormatter.current
    val finance = LocalFinanceColors.current
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("MMM", locale)

    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.analytics_title), onBack = onBack) }) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            insights?.let { s ->
                item(key = "health") { HealthCard(s) }
                item(key = "raise") { RaiseCard(s, onEmergencyGoal, onLog, onBudgets) }
                item(key = "forecast") { ForecastCard(s) }
                item(key = "ask") { AskCard(s) }
            }
            item(key = "recap") {
                com.masroufi.app.ui.components.GlassSecondaryButton(onClick = onRecap, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recap_open))
                }
            }
            item {
                SegmentedChoice(listOf(1, 3, 6, 12), state.months, viewModel::setMonths, { stringResource(R.string.analytics_months, it) })
            }
            val months = state.monthly.map { it.month }.distinct().sorted()
            if (months.isEmpty()) {
                item { EmptyState(MIcons.Insights, stringResource(R.string.analytics_empty_title), stringResource(R.string.analytics_empty_body)) }
                return@LazyColumn
            }
            val incomes = months.map { key -> state.monthly.filter { it.month == key && it.type == TransactionType.INCOME }.sumOf { it.total } }
            val expenses = months.map { key -> state.monthly.filter { it.month == key && it.type == TransactionType.EXPENSE }.sumOf { it.total } }
            val income = incomes.sum()
            val expense = expenses.sum()
            item(key = "net") {
                val positive = income >= expense
                PressableCard(onClick = null, modifier = Modifier.staggerIn(0), color = (if (positive) finance.income else finance.expense).copy(alpha = 0.12f)) {
                    Text(stringResource(R.string.analytics_net_cash_flow), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FitAmount(income - expense, state.currency, style = AmountStyle, color = if (positive) finance.income else finance.expense, signed = true)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatPill(stringResource(R.string.type_income), income, state.currency, finance.income, MIcons.Income, Modifier.weight(1f))
                        StatPill(stringResource(R.string.type_expense), expense, state.currency, finance.expense, MIcons.Expense, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    LabeledRow(stringResource(R.string.analytics_avg_monthly_expense)) { MoneyText(expense / months.size, state.currency) }
                }
            }
            item(key = "flow") {
                PressableCard(onClick = null, modifier = Modifier.staggerIn(1)) {
                    Text(stringResource(R.string.analytics_income_vs_expense), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.analytics_scrub_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    CashFlowChart(
                        labels = months.map { YearMonth.parse(it).format(monthFormat) },
                        income = incomes,
                        expense = expenses,
                        currency = state.currency,
                        incomeColor = finance.income,
                        expenseColor = finance.expense,
                        description = stringResource(R.string.analytics_income_vs_expense),
                    )
                }
            }
            if (state.byCategory.isNotEmpty()) {
                item(key = "donut") {
                    val slices = state.byCategory.take(8).map { (cat, total) -> ChartSlice(categoryLabel(cat), total, categoryColor(cat?.colorIndex)) }
                    PressableCard(onClick = null, modifier = Modifier.staggerIn(2)) {
                        Text(stringResource(R.string.section_spending), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        val shareFormat = stringResource(R.string.analytics_share)
                        SpendingDonut(
                            slices = slices,
                            currency = state.currency,
                            totalLabel = stringResource(R.string.type_expense),
                            shareLabel = { percent -> String.format(locale, shareFormat, percent) },
                            valueText = { money.format(it.value, state.currency) },
                            description = slices.joinToString { it.label },
                        )
                    }
                }
            }
            item(key = "heatmap") {
                PressableCard(onClick = null, modifier = Modifier.staggerIn(3)) {
                    Text(stringResource(R.string.analytics_heatmap), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.analytics_heatmap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    SpendHeatmap(state.daily, LocalDate.now(), MaterialTheme.colorScheme.primary, stringResource(R.string.analytics_heatmap))
                }
            }
            if (state.budgets.isNotEmpty()) {
                item(key = "budgets") {
                    PressableCard(onClick = null) {
                        Text(stringResource(R.string.analytics_budget_adherence), style = MaterialTheme.typography.titleMedium)
                        val within = state.budgets.count { it.progress.status != BudgetStatus.OVER }
                        Text(stringResource(R.string.analytics_budgets_within, within, state.budgets.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.budgets.forEach { b ->
                            val over = b.progress.status == BudgetStatus.OVER
                            Spacer(Modifier.height(12.dp))
                            LabeledRow(b.budget.name) {
                                Text("${(b.progress.fraction * 100).toInt()}%", color = if (over) finance.over else MaterialTheme.colorScheme.onSurface)
                            }
                            AnimatedProgressBar(b.progress.fraction, if (over) finance.over else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item(key = "obligations") {
                PressableCard(onClick = null) {
                    Text(stringResource(R.string.analytics_obligations), style = MaterialTheme.typography.titleMedium)
                    LabeledRow(stringResource(R.string.analytics_savings_progress)) {
                        Text("${money.format(state.savedMinor, state.currency)} / ${money.format(state.targetMinor, state.currency)}")
                    }
                    if (state.targetMinor > 0) AnimatedProgressBar(state.savedMinor.toFloat() / state.targetMinor, MaterialTheme.colorScheme.primary)
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

@Composable
private fun StatPill(
    label: String,
    minor: Long,
    currency: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
) {
    Row(modifier.glassInset(MaterialTheme.shapes.medium).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, size = 34.dp)
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedAmount(minor, currency, style = MaterialTheme.typography.titleSmall)
        }
    }
}

private data class Plan(
    val budgets: List<BudgetWithProgress>,
    val goals: List<com.masroufi.app.data.model.GoalWithSaved>,
    val recurring: List<com.masroufi.app.data.model.RecurringRule>,
    val debts: List<com.masroufi.app.data.model.DebtWithPaid>,
)
