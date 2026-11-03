package com.masroufi.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MoneyFormatterTest {
    private val ar = MoneyFormatter(Locale("ar", "EG"))
    private val en = MoneyFormatter(Locale.US)
    private val nbsp = " "

    @Test fun arabicUsesWesternDigitsAndShortSymbol() = assertEquals("1,105${nbsp}ج.م", ar.format(110_500, "EGP"))

    @Test fun decimalsOnlyWhenNotZero() {
        assertEquals("4,527.50${nbsp}ج.م", ar.format(452_750, "EGP"))
        assertEquals("48${nbsp}ج.م", ar.format(4_800, "EGP"))
    }

    @Test fun signs() {
        assertEquals("-320${nbsp}ج.م", ar.format(-32_000, "EGP"))
        assertEquals("+580${nbsp}ج.م", ar.format(58_000, "EGP", signed = true))
    }

    @Test fun englishPutsCurrencyFirst() = assertEquals("EGP${nbsp}1,105", en.format(110_500, "EGP"))

    @Test fun compactIsWholeWesternNumber() = assertEquals("1,106", ar.formatCompact(110_550, "EGP"))
}
