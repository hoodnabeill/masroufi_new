package com.masroufi.app.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.core.formatDayDate
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.feature.add.PunchFeedback
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.SkeletonBlock
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.skeletonPulse
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.paper.ProverbCard
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershBubble
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.paper.color
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import java.time.LocalDate

const val HomeSkeletonTag = "home_skeleton"

/** Dashed outline drawn inside the bounds (CSS `border: dashed`). */
internal fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color, Offset(w / 2f, w / 2f), Size(size.width - w, size.height - w), CornerRadius(radius.toPx() - w / 2f),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

@Composable
internal fun HomeHeader(today: LocalDate, greeting: Greeting, streak: Int, onOpenAlbum: () -> Unit = {}) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatDayDate(today, locale), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
            Text(
                greetingText(greeting),
                style = TzType.titleTab.copy(lineHeight = 1.2.em),
                color = Tz.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (streak > 0) {
            val cd = stringResource(R.string.home_streak_cd, streak.toString())
            val shape = RoundedCornerShape(22.dp)
            val haptics = LocalHaptics.current
            Row(
                Modifier.height(44.dp).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
                    .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpenAlbum() }
                    .clearAndSetSemantics { contentDescription = cd }
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TzIcon(TzIcons.flame, 18.dp, null, tint = Tz.yellow)
                Text(stringResource(R.string.home_streak, streak.toString()), style = TzType.rowNumber.copy(fontSize = 18.sp), color = Tz.yellow)
            }
        }
    }
}

/** Ticket text in [SubLine] form. */
@Composable
internal fun subLineText(s: SubLine): String = when (s) {
    SubLine.None -> stringResource(R.string.home_ticket_none)
    is SubLine.Spent -> stringResource(R.string.home_ticket_sub, formatPounds(s.ticket), formatPounds(s.spent), s.count.toString())
    is SubLine.Over -> stringResource(if (s.spread) R.string.home_ticket_over else R.string.home_ticket_over_last, formatPounds(s.over))
    is SubLine.Short -> stringResource(R.string.home_ticket_short, formatPounds(s.short))
}

/** The stub under the perforation (Main.dc.html): two lines and the night "قفّل اليوم" pill. */
@Composable
internal fun TicketStub(onCloseDay: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.home_stub_title), style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 1.4.em), color = Tz.night)
            Text(stringResource(R.string.home_stub_sub), style = TzType.caption, color = Tz.yellowInk2)
        }
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier.pressScale(source, PressScale.Primary).height(48.dp).clip(CircleShape).background(Tz.night)
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    haptics.perform(HapticLevel.Tap)
                    onCloseDay()
                }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.home_close_day), style = TzType.button.copy(fontSize = 18.sp), color = Tz.yellow)
        }
    }
}

/** After closing: tomorrow's ticket on yellow paper and the card earned today (tap opens it). */
@Composable
internal fun TomorrowPreview(tomorrow: TomorrowUi, onOpenProverb: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.home_tomorrow, formatPounds(tomorrow.amount)),
            style = TzType.hero.copy(fontSize = 44.sp),
            color = Tz.night,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(TzRadius.hero)).background(Tz.yellow)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        )
        val proverb = tomorrow.proverbNumber?.let { n -> ProverbCatalog.all.firstOrNull { it.number == n } }
        if (proverb != null) {
            Box(
                Modifier.size(width = 150.dp, height = 200.dp).clickable(role = Role.Button) {
                    haptics.perform(HapticLevel.Tap)
                    onOpenProverb()
                },
                contentAlignment = Alignment.Center,
            ) {
                ProverbCard(
                    stringResource(R.string.proverb_set_title), proverb.number, ProverbCatalog.all.size, proverb.text, proverb.line,
                    proverb.set.color(),
                    Modifier.requiredSize(width = 300.dp, height = 400.dp).graphicsLayer { scaleX = 0.5f; scaleY = 0.5f },
                )
            }
        }
    }
}

