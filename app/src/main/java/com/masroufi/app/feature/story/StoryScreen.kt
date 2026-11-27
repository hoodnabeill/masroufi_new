package com.masroufi.app.feature.story

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.theme.Alexandria
import com.masroufi.app.ui.theme.Lalezar
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.data.repo.MonthStory
import com.masroufi.app.ui.components.CappedFontScale
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressClickable
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToLong

private const val TAG = "Story"
private const val CROSSFADE_MS = 200
private const val COUNT_UP_MS = 600
private const val GRID_COLUMNS = 8

/** حكاية الشهر (Story.dc.html): full screen, yellow. Closing (the button, back) marks a first view as seen. */
@Composable
fun StoryScreen(viewModel: StoryViewModel, onClose: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    // Read inside the segments only: it changes 20 times a second.
    val progress = viewModel.progress.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(viewModel) { viewModel.haptics.collect { haptics.perform(it) } }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                viewModel.markShown()
                onClose()
            }
        }
    }
    BackHandler(onBack = close)
    StoryContent(
        ui, progress, viewModel, close,
        onShare = { story, slide ->
            scope.launch {
                try {
                    StoryShare.share(context, "${viewModel.id}_${slide.name.lowercase()}", slideText(context, story, slide, ui.hideAmounts))
                } catch (e: IOException) {
                    Log.e(TAG, "share failed", e)
                    Toast.makeText(context, R.string.story_share_failed, Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

/** The story with its state and actions passed in, so a test can render it (no behaviour of its own). */
@Composable
fun StoryContent(ui: StoryUi, progress: androidx.compose.runtime.State<Float>, viewModel: StoryActions, close: () -> Unit, onShare: (MonthStory, StorySlide) -> Unit) {
    val context = LocalContext.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        Modifier.fillMaxSize().background(Tz.yellow).drawBehind {
            // The mock's six rings, bottom left.
            val center = Offset(size.width * 60f / 390f, size.height * 804f / 844f)
            val k = size.width / 390f
            for (r in 80..230 step 30) drawCircle(Tz.yellowRule2, r * k, center, style = Stroke(1.5f * k))
        },
    ) {
        val story = ui.story
        val slide = ui.slide
        if (story == null || slide == null) {
            if (ui.missing) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { CloseButton(close) }
                    Text(stringResource(R.string.story_missing), style = body(26), color = Tz.night)
                }
            }
            return@Box
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)
                .pointerInput(rtl) {
                    detectTapGestures(
                        onPress = {
                            viewModel.hold(true)
                            try {
                                tryAwaitRelease()
                            } finally {
                                viewModel.hold(false)
                            }
                        },
                        // 04-SCREENS: the left half is next and the right half is back (RTL); mirrored in LTR.
                        onTap = { at -> if ((at.x < size.width / 2f) == rtl) viewModel.next() else viewModel.back() },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Segments(ui.slides.size, ui.index, progress, stringResource(R.string.story_slide_cd, (ui.index + 1).toString(), ui.slides.size.toString()))
            val header = remember(story) { slideText(context, story, StorySlide.OPENING, hide = false).header }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(header, style = alexandria(14, FontWeight.Bold), color = Tz.night, modifier = Modifier.weight(1f))
                CloseButton(close)
            }
            Crossfade(ui.index, Modifier.weight(1f).fillMaxWidth(), animationSpec = tween(CROSSFADE_MS), label = "storySlide") { i ->
                val s = ui.slides.getOrNull(i) ?: slide
                SlideBody(remember(story, s) { slideText(context, story, s, hide = false) }, Modifier.fillMaxSize())
            }
            Controls(
                hide = ui.hideAmounts,
                onHide = { viewModel.setHideAmounts(it) },
                showNext = !ui.isLast,
                onNext = viewModel::next,
                onShare = { onShare(story, slide) },
            )
        }
    }
}

private fun body(size: Int) = androidx.compose.ui.text.TextStyle(fontFamily = Lalezar, fontSize = size.sp, lineHeight = (size * 1.35f).sp)
private fun alexandria(size: Int, weight: FontWeight) =
    androidx.compose.ui.text.TextStyle(fontFamily = Alexandria, fontSize = size.sp, fontWeight = weight)

@Composable
private fun Segments(count: Int, index: Int, progress: androidx.compose.runtime.State<Float>, description: String) {
    // A new slide starts from empty at once; the same slide fills in small linear steps.
    val target = progress.value
    val shown by animateFloatAsState(target, if (target == 0f) snap() else tween(60, easing = LinearEasing), label = "storyProgress")
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            val fraction = when {
                i < index -> 1f
                i == index -> shown
                else -> 0f
            }
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tz.qershRing)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Tz.night))
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.pressClickable(PressScale.Chip, onClick = onClick).size(48.dp).clip(CircleShape).background(Tz.night),
        contentAlignment = Alignment.Center,
    ) {
        TzIcon(TzIcons.close, 18.dp, stringResource(R.string.story_close_cd), tint = Tz.yellow)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlideBody(t: SlideText, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    // The big number counts up once, in 600 ms, each time its slide appears.
    val count = remember(t.slide) { Animatable(if (reduce || t.bigValue == null) 1f else 0f) }
    LaunchedEffect(t.slide) {
        if (!reduce && t.bigValue != null) count.animateTo(1f, tween(COUNT_UP_MS, easing = FastOutSlowInEasing))
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            t.kicker?.let { Text(it, style = alexandria(16, FontWeight.SemiBold), color = Tz.yellowInk) }
            if (t.big.isNotEmpty()) {
                val shown = t.bigValue?.let { v -> format((v * count.value).roundToLong(), t.bigIsMoney) } ?: t.big
                // shortcut: the big number is poster art sized to fit its length, so it keeps the normal font size at a large system font.
                CappedFontScale {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            shown,
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = Lalezar, fontSize = bigSize(t.big.length).sp, lineHeight = (bigSize(t.big.length) * 0.9f).sp,
                            ),
                            color = Tz.night, maxLines = 1,
                        )
                        t.suffix?.let { Text(it, style = androidx.compose.ui.text.TextStyle(fontFamily = Lalezar, fontSize = 34.sp, lineHeight = 37.sp), color = Tz.night) }
                    }
                }
            }
        }
        if (t.summary) t.line?.let { Text(it, style = body(26), color = Tz.night) }
        t.grid?.let { Grid(it, t.gridCd.orEmpty()) }
        if (t.legend.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.legend.forEachIndexed { i, text ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Swatch(i, Modifier.size(10.dp))
                        Text(text, style = alexandria(12, FontWeight.SemiBold), color = Tz.night)
                    }
                }
            }
        }
        if (t.rows.isNotEmpty()) {
            val card = if (t.summary) Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.night).padding(16.dp) else Modifier.fillMaxWidth()
            val fg = if (t.summary) Tz.yellow else Tz.night
            Column(card, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, style = alexandria(15, FontWeight.SemiBold), color = fg, modifier = Modifier.weight(1f, fill = false))
                        Text(r.value, style = androidx.compose.ui.text.TextStyle(fontFamily = Lalezar, fontSize = 22.sp), color = fg, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
        if (!t.summary) t.line?.let { Text(it, style = body(26), color = Tz.night) }
    }
}

