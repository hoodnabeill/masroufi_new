package com.masroufi.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.masroufi.app.R
import com.masroufi.app.data.repo.DebugSampleData
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.add.AddSheet
import com.masroufi.app.feature.close.CloseDayScreen
import com.masroufi.app.feature.gallery.GalleryScreen
import com.masroufi.app.feature.home.HomeScreen
import com.masroufi.app.feature.add.AddViewModel
import com.masroufi.app.feature.onboarding.OnboardingScreen
import com.masroufi.app.feature.payday.PaydayRitualScreen
import com.masroufi.app.feature.placeholder.TabPlaceholder
import com.masroufi.app.feature.plan.EnvelopeDetailScreen
import com.masroufi.app.feature.plan.EnvelopeFormScreen
import com.masroufi.app.feature.plan.FixedCostFormScreen
import com.masroufi.app.feature.plan.FixedCostsScreen
import com.masroufi.app.feature.plan.NewGoalScreen
import com.masroufi.app.feature.plan.PlanScreen
import com.masroufi.app.feature.plan.SeasonDetailScreen
import com.masroufi.app.feature.plan.SubscriptionFormScreen
import com.masroufi.app.feature.plan.SubscriptionsScreen
import com.masroufi.app.feature.plan.ZakatScreen
import com.masroufi.app.feature.pockets.NewPocketScreen
import com.masroufi.app.feature.pockets.PocketDetailScreen
import com.masroufi.app.feature.pockets.PocketsScreen
import com.masroufi.app.feature.proverb.ProverbRewardScreen
import com.masroufi.app.feature.roll.RollScreen
import com.masroufi.app.ui.components.TzTab
import com.masroufi.app.ui.components.TzTabBar
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.nav.CloseDay
import com.masroufi.app.ui.nav.EditEnvelope
import com.masroufi.app.ui.nav.EnvelopeDetail
import com.masroufi.app.ui.nav.FixedCostEdit
import com.masroufi.app.ui.nav.FixedCosts
import com.masroufi.app.ui.nav.NewEnvelope
import com.masroufi.app.ui.nav.NewGoal
import com.masroufi.app.ui.nav.SubscriptionEdit
import com.masroufi.app.ui.nav.Subscriptions
import com.masroufi.app.ui.nav.Gallery
import com.masroufi.app.ui.nav.Home
import com.masroufi.app.ui.nav.NewPocket
import com.masroufi.app.ui.nav.Onboarding
import com.masroufi.app.ui.nav.PaydayRitual
import com.masroufi.app.ui.nav.PeopleTab
import com.masroufi.app.ui.nav.PlanTab
import com.masroufi.app.ui.nav.SeasonEdit
import com.masroufi.app.ui.nav.Zakat
import com.masroufi.app.ui.nav.PocketDetail
import com.masroufi.app.ui.nav.Pockets
import com.masroufi.app.ui.nav.ProverbReward
import com.masroufi.app.ui.nav.RollTab
import com.masroufi.app.ui.theme.MasroufiTheme
import com.masroufi.app.ui.theme.Tz

