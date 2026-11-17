package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickLogParserTest {
    private val cats = listOf("قهوة", "مواصلات", "أكل", "بقالة", "فواتير", "إيجار", "صحة", "تعليم", "حاجة تانية")
    private fun parse(t: String) = parseQuickLog(t, cats)

    @Test fun foodKeywords() = assertEquals(QuickLog(2000, "أكل"), parse("20 فول وطعمية"))
    @Test fun arabicIndicDigitsAndCategoryName() = assertEquals(QuickLog(3500, "مواصلات"), parse("٣٥ مواصلات"))
    @Test fun decimal() = assertEquals(QuickLog(1250, "قهوة"), parse("12.5 قهوة"))
    @Test fun numberAfterKeyword() = assertEquals(QuickLog(8000, "مواصلات"), parse("تاكسي 80"))
    @Test fun noNumber() = assertNull(parse("شيء"))
    @Test fun zeroAmount() = assertNull(parse("0 شاي"))
    @Test fun noCategory() = assertEquals(QuickLog(500, null), parse("5 شيء"))
    @Test fun commaThousands() = assertEquals(QuickLog(125_000, "بقالة"), parse("1,250 بقالة"))
    @Test fun commaDecimal() = assertEquals(QuickLog(1250, "قهوة"), parse("12,5 قهوة"))
    @Test fun arabicDecimalSeparator() = assertEquals(QuickLog(1250, "قهوة"), parse("١٢٫٥ قهوة"))
    @Test fun extendedArabicIndic() = assertEquals(QuickLog(7500, "أكل"), parse("۷۵ فول"))
    @Test fun threeDecimalsRejected() = assertNull(parse("20.555 قهوة"))
    @Test fun hamzaAndTaMarbutaNormalised() = assertEquals(QuickLog(1500, "قهوة"), parse("قهوه 15"))
    @Test fun tatweelIgnored() = assertEquals(QuickLog(5000, "أكل"), parse("ـــ٥٠ فول"))
    @Test fun diacriticsIgnored() = assertEquals(QuickLog(1000, "قهوة"), parse("10 قَهْوة"))
    @Test fun keywordSkippedWhenCategoryMissing() =
        assertEquals(QuickLog(1000, null), parseQuickLog("10 قهوة", listOf("أكل")))
}
