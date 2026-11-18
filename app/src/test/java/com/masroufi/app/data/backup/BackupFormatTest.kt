package com.masroufi.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupFormatTest {
    private val iterations = 1_000
    private val zip = BackupFormat.zip(listOf("a.txt" to "مرحبا".toByteArray(), "b.bin" to byteArrayOf(1, 2, 3)))

    private fun file(password: String = "pw-123456", createdAt: Long = 1_700_000_000_000L, tickets: Int = 412): ByteArray =
        BackupFormat.encrypt(zip, BackupFormat.newKey(password, iterations), createdAt, tickets, "9.9.9")

    @Test fun encryptedFileOpensWithThePasswordAndGivesTheSameZip() {
        val opened = BackupFormat.decrypt(file(), "pw-123456")
        assertArrayEquals(zip, opened)
        val parts = BackupFormat.unzip(opened)
        assertEquals("مرحبا", String(parts.getValue("a.txt")))
        assertArrayEquals(byteArrayOf(1, 2, 3), parts.getValue("b.bin"))
    }

    @Test fun aWrongPasswordDoesNotOpen() {
        try {
            BackupFormat.decrypt(file(), "wrong-password")
            fail("opened with a wrong password")
        } catch (e: BackupWrongKey) {
            // expected
        }
    }

    @Test fun aChangedCiphertextByteFailsTheTag() {
        val f = file()
        f[f.size - 20] = (f[f.size - 20].toInt() xor 1).toByte()
        try {
            BackupFormat.decrypt(f, "pw-123456")
            fail("opened a changed file")
        } catch (e: BackupWrongKey) {
            // expected
        }
    }

    @Test fun aChangedHeaderByteFailsToo() {
        val f = file()
        // The created-at time sits in the header, which is authenticated.
        val at = 6 + 4 + 1 + 16 + 4 + 7
        f[at] = (f[at].toInt() xor 1).toByte()
        try {
            BackupFormat.decrypt(f, "pw-123456")
            fail("opened a file with a changed header")
        } catch (e: BackupWrongKey) {
            // expected
        }
    }

    @Test fun theHeaderReadsWithoutThePassword() {
        val h = BackupFormat.readHeader(file(createdAt = 1_700_000_000_000L, tickets = 412))
        assertEquals(BackupFormat.VERSION, h.version)
        assertEquals(iterations, h.iterations)
        assertEquals(1_700_000_000_000L, h.createdAt)
        assertEquals(412, h.tickets)
        assertEquals("9.9.9", h.appVersion)
        assertEquals(16, h.salt.size)
    }

    @Test fun somethingThatIsNotABackupIsDamaged() {
        listOf(ByteArray(0), "hello world, not a backup".toByteArray(), file().copyOf(20)).forEach {
            try {
                BackupFormat.readHeader(it)
                fail("read a header from junk")
            } catch (e: BackupDamaged) {
                // expected
            }
        }
    }

    @Test fun theSameKeyOpensWhatItMade() {
        val key = BackupFormat.newKey("pw-123456", iterations)
        val f = BackupFormat.encrypt(zip, key, 1L, 1, "1")
        assertArrayEquals(zip, BackupFormat.decrypt(f, key.key))
    }

    @Test fun aKeyIsNeverTheSameTwice() {
        assertTrue(!BackupFormat.newKey("pw", iterations).salt.contentEquals(BackupFormat.newKey("pw", iterations).salt))
    }

    @Test fun onlyTheNewestFourBackupFilesAreKept() {
        val names = (1..7).map { "masroufi-2026-11-0$it-0200.masroufi" } + listOf("notes.txt", "masroufi-photo.jpg")
        val gone = BackupFormat.toDelete(names.shuffled())
        assertEquals(
            listOf("masroufi-2026-11-03-0200.masroufi", "masroufi-2026-11-02-0200.masroufi", "masroufi-2026-11-01-0200.masroufi"),
            gone,
        )
        assertTrue(BackupFormat.toDelete(names.take(4)).isEmpty())
    }
}
