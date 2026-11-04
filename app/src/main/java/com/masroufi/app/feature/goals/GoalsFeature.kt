@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

@file:OptIn(ExperimentalMaterial3Api::class)

package com.masroufi.app.feature.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.masroufi.app.R
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.Goal
import com.masroufi.app.data.model.GoalEntry
import com.masroufi.app.data.model.GoalWithSaved
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.GoalRepository
import com.masroufi.app.domain.GoalBoost
import com.masroufi.app.domain.GoalEstimate
import com.masroufi.app.domain.GoalMath
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.ChartSlice
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.DateChip
import com.masroufi.app.ui.components.DonutChart
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EditorSaveBar
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.ui.components.GlassSheet
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.GlassSwitch
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.navigation.DockClearance
import com.masroufi.app.ui.navigation.GoalDetailRoute
import com.masroufi.app.ui.navigation.GoalEditorRoute
import com.masroufi.app.ui.navigation.TabInsets
import com.masroufi.app.ui.navigation.sharedContainer
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** What a goal sets aside each month: its own plan, or what its deadline needs. Null when neither is known. */
internal fun GoalWithSaved.monthlyPlan(today: LocalDate): Long? =
    goal.plannedMonthlyMinor ?: goal.targetEpochDay?.let { GoalBoost.monthlyFor(goal.targetMinor - savedMinor, today, LocalDate.ofEpochDay(it)) }

@HiltViewModel
class GoalsViewModel @Inject constructor(private val repo: GoalRepository) : ViewModel() {
    val goals: StateFlow<List<GoalWithSaved>?> = repo.observeGoals()
        .map<List<GoalWithSaved>, List<GoalWithSaved>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val effects = EffectChannel<EditorEffect>()

    fun addEntry(goalId: String, amountMinor: Long, note: String) {
        viewModelScope.launch { effects.attempt { repo.addEntry(goalId, amountMinor, note) } }
    }

    /** Keeps the boosted pace as the goal's monthly plan. */
    fun adoptMonthly(goal: Goal, monthlyMinor: Long) {
        viewModelScope.launch { effects.attempt { repo.save(goal.copy(plannedMonthlyMinor = monthlyMinor)) } }
    }
}

/**
 * الأهداف: the goal closest to the user's attention up top (the first one not reached yet) with save and withdraw,
 * a "reach it faster" what-if, then the other goals.
 */
@Composable
fun GoalsScreen(onAdd: () -> Unit, onOpen: (String) -> Unit, embedded: Boolean = false, viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var entrySheet by rememberSaveable { mutableStateOf<Boolean?>(null) }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone = { entrySheet = null })
    val largeTitle = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = if (embedded) Modifier else Modifier.nestedScroll(largeTitle.nestedScrollConnection),
        contentWindowInsets = TabInsets,
        topBar = { if (!embedded) MasroufiTopBar(stringResource(R.string.tab_goals), scrollBehavior = largeTitle) { GlassIconButton(onClick = onAdd) { Icon(MIcons.Plus, stringResource(R.string.goal_add)) } } },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        val list = goals ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(MIcons.Goals, stringResource(R.string.goals_empty_title), stringResource(R.string.goals_empty_body), Modifier.padding(padding), stringResource(R.string.goal_add), onAdd)
            return@Scaffold
        }
        val main = list.firstOrNull { it.savedMinor < it.goal.targetMinor } ?: list.first()
        val others = list.filter { it.goal.id != main.goal.id }
        val today = remember { LocalDate.now() }
        val monthly = main.monthlyPlan(today)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).edgeFade(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, DockClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "main") { GoalHero(main, monthly, today, onOpen, onSave = { entrySheet = true }, onWithdraw = { entrySheet = false }) }
            if (monthly != null && monthly > 0 && main.savedMinor < main.goal.targetMinor) {
                item(key = "boost") { BoostCard(main, monthly, today) { viewModel.adoptMonthly(main.goal, it) } }
            }
            if (others.isNotEmpty()) {
                item(key = "others-h") {
                    Row(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.goals_others), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        Text(others.size.toString(), style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textTertiary)
                    }
                }
                item(key = "others") { OtherGoals(others, onOpen) }
            }
            item(key = "new") { NewGoalButton(onAdd) }
            item(key = "note") {
                Text(stringResource(R.string.goals_earmark_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        entrySheet?.let { contribute ->
            AmountSheet(
                title = stringResource(if (contribute) R.string.goal_save_now else R.string.goal_withdraw),
                currency = main.goal.currency,
                onDismiss = { entrySheet = null },
                onConfirm = { amount, note -> viewModel.addEntry(main.goal.id, if (contribute) amount else -amount, note) },
            )
        }
    }
}

/** The progress ring with the goal's icon at its centre. */
@Composable
internal fun GoalRing(fraction: Float, color: Color, iconKey: String, size: Int) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.09f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val origin = Offset(stroke / 2, stroke / 2)
            drawArc(color.copy(alpha = 0.18f), -90f, 360f, false, origin, arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, origin, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Icon(CategoryIcons.of(iconKey), contentDescription = null, tint = color, modifier = Modifier.size((size * 0.34f).dp))
    }
}

