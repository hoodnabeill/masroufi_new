package com.masroufi.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.masroufi.app.data.backup.BackupSchedule
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.SeedData
import com.masroufi.app.di.AppScope
import com.masroufi.app.notify.NotificationPoster
import com.masroufi.app.notify.NotificationSchedule
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject

/** WorkManager starts on demand with the Hilt worker factory (the default initializer is removed in the manifest). */
@HiltAndroidApp
class MasroufiApplication : Application(), Configuration.Provider {
    @Inject lateinit var seed: SeedData
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var clock: Clock
    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var poster: NotificationPoster

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        poster.ensureChannels()
        appScope.launch {
            try {
                seed.ensureSeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing was written (one transaction); seeding runs again on the next launch.
                Log.e("MasroufiApplication", "first-launch seeding failed", e)
            }
            try {
                val s = settings.current()
                BackupSchedule.ensure(WorkManager.getInstance(this@MasroufiApplication), ZonedDateTime.now(clock), s.backupAuto && s.backupFolder != null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MasroufiApplication", "scheduling the background jobs failed", e)
            }
        }
        // The notification jobs follow the reminder hour: queued at start, and moved when the hour changes (onboarding, Settings, a restore).
        appScope.launch {
            var first = true
            settings.settings.map { it.reminderHour }.distinctUntilChanged().collect { hour ->
                try {
                    NotificationSchedule.ensure(WorkManager.getInstance(this@MasroufiApplication), ZonedDateTime.now(clock), hour, replace = !first)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MasroufiApplication", "scheduling the notifications failed", e)
                }
                first = false
            }
        }
    }
}
