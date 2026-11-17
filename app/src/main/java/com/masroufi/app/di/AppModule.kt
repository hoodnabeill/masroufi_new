package com.masroufi.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.masroufi.app.BuildConfig
import com.masroufi.app.core.SystemZoneClock
import com.masroufi.app.data.db.MIGRATION_1_2
import com.masroufi.app.data.db.MIGRATION_2_3
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.entitlements.ReleaseEntitlements
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** The application-wide scope: work that outlives any screen (e.g. first-launch seeding). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/** The app process's lifecycle (ProcessLifecycleOwner): ON_START is the app coming to the foreground. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProcessLifecycle

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = SystemZoneClock()

    @Provides @ProcessLifecycle fun processLifecycle(): Lifecycle = ProcessLifecycleOwner.get().lifecycle

    @Provides @Singleton @AppScope fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): TazkaraDatabase =
        Room.databaseBuilder(context, TazkaraDatabase::class.java, TazkaraDatabase.FILE_NAME).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    @Provides fun pocketDao(db: TazkaraDatabase) = db.pocketDao()
    @Provides fun entryDao(db: TazkaraDatabase) = db.entryDao()
    @Provides fun categoryDao(db: TazkaraDatabase) = db.categoryDao()
    @Provides fun dayTicketDao(db: TazkaraDatabase) = db.dayTicketDao()
    @Provides fun envelopeDao(db: TazkaraDatabase) = db.envelopeDao()
    @Provides fun recurringDao(db: TazkaraDatabase) = db.recurringDao()
    @Provides fun jarDao(db: TazkaraDatabase) = db.jarDao()
    @Provides fun goalDao(db: TazkaraDatabase) = db.goalDao()
    @Provides fun proverbDao(db: TazkaraDatabase) = db.proverbDao()
    @Provides fun stampDao(db: TazkaraDatabase) = db.stampDao()
    @Provides fun seasonDao(db: TazkaraDatabase) = db.seasonDao()

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        // Not v1's "settings" file: v2 starts fresh and must not inherit v1 keys (PIN hash, sync secrets).
        // A corrupt file resets to empty instead of crashing every launch; the seed regenerates in edit {}.
        PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }) {
            context.preferencesDataStoreFile("tazkara_settings")
        }

    @Provides @Singleton
    fun entitlements(): Entitlements = if (BuildConfig.DEBUG) DebugEntitlements() else ReleaseEntitlements()
}
