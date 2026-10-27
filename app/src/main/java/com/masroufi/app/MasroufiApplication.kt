package com.masroufi.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.db.DefaultCategories
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.security.AppLockManager
import com.masroufi.app.work.DailyWorker
import com.masroufi.app.work.Notifier
import com.masroufi.app.work.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltAndroidApp
class MasroufiApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var recurring: RecurringRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var clock: Clock

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        lockManager.start()
        notifier.ensureChannels()
        DailyWorker.schedule(this)
        appScope.launch {
            if (db.categoryDao().all().isEmpty()) db.categoryDao().upsertAll(DefaultCategories.build(clock.millis()))
            recurring.processDue()
            if (settings.currentSync().isConfigured) SyncWorker.schedule(this@MasroufiApplication)
        }
    }
}
