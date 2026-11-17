package com.masroufi.app.ui.paper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.Tz

// Geometry copied from the inline SVGs of EmptyStates.dc.html (150 x 110 grid; the asleep one is 150 x 100).
// Flat, 3 dp Night outline, the Qersh palette, no shadow or gradient (13-ICON.md).
private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

private fun stroke(width: Float = 3f, dash: FloatArray? = null) =
    Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash?.let { PathEffect.dashPathEffect(it) })

private val SheetLines = p("M74 40h54M74 54h54M74 68h30")
private val SheetFin = p("M36 28 Q42 10 54 18 Q48 22 49 30 Z")
private val Jar = p("M82 20h44v12l10 14v44a10 10 0 0 1-10 10H82a10 10 0 0 1-10-10V46l10-14z")
private val JarLid = p("M82 32h44")
private val JarSlot = p("M96 76h16")
private val JarFin = p("M28 38 Q34 20 46 28 Q40 32 41 40 Z")
private val JarMouth = p("M28 76 Q37 82 46 76")
private val SleepFin = p("M62 26 Q68 8 80 16 Q74 20 75 28 Z")
private val SleepEyes = p("M54 54 q5 4 10 0M72 54 q5 4 10 0")
private val SleepMouth = p("M60 68 Q68 73 76 68")
private val SleepZ = p("M110 34h12l-12 14h12M128 18h9l-9 10h9")

private fun DrawScope.body(c: Offset, r: Float) {
    drawCircle(Tz.yellow, r, c)
    drawCircle(Tz.night, r, c, style = stroke())
}

private fun DrawScope.fin(path: Path) {
    drawPath(path, Tz.turquoise)
    drawPath(path, Tz.night, style = stroke())
}

/** The roll is blank: a paper sheet with dashed lines and a surprised Qersh beside it. */
@Composable
fun BlankSheet(modifier: Modifier = Modifier, width: Dp = 180.dp) {
    Canvas(modifier.size(width, width * 110 / 150)) {
        scale(size.width / 150f, pivot = Offset.Zero) {
            drawRoundRect(Tz.text, Offset(62f, 22f), Size(78f, 66f), CornerRadius(8f))
            drawRoundRect(Tz.night, Offset(62f, 22f), Size(78f, 66f), CornerRadius(8f), style = stroke())
            drawPath(SheetLines, Color(0xFFC9CEEC), style = stroke(dash = floatArrayOf(6f, 6f)))
            fin(SheetFin)
            body(Offset(42f, 58f), 30f)
            drawCircle(Tz.night, 3.8f, Offset(33f, 54f))
            drawCircle(Tz.night, 3.8f, Offset(51f, 54f))
            drawCircle(Tz.night, 4.5f, Offset(42f, 68f))
        }
    }
}

/** The jar waits for its first coin: a glass jar outline with Qersh peeking in. */
@Composable
fun EmptyJar(modifier: Modifier = Modifier, width: Dp = 180.dp) {
    Canvas(modifier.size(width, width * 110 / 150)) {
        scale(size.width / 150f, pivot = Offset.Zero) {
            drawPath(Jar, Tz.night)
            drawPath(Jar, Tz.turquoise, style = stroke())
            drawPath(JarLid, Tz.turquoise, style = stroke())
            drawPath(JarSlot, Tz.line, style = stroke(dash = floatArrayOf(3f, 5f)))
            fin(JarFin)
            body(Offset(34f, 66f), 28f)
            drawCircle(Tz.night, 3.6f, Offset(30f, 62f))
            drawCircle(Tz.night, 3.6f, Offset(46f, 62f))
            drawPath(JarMouth, Tz.night, style = stroke())
        }
    }
}

/** Nothing owed either way: Qersh asleep, with his z. */
@Composable
fun QershAsleep(modifier: Modifier = Modifier, width: Dp = 180.dp) {
    Canvas(modifier.size(width, width * 100 / 150)) {
        scale(size.width / 150f, pivot = Offset.Zero) {
            fin(SleepFin)
            body(Offset(68f, 58f), 32f)
            drawPath(SleepEyes, Tz.night, style = stroke())
            drawPath(SleepMouth, Tz.night, style = stroke())
            drawPath(SleepZ, Tz.text2, style = stroke(2.6f))
        }
    }
}
