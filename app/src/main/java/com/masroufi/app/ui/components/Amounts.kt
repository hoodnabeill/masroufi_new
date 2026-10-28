package com.masroufi.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.masroufi.app.core.MoneyFormatter
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import java.util.Locale

val LocalMoneyFormatter = staticCompositionLocalOf { MoneyFormatter(Locale.getDefault()) }

private fun Char.isAnyDigit(): Boolean = this in '0'..'9' || this in '٠'..'٩' || this in '۰'..'۹'

/** Splits "EGP 1,234.50" / "١٬٢٣٤٫٥٠ ج.م.‏" into the text before the number, the number itself, and the text after. */
private data class AmountParts(val prefix: String, val number: String, val suffix: String)

private fun split(text: String): AmountParts {
    val first = text.indexOfFirst { it.isAnyDigit() }
    if (first < 0) return AmountParts(text, "", "")
    val last = text.indexOfLast { it.isAnyDigit() }
    return AmountParts(text.substring(0, first), text.substring(first, last + 1), text.substring(last + 1))
}

/**
 * Odometer amount: every digit rolls on its own, upwards when the value grew and downwards when it shrank, while
 * the currency symbol and separators stay still. Digits are keyed by their place from the right so the units digit
 * is always the same cell. The number is laid out LTR (numerals are LTR in Arabic too); prefix and suffix keep
 * normal bidi text so Arabic currency symbols stay joined. Screen readers hear only the final value.
 */
@Composable
fun AnimatedAmount(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    signed: Boolean = false,
    @Suppress("UNUSED_PARAMETER") textAlign: TextAlign? = null,
) {
    val formatter = LocalMoneyFormatter.current
    val reduce = LocalReduceMotion.current
    val text = formatter.format(minor, currency, signed)
    val parts = split(text)
    val previous = remember { longArrayOf(minor) }
    val rising = minor >= previous[0]
    SideEffect { previous[0] = minor }
    val tabular = style.copy(fontFeatureSettings = "tnum")

    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        if (parts.prefix.isNotEmpty()) Text(parts.prefix, style = tabular, color = color, maxLines = 1)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row {
                val count = parts.number.length
                parts.number.forEachIndexed { index, ch ->
                    key(count - index) {
                        if (!ch.isAnyDigit() || reduce) {
                            Text(ch.toString(), style = tabular, color = color)
                        } else {
                            AnimatedContent(
                                targetState = ch,
                                transitionSpec = { roll(rising) },
                                modifier = Modifier.clipToBounds(),
                                label = "digit",
                            ) { digit -> Text(digit.toString(), style = tabular, color = color) }
                        }
                    }
                }
            }
        }
        if (parts.suffix.isNotEmpty()) Text(parts.suffix, style = tabular, color = color, maxLines = 1)
    }
}

private fun roll(rising: Boolean): ContentTransform {
    val spatial = Motion.spatial<IntOffset>(false)
    val fade = Motion.snappy<Float>(false)
    val dir = if (rising) 1 else -1
    return (slideInVertically(spatial) { h -> h * dir } + fadeIn(fade)) togetherWith
        (slideOutVertically(spatial) { h -> -h * dir } + fadeOut(fade))
}

@Composable
fun MoneyText(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    signed: Boolean = false,
) {
    Text(
        LocalMoneyFormatter.current.format(minor, currency, signed),
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
    )
}

@Composable
fun typeColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> LocalFinanceColors.current.income
    TransactionType.EXPENSE -> LocalFinanceColors.current.expense
    TransactionType.TRANSFER -> LocalFinanceColors.current.transfer
}
