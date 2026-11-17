package com.masroufi.app.domain

data class QuickLog(val amount: Long, val categoryName: String?)

private val KEYWORDS = listOf(
    "أكل" to listOf("فول", "طعمية", "سندوتش", "غدا", "عشا", "فطار"),
    "قهوة" to listOf("قهوة", "شاي", "نسكافيه"),
    "مواصلات" to listOf("مترو", "اتوبيس", "تاكسي", "أوبر", "ميكروباص", "توكتوك"),
    "بقالة" to listOf("عيش", "خضار", "فاكهة", "سوبر ماركت", "بقالة"),
    "صحة" to listOf("دوا", "صيدلية", "دكتور"),
)

private val NUMBER = Regex("""\d(?:[\d.,٫٬]*\d)?""")

/** Western digits, no tatweel or diacritics, أإآ→ا, ة→ه, ى→ي. */
private fun normalize(s: String): String = buildString {
    for (c in s) when {
        c in '٠'..'٩' -> append('0' + (c - '٠'))
        c in '۰'..'۹' -> append('0' + (c - '۰'))
        c == 'ـ' || c in 'ً'..'ْ' -> Unit
        c == 'أ' || c == 'إ' || c == 'آ' -> append('ا')
        c == 'ة' -> append('ه')
        c == 'ى' -> append('ي')
        else -> append(c)
    }
}

/** Piastres from a normalised number token, or null: `.`/`٫` are decimal, `,` only before 1-2 final digits. */
private fun piastres(token: String): Long? {
    val t = token.replace("٬", "").replace('٫', '.').let { u ->
        val comma = u.lastIndexOf(',')
        when {
            '.' in u -> u.replace(",", "")
            comma >= 0 && u.length - comma - 1 in 1..2 -> u.substring(0, comma).replace(",", "") + "." + u.substring(comma + 1)
            else -> u.replace(",", "")
        }
    }
    val value = t.toBigDecimalOrNull() ?: return null
    if (value.scale() > 2) return null
    return try {
        value.movePointRight(2).longValueExact().takeIf { it > 0 }
    } catch (e: ArithmeticException) {
        null
    }
}

/** First number anywhere in `text` is the amount; the rest picks a category from `categories`, else a keyword. */
fun parseQuickLog(text: String, categories: List<String>): QuickLog? {
    val n = normalize(text)
    val match = NUMBER.find(n) ?: return null
    val amount = piastres(match.value) ?: return null
    val rest = n.removeRange(match.range)
    val byNormal = categories.associateBy { normalize(it) }
    val name = byNormal.keys.firstOrNull { it.isNotEmpty() && it in rest }?.let(byNormal::get)
        ?: KEYWORDS.firstOrNull { (_, words) -> words.any { normalize(it) in rest } }?.let { byNormal[normalize(it.first)] }
    return QuickLog(amount, name)
}