/** Tab bar floats over [content]; content gets bottom padding for it (72 dp bar + 8 dp margin + nav inset). */
@Composable
fun AppScaffold(
    selectedTab: TzTab?,
    onTab: (TzTab) -> Unit,
    onAdd: () -> Unit,
    showTabBar: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().background(Tz.night)) {
        Box(Modifier.fillMaxSize().then(if (showTabBar) Modifier.navigationBarsPadding().padding(bottom = 80.dp) else Modifier)) {
            content()
        }
        if (showTabBar) {
            TzTabBar(selectedTab ?: TzTab.Today, onTab, onAdd, Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun NavDestination?.tab(): TzTab? = when {
    this == null -> null
    hasRoute<Home>() -> TzTab.Today
    hasRoute<RollTab>() -> TzTab.Roll
    hasRoute<PlanTab>() -> TzTab.Plan
    hasRoute<PeopleTab>() -> TzTab.People
    else -> null
}

private fun NavDestination.isPushed() = hasRoute<CloseDay>() || hasRoute<ProverbReward>() || hasRoute<Gallery>() || hasRoute<Pockets>() || hasRoute<PocketDetail>() || hasRoute<NewPocket>() ||
    hasRoute<EnvelopeDetail>() || hasRoute<NewEnvelope>() || hasRoute<NewGoal>() || hasRoute<EditEnvelope>() || hasRoute<FixedCosts>() || hasRoute<FixedCostEdit>() ||
    hasRoute<Subscriptions>() || hasRoute<SubscriptionEdit>() || hasRoute<SeasonEdit>() || hasRoute<Zakat>() || hasRoute<PaydayRitual>()

private fun NavController.switchTab(tab: TzTab) {
    val route: Any = when (tab) {
        TzTab.Today -> Home
        TzTab.Roll -> RollTab
        TzTab.Plan -> PlanTab
        TzTab.People -> PeopleTab
    }
    navigate(route) {
        popUpTo<Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(entitlements: Entitlements, sampleData: DebugSampleData? = null) {
    MasroufiTheme {
        val nav = rememberNavController()
        val onboarded by hiltViewModel<AppRootViewModel>().onboarded.collectAsStateWithLifecycle()
        // Decided once, from the stored value: finishing onboarding must not rebuild the graph under the navigation to Home.
        var start by remember { mutableStateOf<Any?>(null) }
        if (start == null && onboarded != null) start = if (onboarded == true) Home else Onboarding
        val startDestination = start
        val entry by nav.currentBackStackEntryAsState()
        val current = entry?.destination
        var addSheetOpen by rememberSaveable { mutableStateOf(false) }
        // What the quick log hands to the add sheet; null for the + button.
        var addPrefill by remember { mutableStateOf<AddPrefill?>(null) }
        fun openAddSheet(prefill: AddPrefill?) {
            addPrefill = prefill
            addSheetOpen = true
        }
        // The add sheet is closed by the time a saved income is judged; Home and the roll are behind this push.
        val addViewModel: AddViewModel = hiltViewModel()
        LaunchedEffect(addViewModel) { addViewModel.ritualOffer.collect { nav.navigate(PaydayRitual(it)) } }
        val reduce = LocalReduceMotion.current
        // Leading edge = start edge: right in RTL, left in LTR.
        val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1 else -1
        val move = TzSpring.Move.spec<IntOffset>() // reduce motion never slides, so no tween fallback
        val fade = tween<Float>(if (reduce) 120 else 140)
        // 05-MOTION "Anything -> add sheet": the screen behind scales to 0.96 (the sheet's scrim dims it).
        val behind by animateFloatAsState(if (addSheetOpen && !reduce) 0.96f else 1f, TzSpring.Sheet.spec(), label = "behindSheet")

        fun AnimatedContentTransitionScope<NavBackStackEntry>.pushedIn(): EnterTransition =
            if (reduce) fadeIn(tween(120)) else slideInHorizontally(move) { sign * it }
        fun AnimatedContentTransitionScope<NavBackStackEntry>.pushedOut(): ExitTransition =
            if (reduce) fadeOut(tween(120)) else slideOutHorizontally(move) { sign * it }

        AppScaffold(
            selectedTab = current.tab(),
            onTab = nav::switchTab,
            onAdd = { openAddSheet(null) },
            showTabBar = current.tab() != null,
            modifier = Modifier.graphicsLayer { scaleX = behind; scaleY = behind },
        ) {
            // Night (the scaffold) until the stored value is read: no flash of Home before onboarding.
            if (startDestination != null) NavHost(
                nav,
                startDestination = startDestination,
                // 05-MOTION "Tear": the reward cross-fades in over the torn ticket in 160 ms.
                enterTransition = {
                    when {
                        initialState.destination.hasRoute<CloseDay>() && targetState.destination.hasRoute<ProverbReward>() -> fadeIn(tween(160))
                        targetState.destination.isPushed() -> pushedIn()
                        else -> fadeIn(fade)
                    }
                },
                exitTransition = {
                    if (initialState.destination.hasRoute<CloseDay>() && targetState.destination.hasRoute<ProverbReward>()) fadeOut(tween(160))
                    else fadeOut(fade)
                },
                popEnterTransition = { fadeIn(fade) },
                popExitTransition = { if (initialState.destination.isPushed()) pushedOut() else fadeOut(fade) },
            ) {
                composable<Onboarding> {
                    OnboardingScreen(
                        viewModel = hiltViewModel(),
                        onFinished = {
                            nav.navigate(Home) { popUpTo<Onboarding> { inclusive = true } }
                            openAddSheet(null)
                        },
                    )
                }
                composable<Home> {
                    HomeScreen(
                        viewModel = hiltViewModel(),
                        onCloseDay = { nav.navigate(CloseDay(it)) },
                        onOpenProverb = { nav.navigate(ProverbReward(it)) },
                        onOpenPockets = { nav.navigate(Pockets) },
                        onOpenAddSheet = ::openAddSheet,
                    )
                }
                composable<RollTab> {
                    RollScreen(viewModel = hiltViewModel(), onOpenAddSheet = ::openAddSheet, onPayday = { nav.navigate(PaydayRitual(it)) })
                }
                composable<PaydayRitual> {
                    PaydayRitualScreen(
                        viewModel = hiltViewModel(),
                        onHome = { nav.popBackStack<Home>(inclusive = false) },
                        onFixedCosts = { nav.navigate(FixedCosts) },
                    )
                }
                composable<PlanTab> {
                    PlanScreen(
                        viewModel = hiltViewModel(),
                        onOpenEnvelope = { nav.navigate(EnvelopeDetail(it)) },
                        onEditEnvelope = { nav.navigate(EditEnvelope(it)) },
                        onNewEnvelope = { nav.navigate(NewEnvelope) },
                        onOpenSubscriptions = { nav.navigate(Subscriptions) },
                        onOpenFixedCosts = { nav.navigate(FixedCosts) },
                        onNewGoal = { nav.navigate(NewGoal) },
                        onOpenSeason = { nav.navigate(SeasonEdit(it)) },
                        onZakat = { nav.navigate(Zakat) },
                        onToday = { nav.switchTab(TzTab.Today) },
                        onGallery = { nav.navigate(Gallery) },
                    )
                }
                composable<SeasonEdit> { SeasonDetailScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() }) }
                composable<Zakat> { ZakatScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() }) }
                composable<NewGoal> { NewGoalScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() }) }
                composable<Subscriptions> {
                    SubscriptionsScreen(
                        viewModel = hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(SubscriptionEdit(it)) },
                        onNew = { nav.navigate(SubscriptionEdit(0, it)) },
                    )
                }
                composable<SubscriptionEdit> { SubscriptionFormScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() }) }
                composable<EnvelopeDetail> {
                    EnvelopeDetailScreen(
                        viewModel = hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate(EditEnvelope(it)) },
                        onOpenAddSheet = ::openAddSheet,
                    )
                }
                composable<NewEnvelope> {
                    EnvelopeFormScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() }, onNewFixedCost = { nav.navigate(FixedCostEdit(0)) })
                }
                composable<EditEnvelope> {
                    EnvelopeFormScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() }, onNewFixedCost = { nav.navigate(FixedCostEdit(0)) })
                }
                composable<FixedCosts> {
                    FixedCostsScreen(
                        viewModel = hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(FixedCostEdit(it)) },
                        onNew = { nav.navigate(FixedCostEdit(0)) },
                        onOpenAddSheet = ::openAddSheet,
                    )
                }
                composable<FixedCostEdit> { FixedCostFormScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() }) }
                composable<PeopleTab> { TabPlaceholder(R.string.tab_people) }
                composable<CloseDay> {
                    CloseDayScreen(
                        viewModel = hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        // The reward replaces this screen, so back from it lands on Home.
                        onDone = { nav.navigate(ProverbReward(it)) { popUpTo<CloseDay> { inclusive = true } } },
                    )
                }
                composable<ProverbReward> {
                    // Back and "تمام، لبكره" both land on Home.
                    ProverbRewardScreen(hiltViewModel(), onHome = { nav.popBackStack<Home>(inclusive = false) })
                }
                composable<Pockets> {
                    PocketsScreen(
                        viewModel = hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        onOpenPocket = { nav.navigate(PocketDetail(it)) },
                        onNewPocket = { nav.navigate(NewPocket) },
                        onOpenAddSheet = ::openAddSheet,
                    )
                }
                composable<PocketDetail> {
                    PocketDetailScreen(
                        viewModel = hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        onOpenRoll = { nav.switchTab(TzTab.Roll) },
                        onOpenAddSheet = ::openAddSheet,
                    )
                }
                composable<NewPocket> { NewPocketScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() }) }
                composable<Gallery> { GalleryScreen(entitlements, onSampleData = sampleData?.let { it::load }) }
            }
        }
        if (addSheetOpen) AddSheet(addPrefill, onDismiss = { addSheetOpen = false })
    }
}
