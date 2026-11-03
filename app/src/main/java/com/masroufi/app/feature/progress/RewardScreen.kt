package com.masroufi.app.feature.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.repo.ProgressRepository
import com.masroufi.app.data.repo.TodayBudget
import com.masroufi.app.data.repo.TodayRepository
import com.masroufi.app.domain.ProgressSnapshot
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.random.Random

/** Points a day's first entry earns (see [com.masroufi.app.domain.Progress.points]). */
private const val DAY_POINTS = 10

/** Streak lengths worth a burst of confetti. */
private val MILESTONES = setOf(2, 7, 30)

@HiltViewModel
class RewardViewModel @Inject constructor(progress: ProgressRepository, today: TodayRepository) : ViewModel() {
    val state: StateFlow<Pair<ProgressSnapshot, TodayBudget>?> = combine(progress.observe(), today.observe()) { p, t -> p to t }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * After saving from the quick-add sheet: the streak ring, what the entry earned, one honest positive fact and the
 * next badge within reach. The day's first entry is the one that grows the streak and earns points; later entries
 * get a quieter "logged" with the same level and badge progress.
 */
@Composable
fun RewardScreen(type: String, onDone: () -> Unit, onLogAnother: () -> Unit, viewModel: RewardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (snapshot, today) = state ?: return
    val glass = LocalGlass.current
    val streak = snapshot.streak
    val firstToday = snapshot.entriesToday == 1
    val level = snapshot.level

    Box(Modifier.fillMaxSize().background(glass.base)) {
        if (firstToday && streak.current in MILESTONES) Confetti(Modifier.fillMaxSize())
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            StreakRing(streak.current.coerceAtLeast(1))
            Spacer(Modifier.height(24.dp))
            Text(
                if (firstToday) pluralStringResource(R.plurals.reward_headline, streak.current, streak.current) else stringResource(R.string.reward_saved_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (firstToday) pluralStringResource(R.plurals.reward_sub, streak.current + 1, streak.current + 1) else stringResource(R.string.reward_saved_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(26.dp))

            // Points: the level bar with today's share in lime on top of what was there before.
            Card {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        stringResource(R.string.home_points, if (firstToday) DAY_POINTS else 0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    level.nextXp?.let {
                        Text(stringResource(R.string.reward_level_line, level.number, level.xp, it), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                    }
                }
                val span = ((level.nextXp ?: level.xp) - level.floorXp).coerceAtLeast(1)
                val gained = if (firstToday) DAY_POINTS.coerceAtMost(level.xp - level.floorXp) else 0
                val before = (level.xp - level.floorXp - gained).toFloat() / span
                Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(glass.track)) {
                    if (level.nextXp == null) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondary))
                    } else {
                        if (before > 0f) Box(Modifier.weight(before).fillMaxSize().background(MaterialTheme.colorScheme.secondary))
                        if (gained > 0) Box(Modifier.weight(gained.toFloat() / span).fillMaxSize().background(MaterialTheme.colorScheme.primary))
                        val rest = 1f - before - gained.toFloat() / span
                        if (rest > 0f) Spacer(Modifier.weight(rest))
                    }
                }
                Text(
                    level.nextXp?.let { stringResource(R.string.reward_to_next, it - level.xp, levelTitle(level.number + 1)) } ?: stringResource(R.string.xp_max),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            PositiveFact(type, snapshot, today)
            snapshot.badges.firstOrNull { !it.earned }?.let { next ->
                Spacer(Modifier.height(12.dp))
                val info = next.badge.info()
                Card(horizontal = true) {
                    Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.extraSmall).background(info.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(info.icon, contentDescription = null, tint = info.color, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.reward_badge, stringResource(info.name)), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(info.how), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                    }
                    val progress = stringResource(R.string.badge_progress, next.done, next.badge.target)
                    Row(Modifier.semantics { contentDescription = progress }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(next.badge.target) { i ->
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (i < next.done) info.color else glass.track))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            GlassButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.reward_done), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onLogAnother, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.reward_more), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun Card(horizontal: Boolean = false, tint: Color? = null, content: @Composable () -> Unit) {
    val glass = LocalGlass.current
    val shape = MaterialTheme.shapes.large
    val modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(tint?.copy(alpha = 0.10f) ?: glass.fill)
        .border(1.dp, tint?.copy(alpha = 0.25f) ?: glass.strokeTop, shape)
        .padding(18.dp)
    if (horizontal) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** One true, encouraging sentence drawn from today's numbers; never a guess. */
@Composable
private fun PositiveFact(type: String, snapshot: ProgressSnapshot, today: TodayBudget) {
    val formatter = LocalMoneyFormatter.current
    val fact = when {
        type == TransactionType.INCOME.name && today.safeMinor > 0 -> stringResource(R.string.reward_fact_income, formatter.format(today.safeMinor, today.currency))
        type == TransactionType.EXPENSE.name && today.remainingMinor > 0 -> stringResource(R.string.reward_fact_left, formatter.format(today.remainingMinor, today.currency))
        snapshot.entriesToday > 0 -> pluralStringResource(R.plurals.reward_fact_logged, snapshot.entriesToday, snapshot.entriesToday)
        else -> return
    }
    val lime = MaterialTheme.colorScheme.primary
    Card(tint = lime) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.reward_fact_title), style = MaterialTheme.typography.labelMedium, color = lime)
            Text(fact, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StreakRing(days: Int) {
    val amber = LocalFinanceColors.current.streak
    Box(
        Modifier.size(196.dp).clip(CircleShape).border(1.dp, amber.copy(alpha = 0.25f), CircleShape).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(160.dp).clip(CircleShape).background(amber.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.size(124.dp).shadow(32.dp, CircleShape, ambientColor = amber, spotColor = amber).clip(CircleShape).background(amber),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(MIcons.Flame, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(26.dp))
                Text(days.toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onTertiary)
            }
        }
    }
}

/** A single short burst of falling pieces; skipped entirely with reduced motion. */
@Composable
private fun Confetti(modifier: Modifier) {
    if (LocalReduceMotion.current) return
    val finance = LocalFinanceColors.current
    val colors = listOf(MaterialTheme.colorScheme.primary, finance.streak, MaterialTheme.colorScheme.secondary, finance.over, finance.transfer, finance.categories[2])
    val pieces = remember { List(28) { Piece(Random.nextFloat(), Random.nextFloat() * 0.35f, Random.nextFloat() * 360f, Random.nextFloat() * 0.6f + 0.7f, it % 6) } }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2_200, easing = LinearEasing)) }
    Canvas(modifier) {
        val p = t.value
        pieces.forEach { piece ->
            val y = (piece.y0 + p * piece.speed) * size.height * 0.6f
            val x = piece.x * size.width + kotlin.math.sin((p * 6f + piece.x * 10f).toDouble()).toFloat() * 12.dp.toPx()
            val alpha = (1f - p).coerceIn(0f, 1f)
            rotate(piece.spin + p * 360f, Offset(x, y)) {
                drawRoundRect(
                    colors[piece.color].copy(alpha = alpha),
                    topLeft = Offset(x - 4.dp.toPx(), y - 7.dp.toPx()),
                    size = Size(8.dp.toPx(), 14.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

private class Piece(val x: Float, val y0: Float, val spin: Float, val speed: Float, val color: Int)
