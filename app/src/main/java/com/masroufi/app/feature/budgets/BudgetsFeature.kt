@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.masroufi.app.feature.budgets

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.masroufi.app.R
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.masroufi.app.core.DateWindow
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.core.startMillis
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.Budget
import com.masroufi.app.data.model.BudgetPeriod
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.CategoryKind
import com.masroufi.app.data.model.CategoryTotal
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.BudgetRepository
import com.masroufi.app.data.repo.BudgetWithProgress
import com.masroufi.app.data.repo.CategoryRepository
import com.masroufi.app.data.repo.ProgressRepository
import com.masroufi.app.data.repo.TodayRepository
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.domain.Badge
import com.masroufi.app.domain.BadgeProgress
import com.masroufi.app.domain.BudgetProgress
import com.masroufi.app.domain.BudgetPlan
import com.masroufi.app.domain.BudgetStatus
import com.masroufi.app.domain.BudgetSuggestion
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.DateChip
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EditorSaveBar
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.GlassSwitch
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.navigation.BudgetEditorRoute
import com.masroufi.app.ui.navigation.DockClearance
import com.masroufi.app.ui.navigation.TabInsets
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class BudgetsUiState(
    val loading: Boolean = true,
    val items: List<BudgetWithProgress> = emptyList(),
    val categories: Map<String, Category> = emptyMap(),
    /** The suggested plan, offered only while the user has no budgets yet. */
    val suggestions: List<BudgetSuggestion> = emptyList(),
    val currency: String = Money.DEFAULT_CURRENCY,
    val balanceMinor: Long = 0,
    /** The weekly challenge: three home-cooked days in a row, until it is earned. */
    val challenge: BadgeProgress? = null,
)

