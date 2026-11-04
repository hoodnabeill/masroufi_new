package com.masroufi.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.masroufi.app.R
import com.masroufi.app.core.MoneyFormatter
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.ProgressRepository
import com.masroufi.app.data.repo.TodayRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The one daily nudge chosen in onboarding ("إمتى نفكّرك تسجّل؟"): at that hour, if nothing was logged today, a
 * notification with today's number. Days already logged get nothing, so it never nags.
 */
@HiltWorker
class LogReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val progress: ProgressRepository,
    private val today: TodayRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.notificationsEnabled || s.logReminderHour < 0) return Result.success()
        val p = progress.observe().first()
        if (p.streak.loggedToday) return Result.success()
        val t = today.observe().first()
        val ctx = applicationContext
        val number = MoneyFormatter(Locale.getDefault()).format(t.remainingMinor.coerceAtLeast(0), t.currency)
        notifier.show(
            NOTIFICATION_ID,
            ctx.getString(R.string.remind_log_title, number),
            if (p.streak.current > 0) ctx.getString(R.string.remind_log_body_streak, p.streak.current + 1) else ctx.getString(R.string.remind_log_body_start),
        )
        return Result.success()
    }

    companion object {
        private const val NAME = "log_reminder"
        private const val NOTIFICATION_ID = 7_001

        /** Fires every day at [hour] (local time); a negative hour turns the reminder off. */
        fun schedule(context: Context, hour: Int) {
            val work = WorkManager.getInstance(context)
            if (hour < 0) {
                work.cancelUniqueWork(NAME)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            work.enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LogReminderWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}
