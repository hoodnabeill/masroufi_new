package com.masroufi.app.feature.installments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.plan.AmountSheet
import com.masroufi.app.feature.plan.PillButton
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.paper.BlankSheet
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntSize
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle

/** "يناير" or "فبراير 2028": the month's name in the app language, Western digits. */
@Composable
internal fun monthLabel(month: YearMonth, withYear: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(month, withYear, locale) {
        DateTimeFormatter.ofPattern(if (withYear) "MMMM yyyy" else "MMMM", locale).withDecimalStyle(DecimalStyle.STANDARD).format(month)
    }
}

private const val END_FILL_DELAY_MS = 300L
private const val END_FOLD_DELAY_MS = 700L
private const val END_SEEN_DELAY_MS = 400L

/** أقساطي (Installments.dc.html): hero, the freedom date, one row per plan, early payoff, the link to before-you-buy. */
@Composable
fun InstallmentsScreen(viewModel: InstallmentsViewModel, onBack: () -> Unit, onNew: () -> Unit, onEdit: (Long) -> Unit, onOpenBeforeBuy: () -> Unit) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var paying by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.inst_title), onBack, trailing = {
            CircleButton(TzIcons.add, stringResource(R.string.inst_add_cd), onNew)
        })
        when {
            s.loading -> repeat(3) { RowSkeleton() }
            s.empty -> EmptyState({ BlankSheet() }, R.string.inst_empty_title, R.string.inst_empty_line, R.string.inst_empty_action, onOpenBeforeBuy)
            else -> {
                Hero(s)
                if (s.freedom != null && s.bars.isNotEmpty()) FreedomCard(s)
                PlansCard(s, onEdit, onSeen = viewModel::finishedSeen)
                s.early?.let { EarlyCard(it, s.payOffFailed, onPay = { paying = true }, onKeep = { viewModel.keepGoing(it.planId) }) }
                BeforeBuyLink(onOpenBeforeBuy)
            }
        }
    }
    val early = s.early
    if (paying && early != null) {
        AmountSheet(stringResource(R.string.inst_pay_title, early.name), { paying = false }, AmountInput.of(early.amount)) { amount ->
            PrimaryButton(
                stringResource(R.string.inst_pay_confirm, formatPounds(amount)),
                { viewModel.payOff(early.planId, amount); paying = false }, // the end motion fires the Milestone once the plan is really finished
                Modifier.fillMaxWidth(), enabled = amount > 0, height = 52.dp,
            )
        }
    }
}

@Composable
private fun Hero(s: InstallmentsUi) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.inst_hero_label), style = TzType.label.copy(fontSize = 13.sp), color = Tz.text2)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Odometer(s.monthly, TzType.hero.copy(fontSize = 64.sp, lineHeight = 64.sp))
            Text(stringResource(R.string.paper_currency), style = TzType.titlePushed.copy(fontSize = 20.sp), color = Tz.text2, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(s.perDay, R.string.inst_per_day, R.string.inst_per_day_line, Tz.yellow, Tz.night, Tz.night, Modifier.weight(1f))
            Tile(s.remaining, null, R.string.inst_remaining_line, Tz.night, Tz.text, Tz.text2, Modifier.weight(1f))
        }
    }
}

/** A hero tile: a rolling number, [suffix] after it, and a small line. */
@Composable
private fun Tile(value: Long, suffix: Int?, line: Int, bg: Color, ink: Color, inkSmall: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Odometer(value, TzType.rowNumber.copy(fontSize = 24.sp, color = ink))
            if (suffix != null) Text(stringResource(suffix), style = TzType.rowNumber.copy(fontSize = 24.sp), color = ink)
        }
        Text(stringResource(line), style = TzType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = inkSmall)
    }
}

private val BarShades = listOf(Color(0xFF0F5560), Color(0xFF19808A), Color(0xFF22A0A6), Color(0xFF2BBAB8))

