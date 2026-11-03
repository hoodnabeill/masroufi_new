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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
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
import com.masroufi.app.domain.BadgeProgress
import com.masroufi.app.domain.Level
import com.masroufi.app.domain.Progress
import com.masroufi.app.domain.ProgressSnapshot
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.staggerIn
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

/** How a badge looks and reads: icon, accent, name and what earns it. */
data class BadgeInfo(val icon: ImageVector, val color: Color, val name: Int, val how: Int)

@Composable
fun Badge.info(): BadgeInfo {
    val finance = LocalFinanceColors.current
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        Badge.FIRST_LOG -> BadgeInfo(MIcons.Trophy, scheme.primary, R.string.badge_first_log, R.string.badge_first_log_desc)
        Badge.STARTED_RIGHT -> BadgeInfo(MIcons.Flame, finance.streak, R.string.badge_started_right, R.string.badge_started_right_desc)
        Badge.FULL_WEEK -> BadgeInfo(MIcons.Calendar, finance.streak, R.string.badge_full_week, R.string.badge_full_week_desc)
        Badge.ZERO_DAY -> BadgeInfo(MIcons.Check, finance.categories[2], R.string.badge_zero_day, R.string.badge_zero_day_desc)
        Badge.HOME_COOK -> BadgeInfo(MIcons.Food, finance.over, R.string.badge_home_cook, R.string.badge_home_cook_desc)
        Badge.PLANNER -> BadgeInfo(MIcons.Budgets, scheme.secondary, R.string.badge_planner, R.string.badge_planner_desc)
        Badge.EMERGENCY_GUARD -> BadgeInfo(MIcons.Health, finance.over, R.string.badge_emergency, R.string.badge_emergency_desc)
        Badge.GAMEYA_MEMBER -> BadgeInfo(MIcons.Gameya, finance.categories[3], R.string.badge_gameya_member, R.string.badge_gameya_member_desc)
        Badge.GREEN_MONTH -> BadgeInfo(MIcons.Trending, scheme.primary, R.string.badge_green_month, R.string.badge_green_month_desc)
    }
}

@Composable
fun levelTitle(level: Level): String = levelTitle(level.number)

@Composable
fun levelTitle(number: Int): String {
    val titles = stringArrayResource(R.array.level_titles)
    return titles[(number - 1).coerceIn(0, titles.lastIndex)]
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
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    )
}

/** The level number inside a violet ring that fills towards the next level. */
@Composable
fun LevelBadge(level: Level, size: Int, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val sweep = remember { Animatable(if (reduce) level.progress else 0f) }
    LaunchedEffect(level.progress) { if (reduce) sweep.snapTo(level.progress) else sweep.animateTo(level.progress, spring(dampingRatio = 0.85f, stiffness = 50f)) }
    val violet = MaterialTheme.colorScheme.secondary
    val track = LocalGlass.current.track
    Box(modifier.size(size.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(violet, -90f, 360f * sweep.value, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
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

/** Level path, this month's streak calendar with the weekly freeze, and the nine badges with a detail panel. */
@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.achievements_title), onBack = onBack) }) { padding ->
        val s = snapshot ?: return@Scaffold
        // Opens on the next badge to chase, so the detail panel always says what to do next.
        var selected by rememberSaveable { mutableIntStateOf(s.badges.indexOfFirst { !it.earned }.coerceAtLeast(0)) }
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LevelCard(s.level, Modifier.staggerIn(0))
            StreakCalendar(s, Modifier.staggerIn(1))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.achievements_badges), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.badges_count, s.badges.count { it.earned }, s.badges.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalGlass.current.textTertiary,
                )
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3,
            ) {
                s.badges.forEachIndexed { i, b -> BadgeTile(b, i == selected, Modifier.weight(1f)) { selected = i } }
            }
            s.badges.getOrNull(selected)?.let { BadgeDetail(it) }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glass = LocalGlass.current
    val shape = MaterialTheme.shapes.large
    Box(modifier.fillMaxWidth().clip(shape).background(glass.fill).border(1.dp, glass.strokeTop, shape).padding(18.dp)) { content() }
}

