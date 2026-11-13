package com.masroufi.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.masroufi.app.BuildConfig
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.entitlements.ReleaseEntitlements
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = Clock.systemDefaultZone()

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): TazkaraDatabase =
        Room.databaseBuilder(context, TazkaraDatabase::class.java, TazkaraDatabase.FILE_NAME).build()

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

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides @Singleton
    fun entitlements(): Entitlements = if (BuildConfig.DEBUG) DebugEntitlements() else ReleaseEntitlements()
}
