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
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
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
    }
}
