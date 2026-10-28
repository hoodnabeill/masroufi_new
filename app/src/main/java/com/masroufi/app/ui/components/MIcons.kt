package com.masroufi.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Masroufi's own icon set. Every glyph sits on a 24-unit grid and is duotone: a soft body at 30% under crisp
 * 1.7-unit rounded strokes. Icons are single-colour vectors, so `Icon(tint = …)` colours both layers.
 */
object MIcons {
    val Home by lazy { duo("home") { soft { houseBody() }; line { houseBody(); roof() }; line { rect(10f, 14.5f, 14f, 20.5f, 1.3f) } } }
    val Activity by lazy {
        duo("activity") {
            soft { chartArea() }
            line { rect(3.5f, 3.5f, 20.5f, 20.5f, 5f) }
            line { chartLine() }
        }
    }
    val Budgets by lazy {
        duo("budgets") {
            soft { circle(12f, 12f, 8.5f) }
            line { circle(12f, 12f, 8.5f) }
            solid { wedge() }
        }
    }
    val Goals by lazy {
        duo("goals") {
            soft { circle(12f, 12f, 8.5f) }
            line { circle(12f, 12f, 8.5f); circle(12f, 12f, 4.6f) }
            solid { circle(12f, 12f, 1.7f) }
        }
    }
    val More by lazy {
        duo("more") {
            soft { rect(4f, 4f, 10.6f, 10.6f, 2.3f); rect(4f, 13.4f, 10.6f, 20f, 2.3f); rect(13.4f, 13.4f, 20f, 20f, 2.3f) }
            line { rect(4f, 4f, 10.6f, 10.6f, 2.3f); rect(4f, 13.4f, 10.6f, 20f, 2.3f); rect(13.4f, 13.4f, 20f, 20f, 2.3f) }
            solid { circle(16.7f, 7.3f, 3.3f) }
        }
    }

    val Expense by lazy { duo("expense") { soft { circle(12f, 12f, 9f) }; line { moveTo(8.5f, 15.5f); lineTo(15.5f, 8.5f); moveTo(9.5f, 8.5f); lineTo(15.5f, 8.5f); lineTo(15.5f, 14.5f) } } }
    val Income by lazy { duo("income") { soft { circle(12f, 12f, 9f) }; line { moveTo(15.5f, 8.5f); lineTo(8.5f, 15.5f); moveTo(8.5f, 9.5f); lineTo(8.5f, 15.5f); lineTo(14.5f, 15.5f) } } }
    val Transfer by lazy {
        duo("transfer") {
            soft { circle(12f, 12f, 9f) }
            line {
                moveTo(7f, 9.5f); lineTo(17f, 9.5f); moveTo(14f, 6.5f); lineTo(17f, 9.5f); lineTo(14f, 12.5f)
                moveTo(17f, 14.5f); lineTo(7f, 14.5f); moveTo(10f, 11.5f); lineTo(7f, 14.5f); lineTo(10f, 17.5f)
            }
        }
    }
    val Plus by lazy { duo("plus") { line { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } } }

    val Wallet by lazy {
        duo("wallet") {
            soft { rect(3f, 6f, 21f, 20f, 3f) }
            line {
                rect(3f, 6f, 21f, 20f, 3f)
                moveTo(4f, 6.3f); lineTo(15.4f, 3.3f); curveTo(16.4f, 3.05f, 17.5f, 3.8f, 17.5f, 4.9f); lineTo(17.5f, 6f)
                moveTo(21f, 10.5f); lineTo(16.5f, 10.5f); curveTo(15.1f, 10.5f, 14f, 11.6f, 14f, 13f); curveTo(14f, 14.4f, 15.1f, 15.5f, 16.5f, 15.5f); lineTo(21f, 15.5f)
            }
            solid { circle(16.7f, 13f, 0.95f) }
        }
    }
    val Cash by lazy {
        duo("cash") {
            soft { rect(2.5f, 6f, 21.5f, 18f, 2.5f) }
            line { rect(2.5f, 6f, 21.5f, 18f, 2.5f); circle(12f, 12f, 2.7f) }
            solid { circle(6.3f, 12f, 0.9f); circle(17.7f, 12f, 0.9f) }
        }
    }
    val Bank by lazy {
        duo("bank") {
            soft { moveTo(3f, 9.5f); lineTo(12f, 4f); lineTo(21f, 9.5f); close() }
            line {
                moveTo(3f, 9.5f); lineTo(12f, 4f); lineTo(21f, 9.5f); close()
                moveTo(6f, 12f); lineTo(6f, 17f); moveTo(10f, 12f); lineTo(10f, 17f); moveTo(14f, 12f); lineTo(14f, 17f); moveTo(18f, 12f); lineTo(18f, 17f)
                moveTo(3.5f, 20f); lineTo(20.5f, 20f)
            }
        }
    }
    val Phone by lazy { duo("phone") { soft { rect(6.5f, 2.5f, 17.5f, 21.5f, 3f) }; line { rect(6.5f, 2.5f, 17.5f, 21.5f, 3f); moveTo(10.5f, 18.5f); lineTo(13.5f, 18.5f) } } }

