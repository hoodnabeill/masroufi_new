package com.masroufi.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class TokensTest {
    @Test fun colours() {
        assertEquals(Color(0xFF0A1033), Tz.night)
        assertEquals(Color(0xFFFFC72C), Tz.yellow)
        assertEquals(Color(0xFF35E0D0), Tz.turquoise)
        assertEquals(Color(0xFFFF6B4A), Tz.carnelian)
    }

    @Test fun ticketType() {
        assertEquals(104.sp, TzType.ticket.fontSize)
    }

    @Test fun everyStyleIsTabular() {
        val styles = listOf(
            TzType.ticket, TzType.hero, TzType.card, TzType.rowNumber, TzType.titleTab, TzType.titlePushed,
            TzType.cardHeading, TzType.button, TzType.body, TzType.label, TzType.caption,
        )
        assertEquals(11, styles.size)
        styles.forEach { assertEquals("tnum", it.fontFeatureSettings) }
    }
}
