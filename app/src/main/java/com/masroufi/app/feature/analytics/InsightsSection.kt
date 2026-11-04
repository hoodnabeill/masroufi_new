package com.masroufi.app.feature.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.core.startMillis
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.GoalWithSaved
import com.masroufi.app.data.repo.GoalRepository
import com.masroufi.app.data.repo.ProgressRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.data.repo.TodayBudget
import com.masroufi.app.data.repo.TodayRepository
import com.masroufi.app.data.repo.TransactionFilter
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.domain.Forecast
import com.masroufi.app.domain.HealthFactors
import com.masroufi.app.domain.HealthMath
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class InsightsState(
    val today: TodayBudget,
    val factors: HealthFactors,
    val forecast: Forecast,
    val streak: Int,
    /** This month's spending without recurring bills (what the pace is made of). */
    val variable: List<TransactionRow>,
)

/** "فلوسك بالتفصيل": the health score with its four factors, what would raise it, and where the month is heading. */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    transactions: TransactionRepository,
    today: TodayRepository,
    progress: ProgressRepository,
    goals: GoalRepository,
    recurring: RecurringRepository,
    clock: Clock,
) : ViewModel() {
    private val day = LocalDate.now(clock)

    val state: StateFlow<InsightsState?> = combine(
        today.observe(),
        progress.observe(),
        transactions.observeRows(TransactionFilter(type = TransactionType.EXPENSE, fromMillis = day.withDayOfMonth(1).startMillis(clock.zone), toMillis = day.plusDays(1).startMillis(clock.zone), limit = 5_000)),
        goals.observeGoals(),
        recurring.observeAll(),
    ) { t, p, rows, g, r ->
        val variable = rows.filter { it.transaction.recurringId == null && it.transaction.currency == t.currency }
        val forecast = HealthMath.forecast(t.balanceMinor, variable.sumOf { it.transaction.amountMinor }, day)
        val emergency: GoalWithSaved? = g.firstOrNull { it.goal.name.contains("طوارئ") || it.goal.name.contains("emergency", ignoreCase = true) }
        InsightsState(
            today = t,
            factors = HealthMath.factors(
                logDays = p.logDays, today = day, dailyPaceMinor = forecast.dailyPaceMinor, safeMinor = t.safeMinor,
                emergencySaved = emergency?.savedMinor ?: 0, emergencyTarget = emergency?.goal?.targetMinor ?: 0,
                billsTracked = r.any { it.active && it.type == TransactionType.EXPENSE },
            ),
            forecast = forecast,
            streak = p.streak.current,
            variable = variable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glass = LocalGlass.current
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.extraLarge).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** Health score ring (counts up once), its band, and the four factor bars. */
@Composable
internal fun HealthCard(s: InsightsState) {
    val glass = LocalGlass.current
    val finance = LocalFinanceColors.current
    val score = s.factors.score
    val color = when {
        score >= 70 -> MaterialTheme.colorScheme.primary
        score >= 40 -> finance.warning
        else -> finance.over
    }
    val reduce = LocalReduceMotion.current
    val sweep = remember { Animatable(if (reduce) score / 100f else 0f) }
    LaunchedEffect(score) { sweep.animateTo(score / 100f, spring(dampingRatio = 1f, stiffness = 60f)) }
    val track = glass.track
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp).clearAndSetSemantics { contentDescription = "$score / 100" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                    drawArc(color, -90f, 360f * sweep.value, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((sweep.value * 100).toInt().toString(), style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.health_of_100), style = MaterialTheme.typography.labelSmall, color = glass.textTertiary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        when {
                            score >= 70 -> R.string.health_band_good
                            score >= 40 -> R.string.health_band_ok
                            else -> R.string.health_band_start
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(stringResource(R.string.health_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val money = LocalMoneyFormatter.current
        Factor(stringResource(R.string.health_logging), stringResource(R.string.health_logging_value, (s.factors.logging * 14).toInt()), s.factors.logging)
        Factor(
            stringResource(R.string.health_pace),
            stringResource(R.string.health_pace_value, money.format(s.forecast.dailyPaceMinor, s.today.currency), money.format(s.today.safeMinor, s.today.currency)),
            s.factors.pace,
        )
        Factor(stringResource(R.string.health_emergency), if (s.factors.emergency > 0f) "${(s.factors.emergency * 100).toInt()}%" else stringResource(R.string.health_emergency_none), s.factors.emergency)
        Factor(stringResource(R.string.health_bills), stringResource(if (s.factors.bills > 0f) R.string.health_bills_yes else R.string.health_bills_no), s.factors.bills)
    }
}

@Composable
private fun Factor(label: String, value: String, fraction: Float) {
    val glass = LocalGlass.current
    val color = if (fraction >= 0.7f) MaterialTheme.colorScheme.primary else if (fraction >= 0.4f) LocalFinanceColors.current.warning else LocalFinanceColors.current.over
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(glass.track)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(color))
        }
    }
}

/** ارفع رقمك: up to three actions, each with the most points its factor could still add. */
@Composable
internal fun RaiseCard(s: InsightsState, onEmergencyGoal: () -> Unit, onLog: () -> Unit, onBudgets: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val f = s.factors
    val gain = { factor: Float -> ((1f - factor) * 25).toInt() }
    val actions = buildList {
        if (f.emergency < 1f) add(Triple(MIcons.Health, stringResource(R.string.health_act_emergency), gain(f.emergency) to onEmergencyGoal))
        if (f.logging < 1f && s.streak < 7) add(Triple(MIcons.Flame, stringResource(R.string.health_act_streak), gain(f.logging) to onLog))
        if (f.pace < 1f) add(Triple(MIcons.Budgets, stringResource(R.string.health_act_pace, money.format(s.today.safeMinor, s.today.currency)), gain(f.pace) to onBudgets))
    }.filter { it.third.first > 0 }
    if (actions.isEmpty()) return
    Card {
        Text(stringResource(R.string.health_raise), style = MaterialTheme.typography.titleMedium)
        actions.forEach { (icon, text, pair) -> ActionRow(icon, text, pair.first, pair.second) }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, points: Int, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val lime = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(glass.fillStrong).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.health_up_to, points), style = MaterialTheme.typography.labelLarge, color = lime)
    }
}