@Composable
internal fun QuickLogField(text: String, onText: (String) -> Unit, onSubmit: () -> Unit, onMic: (() -> Unit)?) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(28.dp)
    val label = stringResource(R.string.home_quick_label)
    val micLabel = stringResource(R.string.home_mic)
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
            .padding(start = 18.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            textStyle = TzType.body.copy(color = Tz.text),
            singleLine = true,
            cursorBrush = SolidColor(Tz.yellow),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(stringResource(R.string.home_quick_hint), style = TzType.body, color = Tz.text3)
                    inner()
                }
            },
        )
        if (onMic != null) {
            Box(
                Modifier.minimumInteractiveComponentSize()
                    .clickable(role = Role.Button) {
                        haptics.perform(HapticLevel.Tap)
                        onMic()
                    }
                    .semantics { contentDescription = micLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Tz.raised), contentAlignment = Alignment.Center) {
                    TzIcon(TzIcons.mic, 20.dp, null, tint = Tz.turquoise)
                }
            }
        }
    }
}

@Composable
internal fun SaveFailedLine() {
    Text(
        stringResource(R.string.home_save_failed),
        style = TzType.label.copy(fontWeight = FontWeight.Normal),
        color = Tz.carnelianText,
        modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** After a punch from the add sheet: the saved line, and Qersh's bubble on the day's first hole (Home clears both after 3 s). */
@Composable
internal fun PunchFeedbackLines(f: PunchFeedback.Saved, spread: Boolean) {
    Column(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val text = when {
            f.left >= 0 -> stringResource(R.string.home_punch_saved, formatPounds(f.left))
            spread -> stringResource(R.string.home_punch_over, formatPounds(-f.left))
            else -> stringResource(R.string.home_punch_over_last, formatPounds(-f.left))
        }
        Text(text, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text, modifier = Modifier.padding(horizontal = 4.dp))
        if (f.pausedHabit) {
            Text(stringResource(R.string.home_punch_paused), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (f.firstOfDay) QershBubble(stringResource(R.string.home_first_punch))
    }
}

@Composable
internal fun RepeatChips(repeats: List<RepeatChip>, onChip: (RepeatChip) -> Unit) {
    val haptics = LocalHaptics.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.home_repeats_title),
            style = TzType.label.copy(fontWeight = FontWeight.Normal),
            color = Tz.text2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeats.take(3).forEach { chip ->
                val source = remember { MutableInteractionSource() }
                val shape = RoundedCornerShape(TzRadius.chip)
                Column(
                    Modifier.weight(1f).pressScale(source, PressScale.Chip).height(60.dp).clip(shape).background(Tz.surface)
                        .border(1.dp, Tz.line, shape)
                        .clickable(interactionSource = source, indication = null, role = Role.Button) {
                            haptics.perform(HapticLevel.Confirm)
                            onChip(chip)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Text(chip.categoryName, style = TzType.label, color = Tz.text, maxLines = 1)
                    Text(formatPounds(chip.amount), style = TzType.rowNumber.copy(fontSize = 18.sp), color = Tz.yellow)
                }
            }
        }
    }
}

@Composable
internal fun WeekCard(week: List<WeekStub>, closedUnder: Int, onClick: (() -> Unit)? = null) {
    val days = stringArrayResource(R.array.home_week_days)
    TzCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.home_week_title), style = TzType.cardHeading, color = Tz.text, modifier = Modifier.alignByBaseline())
            Text(
                stringResource(R.string.home_week_count, closedUnder.toString()),
                style = TzType.caption,
                color = Tz.text2,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            week.forEachIndexed { i, stub ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        days[i],
                        style = TzType.caption.copy(fontSize = 11.sp, fontWeight = if (stub.isToday) FontWeight.Bold else FontWeight.Normal),
                        color = if (stub.isToday) Tz.yellow else Tz.text2,
                    )
                    WeekStubBox(stub)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun WeekStubBox(stub: WeekStub) {
    val shape = RoundedCornerShape(10.dp)
    val box = Modifier.fillMaxWidth().height(52.dp)
    val number = TzType.rowNumber.copy(fontSize = 15.sp)
    when (stub.kind) {
        StubKind.Under -> Box(box.clip(shape).background(Tz.turquoise), contentAlignment = Alignment.Center) {
            Text("+${formatPounds(stub.amount)}", style = number, color = Tz.turquoiseInk, maxLines = 1)
        }
        StubKind.Over -> Box(box.clip(shape).background(Tz.carnelianDim), contentAlignment = Alignment.Center) {
            Text("-${formatPounds(stub.amount)}", style = number, color = Tz.carnelianText, maxLines = 1)
        }
        StubKind.Today -> Box(box.border(2.dp, Tz.yellow, shape), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.home_week_today),
                style = TzType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = Tz.yellow,
                maxLines = 1,
            )
        }
        StubKind.Empty -> Box(box.dashedBorder(1.5.dp, Tz.dashed, 10.dp))
    }
}