@Composable
private fun rememberLongDate(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
}

@Composable
private fun GoalHero(item: GoalWithSaved, monthly: Long?, today: LocalDate, onOpen: (String) -> Unit, onSave: () -> Unit, onWithdraw: () -> Unit) {
    val goal = item.goal
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val dates = rememberLongDate()
    val color = categoryColor(goal.colorIndex)
    val fraction = GoalMath.fraction(item.savedMinor, goal.targetMinor)
    val deadline = goal.targetEpochDay?.let(LocalDate::ofEpochDay)
    val onTrack = GoalBoost.onTrack(item.savedMinor, goal.targetMinor, goal.createdAt.toLocalDate(), deadline, today)
    Column(
        Modifier
            .fillMaxWidth()
            .sharedContainer("goal-${goal.id}")
            .clip(MaterialTheme.shapes.extraLarge)
            .background(glass.fill)
            .border(1.dp, glass.rim, MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button) { onOpen(goal.id) }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(fraction, color, goal.iconKey, 100)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedAmount(item.savedMinor, goal.currency, style = MaterialTheme.typography.headlineMedium)
                }
                Text(stringResource(R.string.goal_of_target, money.format(goal.targetMinor, goal.currency)), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                when {
                    item.savedMinor >= goal.targetMinor -> StatusChip(stringResource(R.string.goal_reached), MaterialTheme.colorScheme.primary)
                    onTrack == true -> StatusChip(stringResource(R.string.goal_ahead), LocalFinanceColors.current.categories[2])
                    onTrack == false -> StatusChip(stringResource(R.string.goal_behind), LocalFinanceColors.current.warning)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                monthly?.let { stringResource(R.string.goal_pct_monthly, (fraction * 100).toInt(), money.format(it, goal.currency)) } ?: "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            deadline?.let { Text(stringResource(R.string.goal_due, it.format(dates)), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.goal_save_now)) }
            GlassSecondaryButton(onClick = onWithdraw, enabled = item.savedMinor > 0, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.goal_withdraw)) }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** وصّلها أسرع: a few pounds more a day, and the finish date that buys. Nothing changes until the user adopts it. */
@Composable
private fun BoostCard(item: GoalWithSaved, monthly: Long, today: LocalDate, onAdopt: (Long) -> Unit) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val dates = rememberLongDate()
    val violet = MaterialTheme.colorScheme.secondary
    val goal = item.goal
    val unit = Money.unit(goal.currency)
    var extra by rememberSaveable(goal.id) { mutableStateOf(20L) }
    val remaining = goal.targetMinor - item.savedMinor
    val base = GoalBoost.finish(remaining, monthly, 0, today) ?: return
    val boosted = GoalBoost.finish(remaining, monthly, extra * unit, today) ?: base
    val newMonthly = monthly + extra * unit * 30
    val savedDays = ChronoUnit.DAYS.between(boosted, base).toInt()
    val deadline = goal.targetEpochDay?.let(LocalDate::ofEpochDay)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(violet.copy(alpha = 0.08f))
            .border(1.dp, violet.copy(alpha = 0.25f), MaterialTheme.shapes.extraLarge)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MIcons.Trending, contentDescription = null, tint = violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.boost_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.boost_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0L, 10L, 20L, 50L).forEach { option ->
                val on = option == extra
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .background(if (on) violet else Color.Transparent)
                        .border(1.dp, if (on) violet else violet.copy(alpha = 0.3f), CircleShape)
                        .clickable(role = Role.RadioButton) { extra = option }
                        .semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (option == 0L) stringResource(R.string.boost_as_is) else "+$option",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onSecondary else violet,
                        maxLines = 1,
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(glass.base.copy(alpha = 0.5f)).padding(14.dp).semantics(mergeDescendants = true) { },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.boost_reach), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
            Text(boosted.format(dates), style = MaterialTheme.typography.headlineSmall)
            Text(
                when {
                    extra == 0L && deadline != null && !base.isAfter(deadline) -> stringResource(R.string.boost_as_is_early, money.format(monthly, goal.currency))
                    extra == 0L -> stringResource(R.string.boost_as_is_line, money.format(monthly, goal.currency))
                    savedDays >= 7 -> pluralStringResource(R.plurals.boost_faster_weeks, savedDays / 7, savedDays / 7, money.format(newMonthly, goal.currency))
                    else -> pluralStringResource(R.plurals.boost_faster_days, savedDays.coerceAtLeast(1), savedDays.coerceAtLeast(1), money.format(newMonthly, goal.currency))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (extra == 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        if (extra > 0L) {
            TextButton(onClick = { onAdopt(newMonthly) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.boost_adopt, money.format(newMonthly, goal.currency)), color = violet)
            }
        }
    }
}