/** يوم الحرية: the timeline runs left to right as drawn, then the growth line. */
@Composable
private fun FreedomCard(s: InstallmentsUi) {
    val freedom = checkNotNull(s.freedom)
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.turquoise).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.inst_freedom), style = TzType.cardHeading.copy(fontSize = 24.sp), color = Tz.turquoiseInk)
            Text(monthLabel(freedom, withYear = true), style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Tz.turquoiseInk)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    s.bars.forEachIndexed { i, bar ->
                        val h by animateFloatAsState(bar.fraction, motionSpec(TzSpring.Move), label = "freedomBar")
                        val color = when (bar.kind) {
                            BarKind.NOW -> Tz.turquoiseInk
                            BarKind.MONTH -> BarShades[(i - 1) % BarShades.size]
                            BarKind.FREE -> Tz.text
                        }
                        val top = if (bar.kind == BarKind.FREE) 4.dp else 8.dp
                        Box(Modifier.weight(bar.weight.toFloat()).fillMaxHeight(h.coerceIn(0f, 1f)).clip(RoundedCornerShape(top, top, 0.dp, 0.dp)).background(color))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.bars.forEach { bar ->
                        val label = when (bar.kind) {
                            BarKind.NOW -> stringResource(R.string.inst_now)
                            BarKind.MONTH -> monthLabel(checkNotNull(bar.month), withYear = false)
                            BarKind.FREE -> stringResource(R.string.inst_done)
                        }
                        Text(
                            label, Modifier.weight(bar.weight.toFloat()), style = TzType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = Tz.turquoiseInk, maxLines = 1, textAlign = if (bar.kind == BarKind.FREE) TextAlign.End else TextAlign.Start,
                        )
                    }
                }
            }
        }
        val steps = s.growth.map { stringResource(R.string.inst_growth_step, formatPounds(it.growth), monthLabel(it.month, it.withYear)) }
        Text(
            stringResource(R.string.inst_growth_line, steps.joinToString(stringResource(R.string.inst_growth_sep))),
            style = TzType.body.copy(fontSize = 13.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium), color = Tz.turquoiseInk,
        )
    }
}

@Composable
private fun PlansCard(s: InstallmentsUi, onEdit: (Long) -> Unit, onSeen: (Long) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)) {
        s.rows.forEachIndexed { i, row -> PlanItem(row, s.today, first = i == 0, onEdit, onSeen) }
    }
}