/** Full width: the season card beside it is omitted until Phase 6; the gold line waits for Plus pricing. */
@Composable
internal fun JarCard(total: Long) {
    TzCard(Modifier.fillMaxWidth()) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(TzRadius.tile)).background(Tz.turquoiseDim), contentAlignment = Alignment.Center) {
            TzIcon(TzIcons.jar, 20.dp, null, tint = Tz.turquoise)
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.home_jar_title), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
        Spacer(Modifier.height(6.dp))
        val unit = stringResource(R.string.home_jar_unit)
        Text(
            buildAnnotatedString {
                append(formatPounds(total))
                append(" ")
                withStyle(SpanStyle(fontSize = 15.sp, color = Tz.text2)) { append(unit) }
            },
            style = TzType.card.copy(fontSize = 28.sp, lineHeight = 1.1.em),
            color = Tz.text,
        )
    }
}

@Composable
internal fun CycleCard(cycle: CycleUi, onOpenEndOfMonth: () -> Unit = {}) {
    val haptics = LocalHaptics.current
    TzCard(Modifier.fillMaxWidth()) {
        // 04-SCREENS "Main" 7: the title, and a link to end-of-month mode at its end (Main.dc.html).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_cycle_title, cycle.daysLeft.toString()), style = TzType.cardHeading, color = Tz.text)
            TextLink(stringResource(R.string.eom_title), { haptics.perform(HapticLevel.Tap); onOpenEndOfMonth() })
        }
        Spacer(Modifier.height(10.dp))
        TzProgressBar(listOf(cycle.progress to Tz.turquoise), height = 10.dp)
        val projected = cycle.projected
        if (projected != null) {
            Spacer(Modifier.height(10.dp))
            val have = formatPounds(cycle.spendable)
            val y = formatPounds(projected)
            val line = stringResource(R.string.home_cycle_line, have, y, cycle.paydayDay.toString())
            // Y follows the balance in both languages; search after it so an equal balance is not matched.
            val at = line.indexOf(y, line.indexOf(have) + have.length)
            Text(
                buildAnnotatedString {
                    append(line)
                    if (at >= 0) addStyle(SpanStyle(color = Tz.text, fontWeight = FontWeight.SemiBold), at, at + y.length)
                },
                style = TzType.label.copy(fontWeight = FontWeight.Normal, lineHeight = 1.7.em),
                color = Tz.text2,
            )
        }
    }
}

@Composable
internal fun ProverbTeaser(collected: Int) {
    Row(
        Modifier.fillMaxWidth().dashedBorder(1.5.dp, Tz.dashed, TzRadius.card).semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(TzRadius.tile)).background(Tz.raised), contentAlignment = Alignment.Center) {
            TzIcon(TzIcons.lock, 20.dp, null, tint = Tz.text2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.home_teaser_title), style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.4.em), color = Tz.text)
            Text(
                stringResource(R.string.home_teaser_sub, collected.toString(), ProverbCatalog.all.size.toString()),
                style = TzType.caption,
                color = Tz.text2,
            )
        }
    }
}

/** EmptyStates.dc.html loading shape: ticket outline with a blank number block, then two rows. Pulses unless motion is reduced. */
@Composable
internal fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().testTag(HomeSkeletonTag).skeletonPulse(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.size(width = 110.dp, height = 12.dp), Tz.line, RoundedCornerShape(6.dp))
            SkeletonBlock(Modifier.size(width = 140.dp, height = 28.dp))
        }
        val ticket = RoundedCornerShape(TzRadius.hero)
        Box(Modifier.padding(top = 26.dp).fillMaxWidth().height(330.dp).border(1.5.dp, Tz.line, ticket).padding(20.dp)) {
            SkeletonBlock(Modifier.padding(top = 36.dp).size(width = 150.dp, height = 96.dp), shape = RoundedCornerShape(TzRadius.tile))
        }
        TzCard(Modifier.fillMaxWidth()) {
            RowSkeleton(0.6f, 0.36f, 44.dp)
            Spacer(Modifier.height(14.dp))
            RowSkeleton(0.44f, 0.52f, 36.dp)
        }
    }
}

@Composable
internal fun HomeError(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Qersh(QershMood.Thinking, 120.dp)
        Text(stringResource(R.string.home_error), style = TzType.body.copy(textAlign = TextAlign.Center), color = Tz.text2)
    }
}
