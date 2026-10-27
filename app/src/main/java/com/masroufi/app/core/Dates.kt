package com.masroufi.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun LocalDate.startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Half-open millisecond window [start, end). */
data class DateWindow(val startMillis: Long, val endMillis: Long) {
    companion object {
        fun month(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()) = DateWindow(
            month.atDay(1).startMillis(zone),
            month.plusMonths(1).atDay(1).startMillis(zone),
        )

        fun days(start: LocalDate, endInclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
            DateWindow(start.startMillis(zone), endInclusive.plusDays(1).startMillis(zone))
    }
}