    val Food by lazy {
        duo("food") {
            soft { circle(12f, 12f, 9.5f) }
            line {
                moveTo(6.5f, 4f); lineTo(6.5f, 9f); curveTo(6.5f, 10.4f, 7.6f, 11.5f, 9f, 11.5f); curveTo(10.4f, 11.5f, 11.5f, 10.4f, 11.5f, 9f); lineTo(11.5f, 4f)
                moveTo(9f, 4f); lineTo(9f, 20f)
                moveTo(17f, 20f); lineTo(17f, 4f); curveTo(15.2f, 5.2f, 14.6f, 9.5f, 17f, 12f)
            }
        }
    }
    val Cart by lazy {
        duo("cart") {
            soft { rect(6.6f, 7f, 20f, 14.5f, 2f) }
            line { moveTo(3f, 4f); lineTo(5.2f, 4f); lineTo(7.4f, 15.5f); lineTo(18f, 15.5f); moveTo(6.4f, 7f); lineTo(20f, 7f); lineTo(18.6f, 13f) }
            solid { circle(9f, 19f, 1.5f); circle(16.5f, 19f, 1.5f) }
        }
    }
    val Car by lazy {
        duo("car") {
            soft { rect(3.5f, 10.5f, 20.5f, 17f, 3f) }
            line {
                rect(3.5f, 10.5f, 20.5f, 17f, 3f)
                moveTo(5.5f, 10.5f); lineTo(7.2f, 6.6f); curveTo(7.5f, 5.9f, 8.1f, 5.5f, 8.9f, 5.5f); lineTo(15.1f, 5.5f); curveTo(15.9f, 5.5f, 16.5f, 5.9f, 16.8f, 6.6f); lineTo(18.5f, 10.5f)
                moveTo(7.5f, 17f); lineTo(7.5f, 19f); moveTo(16.5f, 17f); lineTo(16.5f, 19f)
            }
            solid { circle(7.5f, 13.7f, 1.1f); circle(16.5f, 13.7f, 1.1f) }
        }
    }
    val Bolt by lazy {
        duo("bolt") {
            soft { bolt() }
            line { bolt() }
        }
    }
    val Health by lazy { duo("health") { soft { rect(3.5f, 3.5f, 20.5f, 20.5f, 6f) }; line { rect(3.5f, 3.5f, 20.5f, 20.5f, 6f); moveTo(12f, 8f); lineTo(12f, 16f); moveTo(8f, 12f); lineTo(16f, 12f) } } }
    val School by lazy {
        duo("school") {
            soft { moveTo(12f, 4f); lineTo(22f, 9f); lineTo(12f, 14f); lineTo(2f, 9f); close() }
            line {
                moveTo(12f, 4f); lineTo(22f, 9f); lineTo(12f, 14f); lineTo(2f, 9f); close()
                moveTo(6f, 11f); lineTo(6f, 15.5f); curveTo(6f, 17.2f, 8.7f, 18.5f, 12f, 18.5f); curveTo(15.3f, 18.5f, 18f, 17.2f, 18f, 15.5f); lineTo(18f, 11f)
                moveTo(22f, 9f); lineTo(22f, 14f)
            }
        }
    }
    val Bag by lazy {
        duo("bag") {
            soft { rect(4.5f, 8f, 19.5f, 21f, 2.5f) }
            line {
                rect(4.5f, 8f, 19.5f, 21f, 2.5f)
                moveTo(8.5f, 10.5f); lineTo(8.5f, 6.5f); curveTo(8.5f, 4.6f, 10.1f, 3f, 12f, 3f); curveTo(13.9f, 3f, 15.5f, 4.6f, 15.5f, 6.5f); lineTo(15.5f, 10.5f)
            }
        }
    }
    val Movie by lazy {
        duo("movie") {
            soft { rect(3f, 5f, 21f, 19f, 3f) }
            line { rect(3f, 5f, 21f, 19f, 3f) }
            solid { moveTo(10f, 9f); lineTo(10f, 15f); lineTo(15.2f, 12f); close() }
        }
    }
    val Family by lazy {
        duo("family") {
            soft { circle(8.5f, 7.5f, 3f); circle(16.5f, 9f, 2.4f) }
            line {
                circle(8.5f, 7.5f, 3f); circle(16.5f, 9f, 2.4f)
                moveTo(3.5f, 19.5f); curveTo(3.5f, 16.2f, 5.7f, 13.5f, 8.5f, 13.5f); curveTo(11.3f, 13.5f, 13.5f, 16.2f, 13.5f, 19.5f)
                moveTo(15f, 19.5f); curveTo(15f, 16.8f, 15.9f, 14.5f, 17.5f, 14.5f); curveTo(19.1f, 14.5f, 20.5f, 16.3f, 20.5f, 19f)
            }
        }
    }
    val Gift by lazy {
        duo("gift") {
            soft { rect(4f, 10.5f, 20f, 20.5f, 2f) }
            line {
                rect(4f, 10.5f, 20f, 20.5f, 2f)
                rect(3f, 7f, 21f, 10.5f, 1.5f)
                moveTo(12f, 7f); lineTo(12f, 20.5f)
                moveTo(12f, 7f); curveTo(10.5f, 4f, 7f, 4f, 7.5f, 6f); curveTo(7.8f, 7f, 10f, 7f, 12f, 7f)
                curveTo(14f, 7f, 16.2f, 7f, 16.5f, 6f); curveTo(17f, 4f, 13.5f, 4f, 12f, 7f)
            }
        }
    }
    val Heart by lazy { duo("heart") { soft { heart() }; line { heart() } } }
    val Work by lazy {
        duo("work") {
            soft { rect(3f, 7.5f, 21f, 19.5f, 2.5f) }
            line {
                rect(3f, 7.5f, 21f, 19.5f, 2.5f)
                moveTo(9f, 7.5f); lineTo(9f, 6f); curveTo(9f, 5.2f, 9.7f, 4.5f, 10.5f, 4.5f); lineTo(13.5f, 4.5f); curveTo(14.3f, 4.5f, 15f, 5.2f, 15f, 6f); lineTo(15f, 7.5f)
                moveTo(3f, 12.5f); lineTo(21f, 12.5f)
            }
        }
    }
    val Trending by lazy {
        duo("trending") {
            soft { moveTo(3f, 17f); lineTo(9f, 10.5f); lineTo(13f, 13.5f); lineTo(20.5f, 6f); lineTo(20.5f, 20f); lineTo(3f, 20f); close() }
            line { moveTo(3f, 17f); lineTo(9f, 10.5f); lineTo(13f, 13.5f); lineTo(20.5f, 6f); moveTo(15.5f, 6f); lineTo(20.5f, 6f); lineTo(20.5f, 11f) }
        }
    }
    val Cafe by lazy {
        duo("cafe") {
            soft { cup() }
            line {
                cup()
                moveTo(17f, 10.5f); lineTo(18.5f, 10.5f); curveTo(19.9f, 10.5f, 21f, 11.6f, 21f, 13f); curveTo(21f, 14.4f, 19.9f, 15.5f, 18.5f, 15.5f); lineTo(16.6f, 15.5f)
                moveTo(9f, 3.5f); lineTo(9f, 6f); moveTo(13f, 3.5f); lineTo(13f, 6f)
            }
        }
    }
    val Shirt by lazy { duo("shirt") { soft { shirt() }; line { shirt() } } }
    val Plane by lazy { duo("plane") { soft { plane() }; line { plane() } } }
    val Paw by lazy {
        duo("paw") {
            soft {
                moveTo(12f, 11f); curveTo(9f, 11f, 6.5f, 14.5f, 6.5f, 17f); curveTo(6.5f, 19f, 8f, 20f, 9.8f, 20f)
                curveTo(10.8f, 20f, 11.3f, 19.5f, 12f, 19.5f); curveTo(12.7f, 19.5f, 13.2f, 20f, 14.2f, 20f)
                curveTo(16f, 20f, 17.5f, 19f, 17.5f, 17f); curveTo(17.5f, 14.5f, 15f, 11f, 12f, 11f); close()
            }
            solid { circle(5f, 10.5f, 1.8f); circle(9f, 6.5f, 1.9f); circle(15f, 6.5f, 1.9f); circle(19f, 10.5f, 1.8f) }
        }
    }
    val Games by lazy {
        duo("games") {
            soft { controller() }
            line { controller(); moveTo(7.5f, 10.5f); lineTo(7.5f, 13.5f); moveTo(6f, 12f); lineTo(9f, 12f) }
            solid { circle(15.5f, 11f, 1f); circle(17.5f, 13f, 1f) }
        }
    }
    val Savings by lazy {
        duo("savings") {
            soft { circle(12f, 12f, 9f) }
            line {
                circle(12f, 12f, 9f)
                moveTo(12f, 6.5f); lineTo(12f, 17.5f)
                moveTo(14.6f, 9.4f); curveTo(14.1f, 8.5f, 13.2f, 8.1f, 12f, 8.1f); curveTo(10.6f, 8.1f, 9.5f, 8.9f, 9.5f, 10f)
                curveTo(9.5f, 12.6f, 14.5f, 11.4f, 14.5f, 14f); curveTo(14.5f, 15.1f, 13.4f, 15.9f, 12f, 15.9f); curveTo(10.9f, 15.9f, 10f, 15.5f, 9.4f, 14.6f)
            }
        }
    }
    val Shapes by lazy {
        duo("shapes") {
            soft { circle(7.5f, 7.5f, 3.6f); rect(13f, 13f, 20f, 20f, 3.5f) }
            line { circle(7.5f, 7.5f, 3.6f); rect(13f, 4f, 20f, 11f, 2f); moveTo(7.5f, 13f); lineTo(11f, 20f); lineTo(4f, 20f); close(); rect(13f, 13f, 20f, 20f, 3.5f) }
        }
    }
}

