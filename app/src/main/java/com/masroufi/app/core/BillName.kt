package com.masroufi.app.core

private val LEADING_WORD = Regex("^(فاتورة|bill)\s+", RegexOption.IGNORE_CASE)
private val TRAILING_WORD = Regex("\s+bill$", RegexOption.IGNORE_CASE)

/** A bill's name for a sentence that already says "bill" (ar "فاتورة X", en "X bill"): a name that starts or ends with the word is not doubled. */
fun billTitle(name: String): String = name.trim().replace(LEADING_WORD, "").replace(TRAILING_WORD, "").trim()
