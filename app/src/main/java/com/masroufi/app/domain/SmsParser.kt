package com.masroufi.app.domain

import com.masroufi.app.data.model.TransactionType

/** Where a pasted message came from, so it can be matched to one of the user's wallets. */
enum class SmsSource { VODAFONE_CASH, ETISALAT_CASH, ORANGE_CASH, WE_PAY, INSTAPAY, BANK }

data class SmsParse(
    val type: TransactionType,
    /** The amount as plain text ("150", "250.5"), ready for the editor. */
    val amount: String,
    val source: SmsSource?,
    /** Who the money went to or came from, or where it was spent, when the message says. */
    val counterparty: String?,
    /** The balance the message reports after the operation, as plain text. */
    val balanceAfter: String?,
)

/**
 * Reads a bank or mobile-wallet SMS the user pasted (the app never reads SMS itself: Play policy blocks it, and it
 * stays local). Handles the usual Egyptian Arabic and English wordings; anything without an amount is not parsed.
 */
object SmsParser {
    // A number next to a currency word, either order: "150 جنيه", "EGP 1,250.00", "LE 50".
    private const val NUMBER = """(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)"""
    private const val CURRENCY = """(?:جنيه|جنية|ج\.م|(?<![A-Za-z])(?:EGP|LE|L\.E\.?|pounds?))"""
    private val amountAfter = Regex("$NUMBER\\s*$CURRENCY", RegexOption.IGNORE_CASE)
    private val amountBefore = Regex("$CURRENCY\\s*$NUMBER", RegexOption.IGNORE_CASE)
    private val balanceMark = Regex("""(?:رصيد|balance|available)""", RegexOption.IGNORE_CASE)

    private val income = listOf("استلام", "استلمت", "تم إضافة", "تم اضافة", "أضيف", "اضيف", "إيداع", "ايداع", "وصلك", "credited", "received", "deposit", "refund", "incoming")
    private val withdrawal = listOf("سحب", "withdraw", "atm", "cash out")

    // Keywords only count at the start of a word ("ل" inside "تحويل" is not "to").
    private val counterpartyAfter = Regex("""(?:^|\s)(إلى|الى|لـ|to|at|من|from)\s+([^\n.,،؛]{2,40})""", RegexOption.IGNORE_CASE)
    private val clauseEnd = Regex("""\s+(?:on|في|يوم|بتاريخ|رصيد|balance)(?:\s|$)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): SmsParse? {
        val t = westernDigits(text)
        val amounts = (amountAfter.findAll(t) + amountBefore.findAll(t))
            .map { it.range.first to it.groupValues[1] }
            .sortedBy { it.first }
            .toList()
        if (amounts.isEmpty()) return null
        // The amount shortly after a "balance" word is the balance; the first other one is the operation.
        val marks = balanceMark.findAll(t).map { it.range.last }.toList()
        val balance = amounts.firstOrNull { (pos, _) -> marks.any { pos > it && pos - it < 25 } }
        val operation = amounts.firstOrNull { it != balance } ?: return null
        val lower = t.lowercase()
        val type = when {
            income.any { it in lower } -> TransactionType.INCOME
            withdrawal.any { it in lower } -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }
        return SmsParse(
            type = type,
            amount = plain(operation.second),
            source = source(lower),
            counterparty = counterparty(t, type),
            balanceAfter = balance?.second?.let(::plain),
        )
    }

    /** "1,250.00" → "1250", "250.50" → "250.5". */
    private fun plain(number: String): String {
        val s = number.replace(",", "")
        return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
    }

    /** Arabic-Indic digits and separators to Western ones; spaces and words stay as they are. */
    private fun westernDigits(s: String): String = buildString(s.length) {
        for (ch in s) {
            append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    '٫' -> '.'
                    '٬' -> ','
                    else -> ch
                },
            )
        }
    }

    private fun source(lower: String): SmsSource? = when {
        "vodafone" in lower || "فودافون" in lower -> SmsSource.VODAFONE_CASH
        "etisalat" in lower || "اتصالات" in lower -> SmsSource.ETISALAT_CASH
        "orange" in lower || "اورنج" in lower || "أورنج" in lower -> SmsSource.ORANGE_CASH
        "we pay" in lower || "wepay" in lower -> SmsSource.WE_PAY
        "instapay" in lower || "انستاباي" in lower || "إنستاباي" in lower -> SmsSource.INSTAPAY
        "card" in lower || "بطاقة" in lower || "كارت" in lower || "account" in lower || "حساب" in lower -> SmsSource.BANK
        else -> null
    }

    private fun counterparty(t: String, type: TransactionType): String? {
        val wanted = if (type == TransactionType.INCOME) setOf("من", "from") else setOf("إلى", "الى", "لـ", "to", "at")
        return counterpartyAfter.findAll(t)
            .filter { it.groupValues[1].lowercase() in wanted }
            .mapNotNull { m ->
                // Stop before the date or the next clause the bank appends.
                m.groupValues[2].split(clauseEnd).first().trim()
                    .takeIf { it.isNotEmpty() && amountAfter.find(it) == null && amountBefore.find(it) == null }
            }
            .firstOrNull()
    }
}
