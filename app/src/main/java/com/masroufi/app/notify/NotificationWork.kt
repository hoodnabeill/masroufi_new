package com.masroufi.app.notify

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.masroufi.app.data.backup.BackupSchedule
import com.masroufi.app.data.prefs.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** One check of the notification rules (see [NotificationService]); the hourly job and the two timed ones all run this. */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: NotificationService,
    private val settings: SettingsStore,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            service.run()
            // Queues tomorrow's timed ones once today's have run (KEEP: a pending or running one stays).
            NotificationSchedule.ensure(WorkManager.getInstance(applicationContext), ZonedDateTime.now(clock), settings.current().reminderHour)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next hour tries again; no retry storm.
            Log.e("NotificationWorker", "notification check failed", e)
        }
        return Result.success()
    }
}

/**
 * Three jobs, none needing the exact-alarm permission: an hourly check (the catch-all, no constraints) and two one-offs
 * aimed at the top of the evening-reminder hour and at 23:00 (streak). A one-off does not re-queue itself: the hourly check
 * (and every app start) re-queues it for the next day with KEEP, so a pending one is never replaced while it can run.
 * WorkManager re-arms its persisted work after a reboot by itself, so there is no boot receiver.
 */
object NotificationSchedule {
    const val HOURLY_NAME = "notify-hourly"
    const val EVENING_NAME = "notify-evening"
    const val STREAK_NAME = "notify-streak"
    const val STREAK_HOUR = 23

    fun hourly(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS).build()

    /** A one-off at the next [hour]:00 after [now]. */
    fun at(now: ZonedDateTime, hour: Int): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(Duration.between(now, BackupSchedule.next(now, LocalTime.of(hour, 0), null)).toMillis(), TimeUnit.MILLISECONDS)
            .build()

    /**
     * Queues what is missing. [replace] (the reminder hour just changed) moves the evening one-off to the new hour;
     * a null hour cancels it.
     */
    fun ensure(wm: WorkManager, now: ZonedDateTime, reminderHour: Int?, replace: Boolean = false) {
        wm.enqueueUniquePeriodicWork(HOURLY_NAME, ExistingPeriodicWorkPolicy.KEEP, hourly())
        wm.enqueueUniqueWork(STREAK_NAME, ExistingWorkPolicy.KEEP, at(now, STREAK_HOUR))
        if (reminderHour == null) {
            wm.cancelUniqueWork(EVENING_NAME)
        } else {
            wm.enqueueUniqueWork(EVENING_NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, at(now, reminderHour))
        }
    }
}
