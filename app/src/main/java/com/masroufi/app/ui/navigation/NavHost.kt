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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.MotionFlags
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.masroufi.app.ui.components.GlassStage
import com.masroufi.app.ui.components.LocalBackdropView
import com.masroufi.app.ui.components.LocalGlassBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.CubicBezierEasing
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

/** Widest the screen content grows; beyond it, content centres over the full-bleed light. */
val ContentMaxWidth = 680.dp

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: Int)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, R.string.tab_home),
    Tab(TransactionsRoute, TransactionsRoute::class, R.string.dock_transactions),
    Tab(BudgetsRoute, BudgetsRoute::class, R.string.tab_budgets),
    Tab(GoalsRoute, GoalsRoute::class, R.string.tab_goals),
    Tab(MoreRoute, MoreRoute::class, R.string.tab_more),
)

val ScreenGlide = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Zoom-through for depth (push/pop): the old screen recedes and fades quickly while the new one rises out of depth,
// and back plays the same path in reverse (following the predictive-back gesture). Tabs are peers, so switching
// between them only cross-fades. With "remove animations" on, everything is a short fade.
inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T>(
        enterTransition = {
            when {
                MotionFlags.reduce -> fadeIn(tween(120))
                isTabSwitch(initialState, targetState) -> fadeIn(tween(220, delayMillis = 80))
                else -> fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(420, delayMillis = 40, easing = ScreenGlide), initialScale = 0.92f)
            }
        },
        exitTransition = {
            when {
                MotionFlags.reduce -> fadeOut(tween(120))
                isTabSwitch(initialState, targetState) -> fadeOut(tween(90))
                else -> fadeOut(tween(120)) + scaleOut(tween(420, easing = ScreenGlide), targetScale = 1.06f)
            }
        },
        popEnterTransition = {
            when {
                MotionFlags.reduce -> fadeIn(tween(120))
                isTabSwitch(initialState, targetState) -> fadeIn(tween(220, delayMillis = 80))
                else -> fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(420, delayMillis = 40, easing = ScreenGlide), initialScale = 1.06f)
            }
        },
        popExitTransition = {
            when {
                MotionFlags.reduce -> fadeOut(tween(120))
                isTabSwitch(initialState, targetState) -> fadeOut(tween(90))
                else -> fadeOut(tween(140)) + scaleOut(tween(420, easing = ScreenGlide), targetScale = 0.92f)
            }
        },
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}

@PublishedApi
internal fun isTabSwitch(from: NavBackStackEntry, to: NavBackStackEntry): Boolean = from.isTab() && to.isTab()

private fun NavBackStackEntry.isTab(): Boolean = tabs.any { tab -> destination.hierarchy.any { it.hasRoute(tab.routeClass) } }

@Composable
fun MasroufiNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val tabIndex = tabs.indexOfFirst { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }
    var lastTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tabIndex) { if (tabIndex >= 0) lastTab = tabIndex }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val scene = rememberLayerBackdrop()
    val reduce = LocalReduceMotion.current
    val dockTabs = remember { tabs.map { DockTab(it.label) } }
    BackHandler(enabled = menuOpen) { menuOpen = false }

    Box(Modifier.fillMaxSize()) {
        // The whole scene (light + screens) is recorded once so the floating chrome above can refract it; glass
        // inside screens refracts only the ambient light, which keeps the two layers free of feedback loops.
        GlassStage(layout = lastTab, modifier = Modifier.layerBackdrop(scene)) {
            // Content is capped at a comfortable reading width on tablets and landscape; the light stays full-bleed.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                SharedTransitionLayout(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavHost(navController = nav, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
                            graph(nav)
                        }
                    }
                }
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides scene, LocalBackdropView provides LocalView.current) {
            QuickAddMenu(
                open = menuOpen && tabIndex >= 0,
                onDismiss = { menuOpen = false },
                onPick = { type ->
                    menuOpen = false
                    nav.navigate(TransactionEditorRoute(type = type))
                },
            )
            AnimatedVisibility(
                visible = tabIndex >= 0,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(Motion.spatial(reduce)) { it } + fadeIn(Motion.effects(reduce)),
                exit = slideOutVertically(Motion.spatial(reduce)) { it } + fadeOut(Motion.snappy(reduce)),
            ) {
                Dock(
                    tabs = dockTabs,
                    selected = lastTab,
                    onSelect = { nav.navigateToTab(tabs[it].route) },
                    menuOpen = menuOpen,
                    onToggleMenu = { menuOpen = !menuOpen },
                )
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
