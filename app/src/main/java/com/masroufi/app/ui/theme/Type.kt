package com.masroufi.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R

val Lalezar = FontFamily(Font(R.font.lalezar, FontWeight.Normal))

// Variable font: one file, four weights picked through the wght axis.
@OptIn(ExperimentalTextApi::class)
val Alexandria = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.alexandria,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private const val TNUM = "tnum"

private fun display(size: Int) = TextStyle(
    fontFamily = Lalezar,
    fontSize = size.sp,
    fontFeatureSettings = TNUM,
)

object TzType {
    val ticket = display(104).copy(lineHeight = 0.95.em, letterSpacing = (-2).sp)
    val hero = display(56)
    val card = display(32)
    val rowNumber = display(20)
    val titleTab = display(30)
    val titlePushed = display(22)
    val cardHeading = display(20)
    val button = display(20)

    val body = TextStyle(
        fontFamily = Alexandria,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 1.7.em,
        fontFeatureSettings = TNUM,
    )
    val label = TextStyle(
        fontFamily = Alexandria,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        fontFeatureSettings = TNUM,
    )
    val caption = TextStyle(
        fontFamily = Alexandria,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        fontFeatureSettings = TNUM,
    )
}
