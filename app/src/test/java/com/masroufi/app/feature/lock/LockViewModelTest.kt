package com.masroufi.app.feature.lock

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.DataWipe
import com.masroufi.app.ui.haptics.HapticLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockViewModelTest : LockTestBase() {
    private lateinit var vm: LockViewModel
    private lateinit var haptics: MutableList<HapticLevel>
    private val ui get() = vm.state.value

    /** The lock is on with PIN [pin]; the screen has just appeared. */
    private fun lockedWith(pin: String = "1234") {
        call { f.pin.set(pin) }
        lockOn()
        vm = make(LockViewModel::class, { it.state }) {
            LockViewModel(f.pin, f.settings, appLock, DataWipe(ApplicationProvider.getApplicationContext(), db, f.settings), clock)
        }
        haptics = vm.record(vm.haptics)
        vm.start()
        try {
            settle("ready") { ui.loaded && locked == true }
        } catch (e: IllegalStateException) {
            throw IllegalStateException("not ready: ui=$ui locked=$locked", e)
        }
    }

    private fun type(pin: String) = pin.forEach { vm.digit(it.digitToInt()) }

    private fun wrongTry() {
        val before = ui.shake
        type("0000")
        settle("a wrong try answered") { ui.shake == before + 1 }
    }

    @Test fun theRightPinUnlocksWithATickPerDigitAndATap() {
        lockedWith()
        type("1234")
        settle("open") { locked == false }
        settle("tap") { haptics.size == 5 }
        assertEquals(listOf(HapticLevel.Tick, HapticLevel.Tick, HapticLevel.Tick, HapticLevel.Tick, HapticLevel.Tap), haptics.toList())
    }

    @Test fun theDotsFollowTheLengthOfThePin() {
        lockedWith("123456")
        assertEquals(6, ui.pinLength)
        type("1234")
        main.scheduler.runCurrent()
        assertEquals("four digits are not a try at a six digit PIN", "1234", ui.digits)
        assertEquals(true, locked)
        type("56")
        settle("open") { locked == false }
    }

    @Test fun aWrongPinShakesOnceClearsAndSaysSo() {
        lockedWith()
        wrongTry()
        assertEquals("", ui.digits)
        assertTrue(ui.wrong)
        assertEquals(1, ui.shake)
        assertEquals(true, locked)
        vm.digit(1)
        assertFalse("the next digit takes the message away", ui.wrong)
    }

    @Test fun fiveWrongTriesQuietTheKeysAndTheCountdownEndsOnTheClock() {
        lockedWith()
        repeat(5) { wrongTry() }
        settle("waiting") { ui.waitMillis == 30_000L }
        assertFalse(ui.wrong)
        vm.digit(1)
        assertEquals("no digits while waiting", "", ui.digits)
        type("1234")
        main.scheduler.runCurrent()
        assertEquals("the right PIN is not even checked during a wait", true, locked)

        clock.instant = clock.instant.plusSeconds(31)
        main.scheduler.advanceTimeBy(1_001)
        main.scheduler.runCurrent()
        settle("wait over") { ui.waitMillis == 0L }
        type("1234")
        settle("open") { locked == false }
    }

    @Test fun aWaitStillRunningIsPickedUpWhenTheScreenAppearsAgain() {
        lockedWith()
        repeat(5) { wrongTry() }
        settle("waiting") { ui.waitMillis > 0 }
        vm.start()
        settle("still waiting") { ui.loaded && ui.waitMillis > 0 }
    }

    @Test fun theFingerprintOpensAndAPinThatCannotBeReadIsForgotten() {
        lockedWith()
        f.cipher.broken = true
        type("1234")
        settle("unreadable") { ui.unreadable }
        assertTrue(call { f.pin.isSet() })
        vm.biometricOk()
        settle("open") { locked == false }
        assertFalse("the unreadable PIN is forgotten", call { f.pin.isSet() })
        assertFalse("and the lock turns off", call { f.settings.current().lockOn })
    }

    @Test fun theForgottenPinPathErasesThePhoneOnlyAfterTheConfirm() {
        call { f.settings.setOnboarded(true) }
        lockedWith()
        vm.openForgot()
        assertTrue(ui.forgot)
        vm.closeForgot()
        assertFalse(ui.forgot)
        assertTrue("nothing erased by looking", call { f.settings.current().onboarded })

        vm.openForgot()
        vm.confirmWipe()
        settle("wiped") { ui.wiped }
        main.scheduler.runCurrent()
        assertEquals("the lock screen stays up so it can relaunch the app", true, locked)
        assertFalse(call { f.settings.current().onboarded })
        assertFalse(call { f.settings.current().lockOn })
    }
}
