package com.masroufi.app.core

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Wall clock that re-reads the device zone on every call, so a timezone change takes effect without a restart. */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun instant(): Instant = Instant.now()
    override fun withZone(zone: ZoneId): Clock = system(zone)
}
