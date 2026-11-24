package com.masroufi.app.feature.kids

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.core.PIN_MAX_DIGITS
import com.masroufi.app.data.repo.ChildJar
import com.masroufi.app.feature.pockets.pocketColor
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.DayOfWeek

/** A child's ticket colours: the palette colour as the paper, a dark tone of it for ink, mid tones for the tear line and the bar's track. */
internal class TicketColors(val paper: Color, val ink: Color, val tear: Color, val track: Color)

internal fun ticketColors(argb: Int): TicketColors {
    val paper = pocketColor(argb)
    val ink = lerp(paper, Color.Black, 0.8f)
    return TicketColors(paper, ink, lerp(paper, ink, 0.45f), lerp(paper, ink, 0.15f))
}

/** "السبت" / "Saturday": the arrays run Saturday first. */
@Composable
internal fun dayName(day: DayOfWeek, bare: Boolean = false): String =
    stringArrayResource(if (bare) R.array.home_week_days else R.array.match_day_names)[(day.value + 1) % 7]

@Composable
internal fun ageText(age: Int): String = when {
    age < 1 -> stringResource(R.string.kids_age_lt1)
    age == 1 -> stringResource(R.string.kids_age_1)
    age == 2 -> stringResource(R.string.kids_age_2)
    age <= 10 -> stringResource(R.string.kids_age_few, age.toString())
    else -> stringResource(R.string.kids_age_many, age.toString())
}

/**
 * Kids.dc.html's ticket: the child's colour, initial and "name · age", the weekly sum, the big "left" number (it rolls when
 * it changes, as at a handover), the punches row, the tear line and [below]. The Arabic forms that need her or his are
 * left out (nothing is stored about it), so every line reads for either.
 */
@Composable
internal fun KidsTicket(
    t: ChildTicketUi,
    handoverDay: DayOfWeek,
    modifier: Modifier = Modifier,
    onHeader: (() -> Unit)? = null,
    hasBelow: Boolean = false,
    below: @Composable ColumnScope.(TicketColors) -> Unit = {},
) {
    val c = ticketColors(t.view.child.color)
    val shape = RoundedCornerShape(26.dp)
    val name = t.view.child.name
    val left = t.view.left
    Column(modifier.fillMaxWidth().clip(shape).background(c.paper)) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().then(if (onHeader != null) Modifier.clickable(role = Role.Button, onClick = onHeader) else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                        Text(name.trim().take(1), style = TzType.titlePushed.copy(fontSize = 20.sp), color = c.paper)
                    }
                    Text(
                        stringResource(R.string.kids_name_age, name, ageText(t.view.age)),
                        style = TzType.card.copy(fontSize = 24.sp, lineHeight = 28.sp), color = c.ink, maxLines = 2,
                    )
                }
                Text(
                    stringResource(R.string.kids_weekly, poundsText(t.view.child.weekly)),
                    style = TzType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = c.ink,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Odometer(left, TzType.hero.copy(fontSize = 60.sp, lineHeight = 60.sp, color = c.ink))
                Text(
                    if (left > 0) stringResource(R.string.kids_left_until, dayName(handoverDay)) else stringResource(R.string.kids_left_done, dayName(t.finishedOn)),
                    style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Punches(t.view.punches, t.slots, c)
        }
        if (hasBelow) {
            Tear(c)
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { below(c) }
        }
    }
}