private val Ink = SolidColor(Color.Black)
private const val STROKE = 1.7f

private class Duo(private val builder: ImageVector.Builder) {
    fun soft(block: PathBuilder.() -> Unit) {
        builder.path(fill = Ink, fillAlpha = 0.3f, pathBuilder = block)
    }

    fun solid(block: PathBuilder.() -> Unit) {
        builder.path(fill = Ink, pathBuilder = block)
    }

    fun line(block: PathBuilder.() -> Unit) {
        builder.path(stroke = Ink, strokeLineWidth = STROKE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
    }
}

private fun duo(name: String, block: Duo.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .also { Duo(it).block() }
        .build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}

private fun PathBuilder.rect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
    moveTo(l + radius, t)
    lineTo(r - radius, t)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = r, y1 = t + radius)
    lineTo(r, b - radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = r - radius, y1 = b)
    lineTo(l + radius, b)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = l, y1 = b - radius)
    lineTo(l, t + radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = l + radius, y1 = t)
    close()
}

private fun PathBuilder.houseBody() = rect(5f, 10.5f, 19f, 20.5f, 2.5f)

private fun PathBuilder.roof() {
    moveTo(3f, 11.5f); lineTo(12f, 3.8f); lineTo(21f, 11.5f)
}

private fun PathBuilder.chartLine() {
    moveTo(7f, 15.5f); lineTo(10.5f, 11.5f); lineTo(13.5f, 14f); lineTo(17.5f, 8.5f)
}

