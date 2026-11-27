@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.masroufi.app.feature.match

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import com.masroufi.app.feature.pockets.LoadingRows
import com.masroufi.app.ui.components.CappedFontScale
import com.masroufi.app.ui.components.isLargeFont
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.domain.EventKind
import com.masroufi.app.domain.GoalKind
import com.masroufi.app.domain.HatTrick
import com.masroufi.app.domain.LeagueRow
import com.masroufi.app.domain.MatchEvent
import com.masroufi.app.domain.MatchView
import com.masroufi.app.domain.WeekResult
import com.masroufi.app.domain.weekNumber
import com.masroufi.app.domain.weekStart
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.TzPanel
import com.masroufi.app.ui.components.pluralText
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The pause before a new goal lands, so the screen is on show when it does. */
private const val GOAL_DELAY_MS = 450L

private val PitchLine = Color(0xFF1B5A66)
private val CircleLine = Color(0xFF6ED5CC)
private val HatLine = Color(0xFFF0E3B5)

/** ماتش الأسبوع (Match.dc.html), a pushed screen. */
@Composable
fun MatchScreen(viewModel: MatchViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    MatchContent(ui, onBack, onContinue)
}

@Composable
fun MatchContent(ui: MatchUi, onBack: () -> Unit, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val view = ui.view
    val fresh = ui.fresh
    // One switch for the whole screen, outside the list so scrolling away and back does not replay it: the score flips
    // and the new rows slide in together, a beat after the screen is on show.
    val animate = view != null && fresh.forGoals + fresh.againstGoals > 0
    var armed by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    LaunchedEffect(animate) {
        if (animate) {
            delay(GOAL_DELAY_MS)
            armed = true
            // 06-HAPTICS: Confirm for a goal, Tap for a goal against; one haptic, the stronger.
            haptics.perform(if (fresh.forGoals > 0) HapticLevel.Confirm else HapticLevel.Tap)
        }
    }
    val landed = armed || !animate
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") { PushedHeader(stringResource(R.string.match_title), onBack) }
        if (view == null) {
            item(key = "skeleton") { LoadingRows() }
        } else {
            item(key = "scoreboard") {
                if (view.firstWeek) {
                    EmptyState({ Qersh(QershMood.Happy, 96.dp) }, R.string.empty_match_title, R.string.empty_match_line)
                } else {
                    Scoreboard(view, shownFor = if (landed) view.goalsFor else fresh.from, bounce = fresh.forGoals > 0)
                }
            }
            if (ui.events.isNotEmpty()) item(key = "events") { Events(ui.events, landed) }
            if (view.hatTrick != HatTrick.NONE) item(key = "hat") { HatTrickCard(view.hatTrick == HatTrick.DONE) }
            if (!view.firstWeek) item(key = "league") { League(view) }
            item(key = "continue") {
                PrimaryButton(stringResource(R.string.match_continue), onContinue, Modifier.fillMaxWidth().padding(top = 6.dp), height = 56.dp)
            }
        }
    }
}

@Composable
private fun Scoreboard(v: MatchView, shownFor: Int, bounce: Boolean) {
    val shape = RoundedCornerShape(TzRadius.hero)
    val cd = stringResource(R.string.match_score_cd, shownFor.toString(), v.lastWeekGoalsFor.toString())
    Box(Modifier.fillMaxWidth().clip(shape).background(Tz.turquoiseDim)) {
        // The pitch: halfway line, centre circle and both penalty boxes.
        Canvas(Modifier.matchParentSize()) {
            val w = 2.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(PitchLine, Offset(cx, 0f), Offset(cx, size.height), w)
            drawCircle(PitchLine, 46.dp.toPx(), Offset(cx, cy), style = Stroke(w))
            val box = Size(50.dp.toPx(), 100.dp.toPx())
            drawRect(PitchLine, Offset(-w, cy - box.height / 2f), box, style = Stroke(w))
            drawRect(PitchLine, Offset(size.width - box.width + w, cy - box.height / 2f), box, style = Stroke(w))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val pillShape = RoundedCornerShape(14.dp)
            Text(
                stringResource(R.string.match_pill, v.weekNumber.toString(), pluralText(R.plurals.match_days_left, v.daysLeft)),
                style = TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = Tz.turquoise,
                modifier = Modifier.clip(pillShape).background(Tz.turquoiseInk).padding(horizontal = 14.dp, vertical = 5.dp),
            )
            // shortcut: the scoreboard is fixed-size art, so its numbers keep the normal font size (TalkBack reads the score).
            CappedFontScale {
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = cd },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Qersh(QershMood.Happy, 56.dp)
                    Text(stringResource(R.string.match_you), style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Tz.text)
                }
                // Digits run left to right: last week on the left, you on the right.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ScoreDigit(v.lastWeekGoalsFor, Tz.text2, bounce = false)
                        Text(":", style = TzType.card.copy(fontSize = 40.sp), color = Tz.turquoise)
                        ScoreDigit(shownFor, Tz.yellow, bounce)
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(56.dp).drawBehind {
                            drawCircle(
                                CircleLine, size.minDimension / 2f - 1.dp.toPx(),
                                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(weekNumber(weekStart(v.today).minusDays(7)).toString(), style = TzType.card.copy(fontSize = 18.sp), color = Tz.turquoiseDimText)
                    }
                    Text(stringResource(R.string.match_last_week), style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Tz.turquoiseDimText)
                }
            }
            }
            Text(
                stringResource(R.string.match_rule),
                style = TzType.caption.copy(fontSize = 13.sp, textAlign = TextAlign.Center),
                color = Tz.turquoiseDimText,
            )
        }
    }
}