/**
 * توقع آخر الشهر: the balance today and two lines to the month's end — at the user's pace (to the run-out day) and at
 * the safe pace. Time runs right-to-left in Arabic, like the reading direction.
 */
@Composable
internal fun ForecastCard(s: InsightsState) {
    val glass = LocalGlass.current
    val finance = LocalFinanceColors.current
    val money = LocalMoneyFormatter.current
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }
    val today = remember { LocalDate.now() }
    val end = today.withDayOfMonth(today.lengthOfMonth())
    val runOut = s.forecast.runOut
    val lime = MaterialTheme.colorScheme.primary
    val coral = finance.over
    val line = glass.strokeBottom
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
    Card {
        Text(stringResource(R.string.forecast_label), style = MaterialTheme.typography.labelMedium, color = glass.textTertiary)
        Text(
            if (runOut != null) stringResource(R.string.forecast_runs_out, runOut.format(dates)) else stringResource(R.string.forecast_lasts),
            style = MaterialTheme.typography.titleMedium,
            color = if (runOut != null) coral else MaterialTheme.colorScheme.onSurface,
        )
        val description = stringResource(R.string.forecast_chart_desc)
        Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }) {
            val days = today.lengthOfMonth().toFloat()
            fun x(day: Int): Float { val t = (day - 1) / (days - 1).coerceAtLeast(1f); return if (rtl) size.width * (1 - t) else size.width * t }
            val top = 12.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val balance = s.today.balanceMinor.coerceAtLeast(1)
            fun y(value: Long): Float = bottom - (bottom - top) * (value.coerceAtLeast(0).toFloat() / balance)
            drawLine(line, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
            val start = Offset(x(today.dayOfMonth), y(balance))
            // Safe pace: what is left lasts exactly to the month's end.
            drawLine(lime, start, Offset(x(end.dayOfMonth), bottom), 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val pace = s.forecast.dailyPaceMinor
            val stop = runOut ?: end
            val left = balance - pace * (stop.toEpochDay() - today.toEpochDay())
            drawLine(coral, start, Offset(x(stop.dayOfMonth), y(left)), 3.dp.toPx(), cap = StrokeCap.Round)
            if (runOut != null) drawCircle(coral, 6.dp.toPx(), Offset(x(runOut.dayOfMonth), bottom))
            drawCircle(Color.White, 5.dp.toPx(), start)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(lime, stringResource(R.string.forecast_safe, money.format(s.today.safeMinor, s.today.currency)))
            Legend(coral, stringResource(R.string.forecast_yours, money.format(s.forecast.dailyPaceMinor, s.today.currency)))
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * اسأل مصروفي: a premium preview that answers a few common questions from the user's own numbers, computed on the
 * phone (no network, no AI service).
 */
@Composable
internal fun AskCard(s: InsightsState) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val violet = MaterialTheme.colorScheme.secondary
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val currency = s.today.currency
    val today = remember { LocalDate.now() }
    val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
    val variableTotal = s.variable.sumOf { it.transaction.amountMinor }
    val top = s.variable.groupBy { it.transaction.categoryId }.maxByOrNull { (_, rows) -> rows.sumOf { it.transaction.amountMinor } }
    val topLabel = top?.value?.first()?.let { categoryLabel(it.categoryName, it.categorySystemKey) }
    val topShare = if (top != null && variableTotal > 0) (top.value.sumOf { it.transaction.amountMinor } * 100 / variableTotal).toInt() else 0
    val unit = com.masroufi.app.core.Money.unit(currency)
    val cut = 500 * unit / daysLeft
    val questions = listOf(
        stringResource(R.string.ask_q_top) to (topLabel?.let { stringResource(R.string.ask_a_top, it, topShare) } ?: stringResource(R.string.ask_a_none)),
        stringResource(R.string.ask_q_save, money.format(500 * unit, currency)) to stringResource(
            R.string.ask_a_save,
            money.format((s.today.safeMinor - cut).coerceAtLeast(0), currency),
            money.format(s.today.safeMinor, currency),
            money.format(cut, currency),
        ),
        stringResource(R.string.ask_q_pace) to (s.forecast.runOut?.let { stringResource(R.string.ask_a_pace_short, money.format(s.forecast.dailyPaceMinor, currency), it.dayOfMonth) }
            ?: stringResource(R.string.ask_a_pace_ok)),
    )
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(violet.copy(alpha = 0.08f)).border(1.dp, violet.copy(alpha = 0.25f), MaterialTheme.shapes.extraLarge).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MIcons.Sparkle, contentDescription = null, tint = violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ask_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.ask_premium),
                style = MaterialTheme.typography.labelSmall,
                color = violet,
                modifier = Modifier.clip(CircleShape).background(violet.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        questions.forEachIndexed { i, (q, _) ->
            val on = i == selected
            Text(
                q,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (on) violet.copy(alpha = 0.16f) else glass.fill)
                    .border(1.dp, if (on) violet else violet.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
                    .clickable(role = Role.RadioButton) { selected = i }
                    .padding(14.dp),
            )
        }
        Text(questions[selected].second, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
    }
}
