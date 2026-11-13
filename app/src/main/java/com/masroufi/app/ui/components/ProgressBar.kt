package com.masroufi.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.theme.Tz

/**
 * Round-ended bar on a Line track. Each part is a fraction (0..1) of the full width; widths spring with Move and are
 * read in the draw phase, so frames do not recompose. Parts summing past 1 are scaled down to fit. 3 dp gaps sit
 * only between non-empty parts.
 */
@Composable
fun TzProgressBar(
    parts: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val fractions = parts.map { animateFloatAsState(it.first.coerceIn(0f, 1f), motionSpec(TzSpring.Move), label = "progressPart") }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val total = parts.sumOf { it.first.coerceIn(0f, 1f).toDouble() }.toFloat().coerceIn(0f, 1f)
    Canvas(modifier.fillMaxWidth().height(height).progressSemantics(total)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(Tz.line, size = size, cornerRadius = r)
        val gap = 3.dp.toPx()
        val values = fractions.map { it.value }
        val sum = values.sum()
        val scale = if (sum > 1f) 1f / sum else 1f
        val nonEmpty = values.count { it > 0.001f }
        val usable = size.width - gap * (nonEmpty - 1).coerceAtLeast(0)
        var x = 0f
        parts.forEachIndexed { i, (_, color) ->
            if (values[i] > 0.001f) {
                val w = values[i] * scale * usable
                val left = if (rtl) size.width - x - w else x
                drawRoundRect(color, Offset(left, 0f), Size(w, size.height), CornerRadius(minOf(r.x, w / 2f)))
                x += w + gap
            }
        }
    }
}
