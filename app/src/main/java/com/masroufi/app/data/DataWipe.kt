package com.masroufi.app.data

import android.content.Context
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** v1's Room file and its preferences DataStore ("settings"); v2 never reads them, but they hold the old data. */
internal const val V1_DATABASE = "masroufi.db"
internal const val V1_SETTINGS_FILE = "datastore/settings.preferences_pb"

/** "Delete all my data" (decision 6): every table, `tazkara_settings`, and v1's leftovers; the app then restarts at onboarding. */
@Singleton
class DataWipe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: TazkaraDatabase,
    private val settings: SettingsStore,
) {
    suspend fun wipe() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settings.clearAll()
        withContext(Dispatchers.IO) {
            context.deleteDatabase(V1_DATABASE) // also removes its -journal / -wal / -shm
            File(context.filesDir, V1_SETTINGS_FILE).delete()
        }
    }
}
