package com.masroufi.app.di

import android.content.Context
import androidx.room.Room
import com.masroufi.app.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