/** Expense totals in [currency], with sub-category spending counted towards its top-level category. */
private fun List<CategoryTotal>.byTopCategory(currency: String, categories: Map<String, Category>): Map<String, Long> =
    filter { it.currency == currency && it.categoryId != null }
        .groupBy { t -> categories[t.categoryId]?.let { it.parentId ?: it.id } }
        .filterKeys { it != null && categories[it]?.kind == CategoryKind.EXPENSE }
        .map { (id, rows) -> id!! to rows.sumOf { it.total } }
        .toMap()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    transactions: TransactionRepository,
    progress: ProgressRepository,
    today: TodayRepository,
    private val clock: Clock,
) : ViewModel() {
    private val day = LocalDate.now(clock)
    private val tomorrow = day.plusDays(1).startMillis(clock.zone)

    private val spending = combine(
        transactions.observeExpenseByCategory(day.minusDays(30).startMillis(clock.zone), tomorrow),
        transactions.observeExpenseByCategory(day.withDayOfMonth(1).startMillis(clock.zone), tomorrow),
        ::Pair,
    )

    val state: StateFlow<BudgetsUiState> = combine(
        budgets.observeWithProgress(),
        categories.observeAll(),
        spending,
        today.observe(),
        progress.observe(),
    ) { b, c, (recent, month), t, p ->
        val cats = c.associateBy { it.id }
        val unit = Money.unit(t.currency)
        BudgetsUiState(
            loading = false,
            items = b,
            categories = cats,
            suggestions = if (b.isNotEmpty()) emptyList() else BudgetPlan.suggest(
                recent.byTopCategory(t.currency, cats),
                month.byTopCategory(t.currency, cats),
                budgeted = b.mapNotNull { it.budget.categoryId }.toSet(),
                unit = unit,
            ),
            currency = t.currency,
            balanceMinor = t.balanceMinor,
            challenge = p.badges.firstOrNull { it.badge == Badge.HOME_COOK }?.takeIf { !it.earned },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    val effects = EffectChannel<EditorEffect>()

    /** Turns the chosen suggestions into monthly budgets; [names] are the categories' shown labels. */
    fun activate(picked: List<BudgetSuggestion>, names: Map<String, String>) {
        val currency = state.value.currency
        viewModelScope.launch {
            effects.attempt {
                picked.forEach { s ->
                    budgets.save(
                        Budget(
                            id = newId(),
                            name = names[s.categoryId].orEmpty(),
                            categoryId = s.categoryId,
                            limitMinor = s.limitMinor,
                            currency = currency,
                            period = BudgetPeriod.MONTHLY,
                            startEpochDay = day.withDayOfMonth(1).toEpochDay(),
                            updatedAt = clock.millis(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetsScreen(onAdd: () -> Unit, onEdit: (String) -> Unit, embedded: Boolean = false, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleEditorEffects(viewModel.effects.flow, snackbar) {}
    val largeTitle = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = if (embedded) Modifier else Modifier.nestedScroll(largeTitle.nestedScrollConnection),
        contentWindowInsets = TabInsets,
        topBar = { if (!embedded) MasroufiTopBar(stringResource(R.string.tab_budgets), scrollBehavior = largeTitle) { GlassIconButton(onClick = onAdd) { Icon(MIcons.Plus, stringResource(R.string.budget_add)) } } },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        if (state.items.isEmpty() && state.suggestions.isEmpty()) {
            EmptyState(MIcons.Budgets, stringResource(R.string.budgets_empty_title), stringResource(R.string.budgets_empty_body), Modifier.padding(padding), stringResource(R.string.budget_add), onAdd)
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).edgeFade(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, DockClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.suggestions.isNotEmpty()) {
                item(key = "plan") { SuggestedPlan(state, onActivate = viewModel::activate, onCustom = onAdd) }
            }
            items(state.items, key = { it.budget.id }) { item -> BudgetCard(item, state.categories, onEdit, Modifier.animateItem()) }
            state.challenge?.let { item(key = "challenge") { ChallengeCard(it) } }
        }
    }
}

/** Days in the period a monthly figure is spread over, and the days still left in it (today included). */
private fun monthDays(today: LocalDate): Pair<Int, Int> = today.lengthOfMonth() to (today.lengthOfMonth() - today.dayOfMonth + 1)

@Composable
private fun SuggestedPlan(state: BudgetsUiState, onActivate: (List<BudgetSuggestion>, Map<String, String>) -> Unit, onCustom: () -> Unit) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val violet = MaterialTheme.colorScheme.secondary
    var off by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val names = state.suggestions.associate { it.categoryId to categoryLabel(state.categories[it.categoryId]) }
    val picked = state.suggestions.filter { it.categoryId !in off }
    val today = remember { LocalDate.now() }
    val (days, _) = monthDays(today)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(glass.fill)
            .border(1.dp, glass.rim, MaterialTheme.shapes.extraLarge)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(MIcons.Sparkle, violet, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.plan_suggest_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.plan_suggest_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.suggestions.forEach { s ->
            val on = s.categoryId !in off
            val category = state.categories[s.categoryId]
            val color = categoryColor(category?.colorIndex)
            val fraction = if (s.limitMinor > 0) s.spentMinor.toFloat() / s.limitMinor else 0f
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Checkbox) { off = if (on) off + s.categoryId else off - s.categoryId }
                    .semantics { selected = on }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .then(if (on) Modifier else Modifier.border(1.5.dp, glass.textTertiary, RoundedCornerShape(8.dp))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(MIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                // Unticked rows fade back so the total visibly follows the ticks.
                IconBadge(CategoryIcons.of(category?.iconKey), color, size = 40.dp, modifier = Modifier.alpha(if (on) 1f else 0.45f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).alpha(if (on) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(names[s.categoryId].orEmpty(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(money.format(s.limitMinor, state.currency), style = MaterialTheme.typography.titleSmall)
                    }
                    AnimatedProgressBar(fraction, if (fraction >= 0.9f) LocalFinanceColors.current.warning else MaterialTheme.colorScheme.primary, height = 6.dp)
                    Text(
                        stringResource(R.string.plan_suggest_row, money.format(s.spentMinor, state.currency), money.format(s.limitMinor / days, state.currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.textTertiary,
                    )
                }
            }
        }
        HorizontalDivider(color = glass.strokeBottom)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plan_total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.plan_total_of, money.format(picked.sumOf { it.limitMinor }, state.currency), money.format(state.balanceMinor, state.currency)),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        GlassButton(onClick = { onActivate(picked, names) }, enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(
                when (picked.size) {
                    0 -> stringResource(R.string.plan_activate_none)
                    1 -> stringResource(R.string.plan_activate_one)
                    2 -> stringResource(R.string.plan_activate_two)
                    else -> stringResource(R.string.plan_activate_many, picked.size)
                },
            )
        }
        TextButton(onClick = onCustom, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.plan_custom), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ChallengeCard(challenge: BadgeProgress) {
    val glass = LocalGlass.current
    val coral = LocalFinanceColors.current.over
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(glass.fill)
            .border(1.dp, glass.rim, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(MIcons.Food, coral, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.plan_challenge), style = MaterialTheme.typography.labelMedium, color = glass.textTertiary)
            Text(stringResource(R.string.plan_challenge_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.plan_challenge_reward, challenge.badge.reward), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(challenge.badge.target) { i -> Box(Modifier.size(10.dp).clip(CircleShape).background(if (i < challenge.done) coral else glass.track)) }
        }
    }
}

@Composable
private fun BudgetCard(item: BudgetWithProgress, categories: Map<String, Category>, onEdit: (String) -> Unit, modifier: Modifier) {
    val finance = LocalFinanceColors.current
    val money = LocalMoneyFormatter.current
    val dates = rememberDateFormatter()
    val p = item.progress
    val category = item.budget.categoryId?.let { categories[it] }
    val color = when (p.status) {
        BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
        BudgetStatus.WARNING -> finance.warning
        BudgetStatus.OVER -> finance.over
    }
    PressableCard(onClick = { onEdit(item.budget.id) }, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(category?.let { CategoryIcons.of(it.iconKey) } ?: MIcons.Budgets, category?.let { categoryColor(it.colorIndex) } ?: MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.budget.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    category?.let { categoryLabel(it) } ?: stringResource(R.string.budget_all_spending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedAmount(p.remainingMinor, item.budget.currency, style = MaterialTheme.typography.titleMedium, color = if (p.remainingMinor < 0) finance.over else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        AnimatedProgressBar(p.fraction, color)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.budget_spent_of, money.format(p.spentMinor, item.budget.currency), money.format(p.effectiveLimitMinor, item.budget.currency)),
            style = MaterialTheme.typography.bodySmall,
        )
        if (p.carriedMinor != 0L) {
            Text(stringResource(R.string.budget_rollover_amount, money.format(p.carriedMinor, item.budget.currency, signed = true)), style = MaterialTheme.typography.bodySmall)
        }
        item.previousSpentMinor?.let {
            Text(stringResource(R.string.budget_last_month, money.format(it, item.budget.currency)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.budget.period == BudgetPeriod.CUSTOM) {
            Text(
                "${item.window.startMillis.toLocalDate().format(dates)} – ${(item.window.endMillis - 1).toLocalDate().format(dates)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (p.status) {
            BudgetStatus.OVER -> Text(stringResource(R.string.budget_over), color = finance.over, style = MaterialTheme.typography.labelLarge)
            BudgetStatus.WARNING -> Text(stringResource(R.string.budget_warning), color = finance.warning, style = MaterialTheme.typography.labelLarge)
            BudgetStatus.ON_TRACK -> Unit
        }
    }
}

data class BudgetForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val categoryId: String? = null,
    val limit: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate = LocalDate.now().plusDays(30),
    val rollover: Boolean = false,
    val categories: List<Category> = emptyList(),
) {
    /** The window the limit applies to, for the live "spent so far" line. */
    fun window(today: LocalDate): DateWindow =
        if (period == BudgetPeriod.MONTHLY) DateWindow.month(YearMonth.from(today)) else DateWindow.days(start, maxOf(start, end))

    /** Days the limit is spread over, and the days of it still ahead (today included). */
    fun days(today: LocalDate): Pair<Int, Int> =
        if (period == BudgetPeriod.MONTHLY) monthDays(today) else {
            val total = (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)
            total to (end.toEpochDay() - maxOf(today, start).toEpochDay() + 1).toInt().coerceIn(1, total)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    transactions: TransactionRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<BudgetEditorRoute>().id
    private var original: Budget? = null
    private val _form = MutableStateFlow(BudgetForm())
    val form: StateFlow<BudgetForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()
    private val today = LocalDate.now(clock)

    /** Spent so far in the chosen category and window, live as the choices change. */
    val spent: StateFlow<Long> = _form
        .map { Triple(it.categoryId, it.currency, it.window(today)) }
        .distinctUntilChanged()
        .flatMapLatest { (category, currency, window) -> budgets.observeSpent(category, currency, window) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** The last 30 days of spending per category (whole spending under null), the basis for the amount presets. */
    private val recent: StateFlow<List<CategoryTotal>> = transactions
        .observeExpenseByCategory(today.minusDays(30).startMillis(clock.zone), today.plusDays(1).startMillis(clock.zone))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val presets: StateFlow<List<Long>> = combine(_form, recent) { f, totals ->
        val unit = Money.unit(f.currency)
        val byCategory = f.categories.associateBy { it.id }
        val base = totals.filter { t ->
            t.currency == f.currency && (f.categoryId == null || t.categoryId == f.categoryId || byCategory[t.categoryId]?.parentId == f.categoryId)
        }.sumOf { it.total }
        val suggested = BudgetPlan.niceLimit(base, unit)
        if (suggested <= 0) listOf(500L, 1_000L, 1_500L, 2_000L).map { it * unit }
        else listOf(0.8, 1.0, 1.2, 1.5).map { BudgetPlan.niceLimit((suggested * it).toLong(), unit) }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val cats = categories.observeAll().first().filter { it.kind == CategoryKind.EXPENSE && it.parentId == null }
            val existing = id?.let { budgets.get(it) }
            original = existing
            _form.value = if (existing != null) {
                BudgetForm(
                    loaded = true,
                    isNew = false,
                    name = existing.name,
                    categoryId = existing.categoryId,
                    limit = Money.toPlainString(existing.limitMinor, existing.currency),
                    currency = existing.currency,
                    period = existing.period,
                    start = LocalDate.ofEpochDay(existing.startEpochDay),
                    end = LocalDate.ofEpochDay(existing.endEpochDay ?: existing.startEpochDay),
                    rollover = existing.rollover,
                    categories = cats,
                )
            } else {
                // No currency field: new budgets follow the primary currency, like the daily number.
                BudgetForm(loaded = true, currency = settings.current().primaryCurrency, start = today, end = today.plusDays(29), rollover = true, categories = cats)
            }
        }
    }

    fun update(block: (BudgetForm) -> BudgetForm) = _form.update(block)

    fun save(defaultName: String) {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                val start = if (f.period == BudgetPeriod.MONTHLY) (original?.let { LocalDate.ofEpochDay(it.startEpochDay) } ?: today.withDayOfMonth(1)) else f.start
                budgets.save(
                    Budget(
                        id = original?.id ?: newId(),
                        name = f.name.ifBlank { defaultName },
                        categoryId = f.categoryId,
                        limitMinor = Money.parse(f.limit, f.currency) ?: 0,
                        currency = f.currency,
                        period = f.period,
                        startEpochDay = start.toEpochDay(),
                        endEpochDay = if (f.period == BudgetPeriod.CUSTOM) maxOf(f.end, f.start).toEpochDay() else null,
                        rollover = f.period == BudgetPeriod.MONTHLY && f.rollover,
                        updatedAt = clock.millis(),
                    ),
                )
            }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch { effects.attempt { budgets.delete(id) } }
    }
}

/** New / edit budget, amount first: what it is for, how much a month, and right away what that means per day. */
@Composable
fun BudgetEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: BudgetEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val spent by viewModel.spent.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    val selectedCategory = form.categories.firstOrNull { it.id == form.categoryId }
    val defaultName = selectedCategory?.let { categoryLabel(it) } ?: stringResource(R.string.budget_all_spending)
    val limitMinor = Money.parse(form.limit, form.currency) ?: 0L
    Scaffold(
        topBar = {
            MasroufiTopBar(stringResource(if (form.isNew) R.string.budget_add else R.string.budget_edit), onBack = onBack) {
                if (!form.isNew) GlassIconButton(onClick = { confirmDelete = true }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbar) },
        bottomBar = { if (form.loaded) EditorSaveBar({ viewModel.save(defaultName) }, enabled = limitMinor > 0, label = stringResource(R.string.budget_save)) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.add_on_what), style = MaterialTheme.typography.titleMedium)
            CategoryGrid(form.categories, form.categoryId) { id -> viewModel.update { it.copy(categoryId = id) } }

            Text(stringResource(R.string.budget_how_much), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            BigAmountInput(form.limit, form.currency) { v -> viewModel.update { it.copy(limit = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    val text = Money.toPlainString(preset, form.currency)
                    PresetPill(LocalMoneyFormatter.current.formatCompact(preset, form.currency), selected = preset == limitMinor, modifier = Modifier.weight(1f)) {
                        viewModel.update { it.copy(limit = text) }
                    }
                }
            }
            DailyTranslation(form, limitMinor, spent)
            BudgetSettings(form, viewModel)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.budget_delete_title), stringResource(R.string.budget_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}

/** "كل الصرف" plus every top-level expense category, four to a row. */
@Composable
private fun CategoryGrid(categories: List<Category>, selected: String?, onSelect: (String?) -> Unit) {
    val options: List<Category?> = listOf(null) + categories
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    val color = category?.let { categoryColor(it.colorIndex) } ?: MaterialTheme.colorScheme.secondary
                    val on = category?.id == selected
                    val glass = LocalGlass.current
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (on) color.copy(alpha = 0.18f) else glass.fill)
                            .border(1.dp, if (on) color else glass.strokeBottom, MaterialTheme.shapes.medium)
                            .clickable(role = Role.RadioButton) { onSelect(category?.id) }
                            .semantics { this.selected = on }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(category?.let { CategoryIcons.of(it.iconKey) } ?: MIcons.Budgets, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            category?.let { categoryLabel(it) } ?: stringResource(R.string.budget_all_spending),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The monthly amount as the page's headline: large, centred, typed with the number pad. */
@Composable
private fun BigAmountInput(value: String, currency: String, onChange: (String) -> Unit) {
    val glass = LocalGlass.current
    val formatter = LocalMoneyFormatter.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val v = Money.normalizeDigits(raw)
                    val fraction = v.substringAfter('.', "")
                    val ok = v.all { it.isDigit() || it == '.' } && v.count { it == '.' } <= 1 &&
                        fraction.length <= Money.fractionDigits(currency) && v.substringBefore('.').length <= 12
                    if (ok) onChange(v)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.displayMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.widthIn(min = 80.dp, max = 260.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) Text("0", style = MaterialTheme.typography.displayMedium, color = glass.textTertiary)
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(formatter.symbol(currency), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun PresetPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val lime = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (selected) lime.copy(alpha = 0.14f) else glass.fill)
            .border(1.dp, if (selected) lime else glass.strokeBottom, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = if (selected) lime else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "يعني X ج.م في اليوم", then how much of it this month's spending has already used. */
@Composable
private fun DailyTranslation(form: BudgetForm, limitMinor: Long, spentMinor: Long) {
    val glass = LocalGlass.current
    val finance = LocalFinanceColors.current
    val money = LocalMoneyFormatter.current
    val today = remember { LocalDate.now() }
    val (days, daysLeft) = form.days(today)
    val left = limitMinor - spentMinor
    val fraction = if (limitMinor > 0) spentMinor.toFloat() / limitMinor else 0f
    val (sub, subColor, barColor) = when {
        limitMinor > 0 && left < 0 -> Triple(stringResource(R.string.budget_sub_over, money.format(spentMinor, form.currency), money.format(-left, form.currency)), finance.over, finance.over)
        spentMinor == 0L -> Triple(stringResource(R.string.budget_sub_none), glass.textTertiary, MaterialTheme.colorScheme.primary)
        else -> Triple(
            stringResource(R.string.budget_sub_left, money.format(spentMinor, form.currency), money.format(left, form.currency), money.format(left / daysLeft, form.currency)),
            glass.textTertiary,
            if (fraction >= 0.8f) finance.warning else MaterialTheme.colorScheme.primary,
        )
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.budget_means), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(money.format(limitMinor / days, form.currency), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.budget_per_day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedProgressBar(fraction, barColor, height = 6.dp)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = subColor)
    }
}

@Composable
private fun BudgetSettings(form: BudgetForm, viewModel: BudgetEditorViewModel) {
    val glass = LocalGlass.current
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.budget_repeat), style = MaterialTheme.typography.bodyMedium)
        SegmentedChoice(
            listOf(BudgetPeriod.MONTHLY, BudgetPeriod.CUSTOM),
            form.period,
            { p -> viewModel.update { it.copy(period = p) } },
            { stringResource(if (it == BudgetPeriod.MONTHLY) R.string.budget_monthly else R.string.budget_custom) },
        )
        if (form.period == BudgetPeriod.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip(form.start, { d -> viewModel.update { it.copy(start = d) } }, label = stringResource(R.string.filter_from))
                DateChip(form.end, { d -> viewModel.update { it.copy(end = d) } }, label = stringResource(R.string.filter_to))
            }
        } else {
            HorizontalDivider(color = glass.strokeBottom)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.budget_rollover), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.budget_rollover_hint), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                }
                GlassSwitch(form.rollover, { v -> viewModel.update { it.copy(rollover = v) } })
            }
        }
        HorizontalDivider(color = glass.strokeBottom)
        // The alert threshold is the app-wide 80% warning; the daily check notifies once per month when it is crossed.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.budget_alert_at), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${BudgetProgress.WARNING_PERCENT}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = glass.strokeBottom)
        TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_name_optional))
    }
}
