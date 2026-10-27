package com.masroufi.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypts small secrets (WebDAV password, sync passphrase) with a non-exportable AES key in Android Keystore. */
@Singleton
class KeystoreCipher @Inject constructor() {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "masroufi_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** Salted PBKDF2 hash for the app PIN; the PIN itself is never stored. */
object PinHasher {
    private const val ITERATIONS = 120_000

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun verify(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt), expected)
}

/**
 * Password-based AES-256-GCM used for backup files and WebDAV sync payloads.
 * Layout: MAGIC(5) | salt(16) | iv(12) | ciphertext+tag.
 */
object PayloadCrypto {
    private val MAGIC = "MSRF1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 210_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12

    class WrongPasswordException : Exception()
    class NotABackupException : Exception()

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        val header = MAGIC.size + SALT_SIZE + IV_SIZE
        if (data.size <= header || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackupException()
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_SIZE)
        val iv = data.copyOfRange(MAGIC.size + SALT_SIZE, header)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return try {
            cipher.doFinal(data, header, data.size - header)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
