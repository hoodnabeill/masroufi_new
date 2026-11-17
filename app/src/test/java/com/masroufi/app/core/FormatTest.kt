package com.masroufi.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FormatTest {
    @Test fun formatPounds() {
        assertEquals("3,120", formatPounds(312_000))
        assertEquals("floors the piastres", "240", formatPounds(24_099))
        assertEquals("0", formatPounds(99))
        assertEquals("1,234,567", formatPounds(123_456_700))
    }

    @Test fun arabicDateWithWesternDigits() {
        assertEquals("الخميس 12 نوفمبر", formatDayDate(LocalDate.of(2026, 11, 12)))
        assertEquals("Thursday 12 November", formatDayDate(LocalDate.of(2026, 11, 12), Locale.ENGLISH))
    }
}
