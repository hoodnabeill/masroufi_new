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
import androidx.navigation.NavDeepLink
import androidx.navigation.navDeepLink
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.masroufi.app.R
import com.masroufi.app.feature.plan.PLAN_BUDGETS
import com.masroufi.app.feature.plan.PLAN_GOALS
import com.masroufi.app.feature.plan.PlanScreen
import androidx.navigation.toRoute
import com.masroufi.app.feature.progress.AchievementsScreen
import com.masroufi.app.feature.progress.RewardScreen
import com.masroufi.app.feature.transactions.AddSheet
import com.masroufi.app.feature.subscriptions.SubscriptionsScreen
import com.masroufi.app.feature.analytics.AnalyticsScreen
import com.masroufi.app.feature.budgets.BudgetEditorScreen
import com.masroufi.app.feature.debts.DebtDetailScreen
import com.masroufi.app.feature.debts.DebtEditorScreen
import com.masroufi.app.feature.debts.DebtsScreen
import com.masroufi.app.feature.gameya.GameyaDetailScreen
import com.masroufi.app.feature.gameya.GameyaEditorScreen
import com.masroufi.app.feature.gameya.GameyaListScreen
import com.masroufi.app.feature.goals.GoalDetailScreen
import com.masroufi.app.feature.goals.GoalEditorScreen
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

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: Int, val glyph: Int)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, R.string.tab_home, glyph = 0),
    Tab(TransactionsRoute, TransactionsRoute::class, R.string.dock_transactions, glyph = 1),
    Tab(PlanRoute(), PlanRoute::class, R.string.tab_plan, glyph = 3),
    Tab(MoreRoute, MoreRoute::class, R.string.tab_more, glyph = 4),
)

