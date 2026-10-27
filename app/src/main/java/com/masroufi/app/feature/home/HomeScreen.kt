@file:OptIn(ExperimentalMaterial3Api::class)

package com.masroufi.app.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.model.DebtDirection
import com.masroufi.app.data.prefs.DashboardSection
import com.masroufi.app.domain.BudgetStatus
import com.masroufi.app.domain.GoalMath
import com.masroufi.app.domain.Insight
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.ChartLegend
import com.masroufi.app.ui.components.ChartSlice
import com.masroufi.app.ui.components.DonutChart
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.TransactionItem
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.navigation.TabInsets
import com.masroufi.app.ui.navigation.sharedContainer
import com.masroufi.app.ui.theme.AmountStyle
import com.masroufi.app.ui.theme.LocalFinanceColors
import java.time.LocalDate

@Composable
fun HomeScreen(
    onAddTransaction: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenWallets: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onCreateWallet: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var customizing by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = TabInsets,
        topBar = {
            MasroufiTopBar(title = stringResource(R.string.app_name)) {
                IconButton(onClick = onOpenAnalytics) { Icon(Icons.Outlined.Insights, contentDescription = stringResource(R.string.analytics_title)) }
                IconButton(onClick = { customizing = true }) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.home_customize)) }
            }
        },
        floatingActionButton = {
            if (state.wallets.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onAddTransaction("EXPENSE") },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_expense)) },
                )
            }
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        if (state.wallets.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                EmptyState(
                    icon = Icons.Outlined.Receipt,
                    title = stringResource(R.string.home_no_wallet_title),
                    body = stringResource(R.string.home_no_wallet_body),
                    actionLabel = stringResource(R.string.wallet_add),
                    onAction = onCreateWallet,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.visible(DashboardSection.BALANCE)) item(key = "balance") { BalanceCard(state, onOpenWallets) }
            if (state.visible(DashboardSection.QUICK_ACTIONS)) item(key = "quick") { QuickActions(onAddTransaction) }
            if (state.visible(DashboardSection.INSIGHTS) && state.insights.isNotEmpty()) {
                item(key = "insights") { InsightsCard(state) }
            }
            if (state.visible(DashboardSection.SPENDING) && state.spending.isNotEmpty()) {
                item(key = "spending") { SpendingCard(state, onOpenAnalytics) }
            }
            if (state.visible(DashboardSection.BUDGETS) && state.budgets.isNotEmpty()) {
                item(key = "budgets") { BudgetsCard(state, onOpenBudgets) }
            }
            if (state.visible(DashboardSection.GOALS) && state.goals.isNotEmpty()) {
                item(key = "goals") { GoalsCard(state, onOpenGoal, onOpenGoals) }
            }
            if (state.visible(DashboardSection.UPCOMING) && state.upcoming.isNotEmpty()) {
                item(key = "upcoming") { UpcomingCard(state, onOpenRecurring) }
            }
            if (state.visible(DashboardSection.DEBTS) && (state.iOweMinor > 0 || state.owedToMeMinor > 0)) {
                item(key = "debts") { DebtsCard(state, onOpenDebts) }
            }
            if (state.visible(DashboardSection.RECENT)) {
                item(key = "recent_header") {
                    SectionHeader(stringResource(R.string.home_recent), action = stringResource(R.string.action_see_all), onAction = onOpenTransactions)
                }
                if (state.recent.isEmpty()) {
                    item(key = "recent_empty") {
                        EmptyState(Icons.Outlined.Receipt, stringResource(R.string.transactions_empty_title), stringResource(R.string.transactions_empty_body))
                    }
                } else {
                    items(state.recent, key = { it.transaction.id }) { row ->
                        PressableCard(onClick = null, modifier = Modifier.animateItem()) {
                            TransactionItem(row, onClick = { onOpenTransaction(row.transaction.id) }, modifier = Modifier.padding(horizontal = 0.dp))
                        }
                    }
                }
            }
        }
    }

    if (customizing) {
        ModalBottomSheet(onDismissRequest = { customizing = false }) {
            Text(stringResource(R.string.home_customize), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            DashboardSection.entries.forEach { section ->
                ListItem(
                    headlineContent = { Text(stringResource(sectionLabel(section))) },
                    trailingContent = {
                        Switch(checked = state.visible(section), onCheckedChange = { viewModel.setSectionVisible(section, it) })
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun sectionLabel(section: DashboardSection): Int = when (section) {
    DashboardSection.BALANCE -> R.string.section_balance
    DashboardSection.QUICK_ACTIONS -> R.string.section_quick_actions
    DashboardSection.SPENDING -> R.string.section_spending
    DashboardSection.BUDGETS -> R.string.tab_budgets
    DashboardSection.GOALS -> R.string.tab_goals
    DashboardSection.UPCOMING -> R.string.section_upcoming
    DashboardSection.DEBTS -> R.string.debts_title
    DashboardSection.INSIGHTS -> R.string.section_insights
    DashboardSection.RECENT -> R.string.home_recent
}

@Composable
private fun BalanceCard(state: HomeUiState, onOpenWallets: () -> Unit) {
    val finance = LocalFinanceColors.current
    PressableCard(onClick = onOpenWallets, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.sharedContainer("wallets")) {
        Text(stringResource(R.string.home_total_balance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        AnimatedAmount(state.primaryBalance, state.currency, style = AmountStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
        state.otherBalances.forEach { other ->
            MoneyText(other.minor, other.currency, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.home_this_month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            FlowStat(Icons.Outlined.ArrowDownward, stringResource(R.string.type_income), state.incomeMinor, state.currency, finance.income, Modifier.weight(1f))
            FlowStat(Icons.Outlined.ArrowUpward, stringResource(R.string.type_expense), state.expenseMinor, state.currency, finance.expense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FlowStat(icon: ImageVector, label: String, minor: Long, currency: String, tint: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, size = 36.dp)
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            AnimatedAmount(minor, currency, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun QuickActions(onAdd: (String) -> Unit) {
    val finance = LocalFinanceColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(Icons.Outlined.ArrowUpward, stringResource(R.string.type_expense), finance.expense, Modifier.weight(1f)) { onAdd("EXPENSE") }
        QuickAction(Icons.Outlined.ArrowDownward, stringResource(R.string.type_income), finance.income, Modifier.weight(1f)) { onAdd("INCOME") }
        QuickAction(Icons.Outlined.SwapHoriz, stringResource(R.string.type_transfer), finance.transfer, Modifier.weight(1f)) { onAdd("TRANSFER") }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    PressableCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, tint)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun InsightsCard(state: HomeUiState) {
    PressableCard(onClick = null, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.section_insights), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Spacer(Modifier.height(8.dp))
        state.insights.take(4).forEach { insight ->
            Text(
                insightText(insight, state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
fun insightText(insight: Insight, state: HomeUiState): String {
    val money = LocalMoneyFormatter.current
    val dates = rememberDateFormatter()
    return when (insight) {
        is Insight.SpendingPace -> when {
            insight.previousSameDaysMinor == 0L -> stringResource(R.string.insight_pace_first, money.format(insight.currentMinor, insight.currency))
            insight.currentMinor > insight.previousSameDaysMinor -> stringResource(
                R.string.insight_pace_more,
                money.format(insight.currentMinor - insight.previousSameDaysMinor, insight.currency),
            )
            else -> stringResource(R.string.insight_pace_less, money.format(insight.previousSameDaysMinor - insight.currentMinor, insight.currency))
        }
        is Insight.CategoryRising -> stringResource(
            R.string.insight_category_rising,
            categoryLabel(state.categories[insight.categoryId]),
            insight.percent,
        )
        is Insight.UpcomingRecurring -> stringResource(
            R.string.insight_upcoming,
            insight.note.ifBlank { stringResource(R.string.recurring_title) },
            money.format(insight.amountMinor, insight.currency),
            insight.date.format(dates),
        )
        is Insight.BudgetAlert -> if (insight.status == BudgetStatus.OVER) {
            stringResource(R.string.insight_budget_over, insight.budgetName)
        } else {
            stringResource(R.string.insight_budget_warning, insight.budgetName, money.format(insight.remainingMinor, insight.currency))
        }
        is Insight.MonthEndProjection -> stringResource(R.string.insight_projection, money.format(insight.projectedMinor, insight.currency))
    }
}

@Composable
private fun SpendingCard(state: HomeUiState, onOpenAnalytics: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val top = state.spending.take(5)
    val rest = state.spending.drop(5).sumOf { it.second }
    val slices = top.map { (cat, total) -> ChartSlice(categoryLabel(cat), total, categoryColor(cat?.colorIndex)) } +
        if (rest > 0) listOf(ChartSlice(stringResource(R.string.category_other_group), rest, MaterialTheme.colorScheme.outline)) else emptyList()
    PressableCard(onClick = onOpenAnalytics) {
        Text(stringResource(R.string.section_spending), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = slices,
                modifier = Modifier.size(132.dp),
                description = slices.joinToString { "${it.label} ${money.format(it.value, state.currency)}" },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.type_expense), style = MaterialTheme.typography.labelSmall)
                    Text(money.formatCompact(state.expenseMinor, state.currency), style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.width(16.dp))
            ChartLegend(slices, valueText = { money.formatCompact(it.value, state.currency) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BudgetsCard(state: HomeUiState, onOpen: () -> Unit) {
    val finance = LocalFinanceColors.current
    PressableCard(onClick = onOpen) {
        Text(stringResource(R.string.tab_budgets), style = MaterialTheme.typography.titleMedium)
        state.budgets.take(3).forEach { item ->
            Spacer(Modifier.height(10.dp))
            Row {
                Text(item.budget.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                MoneyText(item.progress.remainingMinor, item.budget.currency, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            AnimatedProgressBar(
                item.progress.fraction,
                when (item.progress.status) {
                    BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
                    BudgetStatus.WARNING -> finance.warning
                    BudgetStatus.OVER -> finance.expense
                },
            )
        }
    }
}

@Composable
private fun GoalsCard(state: HomeUiState, onOpenGoal: (String) -> Unit, onOpenGoals: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.tab_goals), action = stringResource(R.string.action_see_all), onAction = onOpenGoals)
        state.goals.take(3).forEach { item ->
            PressableCard(onClick = { onOpenGoal(item.goal.id) }, modifier = Modifier.sharedContainer("goal-${item.goal.id}")) {
                Row {
                    Text(item.goal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${(GoalMath.fraction(item.savedMinor, item.goal.targetMinor) * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                AnimatedProgressBar(GoalMath.fraction(item.savedMinor, item.goal.targetMinor), categoryColor(item.goal.colorIndex))
            }
        }
    }
}

@Composable
private fun UpcomingCard(state: HomeUiState, onOpen: () -> Unit) {
    val dates = rememberDateFormatter()
    PressableCard(onClick = onOpen) {
        Text(stringResource(R.string.section_upcoming), style = MaterialTheme.typography.titleMedium)
        state.upcoming.take(4).forEach { rule ->
            Row(Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(rule.note.ifBlank { stringResource(R.string.recurring_title) }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    rule.nextEpochDay?.let { Text(LocalDate.ofEpochDay(it).format(dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                MoneyText(rule.amountMinor, rule.currency, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DebtsCard(state: HomeUiState, onOpen: () -> Unit) {
    val finance = LocalFinanceColors.current
    PressableCard(onClick = onOpen) {
        Text(stringResource(R.string.debts_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(debtLabel(DebtDirection.I_OWE)), style = MaterialTheme.typography.labelMedium)
                MoneyText(state.iOweMinor, state.currency, style = MaterialTheme.typography.titleMedium, color = finance.expense)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(debtLabel(DebtDirection.OWED_TO_ME)), style = MaterialTheme.typography.labelMedium)
                MoneyText(state.owedToMeMinor, state.currency, style = MaterialTheme.typography.titleMedium, color = finance.income)
            }
        }
    }
}

fun debtLabel(direction: DebtDirection): Int = when (direction) {
    DebtDirection.I_OWE -> R.string.debt_i_owe
    DebtDirection.OWED_TO_ME -> R.string.debt_owed_to_me
}
