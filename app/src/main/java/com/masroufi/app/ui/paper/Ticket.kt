package com.masroufi.app.ui.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.launch

private const val Slots = 8

/** Concentric circles like banknote engraving, centred in the box it is given. Decoration only. */
@Composable
fun Guilloche(
    color: Color,
    modifier: Modifier = Modifier,
    radii: IntProgression = 30..100 step 14,
    strokeWidth: Dp = 1.2.dp,
) {
    Canvas(modifier) {
        radii.forEach { drawCircle(color, it.dp.toPx(), center, style = Stroke(strokeWidth.toPx())) }
    }
}

/**
 * Today's ticket (Main.dc.html). [label] is the header line, [amountLabel] the line above the number. The upper part
 * is one TalkBack node reading [contentDescription]; [stub] sits under the perforation outside that node so its
 * buttons stay focusable. When [holes] rises, the newest visible hole is punched: it fills centre-out over 120 ms, the
 * ticket recoils 3 dp with Reward and a paper dot falls 24 dp and fades. Reduced motion: the hole fades in over
 * 120 ms, nothing else moves. Holes past 8 show as "+N" (the recoil still plays). A negative [leftPiastres] shows 0.
 */
@Composable
fun Ticket(
    label: String,
    amountLabel: String,
    leftPiastres: Long,
    subLine: String,
    holes: Int,
    caption: String,
    stub: @Composable () -> Unit,
    showQersh: Boolean = true,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val reduce = LocalReduceMotion.current
    val fill = remember { Animatable(1f) }
    val recoil = remember { Animatable(0f) }
    val dot = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(holes) }
    // True in the composition where holes rose, until the effect has reset the animation: the new hole starts empty.
    val punching = holes > last
    LaunchedEffect(holes) {
        if (holes > last) {
            fill.snapTo(0f)
            dot.snapTo(0f)
            last = holes
            launch { fill.animateTo(1f, tween(120)) }
            if (!reduce) {
                launch { dot.animateTo(1f, tween(450)) }
                launch { recoil.snapTo(1f); recoil.animateTo(0f, TzSpring.Reward.spec()) }
            }
        } else {
            last = holes
            fill.snapTo(1f)
            dot.snapTo(1f)
            recoil.snapTo(0f)
        }
    }

    val shape = RoundedCornerShape(TzRadius.hero)
    Box(modifier.graphicsLayer { translationY = recoil.value * 3.dp.toPx() }) {
        Box(Modifier.fillMaxWidth().clip(shape).background(Tz.yellow)) {
            Guilloche(Tz.yellowRule2, Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-70).dp).size(220.dp))
            Column {
                Column(
                    Modifier.fillMaxWidth()
                        .clearAndSetSemantics { this.contentDescription = contentDescription }
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(label, style = TzType.caption.copy(fontWeight = FontWeight.SemiBold), color = Tz.yellowInk2)
                    Text(
                        amountLabel,
                        style = TzType.label.copy(fontSize = 14.sp),
                        color = Tz.yellowInk,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        Odometer(leftPiastres.coerceAtLeast(0), TzType.ticket.copy(color = Tz.night))
                        Text(
                            stringResource(R.string.paper_currency),
                            style = TzType.titleTab.copy(fontSize = 26.sp),
                            color = Tz.night,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                    Text(subLine, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.yellowInk)
                    Holes(holes, punching, { fill.value }, { dot.value }, reduce, Modifier.padding(top = 14.dp))
                    Text(caption, style = TzType.caption, color = Tz.yellowInk2, modifier = Modifier.padding(top = 6.dp))
                }
                Perforation()
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp)) { stub() }
            }
        }
        if (showQersh) {
            Qersh(QershMood.Happy, 76.dp, Modifier.align(Alignment.TopEnd).padding(end = 22.dp).offset(y = (-40).dp))
        }
    }
}

@Composable
private fun Perforation() {
    // 2 dp tall so the spacing around it is the mock's; the notches overflow the canvas and the ticket clip trims them.
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        val y = size.height / 2f
        val inset = 18.dp.toPx()
        drawLine(
            Tz.yellowRule, Offset(inset, y), Offset(size.width - inset, y), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
        drawCircle(Tz.night, 13.dp.toPx(), Offset(0f, y))
        drawCircle(Tz.night, 13.dp.toPx(), Offset(size.width, y))
    }
}

private val FullHolesWidth = (Slots * 26 + (Slots - 1) * 8).dp
private val PlusNWidth = 44.dp // "+N" plus its 8 dp gap

@Composable
private fun Holes(
    holes: Int,
    punching: Boolean,
    fill: () -> Float,
    dot: () -> Float,
    reduce: Boolean,
    modifier: Modifier,
) {
    val punched = holes.coerceAtLeast(0)
    val shown = minOf(punched, Slots)
    val animates = punched <= Slots // past 8 the newest slot was punched long ago
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val reserve = if (punched > Slots) PlusNWidth else 0.dp
        // Shrinks holes and gaps together on narrow tickets.
        val s = ((maxWidth - reserve) / FullHolesWidth).coerceIn(0.3f, 1f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(FullHolesWidth * s).height(26.dp * s)) {
                val d = 26.dp.toPx() * s
                val pitch = 34.dp.toPx() * s
                val outline = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx() * s, 3.dp.toPx() * s)))
                for (i in 0 until Slots) {
                    val c = Offset((if (rtl) Slots - 1 - i else i) * pitch + d / 2f, d / 2f)
                    val newest = animates && i == shown - 1
                    if (i < shown) {
                        val f = if (!newest) 1f else if (punching) 0f else fill()
                        if (f < 1f) drawCircle(Tz.yellowRule, d / 2f - 1.dp.toPx(), c, style = outline)
                        if (reduce) drawCircle(Tz.night, d / 2f, c, alpha = f) else drawCircle(Tz.night, d / 2f * f, c)
                        val t = dot()
                        if (newest && !reduce && !punching && t < 1f) {
                            drawCircle(Tz.night, 4.dp.toPx(), c + Offset(0f, 24.dp.toPx() * t), alpha = 1f - t)
                        }
                    } else {
                        drawCircle(Tz.yellowRule, d / 2f - 1.dp.toPx(), c, style = outline)
                    }
                }
            }
            if (punched > Slots) {
                Text("+${punched - Slots}", style = TzType.rowNumber, color = Tz.night, maxLines = 1, softWrap = false)
            }
        }
    }
}
