package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickLogParserTest {
    @Test fun amountThenWord() {
        val log = QuickLogParser.parse("50 قهوة")
        assertEquals(50_00L, log.amountMinor)
        assertEquals("food", log.categoryKey)
        assertEquals("قهوة", log.note)
    }

    @Test fun arabicDigitsAndCategories() {
        assertEquals("transport", QuickLogParser.parse("200 بنزين").categoryKey)
        val credit = QuickLogParser.parse("١٥٠ رصيد")
        assertEquals(150_00L, credit.amountMinor)
        assertEquals("mobile", credit.categoryKey)
    }

    @Test fun wordFirstDecimalsAndThousands() {
        assertEquals(120_50L, QuickLogParser.parse("lunch 120.5").amountMinor)
        assertEquals(1_500_00L, QuickLogParser.parse("الإيجار 1,500").amountMinor)
        assertEquals("rent", QuickLogParser.parse("الإيجار 1,500").categoryKey)
    }

    @Test fun noAmountOrUnknownWord() {
        assertNull(QuickLogParser.parse("hello").amountMinor)
        assertNull(QuickLogParser.parse("75 حاجة").categoryKey)
    }
}
