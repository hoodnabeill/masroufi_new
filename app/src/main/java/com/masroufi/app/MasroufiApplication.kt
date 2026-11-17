package com.masroufi.app

import android.app.Application
import android.util.Log
import com.masroufi.app.data.repo.SeedData
import com.masroufi.app.di.AppScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MasroufiApplication : Application() {
    @Inject lateinit var seed: SeedData
    @Inject @AppScope lateinit var appScope: CoroutineScope

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
        }
    }
}
