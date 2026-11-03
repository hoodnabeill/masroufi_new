package com.masroufi.app.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Arabic is applied as `ar-EG-u-nu-latn`: Egyptian Arabic text with Latin (0-9) digits, which is what Egyptians read
 * on receipts, banking apps and prices. Resources still resolve to values-ar.
 */
fun appLocaleTag(language: String): String = if (language == "ar") "ar-EG-u-nu-latn" else language

fun applyAppLanguage(language: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appLocaleTag(language)))

/** Users who picked Arabic before the numbering change get Latin digits too; no-op once applied. */
fun migrateArabicDigits() {
    val current = AppCompatDelegate.getApplicationLocales()
    if (!current.isEmpty && current[0]?.language == "ar" && !current.toLanguageTags().contains("nu-latn")) applyAppLanguage("ar")
}
