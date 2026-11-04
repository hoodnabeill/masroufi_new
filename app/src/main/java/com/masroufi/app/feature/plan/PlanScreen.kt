@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.masroufi.app.feature.plan

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.masroufi.app.feature.budgets.BudgetsScreen
import com.masroufi.app.feature.goals.GoalsScreen
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.navigation.TabInsets

const val PLAN_BUDGETS = "budgets"
const val PLAN_GOALS = "goals"

/** "الخطة": budgets and goals as one tab, switched by a segmented control; + adds to whichever is showing. */
@Composable
fun PlanScreen(
    initialTab: String,
    onAddBudget: () -> Unit,
    onEditBudget: (String) -> Unit,
    onAddGoal: () -> Unit,
    onOpenGoal: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(if (initialTab == PLAN_GOALS) PLAN_GOALS else PLAN_BUDGETS) }
    val largeTitle = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(largeTitle.nestedScrollConnection),
        contentWindowInsets = TabInsets,
        topBar = {
            MasroufiTopBar(stringResource(R.string.tab_plan), scrollBehavior = largeTitle) {
                GlassIconButton(onClick = if (tab == PLAN_GOALS) onAddGoal else onAddBudget) {
                    Icon(MIcons.Plus, stringResource(if (tab == PLAN_GOALS) R.string.goal_add else R.string.budget_add))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SegmentedChoice(
                    options = listOf(PLAN_BUDGETS, PLAN_GOALS),
                    selected = tab,
                    onSelect = { tab = it },
                    label = { stringResource(if (it == PLAN_GOALS) R.string.tab_goals else R.string.tab_budgets) },
                )
            }
            Crossfade(tab, label = "planTab", modifier = Modifier.weight(1f)) { current ->
                if (current == PLAN_GOALS) {
                    GoalsScreen(onAdd = onAddGoal, onOpen = onOpenGoal, embedded = true)
                } else {
                    BudgetsScreen(onAdd = onAddBudget, onEdit = onEditBudget, embedded = true)
                }
            }
        }
    }
}