@Composable
private fun LevelCard(level: Level, modifier: Modifier) {
    val glass = LocalGlass.current
    val violet = MaterialTheme.colorScheme.secondary
    Panel(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LevelBadge(level, size = 112)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.level_label, level.number, levelTitle(level)), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                level.nextXp?.let { stringResource(R.string.xp_next_named, level.xp, it, levelTitle(level.number + 1)) } ?: stringResource(R.string.xp_max),
                style = MaterialTheme.typography.bodySmall,
                color = glass.textTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            // The path: one node per level, filled up to the current one.
            Row(Modifier.fillMaxWidth()) {
                Progress.levelFloors.forEachIndexed { i, floor ->
                    val reached = level.number > i
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (reached) violet else glass.track)
                                .then(if (level.number == i + 1) Modifier.border(3.dp, violet.copy(alpha = 0.35f), CircleShape) else Modifier),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            levelTitle(i + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (reached) MaterialTheme.colorScheme.onSurface else glass.textTertiary,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                        Text(String.format(Locale.US, "%,d", floor), style = MaterialTheme.typography.labelSmall, color = glass.textTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCalendar(s: ProgressSnapshot, modifier: Modifier) {
    val glass = LocalGlass.current
    val amber = LocalFinanceColors.current.streak
    val blue = LocalFinanceColors.current.transfer
    val today = remember { LocalDate.now() }
    val first = today.withDayOfMonth(1)
    val last = first.plusMonths(1).minusDays(1)
    val names = stringArrayResource(R.array.week_short)
    val streak = s.streak
    Panel(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakFlame(lit = streak.loggedToday, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (streak.current > 0) pluralStringResource(R.plurals.streak_days, streak.current, streak.current) else stringResource(R.string.streak_start),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.streak_best, streak.best), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                }
                Text(
                    stringResource(if (streak.freezeAvailable) R.string.freeze_available else R.string.freeze_used),
                    style = MaterialTheme.typography.labelMedium,
                    color = blue,
                    modifier = Modifier.clip(CircleShape).background(blue.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                names.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = glass.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            // Saturday-first weeks, like the dots on Home.
            var week = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
            while (!week.isAfter(last)) {
                Row(Modifier.fillMaxWidth()) {
                    for (offset in 0L until 7L) {
                        val day = week.plusDays(offset)
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (day.month == today.month) {
                                val logged = day in s.logDays
                                val frozen = day in streak.frozenDays
                                val isToday = day == today
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                logged -> amber
                                                frozen -> blue.copy(alpha = 0.22f)
                                                isToday -> Color.Transparent
                                                else -> glass.fillStrong
                                            },
                                        )
                                        .then(if (isToday && !logged) Modifier.border(2.dp, amber, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when {
                                            logged -> MaterialTheme.colorScheme.onTertiary
                                            isToday -> amber
                                            frozen -> blue
                                            else -> glass.textTertiary
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                week = week.plusWeeks(1)
            }
        }
    }
}

@Composable
private fun BadgeTile(progress: BadgeProgress, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val info = progress.badge.info()
    val glass = LocalGlass.current
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .clip(shape)
            .background(glass.fill)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else glass.strokeTop, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(MaterialTheme.shapes.extraSmall).background(if (progress.earned) info.color.copy(alpha = 0.18f) else glass.fillStrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (progress.earned) info.icon else MIcons.Lock,
                contentDescription = null,
                tint = if (progress.earned) info.color else glass.textTertiary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(info.name), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
        Text(
            if (progress.earned) stringResource(R.string.badge_unlocked) else stringResource(R.string.badge_progress, progress.done, progress.badge.target),
            style = MaterialTheme.typography.labelSmall,
            color = glass.textTertiary,
        )
    }
}

@Composable
private fun BadgeDetail(progress: BadgeProgress) {
    val info = progress.badge.info()
    val glass = LocalGlass.current
    Panel(Modifier.semantics(mergeDescendants = true) {}) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.extraSmall).background(info.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(info.icon, contentDescription = null, tint = info.color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(info.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.home_points, progress.badge.reward),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Text(stringResource(info.how), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(glass.track)) {
                Box(Modifier.fillMaxWidth(progress.fraction.coerceAtLeast(0.02f)).height(6.dp).clip(CircleShape).background(info.color))
            }
        }
    }
}
