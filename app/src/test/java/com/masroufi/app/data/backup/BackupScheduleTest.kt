package com.masroufi.app.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Constraints
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Now is Tuesday 2026-11-17 09:00 Cairo. */
@RunWith(AndroidJUnit4::class)
class BackupScheduleTest {
    private val now = ZonedDateTime.of(LocalDate.of(2026, 11, 17), LocalTime.of(9, 0), ZoneId.of("Africa/Cairo"))

    @Test fun theWeeklyBackupRunsEverySevenDaysWhileChargingAndIdle() {
        val spec = BackupSchedule.weekly(now).workSpec
        assertEquals(TimeUnit.DAYS.toMillis(7), spec.intervalDuration)
        assertEquals(Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true).build(), spec.constraints)
    }

    @Test fun theWeeklyBackupFirstRunsOnFridayAtTwoInTheNight() {
        val spec = BackupSchedule.weekly(now).workSpec
        assertEquals(TimeUnit.HOURS.toMillis(2 * 24 + 17), spec.initialDelay) // Tuesday 09:00 -> Friday 02:00
    }

    @Test fun theMidnightPrintRunsDailyJustAfterMidnightWithNoConstraints() {
        val spec = BackupSchedule.midnight(now).workSpec
        assertEquals(TimeUnit.DAYS.toMillis(1), spec.intervalDuration)
        assertEquals(Constraints.NONE, spec.constraints)
        assertEquals(TimeUnit.MINUTES.toMillis(15 * 60 + 5), spec.initialDelay) // 09:00 -> 00:05
    }

    @Test fun theNextFridayNightIsNextWeekWhenFridayNightHasPassed() {
        val fridayNoon = ZonedDateTime.of(LocalDate.of(2026, 11, 20), LocalTime.of(12, 0), now.zone)
        val next = BackupSchedule.next(fridayNoon, LocalTime.of(2, 0), DayOfWeek.FRIDAY)
        assertEquals(LocalDate.of(2026, 11, 27), next.toLocalDate())
    }
}
