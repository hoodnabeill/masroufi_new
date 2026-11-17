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
import com.masroufi.app.feature.placeholder.TabPlaceholder
import com.masroufi.app.ui.components.TzTab
import com.masroufi.app.ui.components.TzTabBar
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.nav.CloseDay
import com.masroufi.app.ui.nav.Gallery
import com.masroufi.app.ui.nav.Home
import com.masroufi.app.ui.nav.PeopleTab
import com.masroufi.app.ui.nav.PlanTab
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

private fun NavDestination.isPushed() = hasRoute<CloseDay>() || hasRoute<ProverbReward>() || hasRoute<Gallery>()

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
        val entry by nav.currentBackStackEntryAsState()
        val current = entry?.destination
        var addSheetOpen by rememberSaveable { mutableStateOf(false) }
        // What the quick log hands to the add sheet; null for the + button.
        var addPrefill by remember { mutableStateOf<AddPrefill?>(null) }
        fun openAddSheet(prefill: AddPrefill?) {
            addPrefill = prefill
            addSheetOpen = true
        }
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
            NavHost(
                nav,
                startDestination = Home,
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
                composable<Home> {
                    HomeScreen(
                        viewModel = hiltViewModel(),
                        onCloseDay = { nav.navigate(CloseDay(it)) },
                        onOpenProverb = { nav.navigate(ProverbReward(it)) },
                        onOpenAddSheet = ::openAddSheet,
                    )
                }
                composable<RollTab> { TabPlaceholder(R.string.tab_roll) }
                composable<PlanTab> { TabPlaceholder(R.string.tab_plan, onGallery = { nav.navigate(Gallery) }) }
                composable<PeopleTab> { TabPlaceholder(R.string.tab_people) }
                composable<CloseDay> {
                    CloseDayScreen(
                        viewModel = hiltViewModel(),
                        onBack = { nav.popBackStack() },
                        // The reward replaces this screen, so back from it lands on Home.
                        onDone = { nav.navigate(ProverbReward(it)) { popUpTo<CloseDay> { inclusive = true } } },
                    )
                }
                composable<ProverbReward> { TabPlaceholder(R.string.tab_today) } // Task 20 replaces this
                composable<Gallery> { GalleryScreen(entitlements, onSampleData = sampleData?.let { it::load }) }
            }
        }
        if (addSheetOpen) AddSheet(addPrefill, onDismiss = { addSheetOpen = false })
    }
}