val ScreenGlide = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Zoom-through for depth (push/pop): the old screen recedes and fades quickly while the new one rises out of depth,
// and back plays the same path in reverse (following the predictive-back gesture). Tabs are peers, so switching
// between them only cross-fades. With "remove animations" on, everything is a short fade.
inline fun <reified T : Any> NavGraphBuilder.screen(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T>(
        deepLinks = deepLinks,
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
    // The quick-add sheet floats over the screen it was opened from, so the dock stays put under it.
    val destination = if (backStack?.destination?.hasRoute(AddSheetRoute::class) == true) nav.previousBackStackEntry?.destination else backStack?.destination
    val tabIndex = tabs.indexOfFirst { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }
    var lastTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tabIndex) { if (tabIndex >= 0) lastTab = tabIndex }
    val scene = rememberLayerBackdrop()
    val reduce = LocalReduceMotion.current
    val dockTabs = remember { tabs.map { DockTab(it.label, it.glyph) } }
    LaunchedEffect(Unit) {
        if (StartRequest.backup) {
            StartRequest.backup = false
            nav.navigate(BackupRoute)
        }
    }

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
                    onAdd = { nav.navigate(AddSheetRoute()) },
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
            onAddTransaction = { type -> nav.navigate(AddSheetRoute(type = type)) },
            onQuickLog = { amount, categoryId, note -> nav.navigate(AddSheetRoute(type = "EXPENSE", categoryId = categoryId, amount = amount, note = note)) },
            onOpenAchievements = { nav.navigate(AchievementsRoute) },
            onOpenTransaction = { nav.navigate(TransactionDetailRoute(it)) },
            onOpenTransactions = { nav.navigateToTab(TransactionsRoute) },
            onOpenWallets = { nav.navigate(WalletsRoute) },
            onOpenBudgets = { nav.navigateToTab(PlanRoute(PLAN_BUDGETS)) },
            onOpenGoal = { nav.navigate(GoalDetailRoute(it)) },
            onOpenGoals = { nav.navigateToTab(PlanRoute(PLAN_GOALS)) },
            onOpenRecurring = { nav.navigate(RecurringRoute) },
            onOpenDebts = { nav.navigate(DebtsRoute) },
            onOpenAnalytics = { nav.navigate(AnalyticsRoute) },
            onCreateWallet = { nav.navigate(WalletEditorRoute()) },
        )
    }
    screen<TransactionsRoute> {
        TransactionsScreen(
            onAdd = { nav.navigate(AddSheetRoute()) },
            onOpen = { nav.navigate(TransactionDetailRoute(it)) },
        )
    }
    screen<PlanRoute> { entry ->
        PlanScreen(
            initialTab = entry.toRoute<PlanRoute>().tab,
            onAddBudget = { nav.navigate(BudgetEditorRoute()) },
            onEditBudget = { nav.navigate(BudgetEditorRoute(it)) },
            onAddGoal = { nav.navigate(GoalEditorRoute()) },
            onOpenGoal = { nav.navigate(GoalDetailRoute(it)) },
        )
    }
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
            onRecap = { nav.navigate(RecapRoute) },
            onAchievements = { nav.navigate(AchievementsRoute) },
            onLegal = { nav.navigate(LegalRoute(it)) },
        )
    }
    // New entries open the quick-add sheet (also the widget's masroufi://add); edits keep the full-screen editor.
    dialog<AddSheetRoute>(
        deepLinks = listOf(navDeepLink<AddSheetRoute>(basePath = "masroufi://add")),
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val replaceSheet: androidx.navigation.NavOptionsBuilder.() -> Unit = { popUpTo<AddSheetRoute> { inclusive = true } }
        AddSheet(
            onClose = back,
            onSaved = { type -> nav.navigate(RewardRoute(type.name), replaceSheet) },
            onLoan = { amount -> nav.navigate(DebtEditorRoute(amount = amount.ifEmpty { null }), replaceSheet) },
            onMoreDetails = { s ->
                nav.navigate(
                    TransactionEditorRoute(type = s.type.name, walletId = s.walletId, categoryId = s.categoryId, amount = s.amount.ifEmpty { null }, note = s.note.ifEmpty { null }),
                    replaceSheet,
                )
            },
        )
    }
    screen<RewardRoute> { entry ->
        RewardScreen(
            type = entry.toRoute<RewardRoute>().type,
            onDone = back,
            onLogAnother = { nav.navigate(AddSheetRoute()) { popUpTo<RewardRoute> { inclusive = true } } },
        )
    }
    screen<TransactionEditorRoute> { TransactionEditorScreen(onDone = back, onBack = back) }
    screen<TransactionDetailRoute> {
        TransactionDetailScreen(
            onBack = back,
            onEdit = { nav.navigate(TransactionEditorRoute(id = it)) },
        )
    }
    screen<WalletsRoute> {
        WalletsScreen(
            onBack = back,
            onAdd = { nav.navigate(WalletEditorRoute()) },
            onAddPreset = { name, type -> nav.navigate(WalletEditorRoute(name = name, type = type.name)) },
            onOpen = { nav.navigate(WalletDetailRoute(it)) },
            onTransfer = { nav.navigate(AddSheetRoute(type = "TRANSFER")) },
            onReconcile = { nav.navigate(WalletDetailRoute(it, reconcile = true)) },
            onCashOut = { from, to, note -> nav.navigate(AddSheetRoute(type = "TRANSFER", walletId = from, toWalletId = to, note = note)) },
            onLogSms = { type, walletId, amount, note -> nav.navigate(AddSheetRoute(type = type.name, walletId = walletId, amount = amount, note = note)) },
        )
    }
    screen<WalletDetailRoute> {
        WalletDetailScreen(
            onBack = back,
            onEdit = { nav.navigate(WalletEditorRoute(it)) },
            onOpenTransaction = { nav.navigate(TransactionDetailRoute(it)) },
            onAddTransaction = { walletId -> nav.navigate(AddSheetRoute(walletId = walletId)) },
        )
    }
    screen<WalletEditorRoute> { WalletEditorScreen(onDone = back, onBack = back) }
    screen<BudgetEditorRoute> { BudgetEditorScreen(onDone = back, onBack = back) }
    screen<GoalDetailRoute> { GoalDetailScreen(onBack = back, onEdit = { nav.navigate(GoalEditorRoute(it)) }) }
    screen<GoalEditorRoute> { GoalEditorScreen(onDone = back, onBack = back) }
    screen<AchievementsRoute> { AchievementsScreen(onBack = back) }
    screen<SubscriptionsRoute> {
        SubscriptionsScreen(
            onBack = back,
            onOpen = { nav.navigate(RecurringEditorRoute(it)) },
            onAdd = { key -> nav.navigate(RecurringEditorRoute(serviceKey = key)) },
        )
    }
    screen<RecurringRoute> {
        RecurringScreen(
            onBack = back,
            onAdd = { nav.navigate(RecurringEditorRoute()) },
            onEdit = { nav.navigate(RecurringEditorRoute(it)) },
            onAddService = { nav.navigate(RecurringEditorRoute(serviceKey = it)) },
            onAddIncome = { nav.navigate(RecurringEditorRoute(type = "INCOME")) },
            onAddNamed = { nav.navigate(RecurringEditorRoute(note = it)) },
            onSubscriptions = { nav.navigate(SubscriptionsRoute) },
        )
    }
    screen<RecurringEditorRoute> { RecurringEditorScreen(onDone = back, onBack = back) }
    screen<DebtsRoute> { DebtsScreen(onBack = back, onAdd = { nav.navigate(DebtEditorRoute()) }, onOpen = { nav.navigate(DebtDetailRoute(it)) }) }
    screen<DebtDetailRoute> { DebtDetailScreen(onBack = back, onEdit = { nav.navigate(DebtEditorRoute(it)) }) }
    screen<DebtEditorRoute> { DebtEditorScreen(onDone = back, onBack = back) }
    screen<GameyaListRoute> { GameyaListScreen(onBack = back, onAdd = { nav.navigate(GameyaEditorRoute()) }, onOpen = { nav.navigate(GameyaDetailRoute(it)) }) }
    screen<GameyaDetailRoute> { GameyaDetailScreen(onBack = back, onEdit = { nav.navigate(GameyaEditorRoute(it)) }) }
    screen<GameyaEditorRoute> { GameyaEditorScreen(onDone = back, onBack = back) }
    screen<SplitRulesRoute> { SplitRulesScreen(onBack = back, onAdd = { nav.navigate(SplitEditorRoute()) }, onEdit = { nav.navigate(SplitEditorRoute(it)) }) }
    screen<SplitEditorRoute> { SplitEditorScreen(onDone = back, onBack = back) }
    screen<AnalyticsRoute> {
        AnalyticsScreen(
            onBack = back,
            onEmergencyGoal = { nav.navigate(GoalEditorRoute()) },
            onLog = { nav.navigate(AddSheetRoute()) },
            onBudgets = { nav.navigateToTab(PlanRoute(PLAN_BUDGETS)) },
            onRecap = { nav.navigate(RecapRoute) },
        )
    }
    screen<RecapRoute> { com.masroufi.app.feature.recap.RecapScreen(onClose = back) }
    screen<LegalRoute> { entry -> com.masroufi.app.feature.legal.LegalScreen(entry.toRoute<LegalRoute>().doc, onBack = back) }
    screen<SettingsRoute> { SettingsScreen(onBack = back, onPremium = { nav.navigate(PremiumRoute) }) }
    screen<SecurityRoute> { SecurityScreen(onBack = back) }
    screen<BackupRoute> { BackupScreen(onBack = back, onPremium = { nav.navigate(PremiumRoute) }) }
    screen<PremiumRoute> { PremiumScreen(onBack = back) }
    screen<CategoriesRoute> { CategoriesScreen(onBack = back) }
}