/** A plan row. A plan seen finished fills its bar, fires the Milestone haptic once, then folds away (05-MOTION "An installment plan ends"). */
@Composable
private fun PlanItem(row: PlanRowUi, today: YearMonth, first: Boolean, onEdit: (Long) -> Unit, onSeen: (Long) -> Unit) {
    val id = row.plan.item.id
    val haptics = LocalHaptics.current
    var filled by remember(id) { mutableStateOf(!row.ending) }
    var gone by remember(id) { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    if (row.ending) {
        LaunchedEffect(id) {
            delay(END_FILL_DELAY_MS)
            filled = true
            haptics.perform(HapticLevel.Milestone)
            delay(END_FOLD_DELAY_MS)
            gone = true
            delay(END_SEEN_DELAY_MS)
            onSeen(id)
        }
    }
    AnimatedVisibility(
        visible = !gone,
        exit = if (reduce) fadeOut(tween(120)) else shrinkVertically(TzSpring.Move.spec<IntSize>()) + fadeOut(TzSpring.Move.spec<Float>()),
    ) {
        PlanRow(row, today, filled, first, onClick = { onEdit(id) })
    }
}

private val TileInks = listOf(Tz.lilac, Tz.yellow, Tz.sky, Tz.turquoise)

private fun planIcon(name: String): TzIconDef = when {
    "نضار" in name -> TzIcons.glasses
    "موبايل" in name || "تليفون" in name || "فون" in name -> TzIcons.phone
    "تلاج" in name -> TzIcons.fridge
    else -> TzIcons.shop
}

@Composable
private fun PlanRow(row: PlanRowUi, today: YearMonth, filled: Boolean, first: Boolean, onClick: () -> Unit) {
    val plan = row.plan
    val ink = TileInks[(plan.item.id % TileInks.size).toInt()]
    val soon = row.line is PlanLine.OneLeft || row.line is PlanLine.TwoLeft || row.line is PlanLine.Done
    Column(
        Modifier.fillMaxWidth().drawBehind { if (!first) drawLine(Tz.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Tz.raised), contentAlignment = Alignment.Center) {
                TzIcon(planIcon(plan.item.name), 22.dp, null, tint = ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(plan.item.name, style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text, maxLines = 1)
                Text(lineText(row.line, today), style = TzType.caption.copy(fontSize = 12.sp), color = if (soon) Tz.turquoise else Tz.text2, maxLines = 2)
            }
            Text(formatPounds(plan.monthly), style = TzType.rowNumber, color = Tz.text)
        }
        val total = plan.total
        if (total != null) PlanBar(if (filled) total else plan.paid.coerceAtMost(total - 1).coerceAtLeast(0), total, ink)
    }
}

@Composable
private fun lineText(line: PlanLine, today: YearMonth): String {
    fun far(m: YearMonth) = java.time.temporal.ChronoUnit.MONTHS.between(today, m) >= 12
    return when (line) {
        is PlanLine.OneLeft -> stringResource(R.string.inst_line_one, monthLabel(line.end, far(line.end)))
        is PlanLine.TwoLeft -> stringResource(R.string.inst_line_two, monthLabel(line.end, far(line.end)))
        is PlanLine.Left -> stringResource(R.string.inst_line_left, line.left.toString(), line.total.toString(), line.day.toString(), monthLabel(line.end, far(line.end)))
        is PlanLine.Open -> stringResource(R.string.inst_line_open, line.day.toString())
        PlanLine.Done -> stringResource(R.string.inst_line_done)
    }
}

/** Up to 12 payments: one pill each (gap 4, or 3 from 7 up, as drawn); more: one bar that fills. */
@Composable
private fun PlanBar(paid: Int, total: Int, color: Color) {
    val cd = stringResource(R.string.inst_bar_cd, paid.toString(), total.toString())
    if (total <= 12) {
        Row(Modifier.fillMaxWidth().semantics { contentDescription = cd }, horizontalArrangement = Arrangement.spacedBy(if (total <= 6) 4.dp else 3.dp)) {
            repeat(total) { i ->
                val c by animateFloatAsState(if (i < paid) 1f else 0f, motionSpec(TzSpring.Move), label = "planPill")
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Tz.line)) {
                    Box(Modifier.fillMaxSize().drawBehind { drawRect(color.copy(alpha = c)) })
                }
            }
        }
    } else {
        val f by animateFloatAsState(paid.toFloat() / total, motionSpec(TzSpring.Move), label = "planBar")
        Canvas(Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = cd }) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(Tz.line, size = size, cornerRadius = r)
            if (f > 0f) drawRoundRect(color, size = Size(size.width * f, size.height), cornerRadius = r)
        }
    }
}

/** "خلّص {name} بدري": the amount is the repository's; "سدّد" opens the keypad sheet to confirm what the seller said. */
@Composable
private fun EarlyCard(e: EarlyUi, failed: Boolean, onPay: () -> Unit, onKeep: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.yellowDim).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.inst_early_title, e.name), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.yellow)
        Text(
            stringResource(R.string.inst_early_body, formatPounds(e.amount), formatPounds(e.growth)),
            style = TzType.body.copy(fontSize = 13.sp, lineHeight = 22.sp), color = Color(0xFFF0E3B5),
        )
        if (failed) Text(stringResource(R.string.inst_early_failed), style = TzType.label.copy(fontSize = 13.sp), color = Tz.carnelianText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.inst_early_pay, formatPounds(e.amount)), true, Tz.yellow, Tz.night, onPay, Modifier.weight(1f))
            PillButton(stringResource(R.string.inst_early_keep), false, Tz.yellow, Tz.night, onKeep, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BeforeBuyLink(onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }
            .drawBehind {
                drawRoundRect(
                    Tz.dashed, cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }.padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.inst_buy_title), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
            Text(stringResource(R.string.inst_buy_line), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
        TzIcon(TzIcons.chevron, 18.dp, null)
    }
}
