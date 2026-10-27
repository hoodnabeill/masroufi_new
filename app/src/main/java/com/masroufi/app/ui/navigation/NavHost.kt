@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.masroufi.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.masroufi.app.R
import com.masroufi.app.feature.analytics.AnalyticsScreen
import com.masroufi.app.feature.budgets.BudgetEditorScreen
import com.masroufi.app.feature.budgets.BudgetsScreen
import com.masroufi.app.feature.debts.DebtDetailScreen
import com.masroufi.app.feature.debts.DebtEditorScreen
import com.masroufi.app.feature.debts.DebtsScreen
import com.masroufi.app.feature.gameya.GameyaDetailScreen
import com.masroufi.app.feature.gameya.GameyaEditorScreen
import com.masroufi.app.feature.gameya.GameyaListScreen
import com.masroufi.app.feature.goals.GoalDetailScreen
import com.masroufi.app.feature.goals.GoalEditorScreen
import com.masroufi.app.feature.goals.GoalsScreen
import com.masroufi.app.feature.home.HomeScreen
import com.masroufi.app.feature.more.MoreScreen
import com.masroufi.app.feature.recurring.RecurringEditorScreen
import com.masroufi.app.feature.recurring.RecurringScreen
import com.masroufi.app.feature.settings.BackupScreen
import com.masroufi.app.feature.settings.CategoriesScreen
import com.masroufi.app.feature.settings.PremiumScreen
import com.masroufi.app.feature.settings.SecurityScreen
import com.masroufi.app.feature.settings.SettingsScreen
import com.masroufi.app.feature.split.SplitEditorScreen
import com.masroufi.app.feature.split.SplitRulesScreen
import com.masroufi.app.feature.transactions.TransactionDetailScreen
import com.masroufi.app.feature.transactions.TransactionEditorScreen
import com.masroufi.app.feature.transactions.TransactionsScreen
import com.masroufi.app.feature.wallets.WalletDetailScreen
import com.masroufi.app.feature.wallets.WalletEditorScreen
import com.masroufi.app.feature.wallets.WalletsScreen
import kotlin.reflect.KClass

/** Tab screens draw no bottom inset themselves: the shell's navigation bar already accounts for it. */
val TabInsets = WindowInsets(0, 0, 0, 0)

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Tab(TransactionsRoute, TransactionsRoute::class, R.string.tab_transactions, Icons.Outlined.Receipt, Icons.Filled.Receipt),
    Tab(BudgetsRoute, BudgetsRoute::class, R.string.tab_budgets, Icons.Outlined.PieChart, Icons.Filled.PieChart),
    Tab(GoalsRoute, GoalsRoute::class, R.string.tab_goals, Icons.Outlined.Flag, Icons.Filled.Flag),
    Tab(MoreRoute, MoreRoute::class, R.string.tab_more, Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T>(
        enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(220)) },
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}

