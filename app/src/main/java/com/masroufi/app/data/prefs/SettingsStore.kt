package com.masroufi.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.masroufi.app.core.Payday
import com.masroufi.app.domain.LeftoverDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val payday: Payday = Payday.Day(25),
    val leftoverDefault: LeftoverDestination = LeftoverDestination.JAR,
    /** Step in pounds (0, 5, 10, 50). */
    val roundUpStep: Int = 0,
    val proverbSeed: Long,
    val onboarded: Boolean = false,
)

@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<Preferences>) {
    private val paydayKey = stringPreferencesKey("payday")
    private val leftoverKey = stringPreferencesKey("leftoverDefault")
    private val roundUpKey = intPreferencesKey("roundUpStep")
    private val seedKey = longPreferencesKey("proverbSeed")
    private val onboardedKey = booleanPreferencesKey("onboarded")

    /** Emits only after the seed exists; edit {} is serialised, so concurrent first reads share one seed. */
    val settings: Flow<Settings> = flow {
        store.edit { if (it[seedKey] == null) it[seedKey] = SecureRandom().nextLong() }
        emitAll(store.data.map(::decode))
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setPayday(v: Payday) { store.edit { it[paydayKey] = encode(v) } }
    suspend fun setLeftoverDefault(v: LeftoverDestination) { store.edit { it[leftoverKey] = v.name } }
    suspend fun setRoundUpStep(v: Int) {
        require(v in setOf(0, 5, 10, 50)) { "round-up step must be 0, 5, 10 or 50" }
        store.edit { it[roundUpKey] = v }
    }
    suspend fun setOnboarded(v: Boolean) { store.edit { it[onboardedKey] = v } }

    private fun decode(p: Preferences) = Settings(
        payday = p[paydayKey]?.let(::decodePayday) ?: Payday.Day(25),
        leftoverDefault = p[leftoverKey]?.let { runCatching { LeftoverDestination.valueOf(it) }.getOrNull() }
            ?: LeftoverDestination.JAR,
        roundUpStep = p[roundUpKey] ?: 0,
        proverbSeed = checkNotNull(p[seedKey]) { "seed is written before the first emit" },
        onboarded = p[onboardedKey] ?: false,
    )

    private fun encode(v: Payday) = when (v) {
        is Payday.Day -> "day:${v.day}"
        Payday.LastDay -> "last"
        Payday.NotFixed -> "notfixed"
    }

    // A corrupt value falls back to the default rather than crashing every settings read.
    private fun decodePayday(s: String): Payday = when {
        s == "last" -> Payday.LastDay
        s == "notfixed" -> Payday.NotFixed
        s.startsWith("day:") -> s.removePrefix("day:").toIntOrNull()?.takeIf { it in 1..31 }?.let { Payday.Day(it) }
            ?: Payday.Day(25)
        else -> Payday.Day(25)
    }
}