@Composable
private fun OtherGoals(others: List<GoalWithSaved>, onOpen: (String) -> Unit) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(vertical = 6.dp),
    ) {
        others.forEach { item ->
            val color = categoryColor(item.goal.colorIndex)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item.goal.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(CategoryIcons.of(item.goal.iconKey), color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(item.goal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            stringResource(R.string.goal_saved_of, money.format(item.savedMinor, item.goal.currency), money.format(item.goal.targetMinor, item.goal.currency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.textTertiary,
                        )
                    }
                    AnimatedProgressBar(GoalMath.fraction(item.savedMinor, item.goal.targetMinor), color, height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun NewGoalButton(onAdd: () -> Unit) {
    val dash = LocalGlass.current.track
    val violet = MaterialTheme.colorScheme.secondary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.large)
            .drawBehind {
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onAdd),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MIcons.Plus, contentDescription = null, tint = violet, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.goal_add), style = MaterialTheme.typography.labelLarge, color = violet)
    }
}

data class GoalDetailState(
    val goal: GoalWithSaved? = null,
    val entries: List<GoalEntry> = emptyList(),
    val estimate: GoalEstimate? = null,
)

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val goals: GoalRepository,
    clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<GoalDetailRoute>().id
    val state: StateFlow<GoalDetailState> = combine(goals.observeGoal(id), goals.observeEntries(id)) { g, e ->
        val today = LocalDate.now(clock)
        GoalDetailState(g, e, g?.let { GoalMath.estimate(e.map { entry -> entry.occurredAt.toLocalDate(clock.zone) to entry.amountMinor }, it.goal.targetMinor, today) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailState())
    val effects = EffectChannel<EditorEffect>()
    val closeEffects = EffectChannel<EditorEffect>()

    fun addEntry(amountMinor: Long, note: String) {
        viewModelScope.launch { effects.attempt { goals.addEntry(id, amountMinor, note) } }
    }

    fun deleteEntry(entry: GoalEntry) {
        viewModelScope.launch { effects.attempt { goals.deleteEntry(entry) } }
    }

    fun delete() {
        viewModelScope.launch { closeEffects.attempt { goals.delete(id) } }
    }
}

@Composable
fun GoalDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: GoalDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var entrySheet by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val dates = rememberDateFormatter()
    val money = LocalMoneyFormatter.current
    val finance = LocalFinanceColors.current
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone = { entrySheet = null })
    HandleEditorEffects(viewModel.closeEffects.flow, snackbar, onDone = onBack)
    val item = state.goal
    Scaffold(
        topBar = {
            MasroufiTopBar(item?.goal?.name.orEmpty(), onBack = onBack) {
                if (item != null) {
                    GlassIconButton(onClick = { onEdit(item.goal.id) }) { Icon(MIcons.Pencil, stringResource(R.string.action_edit)) }
                    GlassIconButton(onClick = { confirmDelete = true }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (item == null) return@Scaffold
        val goal = item.goal
        val fraction = GoalMath.fraction(item.savedMinor, goal.targetMinor)
        val color = categoryColor(goal.colorIndex)
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PressableCard(onClick = null, modifier = Modifier.sharedContainer("goal-${goal.id}")) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DonutChart(
                            slices = listOf(ChartSlice(goal.name, item.savedMinor.coerceAtLeast(0), color), ChartSlice("", (goal.targetMinor - item.savedMinor).coerceAtLeast(0), androidx.compose.ui.graphics.Color.Transparent)),
                            modifier = Modifier.size(180.dp),
                            description = "${(fraction * 100).toInt()}%",
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                                AnimatedAmount(item.savedMinor, goal.currency, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.goal_target, money.format(goal.targetMinor, goal.currency)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.goal_remaining, money.format((goal.targetMinor - item.savedMinor).coerceAtLeast(0), goal.currency)), style = MaterialTheme.typography.bodyMedium)
                    goal.targetEpochDay?.let { Text(stringResource(R.string.goal_target_date, LocalDate.ofEpochDay(it).format(dates)), style = MaterialTheme.typography.bodyMedium) }
                    goal.plannedMonthlyMinor?.let { Text(stringResource(R.string.goal_planned_monthly, money.format(it, goal.currency)), style = MaterialTheme.typography.bodyMedium) }
                    val estimate = state.estimate
                    Text(
                        when {
                            item.savedMinor >= goal.targetMinor -> stringResource(R.string.goal_reached)
                            estimate != null -> stringResource(R.string.goal_estimate, estimate.estimatedDate.format(dates), money.format(estimate.monthlyRateMinor, goal.currency))
                            else -> stringResource(R.string.goal_estimate_unavailable)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton(onClick = { entrySheet = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.goal_contribute)) }
                        GlassSecondaryButton(onClick = { entrySheet = false }, enabled = item.savedMinor > 0, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.goal_withdraw)) }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.goal_history)) }
            if (state.entries.isEmpty()) item { Text(stringResource(R.string.goal_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.entries, key = { it.id }) { entry ->
                Row(Modifier.fillMaxWidth().animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.note.ifBlank { stringResource(if (entry.amountMinor > 0) R.string.goal_contribution else R.string.goal_withdrawal) }, style = MaterialTheme.typography.bodyMedium)
                        Text(entry.occurredAt.toLocalDate().format(dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyText(entry.amountMinor, goal.currency, color = if (entry.amountMinor > 0) finance.income else finance.expense, signed = true)
                    IconButton(onClick = { viewModel.deleteEntry(entry) }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
        }
    }

    entrySheet?.let { contribute ->
        AmountSheet(
            title = stringResource(if (contribute) R.string.goal_contribute else R.string.goal_withdraw),
            currency = item?.goal?.currency ?: Money.DEFAULT_CURRENCY,
            onDismiss = { entrySheet = null },
            onConfirm = { amount, note -> viewModel.addEntry(if (contribute) amount else -amount, note) },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.goal_delete_title), stringResource(R.string.goal_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}

/** Bottom sheet for a single amount plus optional note; reused by goals and debt repayments. */
@Composable
fun AmountSheet(title: String, currency: String, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit, initial: String = "") {
    var amount by rememberSaveable { mutableStateOf(initial) }
    var note by rememberSaveable { mutableStateOf("") }
    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            AmountField(amount, { amount = it }, currency)
            TextInput(note, { note = it }, stringResource(R.string.field_note))
            val parsed = Money.parse(amount, currency) ?: 0
            GlassSecondaryButton(onClick = { onConfirm(parsed, note) }, enabled = parsed > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

data class GoalForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val target: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
    val targetDate: LocalDate? = null,
    /** A plan kept from before (no deadline); a deadline replaces it with what the deadline needs. */
    val monthly: String = "",
    val iconKey: String = "savings",
    val colorIndex: Int = 7,
    val template: String? = null,
    val autoSave: Boolean = false,
    val savedMinor: Long = 0,
) {
    fun monthlyMinor(today: LocalDate): Long? {
        val target = Money.parse(target, currency) ?: return null
        return targetDate?.let { GoalBoost.monthlyFor(target - savedMinor, today, it) } ?: Money.parse(monthly, currency)
    }
}

@HiltViewModel
class GoalEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val goals: GoalRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<GoalEditorRoute>().id
    private var original: Goal? = null
    private val _form = MutableStateFlow(GoalForm())
    val form: StateFlow<GoalForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    init {
        viewModelScope.launch {
            val existing = id?.let { goals.get(it) }
            original = existing
            _form.value = existing?.let {
                GoalForm(
                    loaded = true,
                    isNew = false,
                    name = it.name,
                    target = Money.toPlainString(it.targetMinor, it.currency),
                    currency = it.currency,
                    targetDate = it.targetEpochDay?.let(LocalDate::ofEpochDay),
                    monthly = it.plannedMonthlyMinor?.let { m -> Money.toPlainString(m, it.currency) }.orEmpty(),
                    iconKey = it.iconKey,
                    colorIndex = it.colorIndex,
                    autoSave = it.autoSave,
                    savedMinor = goals.observeGoal(it.id).first()?.savedMinor ?: 0,
                )
            } ?: GoalForm(loaded = true, currency = settings.current().primaryCurrency, targetDate = LocalDate.now(clock).plusMonths(6))
        }
    }

    fun update(block: (GoalForm) -> GoalForm) = _form.update(block)

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val now = clock.millis()
                val monthly = f.monthlyMinor(LocalDate.now(clock))?.takeIf { it > 0 }
                goals.save(
                    Goal(
                        id = original?.id ?: newId(),
                        name = f.name,
                        targetMinor = Money.parse(f.target, f.currency) ?: 0,
                        currency = original?.currency ?: f.currency,
                        targetEpochDay = f.targetDate?.toEpochDay(),
                        iconKey = f.iconKey,
                        colorIndex = f.colorIndex,
                        plannedMonthlyMinor = monthly,
                        autoSave = f.autoSave && monthly != null,
                        archived = original?.archived ?: false,
                        createdAt = original?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}

/** Seasonal Egyptian saving targets; picking one fills the name, icon and colour, never a price. */
private data class GoalTemplate(val key: String, val label: Int, val name: Int?, val icon: String, val color: Int)

private val goalTemplates = listOf(
    GoalTemplate("summer", R.string.tpl_summer, R.string.tpl_summer, "travel", 4),
    GoalTemplate("ramadan", R.string.tpl_ramadan, R.string.tpl_ramadan, "gift", 7),
    GoalTemplate("phone", R.string.tpl_phone, R.string.tpl_phone, "phone", 2),
    GoalTemplate("emergency", R.string.tpl_emergency_short, R.string.tpl_emergency, "health", 0),
    GoalTemplate("wedding", R.string.tpl_wedding, R.string.tpl_wedding, "family", 3),
    GoalTemplate("car", R.string.tpl_car, R.string.tpl_car, "car", 1),
    GoalTemplate("school", R.string.tpl_school_short, R.string.tpl_school, "school", 5),
    GoalTemplate("other", R.string.tpl_other, null, "savings", 7),
)

/** The six goal colours, as palette indices: mint, lime, violet, amber, coral, blue. */
private val goalSwatches = listOf(2 to R.string.swatch_green, 6 to R.string.swatch_lime, 7 to R.string.swatch_violet, 8 to R.string.swatch_amber, 0 to R.string.swatch_coral, 1 to R.string.swatch_blue)

/** New / edit goal: a template, a name and amount, when, and what that means a month; optionally saved automatically. */
@Composable
fun GoalEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: GoalEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val today = remember { LocalDate.now() }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    val monthly = form.monthlyMinor(today)
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(if (form.isNew) R.string.goal_add else R.string.goal_edit), onBack = onBack) },
        snackbarHost = { GlassSnackbarHost(snackbar) },
        bottomBar = { if (form.loaded) EditorSaveBar(viewModel::save, label = stringResource(if (form.isNew) R.string.goal_start else R.string.action_save)) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        val glass = LocalGlass.current
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoalPreview(form, monthly)
            if (form.isNew) {
                Text(stringResource(R.string.goal_pick), style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    goalTemplates.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { t ->
                                val name = t.name?.let { stringResource(it) }.orEmpty()
                                val color = categoryColor(t.color)
                                val on = form.template == t.key
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(if (on) color.copy(alpha = 0.18f) else glass.fill)
                                        .border(1.dp, if (on) color else glass.strokeBottom, MaterialTheme.shapes.medium)
                                        .clickable(role = Role.RadioButton) {
                                            viewModel.update { it.copy(template = t.key, name = name, iconKey = t.icon, colorIndex = t.color) }
                                        }
                                        .semantics { selected = on }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(CategoryIcons.of(t.icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text(stringResource(t.label), style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
            TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_name))
            AmountField(form.target, { v -> viewModel.update { it.copy(target = v) } }, form.currency, label = stringResource(R.string.field_amount))

            Text(stringResource(R.string.goal_when), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(3L, 6L, 12L).forEach { months ->
                    val date = today.plusMonths(months)
                    val on = form.targetDate == date
                    val lime = MaterialTheme.colorScheme.primary
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(if (on) lime.copy(alpha = 0.14f) else glass.fill)
                            .border(1.dp, if (on) lime else glass.strokeBottom, CircleShape)
                            .clickable(role = Role.RadioButton) { viewModel.update { it.copy(targetDate = date) } }
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (months == 12L) stringResource(R.string.goal_span_year) else pluralStringResource(R.plurals.goal_span_months, months.toInt(), months.toInt()),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) lime else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            DateChip(form.targetDate ?: today.plusMonths(6), { d -> viewModel.update { it.copy(targetDate = d) } }, label = stringResource(R.string.goal_pick_date))

            // Auto-save: at the start of each month the plan is earmarked for the goal automatically.
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(MIcons.Repeat, MaterialTheme.colorScheme.primary, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.goal_auto), style = MaterialTheme.typography.titleSmall)
                    Text(
                        monthly?.takeIf { it > 0 }?.let { stringResource(R.string.goal_auto_sub, LocalMoneyFormatter.current.format(it, form.currency)) } ?: stringResource(R.string.goal_auto_needs_plan),
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.textTertiary,
                    )
                }
                GlassSwitch(form.autoSave && monthly != null, { v -> viewModel.update { it.copy(autoSave = v) } }, enabled = monthly != null && monthly > 0)
            }

            Text(stringResource(R.string.goal_color), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                goalSwatches.forEach { (index, label) ->
                    val on = form.colorIndex == index
                    val name = stringResource(label)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(categoryColor(index))
                            .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable(role = Role.RadioButton) { viewModel.update { it.copy(colorIndex = index) } }
                            .semantics { selected = on; contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(MIcons.Check, contentDescription = null, tint = glass.base, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Live preview: the goal's ring and icon, its amount and deadline, and what it needs a month and a day. */
@Composable
private fun GoalPreview(form: GoalForm, monthly: Long?) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val dates = rememberLongDate()
    val color = categoryColor(form.colorIndex)
    val target = Money.parse(form.target, form.currency) ?: 0L
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.3f), MaterialTheme.shapes.extraLarge).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(if (target > 0) form.savedMinor.toFloat() / target else 0f, color, form.iconKey, 72)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(form.name.ifBlank { stringResource(R.string.goal_preview_name) }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(money.format(target, form.currency), style = MaterialTheme.typography.headlineMedium, color = color)
                form.targetDate?.let { Text(stringResource(R.string.goal_until, it.format(dates)), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary) }
            }
        }
        HorizontalDivider(color = color.copy(alpha = 0.25f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.goal_need), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (monthly != null && monthly > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(money.format(monthly, form.currency), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.goal_need_daily, money.format(monthly / 30, form.currency)), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                }
            } else {
                Text("—", style = MaterialTheme.typography.titleMedium, color = glass.textTertiary)
            }
        }
    }
}
