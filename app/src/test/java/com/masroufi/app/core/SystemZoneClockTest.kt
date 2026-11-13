package com.masroufi.app.core

import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemZoneClockTest {
    @Test fun zoneFollowsDefaultTimeZoneChanges() {
        val saved = TimeZone.getDefault()
        try {
            val clock = SystemZoneClock()
            TimeZone.setDefault(TimeZone.getTimeZone("Africa/Cairo"))
            assertEquals(ZoneId.of("Africa/Cairo"), clock.zone)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(ZoneId.of("Asia/Tokyo"), clock.zone)
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
