package com.masroufi.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.masroufi.app.core.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class DashboardSection { BALANCE, QUICK_ACTIONS, SPENDING, BUDGETS, GOALS, UPCOMING, DEBTS, INSIGHTS, RECENT }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val primaryCurrency: String = Money.DEFAULT_CURRENCY,
    val hiddenSections: Set<DashboardSection> = emptySet(),
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 30,
    val hideInRecents: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val lastBackupAt: Long? = null,
)

data class PinRecord(val hash: String, val salt: String)

data class SyncSettings(
    val url: String = "",
    val username: String = "",
    val encryptedPassword: String? = null,
    val encryptedPassphrase: String? = null,
    val lastSyncAt: Long? = null,
    val lastStatus: String? = null,
    val lastConflicts: Int = 0,
    val remoteEtag: String? = null,
) {
    val isConfigured: Boolean get() = url.isNotBlank() && encryptedPassphrase != null
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.dataStore

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val currency = stringPreferencesKey("primary_currency")
        val hidden = stringSetPreferencesKey("hidden_sections")
        val lock = booleanPreferencesKey("lock_enabled")
        val biometric = booleanPreferencesKey("biometric_enabled")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val hideRecents = booleanPreferencesKey("hide_recents")
        val notifications = booleanPreferencesKey("notifications")
        val lastBackup = longPreferencesKey("last_backup")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinFailures = intPreferencesKey("pin_failures")
        val pinLockedUntil = longPreferencesKey("pin_locked_until")
        val syncUrl = stringPreferencesKey("sync_url")
        val syncUser = stringPreferencesKey("sync_user")
        val syncPassword = stringPreferencesKey("sync_password_enc")
        val syncPassphrase = stringPreferencesKey("sync_passphrase_enc")
        val syncLast = longPreferencesKey("sync_last")
        val syncStatus = stringPreferencesKey("sync_status")
        val syncConflicts = intPreferencesKey("sync_conflicts")
        val syncEtag = stringPreferencesKey("sync_etag")
        val lastWallet = stringPreferencesKey("last_wallet")
    }

    // A corrupt or unreadable file falls back to defaults instead of crashing the app.
    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.onboarding] ?: false,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: false,
            primaryCurrency = p[Keys.currency] ?: Money.DEFAULT_CURRENCY,
            hiddenSections = p[Keys.hidden].orEmpty().mapNotNull { runCatching { DashboardSection.valueOf(it) }.getOrNull() }.toSet(),
            lockEnabled = p[Keys.lock] ?: false,
            biometricEnabled = p[Keys.biometric] ?: false,
            lockTimeoutSeconds = p[Keys.lockTimeout] ?: 30,
            hideInRecents = p[Keys.hideRecents] ?: true,
            notificationsEnabled = p[Keys.notifications] ?: true,
            lastBackupAt = p[Keys.lastBackup],
        )
    }

    val sync: Flow<SyncSettings> = data.map { p ->
        SyncSettings(
            url = p[Keys.syncUrl].orEmpty(),
            username = p[Keys.syncUser].orEmpty(),
            encryptedPassword = p[Keys.syncPassword],
            encryptedPassphrase = p[Keys.syncPassphrase],
            lastSyncAt = p[Keys.syncLast],
            lastStatus = p[Keys.syncStatus],
            lastConflicts = p[Keys.syncConflicts] ?: 0,
            remoteEtag = p[Keys.syncEtag],
        )
    }

    suspend fun current(): AppSettings = settings.first()
    suspend fun currentSync(): SyncSettings = sync.first()

    suspend fun pin(): PinRecord? = data.first().let { p ->
        val hash = p[Keys.pinHash] ?: return@let null
        PinRecord(hash, p[Keys.pinSalt] ?: return@let null)
    }

    suspend fun pinThrottle(): Pair<Int, Long> = data.first().let { (it[Keys.pinFailures] ?: 0) to (it[Keys.pinLockedUntil] ?: 0L) }

    suspend fun setPinThrottle(failures: Int, lockedUntil: Long) = store.edit {
        it[Keys.pinFailures] = failures
        it[Keys.pinLockedUntil] = lockedUntil
    }

    suspend fun lastWalletId(): String? = data.first()[Keys.lastWallet]
    suspend fun setLastWalletId(id: String) = store.edit { it[Keys.lastWallet] = id }

    suspend fun setOnboardingDone(currency: String) = store.edit {
        it[Keys.onboarding] = true
        it[Keys.currency] = currency
    }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamic] = enabled }
    suspend fun setPrimaryCurrency(currency: String) = store.edit { it[Keys.currency] = currency }
    suspend fun setHiddenSections(sections: Set<DashboardSection>) = store.edit { p -> p[Keys.hidden] = sections.map { it.name }.toSet() }
    suspend fun setNotifications(enabled: Boolean) = store.edit { it[Keys.notifications] = enabled }
    suspend fun setHideInRecents(enabled: Boolean) = store.edit { it[Keys.hideRecents] = enabled }
    suspend fun setLockTimeout(seconds: Int) = store.edit { it[Keys.lockTimeout] = seconds }
    suspend fun setBiometric(enabled: Boolean) = store.edit { it[Keys.biometric] = enabled }
    suspend fun setLastBackup(at: Long) = store.edit { it[Keys.lastBackup] = at }

    suspend fun enableLock(pin: PinRecord) = store.edit {
        it[Keys.pinHash] = pin.hash
        it[Keys.pinSalt] = pin.salt
        it[Keys.lock] = true
        it[Keys.pinFailures] = 0
    }

    suspend fun disableLock() = store.edit {
        it.remove(Keys.pinHash)
        it.remove(Keys.pinSalt)
        it[Keys.lock] = false
        it[Keys.biometric] = false
    }

    suspend fun saveSyncConfig(url: String, username: String, encryptedPassword: String?, encryptedPassphrase: String) = store.edit {
        it[Keys.syncUrl] = url
        it[Keys.syncUser] = username
        if (encryptedPassword != null) it[Keys.syncPassword] = encryptedPassword else it.remove(Keys.syncPassword)
        it[Keys.syncPassphrase] = encryptedPassphrase
        it.remove(Keys.syncEtag)
    }

    suspend fun clearSyncConfig() = store.edit {
        listOf(Keys.syncUrl, Keys.syncUser, Keys.syncPassword, Keys.syncPassphrase, Keys.syncStatus, Keys.syncEtag).forEach { key -> it.remove(key) }
        it.remove(Keys.syncLast)
        it.remove(Keys.syncConflicts)
    }

    suspend fun recordSync(at: Long?, status: String, conflicts: Int, etag: String?) = store.edit {
        if (at != null) it[Keys.syncLast] = at
        it[Keys.syncStatus] = status
        it[Keys.syncConflicts] = conflicts
        if (etag != null) it[Keys.syncEtag] = etag
    }
}
