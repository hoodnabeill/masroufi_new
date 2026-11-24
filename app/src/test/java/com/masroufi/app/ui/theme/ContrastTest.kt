package com.masroufi.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG contrast of every text colour token on every background token it is drawn on (03-DESIGN-SYSTEM "Colour": body text
 * at least 4.5:1). The pairs come from where the tokens are used in the screens: text on the three dark surfaces and on
 * the tinted panels, the inks on their fills, and the accent colours as text on the dark surfaces. Large text (24 sp, or
 * 18.66 sp bold) only needs 3:1, but every pair here also passes 4.5:1, so none is listed as large.
 *
 * Not in the table on purpose: Text 3 on Raised (4.11:1). 03-DESIGN-SYSTEM allows Text 3 for hints and 11-12 sp labels on
 * Surface and Night only; no screen draws it on Raised (Paper.kt's tint is an icon, which needs 3:1).
 * Text drawn at a reduced alpha (a disabled button at 40%) is exempt from contrast by WCAG and is not checked.
 */
class ContrastTest {
    private class Case(val text: String, val textColor: Color, val bg: String, val bgColor: Color, val large: Boolean = false)

    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private val night = "night" to Tz.night
    private val surface = "surface" to Tz.surface
    private val raised = "raised" to Tz.raised
    private val yellowDim = "yellowDim" to Tz.yellowDim
    private val carnelianDim = "carnelianDim" to Tz.carnelianDim
    private val turquoiseDim = "turquoiseDim" to Tz.turquoiseDim
    private val darkGrounds = listOf(night, surface, raised)
    private val tintedGrounds = listOf(yellowDim, carnelianDim, turquoiseDim)

    private val pairs: List<Case> = buildList {
        fun row(name: String, color: Color, grounds: List<Pair<String, Color>>) =
            grounds.forEach { (bgName, bg) -> add(Case(name, color, bgName, bg)) }
        row("text", Tz.text, darkGrounds + tintedGrounds + ("line" to Tz.line))
        row("text2", Tz.text2, darkGrounds + tintedGrounds + ("line" to Tz.line))
        row("text3", Tz.text3, listOf(night, surface))
        row("yellow", Tz.yellow, darkGrounds + yellowDim)
        row("turquoise", Tz.turquoise, darkGrounds + turquoiseDim)
        row("turquoiseDimText", Tz.turquoiseDimText, listOf(turquoiseDim))
        row("carnelian", Tz.carnelian, darkGrounds)
        row("carnelianText", Tz.carnelianText, darkGrounds + carnelianDim)
        row("sky", Tz.sky, darkGrounds)
        row("lilac", Tz.lilac, darkGrounds)
        // On the accent fills.
        row("night", Tz.night, listOf("yellow" to Tz.yellow, "turquoise" to Tz.turquoise, "carnelian" to Tz.carnelian))
        row("yellowInk", Tz.yellowInk, listOf("yellow" to Tz.yellow))
        row("yellowInk2", Tz.yellowInk2, listOf("yellow" to Tz.yellow, "qershOnYellow" to Tz.qershOnYellow))
        row("turquoiseInk", Tz.turquoiseInk, listOf("turquoise" to Tz.turquoise))
        row("carnelianInk", Tz.carnelianInk, listOf("carnelian" to Tz.carnelian))
    }

    @Test fun everyTextOnBackgroundPairMeetsItsMinimum() {
        val failures = pairs.filter { ratio(it.textColor, it.bgColor) < if (it.large) 3.0 else 4.5 }
            .map { "${it.text} on ${it.bg}: %.2f".format(java.util.Locale.US, ratio(it.textColor, it.bgColor)) }
        assertTrue("below the minimum contrast:\n" + failures.joinToString("\n"), failures.isEmpty())
        println(pairs.joinToString("\n") { "${it.text} on ${it.bg}: %.2f".format(java.util.Locale.US, ratio(it.textColor, it.bgColor)) })
    }

    @Test fun theRatioFormulaMatchesTheKnownExtremes() {
        assertEquals(21.0, ratio(Color.Black, Color.White), 0.001)
        assertEquals(1.0, ratio(Tz.night, Tz.night), 0.001)
    }

    @Test fun textThreeOnRaisedIsTheKnownLowPairNobodyDrawsText() {
        assertTrue(ratio(Tz.text3, Tz.raised) < 4.5)
    }
}
