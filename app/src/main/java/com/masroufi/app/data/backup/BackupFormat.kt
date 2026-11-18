package com.masroufi.app.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The plain, readable part of a backup file: nothing in it needs the password. */
class BackupHeader(
    val version: Int,
    val salt: ByteArray,
    val iterations: Int,
    /** Epoch millis. */
    val createdAt: Long,
    val tickets: Int,
    val appVersion: String,
)

/** A backup key: the password stretched with [salt] ([iterations] rounds). The password itself is never kept. */
class BackupKey(val salt: ByteArray, val iterations: Int, val key: ByteArray)

/** The file is not a Masroufi backup, or it is cut short or changed. */
class BackupDamaged(message: String) : IOException(message)

/** The password is wrong or the encrypted part was changed (AES-GCM cannot tell the two apart). */
class BackupWrongKey : GeneralSecurityException("wrong password or changed file")

/**
 * One file: header (magic, version, salt, iterations, created-at, ticket count, app version) + nonce + AES-256-GCM of a
 * zip. The header is authenticated as GCM associated data, so changing any byte of it fails the open.
 * shortcut: the whole file is built and read in memory (a few MB); stream it if backups ever reach hundreds of MB.
 */
object BackupFormat {
    const val VERSION = 1
    const val DB_ENTRY = "tazkara.db"
    /** Only present when a checkpoint left part of the write-ahead log behind. */
    const val WAL_ENTRY = "tazkara.db-wal"
    const val SETTINGS_ENTRY = "settings.pb"
    /** The key-stretching rounds of a real backup (PinStore's count); tests pass fewer. */
    const val ITERATIONS = 600_000

    private const val MAGIC = "MSRFBK"
    private const val NONCE_BYTES = 12
    private const val SALT_BYTES = 16
    private const val TAG_BITS = 128
    private const val MAX_ITERATIONS = 5_000_000
    private val random = SecureRandom()

    fun newKey(password: String, iterations: Int = ITERATIONS): BackupKey {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        return BackupKey(salt, iterations, derive(password, salt, iterations))
    }

    fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256)).encoded

    /** Header, nonce and ciphertext of [zip] under [key]. */
    fun encrypt(zip: ByteArray, key: BackupKey, createdAt: Long, tickets: Int, appVersion: String): ByteArray {
        val head = ByteArrayOutputStream()
        DataOutputStream(head).run {
            writeBytes(MAGIC)
            writeInt(VERSION)
            writeByte(key.salt.size)
            write(key.salt)
            writeInt(key.iterations)
            writeLong(createdAt)
            writeInt(tickets)
            writeUTF(appVersion)
        }
        val aad = head.toByteArray()
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return aad + nonce + cipher.doFinal(zip)
    }

    /** Reads the header only; throws [BackupDamaged] for anything that is not a backup. */
    fun readHeader(file: ByteArray): BackupHeader = parse(file).header

    /** The zip inside [file], opened with [password]; [BackupWrongKey] when it does not open. */
    fun decrypt(file: ByteArray, password: String): ByteArray {
        val p = parse(file)
        return open(file, p, derive(password, p.header.salt, p.header.iterations))
    }

    /** Same, with an already stretched [key] (the automatic backup's sealed one). */
    fun decrypt(file: ByteArray, key: ByteArray): ByteArray = open(file, parse(file), key)

    private fun open(file: ByteArray, p: Parsed, key: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, file, p.aadEnd, NONCE_BYTES))
        cipher.updateAAD(file, 0, p.aadEnd)
        val from = p.aadEnd + NONCE_BYTES
        cipher.doFinal(file, from, file.size - from)
    } catch (e: GeneralSecurityException) {
        throw BackupWrongKey()
    }

    private class Parsed(val header: BackupHeader, val aadEnd: Int)

    private fun parse(file: ByteArray): Parsed {
        try {
            val input = DataInputStream(ByteArrayInputStream(file))
            val magic = ByteArray(MAGIC.length).also { input.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != MAGIC) throw BackupDamaged("not a backup")
            val version = input.readInt()
            if (version != VERSION) throw BackupDamaged("unknown format version $version")
            val salt = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            val iterations = input.readInt()
            if (iterations !in 1..MAX_ITERATIONS) throw BackupDamaged("bad iteration count")
            val createdAt = input.readLong()
            val tickets = input.readInt()
            val appVersion = input.readUTF()
            val aadEnd = file.size - input.available()
            if (file.size - aadEnd <= NONCE_BYTES) throw BackupDamaged("no data")
            return Parsed(BackupHeader(version, salt, iterations, createdAt, tickets, appVersion), aadEnd)
        } catch (e: IOException) {
            throw if (e is BackupDamaged) e else BackupDamaged("cut short")
        }
    }

    fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun unzip(zip: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(zip)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    out[e.name] = z.readBytes()
                }
            }
        } catch (e: IOException) {
            throw BackupDamaged("bad zip")
        }
        return out
    }

    /** Of the backup files in a folder, the names to delete: all but the newest [keep] (the names carry the time). */
    fun toDelete(names: List<String>, keep: Int = 4): List<String> =
        names.filter { it.startsWith(FILE_PREFIX) && it.endsWith(FILE_SUFFIX) }.sortedDescending().drop(keep)

    const val FILE_PREFIX = "masroufi-"
    const val FILE_SUFFIX = ".masroufi"
}
