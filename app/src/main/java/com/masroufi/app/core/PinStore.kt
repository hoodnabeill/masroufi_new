package com.masroufi.app.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.masroufi.app.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** A box for a small secret: [decrypt] throws a [GeneralSecurityException] when the box cannot be opened. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

/** AES-GCM with a key that lives in the Android Keystore and never leaves it (iv + ciphertext). */
class KeystoreCipher(private val alias: String = "tazkara_pin_key") : SecretCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        if (data.size <= GCM_IV_BYTES) throw GeneralSecurityException("box too short")
        val c = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, data, 0, GCM_IV_BYTES))
        }
        return c.doFinal(data, GCM_IV_BYTES, data.size - GCM_IV_BYTES)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}

sealed interface PinResult {
    data object Ok : PinResult

    /** A wrong PIN: [failures] in a row so far; [waitMillis] > 0 when this try started a wait. */
    data class Wrong(val failures: Int, val waitMillis: Long) : PinResult

    /** Too many wrong tries: nothing is checked until [remainingMillis] passed. */
    data class Waiting(val remainingMillis: Long) : PinResult
    data object NotSet : PinResult

    /** The stored PIN cannot be opened any more (the Keystore key is gone): only the fingerprint or a data wipe gets out. */
    data object Unreadable : PinResult
}

/** 11-DATA "Lock": 5 wrong tries = 30 s, then the wait doubles for every further wrong try, up to 15 minutes. */
fun pinWaitMillis(failures: Int): Long =
    if (failures < PIN_TRIES_BEFORE_WAIT) 0L else minOf(FIRST_WAIT_MS shl minOf(failures - PIN_TRIES_BEFORE_WAIT, 20), MAX_WAIT_MS)

/** Wrong tries in a row before the first wait. */
const val PIN_TRIES_BEFORE_WAIT = 5
private const val FIRST_WAIT_MS = 30_000L
private const val MAX_WAIT_MS = 15 * 60_000L

const val PIN_MIN_DIGITS = 4
const val PIN_MAX_DIGITS = 6

/** The salted PBKDF2 hash of a PIN kept in [store] inside a [cipher] box, with the wrong-try waits (counted against [clock]). */
class PinStore(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val clock: Clock,
    private val iterations: Int = PBKDF2_ITERATIONS,
) {
    private val blobKey = stringPreferencesKey("pinBlob")
    private val failsKey = intPreferencesKey("pinFailures")
    private val untilKey = longPreferencesKey("pinWaitUntil")

    suspend fun isSet(): Boolean = store.data.first()[blobKey] != null

    /** Milliseconds still to wait before a PIN is checked; 0 = none. */
    suspend fun waitRemaining(): Long = ((store.data.first()[untilKey] ?: 0L) - clock.millis()).coerceAtLeast(0L)

    /** The first PIN; once one is set only [change] replaces it. */
    suspend fun set(pin: String) {
        requireValid(pin)
        check(!isSet()) { "a PIN is set already: use change" }
        write(pin)
    }

    /** Forgets the PIN (after the parent proved themself another way, e.g. the PIN cannot be read any more). */
    suspend fun clear() {
        store.edit { it.remove(blobKey); it.remove(failsKey); it.remove(untilKey) }
    }

    /** Replaces the PIN after the old one verified; the result says how that went ([PinResult.Ok] = changed). */
    suspend fun change(old: String, new: String): PinResult {
        requireValid(new)
        val r = verify(old)
        if (r == PinResult.Ok) write(new)
        return r
    }

    suspend fun verify(pin: String): PinResult {
        val prefs = store.data.first()
        val blob = prefs[blobKey] ?: return PinResult.NotSet
        val now = clock.millis()
        val until = prefs[untilKey] ?: 0L
        if (now < until) return PinResult.Waiting(until - now)
        val record = try {
            Record.of(cipher.decrypt(Base64.decode(blob, Base64.NO_WRAP)))
        } catch (e: GeneralSecurityException) {
            return PinResult.Unreadable
        } catch (e: IllegalArgumentException) {
            return PinResult.Unreadable
        }
        val ok = isValid(pin) && MessageDigest.isEqual(hash(pin, record.salt, record.iterations), record.hash)
        if (ok) {
            store.edit { it.remove(failsKey); it.remove(untilKey) }
            return PinResult.Ok
        }
        val failures = (prefs[failsKey] ?: 0) + 1
        val wait = pinWaitMillis(failures)
        store.edit {
            it[failsKey] = failures
            if (wait > 0) it[untilKey] = now + wait else it.remove(untilKey)
        }
        return PinResult.Wrong(failures, wait)
    }

    private suspend fun write(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val record = Record(iterations, salt, hash(pin, salt, iterations))
        val blob = Base64.encodeToString(cipher.encrypt(record.toBytes()), Base64.NO_WRAP)
        store.edit {
            it[blobKey] = blob
            it.remove(failsKey)
            it.remove(untilKey)
        }
    }

    private suspend fun hash(pin: String, salt: ByteArray, iterations: Int): ByteArray = withContext(Dispatchers.Default) {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)).encoded
    }

    private fun isValid(pin: String) = pin.length in PIN_MIN_DIGITS..PIN_MAX_DIGITS && pin.all { it in '0'..'9' }
    private fun requireValid(pin: String) = require(isValid(pin)) { "a PIN is $PIN_MIN_DIGITS to $PIN_MAX_DIGITS digits" }

    private class Record(val iterations: Int, val salt: ByteArray, val hash: ByteArray) {
        fun toBytes(): ByteArray = ByteBuffer.allocate(4 + salt.size + hash.size).putInt(iterations).put(salt).put(hash).array()

        companion object {
            fun of(bytes: ByteArray): Record {
                require(bytes.size == 4 + SALT_BYTES + HASH_BITS / 8) { "unexpected PIN record" }
                val b = ByteBuffer.wrap(bytes)
                val iterations = b.getInt()
                return Record(iterations, ByteArray(SALT_BYTES).also { b.get(it) }, ByteArray(HASH_BITS / 8).also { b.get(it) })
            }
        }
    }

    companion object {
        const val PBKDF2_ITERATIONS = 600_000
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
    }
}

/**
 * The child screen as a mode (phase 9b decision 6), kept in the settings so a cold start, back and recents all land on it.
 * It is cleared only after the parent's PIN verified or the system biometric prompt succeeded.
 */
@Singleton
class ChildMode @Inject constructor(private val settings: SettingsStore, private val pin: PinStore) {
    val activeChildId: Flow<Long?> = settings.settings.map { it.childModeId }

    /** Pins the app to [childId]; a PIN must exist first (the screen asks the parent to set one the first time). */
    suspend fun enter(childId: Long) {
        check(pin.isSet()) { "set a PIN before the child screen" }
        settings.setChildMode(childId)
    }

    /** Leaves when [entered] is the parent's PIN; any other result keeps the mode. */
    suspend fun leaveWithPin(entered: String): PinResult {
        val r = pin.verify(entered)
        if (r == PinResult.Ok) settings.setChildMode(null)
        return r
    }

    /** Only for the success callback of the system biometric prompt (the app never sees biometric data). */
    suspend fun leaveAfterBiometric() = settings.setChildMode(null)

    /** The PIN cannot be read: after the parent's biometric or device credential, forget it and leave; the next hand-over asks for a new one. */
    suspend fun resetPinAfterBiometric() {
        pin.clear()
        settings.setChildMode(null)
    }
}