private fun bigSize(length: Int) = when {
    length <= 3 -> 150
    length <= 5 -> 110
    length <= 7 -> 80
    else -> 56
}

private fun format(v: Long, money: Boolean) = if (money) String.format(Locale.US, "%,d", v) else v.toString()

/** 0 under the ticket (filled), 1 over it (carnelian), 2 not torn (dashed). */
@Composable
private fun Swatch(kind: Int, modifier: Modifier) {
    when (kind) {
        0 -> Box(modifier.clip(RoundedCornerShape(3.dp)).background(Tz.night))
        1 -> Box(modifier.clip(RoundedCornerShape(3.dp)).background(Tz.carnelian))
        else -> Box(modifier.dashedBox(3.dp, 1.5.dp))
    }
}

private fun Modifier.dashedBox(radius: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp) = drawBehind {
    val w = stroke.toPx()
    drawRoundRect(
        Tz.night, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 2.5f, w * 2f))),
    )
}

/** The month as squares, no stagger (05-MOTION): U under the ticket, O over it, N never torn. */
@Composable
private fun Grid(cells: String, description: String) {
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description; role = Role.Image },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        cells.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { ch ->
                    val m = Modifier.weight(1f).height(34.dp)
                    when (ch) {
                        'U' -> Box(m.clip(RoundedCornerShape(6.dp)).background(Tz.night))
                        'O' -> Box(m.clip(RoundedCornerShape(6.dp)).background(Tz.carnelian))
                        else -> Box(m.dashedBox(6.dp, 2.dp))
                    }
                }
                repeat(GRID_COLUMNS - row.length) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Controls(hide: Boolean, onHide: (Boolean) -> Unit, showNext: Boolean, onNext: () -> Unit, onShare: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(hide, role = Role.Switch) { haptics.perform(HapticLevel.Tap); onHide(it) },
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.story_share_hide), style = alexandria(13, FontWeight.SemiBold), color = Tz.night, modifier = Modifier.weight(1f))
            Box(
                Modifier.padding(start = 12.dp).size(48.dp, 28.dp).clip(RoundedCornerShape(14.dp)).background(if (hide) Tz.night else Tz.qershRing).padding(3.dp),
                contentAlignment = if (hide) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (hide) Tz.yellow else Tz.night))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).pressClickable { haptics.perform(HapticLevel.Tap); onShare() }.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Tz.night).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.story_share), style = androidx.compose.ui.text.TextStyle(fontFamily = Lalezar, fontSize = 20.sp), color = Tz.yellow)
            }
            if (showNext) {
                Box(
                    Modifier.pressClickable(PressScale.Chip, onClick = onNext).size(56.dp).clip(CircleShape).border(2.dp, Tz.night, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    TzIcon(TzIcons.chevron, 22.dp, stringResource(R.string.story_next_cd), tint = Tz.night)
                }
            }
        }
    }
}