private fun PathBuilder.chartArea() {
    moveTo(7f, 15.5f); lineTo(10.5f, 11.5f); lineTo(13.5f, 14f); lineTo(17.5f, 8.5f); lineTo(17.5f, 17f); lineTo(7f, 17f); close()
}

private fun PathBuilder.wedge() {
    moveTo(12f, 12f); lineTo(12f, 3.5f)
    arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20.5f, y1 = 12f)
    close()
}

private fun PathBuilder.bolt() {
    moveTo(13f, 2.5f); lineTo(5.5f, 13.5f); lineTo(11.5f, 13.5f); lineTo(10.5f, 21.5f); lineTo(18.5f, 10f); lineTo(12.5f, 10f); close()
}

private fun PathBuilder.heart() {
    moveTo(12f, 20f); curveTo(12f, 20f, 3.5f, 15f, 3.5f, 9f); curveTo(3.5f, 6.5f, 5.5f, 4.5f, 8f, 4.5f)
    curveTo(9.7f, 4.5f, 11.2f, 5.4f, 12f, 6.8f); curveTo(12.8f, 5.4f, 14.3f, 4.5f, 16f, 4.5f)
    curveTo(18.5f, 4.5f, 20.5f, 6.5f, 20.5f, 9f); curveTo(20.5f, 15f, 12f, 20f, 12f, 20f); close()
}

