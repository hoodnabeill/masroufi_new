package com.masroufi.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.masroufi.app.core.MoneyFormatter
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalReduceMotion
import java.util.Locale

val LocalMoneyFormatter = staticCompositionLocalOf { MoneyFormatter(Locale.getDefault()) }

/**
 * Counts from the previous value to the new one. The interpolation runs on Long minor units and finishes on the
 * exact target, so the settled number is always the true amount; screen readers only hear the final value.
 */
@Composable
fun AnimatedAmount(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    signed: Boolean = false,
    textAlign: TextAlign? = null,
) {
    val formatter = LocalMoneyFormatter.current
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableLongStateOf(minor) }
    LaunchedEffect(minor, reduce) {
        if (reduce || shown == minor) {
            shown = minor
            return@LaunchedEffect
        }
        val start = shown
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing)) {
            shown = start + ((minor - start) * value.toDouble()).toLong()
        }
        shown = minor
    }
    val finalText = formatter.format(minor, currency, signed)
    Text(
        text = formatter.format(shown, currency, signed),
        modifier = modifier.semantics { contentDescription = finalText },
        style = style,
        color = color,
        maxLines = 1,
        textAlign = textAlign,
    )
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
    Text(LocalMoneyFormatter.current.format(minor, currency, signed), modifier = modifier, style = style, color = color, maxLines = 1)
}

@Composable
fun typeColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> LocalFinanceColors.current.income
    TransactionType.EXPENSE -> LocalFinanceColors.current.expense
    TransactionType.TRANSFER -> LocalFinanceColors.current.transfer
}
