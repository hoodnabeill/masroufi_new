package com.masroufi.app.feature.add

private const val MAX_WHOLE_DIGITS = 8
private const val MAX_DECIMALS = 2

/** What the keypad has typed: at most 8 whole digits, one point, at most 2 decimals, no leading zeros ("007" is "7"). */
data class AmountInput(val text: String = "") {
    fun digit(d: Int): AmountInput {
        require(d in 0..9) { "digit must be 0..9" }
        val dot = text.indexOf('.')
        return when {
            dot >= 0 -> if (text.length - dot - 1 >= MAX_DECIMALS) this else AmountInput(text + d)
            text == "0" -> AmountInput(d.toString())
            text.length >= MAX_WHOLE_DIGITS -> this
            else -> AmountInput(text + d)
        }
    }

    fun point(): AmountInput = when {
        '.' in text -> this
        text.isEmpty() -> AmountInput("0.")
        else -> AmountInput("$text.")
    }

    fun backspace(): AmountInput = AmountInput(text.dropLast(1))

    /** Exact, from the digits: "12.5" is 1250. Empty or "." is 0. */
    fun toPiastres(): Long {
        val whole = text.substringBefore('.').ifEmpty { "0" }
        val decimals = text.substringAfter('.', "").padEnd(MAX_DECIMALS, '0')
        return whole.toLong() * 100 + decimals.toLong()
    }

    val hasPoint: Boolean get() = '.' in text

    companion object {
        /** A prefilled amount; empty when it does not fit 8 whole digits. */
        fun of(piastres: Long): AmountInput {
            val whole = piastres / 100
            val rest = piastres % 100
            if (piastres <= 0 || whole.toString().length > MAX_WHOLE_DIGITS) return AmountInput()
            return AmountInput(if (rest == 0L) "$whole" else "$whole." + rest.toString().padStart(2, '0'))
        }
    }
}
