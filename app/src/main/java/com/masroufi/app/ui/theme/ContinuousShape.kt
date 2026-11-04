package com.masroufi.app.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Apple's continuous ("squircle") corner: the curvature ramps in over ~1.53 × the radius instead of jumping from
 * straight to circular, so corners read as soft rather than stamped. The curve is the well-known reverse-engineered
 * UIBezierPath(roundedRect:cornerRadius:) approximation. When a side is too short for the full ramp (pills, small
 * chips) the corner falls back to a plain circular arc, exactly as UIKit does.
 */
class ContinuousShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: CornerSize) : this(radius, radius, radius, radius)

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        ContinuousShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = if (ltr) topStart else topEnd
        val tr = if (ltr) topEnd else topStart
        val br = if (ltr) bottomEnd else bottomStart
        val bl = if (ltr) bottomStart else bottomEnd
        val w = size.width
        val h = size.height
        val limit = minOf(w, h) / 2f
        val path = Path()

        // Each corner is drawn clockwise from the top edge. `k` is the ramp length factor.
        fun fits(r: Float) = r * K <= limit

        // Top-left start point.
        if (fits(tl)) path.moveTo(tl * K, 0f) else path.moveTo(minOf(tl, limit), 0f)

        // Top-right.
        val r1 = tr
        if (fits(r1)) {
            path.lineTo(w - r1 * K, 0f)
            path.cubicTo(w - r1 * 1.08849f, 0f, w - r1 * 0.86841f, 0f, w - r1 * 0.63149f, r1 * 0.07491f)
            path.cubicTo(w - r1 * 0.37282f, r1 * 0.16906f, w - r1 * 0.16906f, r1 * 0.37282f, w - r1 * 0.07491f, r1 * 0.63149f)
            path.cubicTo(w, r1 * 0.86841f, w, r1 * 1.08849f, w, r1 * K)
        } else {
            val r = minOf(r1, limit)
            path.lineTo(w - r, 0f)
            path.cubicTo(w - r * (1 - C), 0f, w, r * (1 - C), w, r)
        }

        // Bottom-right.
        val r2 = br
        if (fits(r2)) {
            path.lineTo(w, h - r2 * K)
            path.cubicTo(w, h - r2 * 1.08849f, w, h - r2 * 0.86841f, w - r2 * 0.07491f, h - r2 * 0.63149f)
            path.cubicTo(w - r2 * 0.16906f, h - r2 * 0.37282f, w - r2 * 0.37282f, h - r2 * 0.16906f, w - r2 * 0.63149f, h - r2 * 0.07491f)
            path.cubicTo(w - r2 * 0.86841f, h, w - r2 * 1.08849f, h, w - r2 * K, h)
        } else {
            val r = minOf(r2, limit)
            path.lineTo(w, h - r)
            path.cubicTo(w, h - r * (1 - C), w - r * (1 - C), h, w - r, h)
        }

        // Bottom-left.
        val r3 = bl
        if (fits(r3)) {
            path.lineTo(r3 * K, h)
            path.cubicTo(r3 * 1.08849f, h, r3 * 0.86841f, h, r3 * 0.63149f, h - r3 * 0.07491f)
            path.cubicTo(r3 * 0.37282f, h - r3 * 0.16906f, r3 * 0.16906f, h - r3 * 0.37282f, r3 * 0.07491f, h - r3 * 0.63149f)
            path.cubicTo(0f, h - r3 * 0.86841f, 0f, h - r3 * 1.08849f, 0f, h - r3 * K)
        } else {
            val r = minOf(r3, limit)
            path.lineTo(r, h)
            path.cubicTo(r * (1 - C), h, 0f, h - r * (1 - C), 0f, h - r)
        }

        // Top-left.
        val r4 = tl
        if (fits(r4)) {
            path.lineTo(0f, r4 * K)
            path.cubicTo(0f, r4 * 1.08849f, 0f, r4 * 0.86841f, r4 * 0.07491f, r4 * 0.63149f)
            path.cubicTo(r4 * 0.16906f, r4 * 0.37282f, r4 * 0.37282f, r4 * 0.16906f, r4 * 0.63149f, r4 * 0.07491f)
            path.cubicTo(r4 * 0.86841f, 0f, r4 * 1.08849f, 0f, r4 * K, 0f)
        } else {
            val r = minOf(r4, limit)
            path.lineTo(0f, r)
            path.cubicTo(0f, r * (1 - C), r * (1 - C), 0f, r, 0f)
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is ContinuousShape && topStart == other.topStart && topEnd == other.topEnd && bottomEnd == other.bottomEnd && bottomStart == other.bottomStart

    override fun hashCode(): Int = ((topStart.hashCode() * 31 + topEnd.hashCode()) * 31 + bottomEnd.hashCode()) * 31 + bottomStart.hashCode()

    private companion object {
        /** How far along each edge the corner curve begins, as a multiple of the radius. */
        const val K = 1.52866f

        /** Cubic-bezier circle constant, for the circular fallback. */
        const val C = 0.5523f
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)

/** A continuous corner of [radius] on all four corners. */
fun continuous(radius: Int) = ContinuousShape(CornerSize(radius.dp))

/** A continuous corner as a share of the smaller side (icon tiles use 22.5%, like app icons). */
fun continuousPercent(percent: Int) = ContinuousShape(CornerSize(percent))
