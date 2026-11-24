package com.masroufi.app.notify

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Constraints
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Now is Tuesday 2026-11-17 09:00 Cairo. */
@RunWith(AndroidJUnit4::class)
class NotificationScheduleTest {
    private val now = ZonedDateTime.of(LocalDate.of(2026, 11, 17), LocalTime.of(9, 0), ZoneId.of("Africa/Cairo"))

    @Test fun theHourlyCheckRepeatsEveryHourWithNoConstraints() {
        val spec = NotificationSchedule.hourly().workSpec
        assertEquals(TimeUnit.HOURS.toMillis(1), spec.intervalDuration)
        assertEquals(Constraints.NONE, spec.constraints)
    }

    @Test fun theEveningOneOffWaitsForTheReminderHourToday() {
        assertEquals(TimeUnit.HOURS.toMillis(12), NotificationSchedule.at(now, 21).workSpec.initialDelay)
        assertEquals(Constraints.NONE, NotificationSchedule.at(now, 21).workSpec.constraints)
    }

    @Test fun theStreakOneOffWaitsForTwentyThree() {
        assertEquals(TimeUnit.HOURS.toMillis(14), NotificationSchedule.at(now, NotificationSchedule.STREAK_HOUR).workSpec.initialDelay)
    }

    @Test fun anHourThatHasPassedMeansTomorrow() {
        assertEquals(TimeUnit.HOURS.toMillis(23), NotificationSchedule.at(now, 8).workSpec.initialDelay)
        // exactly now is not "later": the next one is tomorrow
        assertEquals(TimeUnit.HOURS.toMillis(24), NotificationSchedule.at(now, 9).workSpec.initialDelay)
    }
}