/** This week's spends as punched holes: the rest are dashed circles. They all reset in one step at a handover. */
@Composable
private fun Punches(punches: Int, slots: Int, c: TicketColors) {
    Row(
        Modifier.semantics { contentDescription = "$punches/$slots" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(slots) { i ->
            if (i < punches) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(c.ink))
            } else {
                Canvas(Modifier.size(20.dp)) {
                    drawCircle(
                        c.tear, size.minDimension / 2f - 1.dp.toPx(),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
            }
        }
    }
}

/** The dashed tear line with a Night notch at each end. */
@Composable
private fun Tear(c: TicketColors) {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        val y = size.height / 2f
        val inset = 16.dp.toPx()
        drawLine(c.tear, Offset(inset, y), Offset(size.width - inset, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        drawCircle(Tz.night, 11.dp.toPx(), Offset(0f, y))
        drawCircle(Tz.night, 11.dp.toPx(), Offset(size.width, y))
    }
}

/** "الحصّالة: 120 من 1,500 · العجلة", the bar and its percent; [matchLine] is the line under it. */
@Composable
internal fun JarBlock(jar: ChildJar, c: TicketColors, matchLine: String?) {
    val saved = poundsText(jar.saved)
    val amounts = if (jar.target != null) stringResource(R.string.kids_jar_of, saved, poundsText(jar.target)) else saved
    val title = stringResource(R.string.kids_jar_title, listOfNotNull(amounts, jar.label).joinToString(" · "))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.weight(1f))
            if (jar.target != null) Text(stringResource(R.string.kids_percent, jar.percent.toString()), style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
        }
        if (jar.target != null) {
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(c.track, size = size, cornerRadius = r)
                val w = size.width * jar.percent / 100f
                if (w > 0f) drawRoundRect(c.ink, Offset(if (rtl) size.width - w else 0f, 0f), Size(w, size.height), r)
            }
        }
        if (matchLine != null) Text(matchLine, style = TzType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = c.ink)
    }
}

/** A 44 dp pill in a ticket: filled with the ink, or outlined in it. */
@Composable
internal fun TicketPill(text: String, c: TicketColors, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier.pressScale(source, PressScale.Chip, release = TzSpring.Snappy).heightIn(min = 48.dp).clip(shape)
            .then(if (filled) Modifier.background(c.ink) else Modifier.border(2.dp, c.ink, shape))
            .clickable(interactionSource = source, indication = null, role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TzType.button.copy(fontSize = 16.sp), color = if (filled) c.paper else c.ink, maxLines = 1)
    }
}

/**
 * A PIN pad (Tick per digit is the caller's; the keys here only report): [typed] dots out of [PIN_MAX_DIGITS], digits, delete,
 * an optional [fingerprint] key, and the check mark button. Digits run left to right even in RTL.
 */
@Composable
internal fun PinPad(
    typed: Int,
    enabled: Boolean,
    okEnabled: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onOk: () -> Unit,
    fingerprint: (() -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(PIN_MAX_DIGITS) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).then(if (i < typed) Modifier.background(Tz.yellow) else Modifier.border(2.dp, Tz.dashed, CircleShape)))
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { d -> PinKey(Tz.raised, Modifier.weight(1f), enabled, onClick = { onDigit(d) }) { Text(d.toString(), style = TzType.button.copy(fontSize = 26.sp), color = Tz.text) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fingerprint != null) {
                        PinKey(Color.Transparent, Modifier.weight(1f), true, onClick = fingerprint) {
                            TzIcon(TzIcons.fingerprint, 26.dp, stringResource(R.string.kids_unlock_fingerprint), tint = Tz.yellow)
                        }
                    } else {
                        Box(Modifier.weight(1f))
                    }
                    PinKey(Tz.raised, Modifier.weight(1f), enabled, onClick = { onDigit(0) }) { Text("0", style = TzType.button.copy(fontSize = 26.sp), color = Tz.text) }
                    PinKey(Color.Transparent, Modifier.weight(1f), enabled, onClick = onBackspace) {
                        TzIcon(TzIcons.delete, 24.dp, stringResource(R.string.add_backspace), tint = Tz.text)
                    }
                }
            }
        }
        PrimaryButton(
            stringResource(R.string.kids_pin_ok),
            { haptics.perform(HapticLevel.Confirm); onOk() },
            Modifier.fillMaxWidth(), enabled = okEnabled && enabled, height = 52.dp,
        )
    }
}

@Composable
internal fun PinKey(
    fill: Color,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    height: Dp = 56.dp,
    corner: Dp = 18.dp,
    outline: Color? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier.pressScale(source, PressScale.Chip, release = TzSpring.Snappy).height(height).clip(shape).background(fill)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A centred line of plain text for a PIN pad's state. */
@Composable
internal fun PinLine(text: String, error: Boolean = false) {
    Text(text, style = TzType.body.copy(fontSize = 14.sp, textAlign = TextAlign.Center), color = if (error) Tz.carnelianText else Tz.text2, modifier = Modifier.fillMaxWidth())
}
