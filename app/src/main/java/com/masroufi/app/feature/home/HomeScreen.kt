package com.masroufi.app.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.masroufi.app.domain.PayCycle
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.liquidPress
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.CategoryIcons
import java.time.LocalDate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.glassInset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.feature.progress.ProgressStrip
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.RepeatSpend
import com.masroufi.app.ui.components.GlassSheet
import com.masroufi.app.ui.components.GlassSwitch
import com.masroufi.app.ui.components.FitAmount
import com.masroufi.app.data.model.DebtDirection
import com.masroufi.app.data.prefs.DashboardSection
import com.masroufi.app.domain.BudgetStatus
import com.masroufi.app.domain.GoalMath
import com.masroufi.app.domain.Insight
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.ChartLegend
import com.masroufi.app.ui.components.ChartSlice
import com.masroufi.app.ui.components.DonutChart
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.TransactionItem
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.components.staggerIn
import com.masroufi.app.ui.navigation.DockClearance
import com.masroufi.app.ui.navigation.sharedContainer
import com.masroufi.app.ui.theme.AmountStyle
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.FormatStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onAddTransaction: (String) -> Unit,
    onQuickLog: (amount: String?, categoryId: String?, note: String?) -> Unit,
    onOpenAchievements: () -> Unit,
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
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val spentToday by viewModel.spentToday.collectAsStateWithLifecycle()
    val lastSpends by viewModel.lastSpends.collectAsStateWithLifecycle()
    var customizing by rememberSaveable { mutableStateOf(false) }
    if (state.loading) {
        HomeSkeleton()
        return
    }

    if (state.wallets.isEmpty()) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = MIcons.Activity,
                title = stringResource(R.string.home_no_wallet_title),
                body = stringResource(R.string.home_no_wallet_body),
                actionLabel = stringResource(R.string.wallet_add),
                onAction = onCreateWallet,
            )
        }
        return
    }

    var order = 0
    val hidden = state.settings.hideAmounts
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        // Cards dissolve under the status bar instead of sliding beneath the clock and icons.
        Modifier.fillMaxSize().edgeFade(top = statusTop + 10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") { HomeHeader(progress?.streak?.current ?: 0, onOpenAchievements, onCustomize = { customizing = true }) }
        if (state.visible(DashboardSection.BALANCE)) {
            val i = order++
            item(key = "hero") {
                Box(Modifier.staggerIn(i)) {
                    SafeHero(state, spentToday, hidden, onToggleHidden = { viewModel.setHideAmounts(!hidden) }, onOpenWallets = onOpenWallets)
                }
            }
        }
        progress?.let { snapshot ->
            val i = order++
            item(key = "streak") { Box(Modifier.staggerIn(i)) { StreakCard(snapshot, onOpenAchievements) } }
        }
        if (state.visible(DashboardSection.QUICK_ACTIONS)) {
            val i = order++
            item(key = "quick") {
                Box(Modifier.staggerIn(i)) {
                    QuickLogSection(lastSpends, state.categories, state.currency, onLog = onQuickLog, onAllCategories = { onAddTransaction("EXPENSE") })
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            item(key = "suggestions") { SuggestionsCard(suggestions, state.categories, viewModel::logRepeat, Modifier.animateItem()) }
        }
        if (state.visible(DashboardSection.INSIGHTS) && state.insights.isNotEmpty()) {
            val i = order++
            item(key = "insights") { Box(Modifier.staggerIn(i)) { InsightsRow(state, onOpenAll = onOpenAnalytics, onMakeBudget = onOpenBudgets) } }
        }
        if (state.visible(DashboardSection.SPENDING) && state.spending.isNotEmpty()) {
            val i = order++
            item(key = "spending") { Box(Modifier.staggerIn(i)) { MonthSpendCard(state, hidden, onOpenDetails = onOpenTransactions) } }
        }
        if (state.visible(DashboardSection.RECENT) && state.recent.isNotEmpty()) {
            val i = order++
            item(key = "recent") { Box(Modifier.staggerIn(i)) { RecentByDay(state.recent, state.currency, onOpenTransaction, onOpenTransactions) } }
        }
        if (state.visible(DashboardSection.BUDGETS) && state.budgets.isNotEmpty()) {
            val i = order++
            item(key = "budgets") { BudgetsCard(state, onOpenBudgets, Modifier.staggerIn(i)) }
        }
        if (state.visible(DashboardSection.GOALS) && state.goals.isNotEmpty()) {
            val i = order++
            item(key = "goals") { GoalsCarousel(state, onOpenGoal, onOpenGoals, Modifier.staggerIn(i)) }
        }
        if (state.visible(DashboardSection.UPCOMING) && state.upcoming.isNotEmpty()) {
            val i = order++
            item(key = "upcoming") { UpcomingCard(state, onOpenRecurring, Modifier.staggerIn(i)) }
        }
        if (state.visible(DashboardSection.DEBTS) && (state.iOweMinor > 0 || state.owedToMeMinor > 0)) {
            val i = order++
            item(key = "debts") { DebtsCard(state, onOpenDebts, Modifier.staggerIn(i)) }
        }
    }

    if (customizing) {
        GlassSheet(onDismissRequest = { customizing = false }) {
            Text(stringResource(R.string.home_customize), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            DashboardSection.entries.forEach { section ->
                ListItem(
                    headlineContent = { Text(stringResource(sectionLabel(section))) },
                    trailingContent = { GlassSwitch(checked = state.visible(section), onCheckedChange = { viewModel.setSectionVisible(section, it) }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
private fun BudgetsCard(state: HomeUiState, onOpen: () -> Unit, modifier: Modifier) {
    val finance = LocalFinanceColors.current
    PressableCard(onClick = onOpen, modifier = modifier) {
        Text(stringResource(R.string.tab_budgets), style = MaterialTheme.typography.titleMedium)
        state.budgets.take(3).forEach { item ->
            Spacer(Modifier.height(12.dp))
            Row {
                Text(item.budget.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                MoneyText(item.progress.remainingMinor, item.budget.currency, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(6.dp))
            AnimatedProgressBar(
                item.progress.fraction,
                when (item.progress.status) {
                    BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
                    BudgetStatus.WARNING -> finance.warning
                    BudgetStatus.OVER -> finance.over
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoalsCarousel(state: HomeUiState, onOpenGoal: (String) -> Unit, onOpenGoals: () -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    Column(modifier) {
        SectionHeader(stringResource(R.string.tab_goals), action = stringResource(R.string.action_see_all), onAction = onOpenGoals)
        LazyRow(state = listState, flingBehavior = rememberSnapFlingBehavior(listState), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.goals, key = { it.goal.id }) { item ->
                val fraction = GoalMath.fraction(item.savedMinor, item.goal.targetMinor)
                val color = categoryColor(item.goal.colorIndex)
                PressableCard(
                    onClick = { onOpenGoal(item.goal.id) },
                    color = color.copy(alpha = 0.10f),
                    modifier = Modifier.width(170.dp).sharedContainer("goal-${item.goal.id}"),
                ) {
                    DonutChart(
                        slices = listOf(ChartSlice(item.goal.name, (fraction * 1000).toLong(), color), ChartSlice("", ((1 - fraction) * 1000).toLong(), Color.Transparent)),
                        modifier = Modifier.size(64.dp),
                        thickness = 8.dp,
                        description = "${(fraction * 100).toInt()}%",
                    ) {
                        Icon(CategoryIcons.of(item.goal.iconKey), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(item.goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UpcomingCard(state: HomeUiState, onOpen: () -> Unit, modifier: Modifier) {
    val dates = rememberDateFormatter()
    PressableCard(onClick = onOpen, modifier = modifier) {
        Text(stringResource(R.string.section_upcoming), style = MaterialTheme.typography.titleMedium)
        state.upcoming.take(4).forEach { rule ->
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(rule.note.ifBlank { stringResource(R.string.recurring_title) }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    rule.nextEpochDay?.let { Text(LocalDate.ofEpochDay(it).format(dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                MoneyText(rule.amountMinor, rule.currency, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DebtsCard(state: HomeUiState, onOpen: () -> Unit, modifier: Modifier) {
    val finance = LocalFinanceColors.current
    PressableCard(onClick = onOpen, modifier = modifier) {
        Text(stringResource(R.string.debts_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(debtLabel(DebtDirection.I_OWE)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedAmount(state.iOweMinor, state.currency, style = MaterialTheme.typography.titleLarge, color = finance.expense)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(debtLabel(DebtDirection.OWED_TO_ME)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedAmount(state.owedToMeMinor, state.currency, style = MaterialTheme.typography.titleLarge, color = finance.income)
            }
        }
    }
}


fun debtLabel(direction: DebtDirection): Int = when (direction) {
    DebtDirection.I_OWE -> R.string.debt_i_owe
    DebtDirection.OWED_TO_ME -> R.string.debt_owed_to_me
}


/** Loading placeholder in the shape of the dashboard, with a soft light sweep across it (still when motion is off). */
@Composable
private fun HomeSkeleton() {
    val reduce = LocalReduceMotion.current
    val sweep = if (reduce) null else rememberInfiniteTransition(label = "skeleton").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "sweep",
    )
    val bar = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val card = MaterialTheme.shapes.large
    val shimmer = Modifier.drawWithContent {
        drawContent()
        val x = (sweep?.value ?: -1f) * size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                start = Offset(x - size.width * 0.4f, 0f),
                end = Offset(x + size.width * 0.4f, size.height),
            ),
        )
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp).clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Box(Modifier.size(120.dp, 14.dp).clip(CircleShape).background(bar))
        Box(Modifier.size(220.dp, 32.dp).clip(CircleShape).background(bar))
        Box(Modifier.fillMaxWidth().height(210.dp).glass(card).then(shimmer))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { Box(Modifier.weight(1f).height(92.dp).glass(card).then(shimmer)) }
        }
        Box(Modifier.fillMaxWidth().height(160.dp).glass(card).then(shimmer))
    }
}





/**
 * Smart one-tap logging: spends the user repeats (same category, amount, wallet) offered as "same as usual?". One tap
 * saves it for now, with a haptic, and the row leaves because today's entry now exists.
 */
@Composable
private fun SuggestionsCard(
    suggestions: List<RepeatSpend>,
    categories: Map<String, Category>,
    onLog: (RepeatSpend) -> Unit,
    modifier: Modifier,
) {
    val haptics = rememberHaptics()
    PressableCard(onClick = null, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MIcons.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.suggest_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        suggestions.forEach { spend ->
            val category = categories[spend.categoryId]
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(CategoryIcons.of(category?.iconKey), categoryColor(category?.colorIndex), size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoneyText(spend.amountMinor, spend.currency, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(6.dp))
                        Text(categoryLabel(category), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    Text(
                        stringResource(R.string.suggest_times, spend.times),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlassButton(
                    onClick = {
                        haptics.confirm()
                        onLog(spend)
                    },
                    modifier = Modifier.height(44.dp),
                ) { Text(stringResource(R.string.suggest_log)) }
            }
        }
    }
}
