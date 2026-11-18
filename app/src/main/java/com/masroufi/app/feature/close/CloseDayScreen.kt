package com.masroufi.app.feature.close

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatDayDate
import com.masroufi.app.core.formatPounds
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.feature.home.HomeError
import com.masroufi.app.feature.home.HomeSkeleton
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/** Skip the skeleton for loads under this (10-PERFORMANCE). */
private const val SKELETON_AFTER_MS = 150L

/** Marks where an animated number goes inside a formatted string. */
private const val MARK = "\u0001"

private const val SLOTS = 8
private const val REST_TILT = -3f
private const val MAX_TILT = 3f
private val SEPARATION = 16.dp
private val ARC_HEIGHT = 48.dp
private val TILT_RANGE = 120.dp

private val TicketTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 10.dp, bottomStart = 10.dp)
private val StubShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 22.dp, bottomStart = 22.dp)

private enum class Phase { Blank, Skeleton, Content, Error }

@Composable
fun CloseDayScreen(viewModel: CloseDayViewModel, onBack: () -> Unit, onDone: (epochDay: Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tear by viewModel.tear.collectAsStateWithLifecycle()
    CloseDayContent(state, tear, { viewModel.tear(it) }, onBack, onDone = { onDone(viewModel.date.toEpochDay()) })
}

/** Stateless close-the-day screen: skeleton after 150 ms of loading, 160 ms cross-fade to the content or the error. */
@Composable
fun CloseDayContent(
    state: CloseDayUi,
    tear: TearState,
    onTear: (LeftoverDestination) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var skeletonDue by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SKELETON_AFTER_MS)
        skeletonDue = true
    }
    val phase = when (state) {
        is CloseDayUi.Ready -> Phase.Content
        CloseDayUi.Error -> Phase.Error
        CloseDayUi.Loading -> if (skeletonDue) Phase.Skeleton else Phase.Blank
    }
    Crossfade(phase, modifier.fillMaxSize(), animationSpec = tween(160), label = "closeDay") { p ->
        when (p) {
            Phase.Blank -> Box(Modifier.fillMaxSize())
            Phase.Skeleton -> HomeSkeleton(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp))
            Phase.Error -> HomeError(Modifier.statusBarsPadding())
            Phase.Content -> (state as? CloseDayUi.Ready)?.let { CloseDayReady(it, tear, onTear, onBack, onDone) }
        }
    }
}

