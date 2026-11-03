package com.masroufi.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money is always stored as a Long count of minor units (piastres for EGP).
 * BigDecimal is used only at the text boundary; no floating point touches amounts.
 */
object Money {
    const val DEFAULT_CURRENCY = "EGP"
    val SUPPORTED_CURRENCIES = listOf("EGP", "USD", "EUR", "SAR", "AED", "KWD", "GBP")

    fun fractionDigits(currency: String): Int =
        runCatching { Currency.getInstance(currency).defaultFractionDigits }
            .getOrDefault(2)
            .coerceAtLeast(0)

    /** Parses user input (Western or Arabic-Indic digits) into minor units; null when invalid or too precise. */
    fun parse(input: String, currency: String): Long? {
        val normalized = normalizeDigits(input).trim()
        if (normalized.isEmpty()) return null
        val decimal = normalized.toBigDecimalOrNull() ?: return null
        if (decimal.signum() < 0) return null
        return try {
            decimal.movePointRight(fractionDigits(currency))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun toPlainString(minor: Long, currency: String): String =
        BigDecimal.valueOf(minor, fractionDigits(currency)).stripTrailingZeros().let {
            if (it.scale() < 0) it.setScale(0) else it
        }.toPlainString()

    fun normalizeDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> append('0' + (ch - '٠'))
                in '۰'..'۹' -> append('0' + (ch - '۰'))
                '٫' -> append('.')
                '٬', ',', ' ', ' ' -> Unit
                else -> append(ch)
            }
        }
    }
}

/**
 * Money as Egyptians write it: Western digits grouped by thousands, decimals only when they are not zero, and the
 * currency after the number in Arabic (`1,105 ج.م`, no trailing dot) or before it in English (`EGP 1,105`).
 * The space between number and currency is non-breaking so an amount never wraps.
 */
class MoneyFormatter(locale: Locale) {
    private val arabic = locale.language == "ar"
    private val symbolLocale = locale
    private val numbers = HashMap<Int, NumberFormat>()

    fun format(minor: Long, currency: String, signed: Boolean = false): String {
        val digits = Money.fractionDigits(currency)
        val value = BigDecimal.valueOf(minor, digits).abs()
        val fraction = if (value.remainder(BigDecimal.ONE).signum() == 0) 0 else digits
        val number = numberFormat(fraction).format(value)
        val sign = when {
            minor < 0 -> "-"
            signed && minor > 0 -> "+"
            else -> ""
        }
        val symbol = symbol(currency)
        return if (arabic) "$sign$number $symbol" else "$sign$symbol $number"
    }

    /** Whole units only, still in Western digits (chart axes and compact labels). */
    fun formatCompact(minor: Long, currency: String): String {
        val whole = BigDecimal.valueOf(minor, Money.fractionDigits(currency)).setScale(0, RoundingMode.HALF_UP)
        return numberFormat(0).format(whole)
    }

    private fun numberFormat(fraction: Int): NumberFormat = numbers.getOrPut(fraction) {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = fraction
            maximumFractionDigits = fraction
            roundingMode = RoundingMode.HALF_UP
        }
    }

    fun symbol(currency: String): String =
        if (arabic) {
            ARABIC_SYMBOLS[currency] ?: currency
        } else {
            runCatching { Currency.getInstance(currency).getSymbol(symbolLocale) }.getOrDefault(currency)
        }

    private companion object {
        /** Short Arabic symbols without the trailing dot and direction marks the JDK adds (`ج.م.‏`). */
        val ARABIC_SYMBOLS = mapOf(
            "EGP" to "ج.م", "SAR" to "ر.س", "AED" to "د.إ", "KWD" to "د.ك",
            "USD" to "\$", "EUR" to "€", "GBP" to "£",
        )
    }
}
