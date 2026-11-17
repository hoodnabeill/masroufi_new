package com.masroufi.app.feature.pockets

import com.masroufi.app.data.repo.cashCount
import com.masroufi.app.feature.add.AmountInput

/** What the note counter shows: notes of 200, 100 and 50, plus small change typed in pounds. */
data class CashCounter(
    val n200: Int = 0,
    val n100: Int = 0,
    val n50: Int = 0,
    val change: AmountInput = AmountInput(),
) {
    /** The counted total in piastres. */
    val total: Long get() = cashCount(n200, n100, n50, change.toPiastres())

    fun step(denomination: Int, delta: Int): CashCounter = when (denomination) {
        200 -> copy(n200 = (n200 + delta).coerceAtLeast(0))
        100 -> copy(n100 = (n100 + delta).coerceAtLeast(0))
        50 -> copy(n50 = (n50 + delta).coerceAtLeast(0))
        else -> this
    }

    /** The field's text, run through the keypad rules (digits and one point, 2 decimals). */
    fun withChange(text: String): CashCounter = copy(
        change = text.fold(AmountInput()) { a, c ->
            when {
                c.isDigit() -> a.digit(c.digitToInt())
                c == '.' -> a.point()
                else -> a
            }
        },
    )

    companion object {
        /** The fewest big notes that fit [balance] (piastres); what is left is the change. A negative balance counts as 0. */
        fun of(balance: Long): CashCounter {
            val b = balance.coerceAtLeast(0)
            val pounds = b / 100
            val n200 = (pounds / 200).toInt()
            val n100 = (pounds % 200 / 100).toInt()
            val n50 = (pounds % 100 / 50).toInt()
            return CashCounter(n200, n100, n50, AmountInput.of(pounds % 50 * 100 + b % 100))
        }
    }
}

/** What reconciling did, for the line shown in place. */
enum class ReconcileResult { Done, Already }