private fun weekday(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEEE", locale).withDecimalStyle(DecimalStyle.STANDARD).format(date)

@Composable
private fun CloseDayReady(
    s: CloseDayUi.Ready,
    tear: TearState,
    onTear: (LeftoverDestination) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val hasLeftover = s.leftover > 0
    val over = s.over > 0

    var selected by rememberSaveable(s.defaultDestination) { mutableStateOf(s.defaultDestination) }
    var tearing by remember { mutableStateOf(false) }
    var landed by remember { mutableStateOf(false) }
    var tiltTarget by remember { mutableFloatStateOf(REST_TILT) }
    val tilt by animateFloatAsState(tiltTarget, motionSpec(TzSpring.Press), label = "stubTilt")
    val slide = remember { Animatable(0f) }
    val rot = remember { Animatable(REST_TILT) }
    val flight = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }

    var rootAt by remember { mutableStateOf(Offset.Zero) }
    var labelAt by remember { mutableStateOf<Offset?>(null) }
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    val cardCentres = remember { HashMap<LeftoverDestination, Offset>() }

    val currentTear by rememberUpdatedState(tear)
    val currentTilt by rememberUpdatedState(tilt)
    val currentDone by rememberUpdatedState(onDone)

    fun startTear() {
        if (tearing) return
        tearing = true
        onTear(selected)
    }

    // 05-MOTION "Tear": separate, fly the amount into its card, pulse, then hand over once the day is saved.
    LaunchedEffect(tearing) {
        if (!tearing) return@LaunchedEffect
        haptics.perform(HapticLevel.Heavy)
        haptics.sound()
        if (reduce) {
            haptics.perform(HapticLevel.Tap)
            landed = true
            delay(300)
        } else {
            rot.snapTo(currentTilt)
            launch { slide.animateTo(1f, TzSpring.Reward.spec()) }
            launch { rot.animateTo(REST_TILT, TzSpring.Reward.spec()) }
            if (hasLeftover && labelAt != null && cardCentres[selected] != null) {
                flight.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            }
            landed = true
            haptics.perform(HapticLevel.Tap)
            launch {
                pulse.animateTo(1f, tween(140))
                pulse.animateTo(0f, tween(220))
            }
            delay(360)
        }
        snapshotFlow { currentTear }.first { it == TearState.Done }
        currentDone()
    }
    // A failed save: the stub comes back and the button works again.
    LaunchedEffect(tear) {
        if (tear == TearState.Failed) {
            tearing = false
            landed = false
            tiltTarget = REST_TILT
            slide.snapTo(0f)
            rot.snapTo(REST_TILT)
            flight.snapTo(0f)
            pulse.snapTo(0f)
        }
    }
    BackHandler(enabled = tearing) {}

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootAt = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Header(s.date, locale) { if (!tearing) onBack() }
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.close_title),
                        style = TzType.titleTab.copy(fontSize = 34.sp, lineHeight = 1.2.em),
                        color = Tz.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.close_sub), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal), color = Tz.text2)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TicketTop(s, weekday(s.date, locale))
                    if (hasLeftover) {
                        Stub(
                            amount = s.leftover,
                            hideLabel = tearing && !reduce,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .graphicsLayer {
                                    translationY = slide.value * SEPARATION.toPx()
                                    rotationZ = if (tearing) rot.value else tilt
                                }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    onClick { startTear(); true }
                                }
                                .pointerInput(tearing) {
                                    if (tearing) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        haptics.perform(HapticLevel.Tick)
                                        var released = false
                                        while (true) {
                                            // Final pass: a parent scroll that took the drag has consumed it by now, so no tear.
                                            val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                                            if (change.isConsumed && !change.changedToUp()) break
                                            if (change.changedToUp()) {
                                                change.consume()
                                                released = true
                                                break
                                            }
                                            if (!change.pressed) break
                                            if (!reduce) {
                                                val dx = (change.position.x - down.position.x) / TILT_RANGE.toPx()
                                                tiltTarget = dx.coerceIn(-1f, 1f) * MAX_TILT
                                            }
                                        }
                                        if (released) startTear() else tiltTarget = REST_TILT
                                    }
                                },
                            onLabel = { at, size -> labelAt = at; labelSize = size },
                        )
                    }
                }
                if (hasLeftover) {
                    Destinations(s, selected, tearing, landed, pulse.value, weekday(s.date.plusDays(1), locale), cardCentres) {
                        if (it != selected) haptics.perform(HapticLevel.Tick)
                        selected = it
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (tear == TearState.Failed) {
                Text(
                    stringResource(R.string.home_save_failed),
                    style = TzType.label,
                    color = Tz.carnelianText,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton(
                text = stringResource(if (over) R.string.close_button_over else R.string.close_button),
                onClick = ::startTear,
                modifier = Modifier.fillMaxWidth(),
                height = 60.dp,
                textStyle = TzType.button.copy(fontSize = 22.sp),
            )
        }

        val from = labelAt
        val to = cardCentres[selected]
        if (tearing && !reduce && hasLeftover && from != null && to != null && flight.value in 0.001f..0.999f) {
            val p = flight.value
            val target = to - Offset(labelSize.width / 2f, labelSize.height / 2f)
            Text(
                stringResource(R.string.close_stub_amount, formatPounds(s.leftover)),
                style = TzType.rowNumber,
                color = Tz.yellow,
                modifier = Modifier
                    .offset {
                        val x = from.x + (target.x - from.x) * p - rootAt.x
                        val y = from.y + (target.y - from.y) * p - rootAt.y - sin(PI.toFloat() * p) * ARC_HEIGHT.toPx()
                        IntOffset(x.toInt(), y.toInt())
                    }
                    .graphicsLayer { alpha = if (p > 0.8f) (1f - p) / 0.2f else 1f }
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun Header(date: LocalDate, locale: Locale, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier.pressScale(source, PressScale.Chip).size(44.dp).clip(CircleShape).background(Tz.surface).border(1.dp, Tz.line, CircleShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            TzIcon(TzIcons.back, 20.dp, stringResource(R.string.close_back), tint = Tz.text)
        }
        Text(formatDayDate(date, locale), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
        Spacer(Modifier.width(44.dp))
    }
}

@Composable
private fun TicketTop(s: CloseDayUi.Ready, weekday: String) {
    val line = when {
        s.over > 0 && s.spreadDays >= 1 ->
            stringResource(R.string.close_over_spread, formatPounds(s.over), s.spreadDays.toString(), formatPounds(s.spreadPerDay))
        s.over > 0 -> stringResource(R.string.close_over, formatPounds(s.over))
        s.leftover == 0L -> stringResource(R.string.close_zero)
        s.biggest?.categoryName != null -> stringResource(
            R.string.close_line_biggest, formatPounds(s.spent), s.holes.toString(), s.biggest.categoryName, formatPounds(s.biggest.amount),
        )
        else -> stringResource(R.string.close_line, formatPounds(s.spent), s.holes.toString())
    }
    Column(
        Modifier.fillMaxWidth().clip(TicketTopShape).background(Tz.yellow)
            .drawWithContent {
                drawContent()
                val y = size.height - 1.dp.toPx()
                drawLine(
                    Tz.yellowRule, Offset(10.dp.toPx(), y), Offset(size.width - 10.dp.toPx(), y), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.close_ticket_label, weekday), style = TzType.caption.copy(fontWeight = FontWeight.SemiBold), color = Tz.yellowInk2)
        Text(stringResource(R.string.close_left_label), style = TzType.label.copy(fontSize = 14.sp), color = Tz.yellowInk, modifier = Modifier.padding(top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Odometer(s.leftover, TzType.ticket.copy(fontSize = 88.sp, color = Tz.night))
            Text(
                stringResource(R.string.paper_currency),
                style = TzType.titleTab.copy(fontSize = 24.sp),
                color = Tz.night,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Holes(s.holes, Modifier.padding(top = 10.dp))
        Text(line, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.yellowInk, modifier = Modifier.padding(top = 8.dp))
    }
}

/** 24 dp holes, gap 8: one punched (night) per entry, the rest dashed; a "+N" past the 8th. */
@Composable
private fun Holes(count: Int, modifier: Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val punched = count.coerceAtLeast(0)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = (SLOTS * 24 + (SLOTS - 1) * 8).dp, height = 24.dp)) {
            val d = 24.dp.toPx()
            val outline = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            for (i in 0 until SLOTS) {
                val c = Offset((if (rtl) SLOTS - 1 - i else i) * (d + 8.dp.toPx()) + d / 2f, d / 2f)
                if (i < punched) {
                    drawCircle(Tz.night, d / 2f, c)
                } else {
                    drawCircle(Tz.yellowRule, d / 2f - 1.dp.toPx(), c, style = outline)
                }
            }
        }
        if (punched > SLOTS) Text("+${punched - SLOTS}", style = TzType.rowNumber, color = Tz.night, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Stub(
    amount: Long,
    hideLabel: Boolean,
    modifier: Modifier,
    onLabel: (Offset, IntSize) -> Unit,
) {
    Row(
        modifier.width(250.dp).clip(StubShape).background(Tz.yellow)
            .drawWithContent {
                drawContent()
                val y = 1.dp.toPx()
                drawLine(
                    Tz.yellowRule, Offset(10.dp.toPx(), y), Offset(size.width - 10.dp.toPx(), y), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.close_stub_title), style = TzType.label.copy(fontWeight = FontWeight.Bold), color = Tz.night)
        Text(
            stringResource(R.string.close_stub_amount, formatPounds(amount)),
            style = TzType.rowNumber,
            color = Tz.night,
            modifier = Modifier
                .onGloballyPositioned { onLabel(it.positionInRoot(), it.size) }
                .graphicsLayer { alpha = if (hideLabel) 0f else 1f },
        )
    }
}

@Composable
private fun Destinations(
    s: CloseDayUi.Ready,
    selected: LeftoverDestination,
    tearing: Boolean,
    landed: Boolean,
    pulse: Float,
    tomorrowName: String,
    centres: MutableMap<LeftoverDestination, Offset>,
    onSelect: (LeftoverDestination) -> Unit,
) {
    // Only the card the amount flies into changes while the day is torn: it rolls from "before" to "after".
    val rolling = tearing && !landed
    Column(Modifier.padding(top = 4.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.close_where, formatPounds(s.leftover)),
            style = TzType.cardHeading,
            color = Tz.text,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp).semantics { heading() },
        )
        DestinationCard(
            title = stringResource(R.string.close_dest_jar),
            template = stringResource(R.string.close_dest_jar_sub, MARK),
            value = if (selected == LeftoverDestination.JAR && rolling) s.jarAfter - s.leftover else s.jarAfter,
            icon = TzIcons.jar, tileBg = Tz.turquoise, tileFg = Tz.turquoiseInk, accent = Tz.turquoise,
            selected = selected == LeftoverDestination.JAR,
            pulse = if (selected == LeftoverDestination.JAR) pulse else 0f,
            onCentre = { centres[LeftoverDestination.JAR] = it },
        ) { onSelect(LeftoverDestination.JAR) }
        DestinationCard(
            title = stringResource(R.string.close_dest_tomorrow),
            template = stringResource(R.string.close_dest_tomorrow_sub, tomorrowName, MARK, formatPounds(s.tomorrowWithout)),
            value = if (selected == LeftoverDestination.TOMORROW && rolling) s.tomorrowWithout else s.tomorrowWith,
            icon = TzIcons.today, tileBg = Tz.yellowDim, tileFg = Tz.yellow, accent = Tz.yellow,
            selected = selected == LeftoverDestination.TOMORROW,
            pulse = if (selected == LeftoverDestination.TOMORROW) pulse else 0f,
            onCentre = { centres[LeftoverDestination.TOMORROW] = it },
        ) { onSelect(LeftoverDestination.TOMORROW) }
        DestinationCard(
            title = stringResource(R.string.close_dest_emergency),
            template = stringResource(R.string.close_dest_emergency_sub, MARK),
            value = if (selected == LeftoverDestination.EMERGENCY && tearing && landed) s.emergency + s.leftover else s.emergency,
            icon = TzIcons.envelope, tileBg = Tz.raised, tileFg = Tz.text2, accent = Tz.yellow,
            selected = selected == LeftoverDestination.EMERGENCY,
            pulse = if (selected == LeftoverDestination.EMERGENCY) pulse else 0f,
            onCentre = { centres[LeftoverDestination.EMERGENCY] = it },
        ) { onSelect(LeftoverDestination.EMERGENCY) }
    }
}

@Composable
private fun DestinationCard(
    title: String,
    template: String,
    value: Long,
    icon: TzIconDef,
    tileBg: Color,
    tileFg: Color,
    accent: Color,
    selected: Boolean,
    pulse: Float,
    onCentre: (Offset) -> Unit,
    onSelect: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val border: Dp = if (selected) 2.dp else 1.dp
    // The border is 2 dp when selected and 1 dp otherwise; the padding takes up the difference so the cards never jump.
    val extra = 2.dp - border
    Row(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { onCentre(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)) }
            .pressScale(source, PressScale.Card)
            .clip(shape)
            .background(if (selected) Tz.turquoiseDim else Tz.surface)
            .drawBehind { drawRect(Tz.turquoise.copy(alpha = 0.3f * pulse)) }
            .border(border, if (selected) Tz.turquoise else Tz.line, shape)
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp + extra, vertical = 14.dp + extra),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(TzRadius.tile)).background(tileBg), contentAlignment = Alignment.Center) {
            TzIcon(icon, 20.dp, null, tint = tileFg)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.3.em), color = Tz.text)
            NumberLine(template, value, TzType.caption)
        }
        Canvas(Modifier.size(22.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(if (selected) accent else Tz.text3, size.width / 2f - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            if (selected) drawCircle(accent, 6.dp.toPx(), c)
        }
    }
}

/** [template] holds [MARK] where [piastres] rolls in; read by TalkBack as the plain sentence. */
@Composable
private fun NumberLine(template: String, piastres: Long, style: TextStyle) {
    val before = template.substringBefore(MARK)
    val after = template.substringAfter(MARK, "")
    val plain = template.replace(MARK, formatPounds(piastres))
    Row(Modifier.clearAndSetSemantics { text = AnnotatedString(plain) }, verticalAlignment = Alignment.CenterVertically) {
        Text(before, style = style, color = Tz.text2)
        Odometer(piastres, style.copy(color = Tz.text2))
        Text(after, style = style, color = Tz.text2)
    }
}
