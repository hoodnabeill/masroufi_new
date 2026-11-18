package com.masroufi.app.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.security.GeneralSecurityException
import java.time.Instant

/** A cipher that keeps the bytes as they are: the Android Keystore does not exist under Robolectric. */
class PlainCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = plain.copyOf()
    override fun decrypt(data: ByteArray): ByteArray = data.copyOf()
}

@RunWith(AndroidJUnit4::class)
class PinStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = TestClock(Instant.parse("2026-11-18T10:00:00Z"))

    private fun TestScope.store(file: String = "pin.preferences_pb", cipher: SecretCipher = PlainCipher()) =
        PinStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, file) }, cipher, clock, iterations = 1_000)

    private fun passSeconds(s: Long) { clock.instant = clock.instant.plusSeconds(s) }

    @Test fun waitScheduleIs30SecondsThenDoublesUpTo15Minutes() {
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), (0..4).map { pinWaitMillis(it) })
        assertEquals(
            listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L),
            (5..11).map { pinWaitMillis(it) },
        )
        assertEquals(900_000L, pinWaitMillis(1_000))
    }

    @Test fun setThenVerify() = runTest {
        val pin = store()
        assertFalse(pin.isSet())
        assertEquals(PinResult.NotSet, pin.verify("1234"))
        pin.set("1234")
        assertTrue(pin.isSet())
        assertEquals(PinResult.Ok, pin.verify("1234"))
        assertEquals(PinResult.Wrong(1, 0), pin.verify("1235"))
        assertEquals("a longer PIN is not a prefix match", PinResult.Wrong(2, 0), pin.verify("12345"))
    }

    @Test fun aPinIsFourToSixDigits() = runTest {
        val pin = store()
        listOf("", "123", "1234567", "12a4", "١٢٣٤", " 1234").forEach { bad ->
            try {
                pin.set(bad)
                fail("accepted '$bad'")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        assertFalse(pin.isSet())
        pin.set("123456")
        assertEquals(PinResult.Ok, pin.verify("123456"))
    }

    @Test fun onlyChangeReplacesAPinAndItNeedsTheOldOne() = runTest {
        val pin = store()
        pin.set("1234")
        try {
            pin.set("9999")
            fail("set over a set PIN")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals(PinResult.Wrong(1, 0), pin.change("0000", "5555"))
        assertEquals(PinResult.Ok, pin.verify("1234"))
        assertEquals(PinResult.Ok, pin.change("1234", "5555"))
        assertEquals(PinResult.Ok, pin.verify("5555"))
        assertEquals(PinResult.Wrong(1, 0), pin.verify("1234"))
    }

    @Test fun whatIsStoredHoldsNeitherThePinNorTheSameBytesTwice() = runTest {
        val dataA = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "a.preferences_pb") }
        val dataB = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "b.preferences_pb") }
        PinStore(dataA, PlainCipher(), clock, iterations = 1_000).set("1234")
        PinStore(dataB, PlainCipher(), clock, iterations = 1_000).set("1234")
        val key = stringPreferencesKey("pinBlob")
        val blobA = checkNotNull(dataA.data.first()[key])
        assertNotEquals("salted: the same PIN stores differently", blobA, dataB.data.first()[key])
        assertFalse(String(java.util.Base64.getDecoder().decode(blobA), Charsets.ISO_8859_1).contains("1234"))
    }

    @Test fun fiveWrongTriesWait30SecondsAndEachFurtherOneDoubles() = runTest {
        val pin = store()
        pin.set("1234")
        repeat(4) { assertEquals(PinResult.Wrong(it + 1, 0), pin.verify("0000")) }
        assertEquals("the fifth starts the wait", PinResult.Wrong(5, 30_000), pin.verify("0000"))
        assertEquals(30_000L, pin.waitRemaining())
        assertEquals("nothing is checked while waiting, not even the right PIN", PinResult.Waiting(30_000), pin.verify("1234"))
        passSeconds(10)
        assertEquals(PinResult.Waiting(20_000), pin.verify("1234"))
        passSeconds(20)
        assertEquals(0L, pin.waitRemaining())
        assertEquals("one more wrong try: 60 seconds", PinResult.Wrong(6, 60_000), pin.verify("0000"))
        passSeconds(60)
        assertEquals(PinResult.Wrong(7, 120_000), pin.verify("0000"))
        passSeconds(120)
        assertEquals(PinResult.Wrong(8, 240_000), pin.verify("0000"))
        passSeconds(240)
        assertEquals(PinResult.Wrong(9, 480_000), pin.verify("0000"))
        passSeconds(480)
        assertEquals(PinResult.Wrong(10, 900_000), pin.verify("0000"))
        passSeconds(900)
        assertEquals("capped at 15 minutes", PinResult.Wrong(11, 900_000), pin.verify("0000"))
        passSeconds(900)
        assertEquals(PinResult.Ok, pin.verify("1234"))
        assertEquals("a right PIN starts the count over", PinResult.Wrong(1, 0), pin.verify("0000"))
        assertEquals(0L, pin.waitRemaining())
    }

    @Test fun theWaitSurvivesARestartOfTheStore() = runTest {
        val file = File(tmp.root, "pin.preferences_pb")
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val first = PinStore(data, PlainCipher(), clock, iterations = 1_000)
        first.set("1234")
        repeat(5) { first.verify("0000") }
        val second = PinStore(data, PlainCipher(), clock, iterations = 1_000) // a new process reads the same file
        assertEquals(PinResult.Waiting(30_000), second.verify("1234"))
    }

    @Test fun aPinThatCannotBeOpenedIsUnreadableNotUnset() = runTest {
        val file = File(tmp.root, "pin.preferences_pb")
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        PinStore(data, PlainCipher(), clock, iterations = 1_000).set("1234")
        val broken = object : SecretCipher {
            override fun encrypt(plain: ByteArray) = plain
            override fun decrypt(data: ByteArray): ByteArray = throw GeneralSecurityException("key gone")
        }
        val pin = PinStore(data, broken, clock, iterations = 1_000)
        assertTrue(pin.isSet())
        assertEquals(PinResult.Unreadable, pin.verify("1234"))
    }

    @Test fun childModeIsPersistedAndLeftOnlyAfterAVerifiedPinOrBiometric() = runTest {
        val dir = tmp.newFolder()
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "s.preferences_pb") }, clock)
        val pin = store()
        val mode = ChildMode(settings, pin)

        try {
            mode.enter(7)
            fail("entered without a PIN")
        } catch (e: IllegalStateException) {
            // expected: the screen asks the parent to set a PIN first
        }
        assertNull(settings.current().childModeId)

        pin.set("2468")
        mode.enter(7)
        assertEquals("kept in the settings, so a cold start finds it", 7L, settings.current().childModeId)
        assertEquals(7L, mode.activeChildId.first())

        assertEquals(PinResult.Wrong(1, 0), mode.leaveWithPin("1111"))
        assertEquals("a wrong PIN does not leave", 7L, settings.current().childModeId)
        assertEquals(PinResult.Ok, mode.leaveWithPin("2468"))
        assertNull(settings.current().childModeId)
        assertNull(mode.activeChildId.first())

        mode.enter(8)
        repeat(5) { mode.leaveWithPin("0000") }
        assertTrue("waiting: not even the right PIN leaves", mode.leaveWithPin("2468") is PinResult.Waiting)
        assertEquals(8L, settings.current().childModeId)
        mode.leaveAfterBiometric()
        assertNull("the biometric prompt's success leaves", settings.current().childModeId)
    }
}
