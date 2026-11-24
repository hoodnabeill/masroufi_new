package com.masroufi.app.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The summary lives as one JSON string in the settings DataStore (no migration; "delete all my data" clears it with the rest). */
@Singleton
class DaySummaryStore @Inject constructor(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("daySummary")

    suspend fun read(): DaySummary? = daySummaryFromJson(store.data.first()[key])

    suspend fun write(summary: DaySummary) {
        val text = summary.toJson()
        store.edit { if (it[key] != text) it[key] = text }
    }
}
