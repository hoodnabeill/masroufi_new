package com.masroufi.app.feature.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
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
import com.masroufi.app.data.repo.ProgressRepository
import com.masroufi.app.domain.Badge
import com.masroufi.app.domain.Level
import com.masroufi.app.domain.ProgressSnapshot
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.components.glassInset
import com.masroufi.app.ui.components.staggerIn
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private data class BadgeInfo(val icon: ImageVector, val name: Int, val description: Int)

private fun Badge.info(): BadgeInfo = when (this) {
    Badge.FIRST_LOG -> BadgeInfo(MIcons.Pencil, R.string.badge_first_log, R.string.badge_first_log_desc)
    Badge.STREAK_3 -> BadgeInfo(MIcons.Flame, R.string.badge_streak_3, R.string.badge_streak_3_desc)
    Badge.STREAK_7 -> BadgeInfo(MIcons.Flame, R.string.badge_streak_7, R.string.badge_streak_7_desc)
    Badge.STREAK_30 -> BadgeInfo(MIcons.Flame, R.string.badge_streak_30, R.string.badge_streak_30_desc)
    Badge.STREAK_100 -> BadgeInfo(MIcons.Flame, R.string.badge_streak_100, R.string.badge_streak_100_desc)
    Badge.LOGS_50 -> BadgeInfo(MIcons.Activity, R.string.badge_logs_50, R.string.badge_logs_50_desc)
    Badge.LOGS_250 -> BadgeInfo(MIcons.Activity, R.string.badge_logs_250, R.string.badge_logs_250_desc)
    Badge.FIRST_GOAL -> BadgeInfo(MIcons.Goals, R.string.badge_first_goal, R.string.badge_first_goal_desc)
    Badge.GOAL_DONE -> BadgeInfo(MIcons.Trophy, R.string.badge_goal_done, R.string.badge_goal_done_desc)
    Badge.SUBSCRIPTIONS -> BadgeInfo(MIcons.Repeat, R.string.badge_subscriptions, R.string.badge_subscriptions_desc)
    Badge.GAMEYA -> BadgeInfo(MIcons.Gameya, R.string.badge_gameya, R.string.badge_gameya_desc)
    Badge.PAYDAY -> BadgeInfo(MIcons.Calendar, R.string.badge_payday, R.string.badge_payday_desc)
}

@Composable
fun levelTitle(level: Level): String {
    val titles = stringArrayResource(R.array.level_titles)
    return titles[(level.number - 1).coerceIn(0, titles.lastIndex)]
}

/** A flame that flickers while today's streak is secured, and sits still (dimmed) while it is at risk. */
@Composable
fun StreakFlame(lit: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val flicker = if (!lit || reduce) null else rememberInfiniteTransition(label = "flame").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart), label = "f",
    )
    val warm = LocalFinanceColors.current.streak
    Icon(
        MIcons.Flame,
        contentDescription = null,
        tint = if (lit) warm else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.graphicsLayer {
            val t = flicker?.value ?: 0f
            scaleY = 1f + 0.06f * kotlin.math.sin(t * 2f)
            scaleX = 1f - 0.03f * kotlin.math.sin(t * 2f + 1f)
            rotationZ = 3f * kotlin.math.sin(t)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        },
    )
}

/** Home's progress strip: streak, level and a thin bar to the next level. Tapping opens Achievements. */
@Composable
fun ProgressStrip(snapshot: ProgressSnapshot, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val streak = snapshot.streak
    PressableCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).glassInset(CircleShape), contentAlignment = Alignment.Center) {
                StreakFlame(lit = streak.loggedToday, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (streak.current > 0) pluralStringResource(R.plurals.streak_days, streak.current, streak.current) else stringResource(R.string.streak_start),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(if (streak.loggedToday) R.string.streak_done else if (streak.current > 0) R.string.streak_keep else R.string.streak_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(10.dp))
            LevelBadge(snapshot.level, size = 44)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.level_label, snapshot.level.number, levelTitle(snapshot.level)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LevelBar(snapshot.level.progress)
    }
}

@Composable
private fun LevelBar(progress: Float) {
    val reduce = LocalReduceMotion.current
    val fill = remember { Animatable(if (reduce) progress else 0f) }
    LaunchedEffect(progress) { if (reduce) fill.snapTo(progress) else fill.animateTo(progress, spring(dampingRatio = 0.8f, stiffness = 60f)) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
        Box(
            Modifier
                .fillMaxWidth(fill.value.coerceIn(0.02f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(primary, secondary))),
        )
    }
}

/** The level number inside a ring that fills towards the next level. */
@Composable
fun LevelBadge(level: Level, size: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val sweep = remember { Animatable(if (reduce) level.progress else 0f) }
    LaunchedEffect(level.progress) { if (reduce) sweep.snapTo(level.progress) else sweep.animateTo(level.progress, spring(dampingRatio = 0.85f, stiffness = 50f)) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Box(modifier.size(size.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.1f
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(primary, secondary, primary)),
                -90f, 360f * sweep.value, false, Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            level.number.toString(),
            style = if (size >= 100) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@HiltViewModel
class AchievementsViewModel @Inject constructor(progress: ProgressRepository) : ViewModel() {
    val state: StateFlow<ProgressSnapshot?> = progress.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Level hero, streak stats and every badge, earned ones lit and the rest waiting behind a lock. */
@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.achievements_title), onBack = onBack) }) { padding ->
        val s = snapshot ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PressableCard(onClick = null, modifier = Modifier.fillMaxWidth().staggerIn(0), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LevelBadge(s.level, size = 132)
                    Spacer(Modifier.height(14.dp))
                    Text(levelTitle(s.level), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.level.nextXp?.let { stringResource(R.string.xp_to_next, it - s.level.xp) } ?: stringResource(R.string.xp_max),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().staggerIn(1), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Modifier.weight(1f),
                    icon = { StreakFlame(s.streak.loggedToday, Modifier.size(28.dp)) },
                    value = pluralStringResource(R.plurals.streak_days, s.streak.current, s.streak.current),
                )
                StatTile(
                    Modifier.weight(1f),
                    icon = { Icon(MIcons.Trophy, contentDescription = null, tint = LocalFinanceColors.current.warning, modifier = Modifier.size(28.dp)) },
                    value = stringResource(R.string.streak_best, s.streak.best),
                )
            }
            SectionHeader(stringResource(R.string.achievements_badges))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = 3,
            ) {
                Badge.entries.forEachIndexed { i, badge -> BadgeMedal(badge, badge in s.badges, i, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, icon: @Composable () -> Unit, value: String) {
    Column(modifier.glass(MaterialTheme.shapes.large).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
}

/** A medal: earned ones glow in the accent and pop in one after another; locked ones are recessed and dim. */
@Composable
private fun BadgeMedal(badge: Badge, earned: Boolean, index: Int, modifier: Modifier) {
    val info = badge.info()
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduce || !earned) 1f else 0f) }
    LaunchedEffect(earned) {
        if (earned && pop.value < 1f) {
            delay(120L + index * 70L)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val name = stringResource(info.name)
    val description = stringResource(info.description)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$name. $description" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .graphicsLayer {
                    val p = pop.value
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.6f + 0.4f * p
                    alpha = if (earned) p.coerceIn(0f, 1f) else 0.55f
                }
                .then(if (earned) Modifier.glass(CircleShape, tint = primary.copy(alpha = 0.55f), strong = true) else Modifier.glassInset(CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (earned) info.icon else MIcons.Lock,
                contentDescription = null,
                tint = if (earned) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
        Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}
