package com.masroufi.app.feature.proverb

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.Proverb
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.feature.home.HomeError
import com.masroufi.app.feature.home.HomeSkeleton
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.paper.Guilloche
import com.masroufi.app.ui.paper.ProverbCard
import com.masroufi.app.ui.paper.Stamp
import com.masroufi.app.ui.paper.color
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/** Skip the skeleton for loads under this (10-PERFORMANCE). */
private const val SKELETON_AFTER_MS = 150L

private const val TILE_STAGGER_MS = 80L
private const val TAG = "ProverbReward"

private enum class Phase { Blank, Skeleton, Content, Error }

@Composable
fun ProverbRewardScreen(viewModel: ProverbRewardViewModel, onHome: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ProverbRewardContent(
        state,
        onShare = { p ->
            try {
                ProverbShare.share(context, p)
            } catch (e: IOException) {
                Log.e(TAG, "share failed", e)
                Toast.makeText(context, R.string.proverb_share_failed, Toast.LENGTH_SHORT).show()
            }
        },
        onHome = onHome,
    )
}

/** Stateless reward screen: skeleton after 150 ms of loading, 160 ms cross-fade to the content or the error. */
@Composable
fun ProverbRewardContent(
    state: ProverbRewardUi,
    onShare: (Proverb) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onHome)
    var skeletonDue by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SKELETON_AFTER_MS)
        skeletonDue = true
    }
    val phase = when (state) {
        is ProverbRewardUi.Ready -> Phase.Content
        ProverbRewardUi.Error -> Phase.Error
        ProverbRewardUi.Loading -> if (skeletonDue) Phase.Skeleton else Phase.Blank
    }
    Crossfade(phase, modifier.fillMaxSize(), animationSpec = tween(160), label = "proverbReward") { p ->
        when (p) {
            Phase.Blank -> Box(Modifier.fillMaxSize())
            Phase.Skeleton -> HomeSkeleton(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp))
            Phase.Error -> HomeError(Modifier.statusBarsPadding())
            Phase.Content -> (state as? ProverbRewardUi.Ready)?.let { Ready(it, onShare, onHome) }
        }
    }
}

@Composable
private fun Ready(s: ProverbRewardUi.Ready, onShare: (Proverb) -> Unit, onHome: () -> Unit) {
    val haptics = LocalHaptics.current
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val color = s.proverb.set.color()
    val ink = if (color == Tz.turquoise) Tz.turquoiseInk else Tz.night

    // 05-MOTION "Card flip": face-down 20 dp below, flips on Y with one overshoot; Milestone as it settles.
    val rotation = remember { Animatable(if (reduce) 0f else 180f) }
    val rise = remember { Animatable(if (reduce) 0f else 20f) }
    var settled by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) {
        if (!reduce) coroutineScope {
            launch { rise.animateTo(0f, TzSpring.Reward.spec()) }
            rotation.animateTo(0f, TzSpring.Reward.spec())
        }
        haptics.perform(HapticLevel.Milestone)
        settled = true
    }
    val faceDown by remember { derivedStateOf { rotation.value > 90f } }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.proverb_ticket_cut, s.ticketNumber.toString()),
                    style = TzType.label.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    color = Tz.turquoise,
                )
                Text(
                    stringResource(if (s.allCollected) R.string.proverb_album_done else R.string.proverb_new),
                    style = TzType.card.copy(textAlign = TextAlign.Center),
                    color = Tz.text,
                    modifier = Modifier.semantics { heading() },
                )
            }

            Box(
                Modifier.size(width = 300.dp, height = 400.dp).graphicsLayer {
                    rotationY = rotation.value
                    translationY = rise.value.dp.toPx()
                    cameraDistance = 12f * density.density
                },
            ) {
                if (faceDown) {
                    Box(Modifier.size(width = 300.dp, height = 400.dp).graphicsLayer { rotationZ = 2f }.clip(RoundedCornerShape(TzRadius.hero)).background(color)) {
                        Guilloche(ink.copy(alpha = 0.25f), Modifier.align(Alignment.Center).size(300.dp), radii = 50..140 step 18, strokeWidth = 1.4.dp)
                    }
                } else {
                    val withSaving = s.leftover > 0 && s.destination == LeftoverDestination.JAR
                    val line = if (withSaving) {
                        s.proverb.line + " " + stringResource(R.string.proverb_line_saved, formatPounds(s.leftover))
                    } else {
                        s.proverb.line
                    }
                    ProverbCard(
                        setName = stringResource(R.string.proverb_set_title),
                        number = s.proverb.number,
                        total = ProverbCatalog.all.size,
                        proverb = s.proverb.text,
                        line = line,
                        color = color,
                    )
                    if (s.rare) Stamp(stringResource(R.string.proverb_rare), ink, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 8.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tileLabel = when (s.destination) {
                    LeftoverDestination.TOMORROW -> R.string.proverb_tile_tomorrow
                    LeftoverDestination.EMERGENCY -> R.string.proverb_tile_emergency
                    else -> R.string.proverb_tile_jar
                }
                StatTile(0, settled, reduce, stringResource(tileLabel), Modifier.weight(1f)) { shown ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row {
                            Text("+", style = TzType.cardHeading.copy(fontSize = 24.sp), color = Tz.turquoise)
                            Odometer(if (shown) s.leftover else 0L, TzType.cardHeading.copy(fontSize = 24.sp, color = Tz.turquoise))
                        }
                    }
                }
                StatTile(1, settled, reduce, stringResource(R.string.proverb_streak_label), Modifier.weight(1f)) { shown ->
                    val n by animateIntAsState(if (shown) s.streak else 0, if (reduce) snap() else tween(500), label = "streak")
                    Text(stringResource(R.string.proverb_streak_value, n.toString()), style = TzType.cardHeading.copy(fontSize = 24.sp), color = Tz.yellow)
                }
                StatTile(2, settled, reduce, stringResource(R.string.proverb_week_label), Modifier.weight(1f)) { shown ->
                    val n by animateIntAsState(if (shown) s.weekDays else 0, if (reduce) snap() else tween(500), label = "week")
                    Text(stringResource(R.string.proverb_week_value, n.toString()), style = TzType.cardHeading.copy(fontSize = 24.sp), color = Tz.text)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.proverb_share), {
                haptics.perform(HapticLevel.Tap)
                onShare(s.proverb)
            }, Modifier.weight(1f), height = 56.dp)
            PrimaryButton(stringResource(R.string.proverb_done), {
                haptics.perform(HapticLevel.Confirm)
                onHome()
            }, Modifier.weight(1f), height = 56.dp)
        }
    }
}

/** Surface tile (12 x 8 padding, 18 dp radius, 1 dp Line). [content] gets true once this tile's turn has come: tile [index] starts 80 ms after the one before. */
@Composable
private fun StatTile(
    index: Int,
    armed: Boolean,
    reduce: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (shown: Boolean) -> Unit,
) {
    var shown by remember { mutableStateOf(reduce) }
    LaunchedEffect(armed) {
        if (armed && !shown) {
            delay(index * TILE_STAGGER_MS)
            shown = true
        }
    }
    val shape = RoundedCornerShape(TzRadius.chip)
    Column(
        modifier.clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content(shown)
        Text(label, style = TzType.caption.copy(fontSize = 11.sp, textAlign = TextAlign.Center), color = Tz.text2)
    }
}
