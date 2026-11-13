package com.masroufi.app.ui.paper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType

/** The torn piece under a ticket (CloseDay.dc.html): yellow, dashed top edge, tilted 2-3 degrees. */
@Composable
fun Stub(tiltDegrees: Float = 2.5f, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
    Box(
        modifier.graphicsLayer { rotationZ = tiltDegrees }
            .clip(shape)
            .background(Tz.yellow)
            .drawBehind {
                drawLine(
                    Tz.yellowRule, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) { content() }
}

/**
 * Envelope (Envelopes.dc.html): near-square top corners, rounded bottom, V flap line in the envelope colour.
 * [sealed] swaps the number for the "paid" word and fills the bar.
 */
@Composable
fun EnvelopeCard(
    name: String,
    color: Color,
    leftPiastres: Long,
    bar: Float,
    line: String?,
    sealed: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
    Box(modifier.clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).semantics(mergeDescendants = true) {}) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val flap = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, 28.dp.toPx())
                lineTo(size.width, 0f)
            }
            drawPath(flap, color, style = Stroke(2.dp.toPx()))
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 36.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.text)
            if (sealed) {
                Text(stringResource(R.string.paper_sealed), style = TzType.card, color = color)
            } else {
                Odometer(leftPiastres, TzType.card.copy(color = color))
            }
            if (line != null) Text(line, style = TzType.caption, color = Tz.text2)
            TzProgressBar(listOf((if (sealed) 1f else bar) to color), Modifier.padding(top = 4.dp), height = 6.dp)
        }
    }
}

/** Double-ring stamp (Album.dc.html). [color] null = locked: dashed ring and label. */
@Composable
fun Stamp(label: String, color: Color?, modifier: Modifier = Modifier, tilt: Float = -8f) {
    Box(modifier.size(96.dp).graphicsLayer { rotationZ = tilt }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            if (color != null) {
                // CSS "3px double": 1 dp line, 1 dp gap, 1 dp line.
                drawCircle(color, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawCircle(color, r - 2.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            } else {
                drawCircle(
                    Tz.dashed, r - 1.dp.toPx(),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
        }
        Text(
            label,
            style = TzType.cardHeading.copy(fontSize = 17.sp, lineHeight = 1.1.em, textAlign = TextAlign.Center),
            color = color ?: Tz.text3,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** The reward card (Proverb.dc.html): 300 x 400, solid [color], tilted 2 degrees. */
@Composable
fun ProverbCard(
    setName: String,
    number: Int,
    total: Int,
    proverb: String,
    line: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val ink = if (color == Tz.turquoise) Tz.turquoiseInk else Tz.night
    Box(
        modifier.size(width = 300.dp, height = 400.dp).graphicsLayer { rotationZ = 2f }
            .clip(RoundedCornerShape(TzRadius.hero))
            .background(color)
            .semantics(mergeDescendants = true) {},
    ) {
        Guilloche(
            ink.copy(alpha = 0.25f),
            Modifier.align(Alignment.BottomEnd).offset(x = 90.dp, y = 120.dp).size(300.dp),
            radii = 50..140 step 18,
            strokeWidth = 1.4.dp,
        )
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    setName,
                    style = TzType.caption.copy(fontWeight = FontWeight.Bold),
                    color = color,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(ink).padding(horizontal = 12.dp, vertical = 5.dp),
                )
                Text("$number / $total", style = TzType.cardHeading.copy(fontSize = 18.sp), color = ink)
            }
            Text(proverb, style = TzType.hero.copy(fontSize = 44.sp, lineHeight = 1.25.em), color = ink)
            Text(line, style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = ink)
        }
    }
}

/** Qersh's speech bubble: Surface, the corner nearest him squared to 6 dp, the others 18 dp. */
@Composable
fun QershBubble(
    text: String,
    qershSize: Dp = 64.dp,
    modifier: Modifier = Modifier,
    mood: QershMood = QershMood.Happy,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Qersh(mood, qershSize)
        val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp)
        Text(
            text,
            style = TzType.body,
            color = Tz.text,
            modifier = Modifier.weight(1f, fill = false).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
