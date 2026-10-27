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

class MoneyFormatter(private val locale: Locale) {
    private val cache = HashMap<String, NumberFormat>()

    fun format(minor: Long, currency: String, signed: Boolean = false): String {
        val nf = cache.getOrPut(currency) {
            NumberFormat.getCurrencyInstance(locale).apply {
                runCatching { this.currency = Currency.getInstance(currency) }
                val digits = Money.fractionDigits(currency)
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }
        }
        val text = nf.format(BigDecimal.valueOf(minor, Money.fractionDigits(currency)))
        return if (signed && minor > 0) "+$text" else text
    }

    fun formatCompact(minor: Long, currency: String): String {
        val digits = Money.fractionDigits(currency)
        val whole = BigDecimal.valueOf(minor, digits).setScale(0, RoundingMode.HALF_UP)
        val nf = NumberFormat.getIntegerInstance(locale)
        return nf.format(whole)
    }
}
