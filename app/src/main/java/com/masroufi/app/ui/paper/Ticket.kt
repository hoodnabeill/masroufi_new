package com.masroufi.app.ui.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
 * Today's ticket (Main.dc.html). The upper part is one TalkBack node reading [contentDescription]; [stub] sits under
 * the perforation outside that node so its buttons stay focusable. When [holes] rises, the newest visible hole is
 * punched: it fills centre-out over 120 ms, the ticket recoils 3 dp with Reward and a paper dot falls 24 dp and
 * fades. Reduced motion: the hole fades in over 120 ms, nothing else moves. Holes past 8 show as "+N".
 */
@Composable
fun Ticket(
    label: String,
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
    LaunchedEffect(holes) {
        val rose = holes > last
        last = holes
        if (!rose) return@LaunchedEffect
        fill.snapTo(0f)
        launch { fill.animateTo(1f, tween(120)) }
        if (!reduce) {
            dot.snapTo(0f)
            launch { dot.animateTo(1f, tween(450)) }
            launch { recoil.snapTo(1f); recoil.animateTo(0f, TzSpring.Reward.spec()) }
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
                        .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(label, style = TzType.label.copy(fontSize = 14.sp), color = Tz.yellowInk)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        Odometer(leftPiastres, TzType.ticket.copy(color = Tz.night))
                        Text(
                            stringResource(R.string.paper_currency),
                            style = TzType.titleTab.copy(fontSize = 26.sp),
                            color = Tz.night,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                    Text(subLine, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.yellowInk)
                    Holes(holes, fill.value, dot.value, reduce, Modifier.padding(top = 10.dp))
                    Text(caption, style = TzType.caption, color = Tz.yellowInk2, modifier = Modifier.padding(top = 2.dp))
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
    Canvas(Modifier.fillMaxWidth().height(26.dp)) {
        val y = size.height / 2f
        val inset = 18.dp.toPx()
        drawLine(
            Tz.yellowRule, Offset(inset, y), Offset(size.width - inset, y), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
        // Notches: night half-circles centred on each edge (the mock's -31 dp spans, clipped by the ticket).
        drawCircle(Tz.night, 13.dp.toPx(), Offset(0f, y))
        drawCircle(Tz.night, 13.dp.toPx(), Offset(size.width, y))
    }
}

@Composable
private fun Holes(holes: Int, fill: Float, dot: Float, reduce: Boolean, modifier: Modifier) {
    val punched = holes.coerceAtLeast(0)
    val shown = minOf(punched, Slots)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width((Slots * 26 + (Slots - 1) * 8).dp).height(26.dp)) {
            val d = 26.dp.toPx()
            val pitch = 34.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            for (i in 0 until Slots) {
                val cx = (if (rtl) Slots - 1 - i else i) * pitch + d / 2f
                val c = Offset(cx, d / 2f)
                val newest = i == shown - 1
                if (i < shown) {
                    val f = if (newest) fill else 1f
                    if (f < 1f) drawCircle(Tz.yellowRule, d / 2f - 1.dp.toPx(), c, style = Stroke(2.dp.toPx(), pathEffect = dash))
                    if (reduce) drawCircle(Tz.night, d / 2f, c, alpha = f) else drawCircle(Tz.night, d / 2f * f, c)
                    if (newest && !reduce && dot < 1f) {
                        drawCircle(Tz.night, 4.dp.toPx(), c + Offset(0f, 24.dp.toPx() * dot), alpha = 1f - dot)
                    }
                } else {
                    drawCircle(Tz.yellowRule, d / 2f - 1.dp.toPx(), c, style = Stroke(2.dp.toPx(), pathEffect = dash))
                }
            }
        }
        if (punched > Slots) Text("+${punched - Slots}", style = TzType.rowNumber, color = Tz.night)
    }
}