/** 05-MOTION section 5: the old digit goes up and out, the new one comes in from below (with a bounce for a goal for). */
@Composable
private fun ScoreDigit(value: Int, color: Color, bounce: Boolean) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (reduce || targetState == initialState) {
                fadeIn(tween(120)) togetherWith fadeOut(tween(120))
            } else {
                val spec = (if (bounce) TzSpring.Reward else TzSpring.Move).spec<IntOffset>()
                (slideInVertically(spec) { it } + fadeIn(tween(120))) togetherWith (slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)))
            }
        },
        label = "score",
    ) { n ->
        Text(n.toString(), style = TzType.card.copy(fontSize = 84.sp, lineHeight = 1.em), color = color)
    }
}

@Composable
private fun Events(events: List<MatchEvent>, landed: Boolean) {
    val days = stringArrayResource(R.array.match_day_names)
    TzPanel {
        Text(stringResource(R.string.match_events_title), style = TzType.cardHeading, color = Tz.text)
        events.forEach { e -> androidx.compose.runtime.key(e.date.toEpochDay()) { EventRow(e, days[(e.date.dayOfWeek.value + 1) % 7], landed) } }
    }
}

@Composable
private fun EventRow(e: MatchEvent, dayName: String, landed: Boolean) {
    val reduce = LocalReduceMotion.current
    // Leading edge = start edge: right in RTL, left in LTR.
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1 else -1
    val waiting = e.fresh && !landed
    val offset = remember { Animatable(if (waiting && !reduce) 1f else 0f) }
    val alpha = remember { Animatable(if (waiting) 0f else 1f) }
    LaunchedEffect(landed) {
        if (e.fresh && landed) coroutineScope {
            launch { alpha.animateTo(1f, tween(if (reduce) 120 else 160)) }
            // A goal against uses the same slide without the bounce.
            offset.animateTo(0f, (if (e.kind == EventKind.AGAINST) TzSpring.Move else TzSpring.Reward).spec())
        }
    }
    val text = when (e.kind) {
        EventKind.GOAL -> stringResource(R.string.match_ev_goal, poundsText(e.amount))
        EventKind.EXACT -> stringResource(R.string.match_ev_exact)
        EventKind.BEST -> stringResource(R.string.match_ev_best, poundsText(e.amount))
        EventKind.LAST_MINUTE -> stringResource(R.string.match_ev_last, poundsText(e.amount))
        EventKind.AGAINST -> stringResource(R.string.match_ev_against, poundsText(e.amount))
        EventKind.OPEN -> stringResource(R.string.match_ev_open, poundsText(e.amount))
    }
    val open = e.kind == EventKind.OPEN
    Row(
        Modifier.fillMaxWidth().graphicsLayer {
            translationX = sign * offset.value * size.width
            this.alpha = alpha.value
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            dayName,
            style = TzType.caption.copy(fontSize = 12.sp, fontWeight = if (open) FontWeight.Bold else FontWeight.Normal),
            color = if (open) Tz.yellow else Tz.text2,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        Box(Modifier.size(22.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            when (e.kind) {
                EventKind.AGAINST -> Box(Modifier.size(width = 16.dp, height = 22.dp).clip(RoundedCornerShape(3.dp)).background(Tz.carnelian))
                EventKind.OPEN -> Box(Modifier.size(22.dp).drawBehind {
                    drawCircle(
                        Tz.yellow, size.minDimension / 2f - 1.dp.toPx(),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                })
                else -> Box(Modifier.size(22.dp).clip(CircleShape).background(Tz.yellow))
            }
        }
        Text(
            text,
            style = TzType.body.copy(fontSize = 14.sp, fontWeight = if (e.kind == EventKind.AGAINST || open) FontWeight.Normal else FontWeight.SemiBold),
            color = when (e.kind) { EventKind.AGAINST -> Tz.carnelianText; EventKind.OPEN -> Tz.text2; else -> Tz.text },
            modifier = Modifier.weight(1f),
        )
    }
}

/** The nudge while one more goal today makes three in a row; once it is done, the stamp is shown as earned. */
@Composable
private fun HatTrickCard(done: Boolean) {
    val shape = RoundedCornerShape(TzRadius.card)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Tz.yellowDim).padding(16.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(52.dp).graphicsLayer { rotationZ = -8f }.clip(CircleShape)
                .then(if (done) Modifier.background(Tz.yellow) else Modifier.border(3.dp, Tz.yellow, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            // shortcut: the stamp is a fixed 52 dp ring, so its word keeps the normal font size.
            CappedFontScale {
                Text(
                    stringResource(R.string.match_hat_stamp),
                    style = TzType.cardHeading.copy(fontSize = 13.sp, lineHeight = 1.1.em, textAlign = TextAlign.Center),
                    color = if (done) Tz.night else Tz.yellow,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (done) R.string.match_hat_done_title else R.string.match_hat_nudge_title),
                style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = Tz.yellow,
            )
            Text(
                stringResource(if (done) R.string.match_hat_done_line else R.string.match_hat_nudge_line),
                style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 1.6.em),
                color = HatLine,
            )
        }
    }
}

@Composable
private fun League(v: MatchView) {
    TzPanel(spacing = 10.dp) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.match_league_title), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.match_league_sub), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
        if (!isLargeFont()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.match_col_week), Modifier.width(LABEL_WIDTH), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2)
            Text(stringResource(R.string.match_col_result), Modifier.weight(1f), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2)
            Text(stringResource(R.string.match_col_saved), Modifier.width(56.dp), style = TzType.caption.copy(fontSize = 13.sp, textAlign = TextAlign.End), color = Tz.text2)
        }
        v.league.forEach { r -> androidx.compose.runtime.key(r.start.toEpochDay()) { LeagueRowView(r) } }
        if (v.ofLast > 0) {
            Text(
                stringResource(
                    R.string.match_summary, v.wonOf.toString(),
                    pluralText(R.plurals.match_weeks, v.ofLast), pluralText(R.plurals.match_weeks, v.longestWinRun),
                ),
                style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 1.7.em),
                color = Tz.text2,
            )
        }
        if (v.lockedWeeks > 0) {
            Text(pluralText(R.plurals.match_locked_count, v.lockedWeeks), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
            // Placeholder rows only: no real week is drawn behind the blur.
            PlusLocked(R.string.match_locked_what, PlusFeature.MATCH_HISTORY) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(minOf(v.lockedWeeks, 2)) { LeagueRowView(LeagueRow(v.today, 0, WeekResult.WIN, 0, List(7) { GoalKind.NONE }, 0, false)) }
                }
            }
        }
    }
}

