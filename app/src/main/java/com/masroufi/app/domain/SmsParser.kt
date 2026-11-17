package com.masroufi.app.domain

enum class SmsDirection { SPENT, RECEIVED, MOVED_OUT }

/** amount and balance are in piastres. */
data class ParsedSms(val amount: Long, val direction: SmsDirection, val party: String?, val balance: Long?)

private class Money(val start: Int, val end: Int, val value: Long)

private val moneyRe = Regex("\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?")
private val currencyRe = Regex("جنيه|ج\\.م|egp|\\ble\\b|\\bl\\.e")
private val balanceRe = Regex("رصيد|balance|available")
private val partyRe = Regex("(?<!\\p{L})(?:الي|من|to|from|at)\\s+([^.,;:!\\n،؛]+)")
private val spentRe = Regex("خصم|شراء|دفع|سداد|\\bpurchase|\\bdebit|\\bpaid\\b|\\bspent\\b")
private val outRe = Regex("سحب|\\btransfer(?:red)?\\s+to\\b|\\bwithdraw|\\bsent\\b")
private val recvRe = Regex("اضافه|استلام|ايداع|\\bcredit|\\breceived\\b|\\bdeposit")
private val transferRe = Regex("تحويل")
private val sentenceEndRe = Regex("\\.(?!\\d)|\\n")
private val toRe = Regex("(?<!\\p{L})(?:الي\\s+|ل\\s*)\\S")
private val fromYouRe = Regex("(?<!\\p{L})منك")

// 1:1 char mapping so indexes line up with the diacritic-stripped original (used for the party text).
private fun normChar(c: Char): Char = when (c) {
    in '٠'..'٩' -> '0' + (c - '٠')
    in '۰'..'۹' -> '0' + (c - '۰')
    '٬' -> ','
    '٫' -> '.'
    'أ', 'إ', 'آ' -> 'ا'
    'ة' -> 'ه'
    'ى' -> 'ي'
    else -> c.lowercaseChar()
}

private fun toPiastres(token: String): Long? {
    val parts = token.replace(",", "").split(".")
    if (parts[0].length > 12) return null
    val dec = (parts.getOrNull(1) ?: "").padEnd(2, '0')
    return parts[0].toLong() * 100 + dec.toLong()
}

// shortcut: dates/times in the text can be read as amounts when no currency word sits next to the real one.
fun parseSms(text: String): ParsedSms? {
    val clean = text.filter { it !in 'ً'..'ٟ' && it != 'ٰ' && it != 'ـ' }
    val n = String(CharArray(clean.length) { normChar(clean[it]) })

    val moneys = moneyRe.findAll(n).mapNotNull { m ->
        val plain = m.value.none { it == ',' || it == '.' }
        // 8+ bare digits is a phone or account number, not money
        if (plain && m.value.length >= 8) null else toPiastres(m.value)?.let { Money(m.range.first, m.range.last + 1, it) }
    }.toList()

    val balanceKw = balanceRe.find(n)
    val balanceMoney = balanceKw?.let { kw -> moneys.firstOrNull { it.start >= kw.range.last + 1 } }
    val candidates = moneys.filter { it !== balanceMoney && it.value > 0 }
    val amount = candidates.firstOrNull {
        currencyRe.containsMatchIn(n.substring(maxOf(0, it.start - 5), minOf(n.length, it.end + 8)))
    } ?: candidates.firstOrNull() ?: return null

    val hits = ArrayList<Pair<Int, SmsDirection>>()
    spentRe.findAll(n).forEach { hits.add(it.range.first to SmsDirection.SPENT) }
    outRe.findAll(n).forEach { hits.add(it.range.first to SmsDirection.MOVED_OUT) }
    recvRe.findAll(n).forEach { hits.add(it.range.first to SmsDirection.RECEIVED) }
    transferRe.findAll(n).forEach { m ->
        val from = m.range.last + 1
        val to = minOf(n.length, from + 50)
        val tail = n.substring(from, to).let { t -> t.substring(0, sentenceEndRe.find(t)?.range?.first ?: t.length) }
        val dir = when {
            "اليك" in tail -> SmsDirection.RECEIVED
            fromYouRe.containsMatchIn(tail) || toRe.containsMatchIn(tail) -> SmsDirection.MOVED_OUT
            else -> null
        }
        if (dir != null) hits.add(m.range.first to dir)
    }
    val direction = hits.minByOrNull { Math.abs(it.first - amount.start) }?.second ?: return null

    val party = partyRe.find(n)?.let { m ->
        val g = m.groups[1]!!.range
        val end = balanceKw?.range?.first?.takeIf { it > g.first && it <= g.last }?.let { it - 1 } ?: g.last
        clean.substring(g.first, end + 1).trim().take(40).ifEmpty { null }
    }
    return ParsedSms(amount.value, direction, party, balanceMoney?.value)
}
