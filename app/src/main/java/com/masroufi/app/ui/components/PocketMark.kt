package com.masroufi.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

private val Coin = Color(0xFFC8F169)
private val CoinRing = Color(0xFF9FCC45)
private val Pocket = Color(0xFFA99BFF)
private val Hem = Color(0xFF9483F5)
private val Stitch = Color(0xFF7463E3)

/**
 * "الجيب", the app's mark (README §6), drawn on a 100-unit grid: a lime coin dropping into a violet pocket.
 * [coinDrop] lifts the coin by that many grid units (0 = resting in the pocket), for the onboarding coin-drop.
 */
@Composable
fun PocketMark(modifier: Modifier = Modifier, coinDrop: () -> Float = { 0f }) {
    val pocket = remember { PathParser().parsePathString("M23 45H77V65Q77 71.5 71 74.5L54 82.5Q50 84.5 46 82.5L29 74.5Q23 71.5 23 65Z").toPath() }
    Canvas(modifier) {
        val unit = size.minDimension / 100f
        scale(unit, unit, pivot = Offset.Zero) {
            translate(left = (size.width / unit - 100f) / 2f) {
                val lift = coinDrop()
                // The coin is hidden where it has sunk below the pocket's rim, so it reads as going in.
                clipRect(bottom = 48f) {
                    drawCircle(Coin, 17f, Offset(50f, 38f - lift))
                    drawCircle(CoinRing, 11.5f, Offset(50f, 38f - lift), style = Stroke(2f))
                }
                drawPath(pocket, Pocket)
                drawRect(Hem, Offset(23f, 45f), androidx.compose.ui.geometry.Size(54f, 3.5f))
                var x = 29f
                while (x < 71f) {
                    drawLine(Stitch, Offset(x, 52f), Offset(x + 3.5f, 52f), strokeWidth = 1.4f)
                    x += 6f
                }
            }
        }
    }
}