@Composable
fun MasroufiNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateToTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = TabInsets,
    ) { padding ->
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                ) {
                    graph(nav)
                }
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavGraphBuilder.graph(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    screen<HomeRoute> {
        HomeScreen(
            onAddTransaction = { type -> nav.navigate(TransactionEditorRoute(type = type)) },
            onOpenTransaction = { nav.navigate(TransactionDetailRoute(it)) },
            onOpenTransactions = { nav.navigateToTab(TransactionsRoute) },
            onOpenWallets = { nav.navigate(WalletsRoute) },
            onOpenBudgets = { nav.navigateToTab(BudgetsRoute) },
            onOpenGoal = { nav.navigate(GoalDetailRoute(it)) },
            onOpenGoals = { nav.navigateToTab(GoalsRoute) },
            onOpenRecurring = { nav.navigate(RecurringRoute) },
            onOpenDebts = { nav.navigate(DebtsRoute) },
            onOpenAnalytics = { nav.navigate(AnalyticsRoute) },
            onCreateWallet = { nav.navigate(WalletEditorRoute()) },
        )
    }
    screen<TransactionsRoute> {
        TransactionsScreen(
            onAdd = { nav.navigate(TransactionEditorRoute()) },
            onOpen = { nav.navigate(TransactionDetailRoute(it)) },
        )
    }
    screen<BudgetsRoute> { BudgetsScreen(onAdd = { nav.navigate(BudgetEditorRoute()) }, onEdit = { nav.navigate(BudgetEditorRoute(it)) }) }
    screen<GoalsRoute> { GoalsScreen(onAdd = { nav.navigate(GoalEditorRoute()) }, onOpen = { nav.navigate(GoalDetailRoute(it)) }) }
    screen<MoreRoute> {
        MoreScreen(
            onWallets = { nav.navigate(WalletsRoute) },
            onRecurring = { nav.navigate(RecurringRoute) },
            onGameya = { nav.navigate(GameyaListRoute) },
            onDebts = { nav.navigate(DebtsRoute) },
            onSplit = { nav.navigate(SplitRulesRoute) },
            onAnalytics = { nav.navigate(AnalyticsRoute) },
            onCategories = { nav.navigate(CategoriesRoute) },
            onBackup = { nav.navigate(BackupRoute) },
            onSecurity = { nav.navigate(SecurityRoute) },
            onSettings = { nav.navigate(SettingsRoute) },
            onPremium = { nav.navigate(PremiumRoute) },
        )
    }
    screen<TransactionEditorRoute> {
        TransactionEditorScreen(onDone = back, onBack = back)
    }
    screen<TransactionDetailRoute> {
        TransactionDetailScreen(
            onBack = back,
            onEdit = { nav.navigate(TransactionEditorRoute(id = it)) },
        )
    }
    screen<WalletsRoute> {
        WalletsScreen(onBack = back, onAdd = { nav.navigate(WalletEditorRoute()) }, onOpen = { nav.navigate(WalletDetailRoute(it)) })
    }
    screen<WalletDetailRoute> {
        WalletDetailScreen(
            onBack = back,
            onEdit = { nav.navigate(WalletEditorRoute(it)) },
            onOpenTransaction = { nav.navigate(TransactionDetailRoute(it)) },
            onAddTransaction = { walletId -> nav.navigate(TransactionEditorRoute(walletId = walletId)) },
        )
    }
    screen<WalletEditorRoute> { WalletEditorScreen(onDone = back, onBack = back) }
    screen<BudgetEditorRoute> { BudgetEditorScreen(onDone = back, onBack = back) }
    screen<GoalDetailRoute> { GoalDetailScreen(onBack = back, onEdit = { nav.navigate(GoalEditorRoute(it)) }) }
    screen<GoalEditorRoute> { GoalEditorScreen(onDone = back, onBack = back) }
    screen<RecurringRoute> { RecurringScreen(onBack = back, onAdd = { nav.navigate(RecurringEditorRoute()) }, onEdit = { nav.navigate(RecurringEditorRoute(it)) }) }
    screen<RecurringEditorRoute> { RecurringEditorScreen(onDone = back, onBack = back) }
    screen<DebtsRoute> { DebtsScreen(onBack = back, onAdd = { nav.navigate(DebtEditorRoute()) }, onOpen = { nav.navigate(DebtDetailRoute(it)) }) }
    screen<DebtDetailRoute> { DebtDetailScreen(onBack = back, onEdit = { nav.navigate(DebtEditorRoute(it)) }) }
    screen<DebtEditorRoute> { DebtEditorScreen(onDone = back, onBack = back) }
    screen<GameyaListRoute> { GameyaListScreen(onBack = back, onAdd = { nav.navigate(GameyaEditorRoute()) }, onOpen = { nav.navigate(GameyaDetailRoute(it)) }) }
    screen<GameyaDetailRoute> { GameyaDetailScreen(onBack = back, onEdit = { nav.navigate(GameyaEditorRoute(it)) }) }
    screen<GameyaEditorRoute> { GameyaEditorScreen(onDone = back, onBack = back) }
    screen<SplitRulesRoute> { SplitRulesScreen(onBack = back, onAdd = { nav.navigate(SplitEditorRoute()) }, onEdit = { nav.navigate(SplitEditorRoute(it)) }) }
    screen<SplitEditorRoute> { SplitEditorScreen(onDone = back, onBack = back) }
    screen<AnalyticsRoute> { AnalyticsScreen(onBack = back) }
    screen<SettingsRoute> { SettingsScreen(onBack = back) }
    screen<SecurityRoute> { SecurityScreen(onBack = back) }
    screen<BackupRoute> { BackupScreen(onBack = back, onPremium = { nav.navigate(PremiumRoute) }) }
    screen<PremiumRoute> { PremiumScreen(onBack = back) }
    screen<CategoriesRoute> { CategoriesScreen(onBack = back) }
}
