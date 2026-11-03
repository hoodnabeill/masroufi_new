package com.masroufi.app.domain

import com.masroufi.app.core.Money

/** What "type to log" understood from a line like `50 قهوة`: an amount, a likely category, and the words as a note. */
data class QuickLog(val amountMinor: Long?, val categoryKey: String?, val note: String)

/**
 * Parses the Home "سجّل في ثانية" box. The first number is the amount (Western or Arabic-Indic digits, `1,500` or
 * `12.5`); the remaining words become the note and pick a system category by keyword, in Egyptian Arabic or
 * English. Unknown words simply leave the category for the user to choose.
 */
object QuickLogParser {
    private val number = Regex("""\d+(?:[.,]\d+)*""")

    private val keywords: List<Pair<String, List<String>>> = listOf(
        "food" to listOf("قهوه", "كافيه", "اكل", "غدا", "فطار", "عشا", "مطعم", "شاورما", "كشري", "فول", "طعميه", "بيتزا", "برجر", "حلويات", "coffee", "food", "lunch", "dinner", "breakfast", "restaurant"),
        "groceries" to listOf("بقاله", "سوبر", "ماركت", "خضار", "فاكهه", "عيش", "لبن", "جبنه", "بيض", "grocery", "groceries", "market"),
        "transport" to listOf("بنزين", "مواصلات", "اوبر", "كريم", "تاكسي", "ميكروباص", "مترو", "توكتوك", "اتوبيس", "uber", "careem", "taxi", "fuel", "metro", "bus"),
        "mobile" to listOf("رصيد", "كارت", "شحن", "باقه", "انترنت", "نت", "موبايل", "credit", "recharge", "internet"),
        "bills" to listOf("كهربا", "كهرباء", "ميه", "مياه", "غاز", "فاتوره", "bill", "electricity", "water"),
        "health" to listOf("دوا", "دواء", "صيدليه", "دكتور", "كشف", "pharmacy", "doctor", "medicine"),
        "shopping" to listOf("لبس", "هدوم", "تيشيرت", "جزمه", "شنطه", "clothes", "shoes"),
        "rent" to listOf("ايجار", "rent"),
        "entertainment" to listOf("سينما", "خروجه", "لعب", "cinema", "movie", "games"),
        "education" to listOf("دروس", "درس", "مدرسه", "كورس", "كتب", "school", "course", "books"),
        "gifts" to listOf("هديه", "عيديه", "gift"),
        "charity" to listOf("صدقه", "زكاه", "تبرع", "charity"),
    )

    fun parse(text: String, currency: String = Money.DEFAULT_CURRENCY): QuickLog {
        val western = buildString(text.length) {
            for (ch in text) {
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
        val match = number.find(western)
        val amount = match?.value?.let { raw ->
            // "1,500" is thousands; "12,5" is a decimal comma.
            val cleaned = if (Regex("""\d{1,3}(,\d{3})+(\.\d+)?""").matches(raw)) raw.replace(",", "") else raw.replace(',', '.')
            Money.parse(cleaned, currency)?.takeIf { it > 0 }
        }
        val words = (match?.let { western.removeRange(it.range) } ?: western).replace(Regex("""\s+"""), " ").trim()
        val normalized = normalize(words)
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        val category = keywords.firstOrNull { (_, list) -> list.any { k -> tokens.any { t -> t == k || t.startsWith(k) } } }?.first
        return QuickLog(amount, category, words)
    }

    /** Folds the spellings people type interchangeably: alef forms, taa marbuta, alef maqsura, and case. */
    private fun normalize(text: String): String = text.lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')
        .removePrefix("ال").replace(" ال", " ")
}