private fun PathBuilder.cup() {
    moveTo(5f, 9f); lineTo(17f, 9f); lineTo(17f, 14f); curveTo(17f, 17.3f, 14.3f, 20f, 11f, 20f); curveTo(7.7f, 20f, 5f, 17.3f, 5f, 14f); close()
}

private fun PathBuilder.shirt() {
    moveTo(8.5f, 3.5f); lineTo(3f, 6.5f); lineTo(5f, 11f); lineTo(7f, 10f); lineTo(7f, 20.5f); lineTo(17f, 20.5f); lineTo(17f, 10f)
    lineTo(19f, 11f); lineTo(21f, 6.5f); lineTo(15.5f, 3.5f); curveTo(15f, 5f, 13.7f, 6f, 12f, 6f); curveTo(10.3f, 6f, 9f, 5f, 8.5f, 3.5f); close()
}

private fun PathBuilder.plane() {
    moveTo(21f, 15.5f); lineTo(21f, 13.7f); lineTo(13.5f, 9f); lineTo(13.5f, 4.5f); curveTo(13.5f, 3.7f, 12.8f, 3f, 12f, 3f)
    curveTo(11.2f, 3f, 10.5f, 3.7f, 10.5f, 4.5f); lineTo(10.5f, 9f); lineTo(3f, 13.7f); lineTo(3f, 15.5f); lineTo(10.5f, 13.2f)
    lineTo(10.5f, 18f); lineTo(8.5f, 19.5f); lineTo(8.5f, 21f); lineTo(12f, 20f); lineTo(15.5f, 21f); lineTo(15.5f, 19.5f)
    lineTo(13.5f, 18f); lineTo(13.5f, 13.2f); close()
}

private fun PathBuilder.controller() {
    moveTo(7f, 7.5f); lineTo(17f, 7.5f); curveTo(19.5f, 7.5f, 21.5f, 10f, 21.5f, 14f); curveTo(21.5f, 17f, 20.5f, 18.5f, 19f, 18.5f)
    curveTo(17.5f, 18.5f, 16.5f, 16.5f, 15f, 15.5f); lineTo(9f, 15.5f); curveTo(7.5f, 16.5f, 6.5f, 18.5f, 5f, 18.5f)
    curveTo(3.5f, 18.5f, 2.5f, 17f, 2.5f, 14f); curveTo(2.5f, 10f, 4.5f, 7.5f, 7f, 7.5f); close()
}

private fun gridPath(block: PathBuilder.() -> Unit): Path =
    androidx.compose.ui.graphics.vector.PathParser().addPathNodes(PathBuilder().apply(block).nodes).toPath()

/**
 * Tab glyphs that act out their meaning when selected, driven by a 0→1 [progress] that may overshoot (bouncy
 * springs). The house's roof hops and its door lights, the chart's point runs along its line, the budget slice
 * lifts out of the pie, an arrow lands in the goal, and the grid spins one tile into a dot. Progress is read only
 * while drawing, so the animation never recomposes.
 */
