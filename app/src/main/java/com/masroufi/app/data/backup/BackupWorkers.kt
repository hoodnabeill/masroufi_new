package com.masroufi.app.data.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.TicketRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** The weekly automatic backup (decision 5): the work is [BackupService.runAuto]; a failure is noted there, quietly. */
@HiltWorker
class WeeklyBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backup: BackupService,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (backup.runAuto()) Result.success() else Result.failure()
}

/** Prints the new day's ticket just after midnight (decision 6), so widgets and notifications find it. */
@HiltWorker
class MidnightPrintWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tickets: TicketRepository,
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            if (settings.current().onboarded) tickets.printToday()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Opening the app prints the day anyway; no retry storm.
            Log.e("MidnightPrintWorker", "print failed", e)
        }
        return Result.success()
    }
}

/** The two periodic jobs. Requests are built from the clock so the constraints and period can be asserted. */
object BackupSchedule {
    const val WEEKLY_NAME = "weekly-backup"
    const val MIDNIGHT_NAME = "midnight-print"

    /** Fridays at 02:00 (the first run only; WorkManager then repeats every 7 days, while charging and idle). */
    private val FRIDAY_NIGHT = LocalTime.of(2, 0)
    private val JUST_AFTER_MIDNIGHT = LocalTime.of(0, 5)

    fun weekly(now: ZonedDateTime): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<WeeklyBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresDeviceIdle(true).build())
            .setInitialDelay(millisUntil(now, next(now, FRIDAY_NIGHT, DayOfWeek.FRIDAY)), TimeUnit.MILLISECONDS)
            .build()

    fun midnight(now: ZonedDateTime): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<MidnightPrintWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntil(now, next(now, JUST_AFTER_MIDNIGHT, null)), TimeUnit.MILLISECONDS)
            .build()

    /** The first moment after [now] at [time], on [day] when given. */
    fun next(now: ZonedDateTime, time: LocalTime, day: DayOfWeek?): ZonedDateTime {
        var t = now.with(time)
        if (day != null) t = t.with(TemporalAdjusters.nextOrSame(day))
        if (!t.isAfter(now)) t = t.plusDays(if (day != null) 7 else 1)
        return t
    }

    private fun millisUntil(from: ZonedDateTime, to: ZonedDateTime) = java.time.Duration.between(from, to).toMillis()

    /** Called on every start: the midnight print is queued once; the weekly backup follows the switch and the chosen folder. */
    fun ensure(wm: WorkManager, now: ZonedDateTime, weeklyOn: Boolean) {
        wm.enqueueUniquePeriodicWork(MIDNIGHT_NAME, ExistingPeriodicWorkPolicy.KEEP, midnight(now))
        setWeekly(wm, now, weeklyOn)
    }

    fun setWeekly(wm: WorkManager, now: ZonedDateTime, on: Boolean) {
        if (on) wm.enqueueUniquePeriodicWork(WEEKLY_NAME, ExistingPeriodicWorkPolicy.UPDATE, weekly(now)) else wm.cancelUniqueWork(WEEKLY_NAME)
    }
}