private val LABEL_WIDTH = 84.dp

@Composable
private fun LeagueRowView(r: LeagueRow) {
    val label = when {
        r.current -> stringResource(R.string.match_row_now, r.number.toString())
        r.result == WeekResult.WIN -> stringResource(R.string.match_row_win, r.number.toString())
        r.result == WeekResult.DRAW -> stringResource(R.string.match_row_draw, r.number.toString())
        r.result == WeekResult.LOSS -> stringResource(R.string.match_row_loss, r.number.toString())
        else -> r.number.toString()
    }
    val shape = RoundedCornerShape(14.dp)
    if (isLargeFont()) {
        // the table's three columns do not fit a large font: the week and the sum on one line, the seven days under them
        Column(
            Modifier.fillMaxWidth().then(if (r.current) Modifier.clip(shape).background(Tz.night) else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    label, style = TzType.body.copy(fontSize = 14.sp, fontWeight = if (r.current) FontWeight.Bold else FontWeight.Normal),
                    color = if (r.current) Tz.yellow else Tz.text2,
                )
                Text(poundsText(r.saved), style = TzType.card.copy(fontSize = 16.sp), color = Tz.text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { r.days.forEach { g -> Dot(g, r.current) } }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().then(if (r.current) Modifier.clip(shape).background(Tz.night) else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            Modifier.width(LABEL_WIDTH - 24.dp),
            style = TzType.body.copy(fontSize = 14.sp, fontWeight = if (r.current) FontWeight.Bold else FontWeight.Normal),
            color = if (r.current) Tz.yellow else Tz.text2,
            maxLines = 1,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            r.days.forEach { g -> Dot(g, r.current) }
        }
        Text(
            poundsText(r.saved),
            Modifier.width(56.dp),
            style = TzType.card.copy(fontSize = 16.sp, textAlign = TextAlign.End),
            color = Tz.text,
        )
    }
}

@Composable
private fun Dot(goal: GoalKind, current: Boolean) {
    when (goal) {
        GoalKind.FOR -> Box(Modifier.size(14.dp).clip(CircleShape).background(if (current) Tz.yellow else Tz.turquoise))
        GoalKind.AGAINST -> Box(Modifier.size(14.dp).clip(CircleShape).background(Tz.carnelian))
        GoalKind.NONE -> Box(Modifier.size(14.dp).drawBehind {
            drawCircle(
                Tz.dashed, size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
            )
        })
    }
}