@Composable
fun TabGlyph(kind: Int, progress: () -> Float, color: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .size(26.dp)
            .drawWithCache {
                val unit = size.minDimension / 24f
                val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val paths = glyphPaths(kind)
                val measure = PathMeasure().apply { setPath(paths.getOrNull(1), false) }
                onDrawBehind {
                    val p = progress()
                    val a = p.coerceIn(0f, 1f)
                    scale(unit, pivot = Offset.Zero) { drawGlyph(kind, paths, measure, p, a, color, stroke) }
                }
            },
    )
}

private fun glyphPaths(kind: Int): List<Path> = when (kind) {
    0 -> listOf(gridPath { houseBody() }, gridPath { roof() }, gridPath { rect(10f, 14.5f, 14f, 20.5f, 1.3f) })
    1 -> listOf(gridPath { rect(3.5f, 3.5f, 20.5f, 20.5f, 5f) }, gridPath { chartLine() }, gridPath { chartArea() })
    2 -> listOf(gridPath { circle(12f, 12f, 8.5f) }, gridPath { wedge() })
    3 -> listOf(
        gridPath { circle(12f, 12f, 8.5f) },
        gridPath { moveTo(12f, 12f); lineTo(19.5f, 4.5f); moveTo(16.6f, 4.2f); lineTo(19.5f, 4.5f); lineTo(19.8f, 7.4f) },
        gridPath { circle(12f, 12f, 4.6f) },
    )
    else -> emptyList()
}

private fun DrawScope.drawGlyph(kind: Int, paths: List<Path>, measure: PathMeasure, p: Float, a: Float, color: Color, stroke: Stroke) {
    when (kind) {
        0 -> {
            val (body, roof, door) = paths
            drawPath(body, color.copy(alpha = 0.28f * a))
            drawPath(body, color, style = stroke)
            translate(top = -1.8f * sin(PI.toFloat() * a)) { drawPath(roof, color, style = stroke) }
            drawPath(door, color.copy(alpha = 1f - a), style = stroke)
            withTransform({ scale(1f, p.coerceAtLeast(0f), pivot = Offset(12f, 20.5f)) }) { drawPath(door, color) }
        }
        1 -> {
            val (frame, line, area) = paths
            drawPath(frame, color.copy(alpha = 0.2f * a))
            drawPath(frame, color, style = stroke)
            drawPath(area, color.copy(alpha = 0.32f * a))
            drawPath(line, color, style = stroke)
            if (a > 0.01f) {
                val point = measure.getPosition(measure.length * a)
                drawCircle(color.copy(alpha = 0.25f * a), 3.6f * a, point)
                drawCircle(color, 1.9f * p.coerceAtLeast(0f), point)
            }
        }
        2 -> {
            val (ring, wedge) = paths
            drawCircle(color.copy(alpha = 0.22f * a), 8.5f, Offset(12f, 12f))
            drawPath(ring, color, style = stroke)
            translate(left = 1.7f * p, top = -1.7f * p) {
                drawPath(wedge, color.copy(alpha = 0.35f + 0.65f * a))
                drawPath(wedge, color, style = stroke)
            }
        }
        3 -> {
            val (ring, arrow, inner) = paths
            drawCircle(color.copy(alpha = 0.2f * a), 8.5f, Offset(12f, 12f))
            drawPath(ring, color, style = stroke)
            drawPath(inner, color, style = stroke)
            drawCircle(color, 1.6f + 0.9f * p, Offset(12f, 12f))
            if (a > 0.01f) {
                translate(left = 5f * (1f - p), top = -5f * (1f - p)) { drawPath(arrow, color.copy(alpha = a), style = stroke) }
            }
        }
        else -> rotate(90f * p, pivot = Offset(12f, 12f)) {
            val tile = Size(6.6f, 6.6f)
            listOf(Offset(4f, 4f), Offset(13.4f, 4f), Offset(4f, 13.4f), Offset(13.4f, 13.4f)).forEachIndexed { i, origin ->
                val dot = i == 1
                val radius = CornerRadius(if (dot) 2.3f + 1f * a else 2.3f)
                drawRoundRect(color.copy(alpha = if (dot) a else 0.28f * a), origin, tile, radius)
                drawRoundRect(color, origin, tile, radius, style = stroke)
            }
        }
    }
}
