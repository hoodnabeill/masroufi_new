package com.masroufi.app.feature.lock

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.PinResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockSettingsViewModelTest : LockTestBase() {
    private lateinit var vm: LockSettingsViewModel
    private val ui get() = vm.state.value

    private fun screen() {
        vm = make(LockSettingsViewModel::class, { it.state }) { LockSettingsViewModel(f.pin, f.settings, appLock) }
        settle("read") { !ui.loading }
    }

    private fun enter(pin: String) {
        pin.forEach { vm.digit(it.digitToInt()) }
        vm.ok()
    }

    private fun lockWithPin(pin: String = "1234") {
        call { f.pin.set(pin) }
        lockOn()
        screen()
        settle("on") { ui.lockOn }
    }

    @Test fun turningOnAsksForAPinTwiceAndAMismatchStartsOver() {
        screen()
        val seen = mutableListOf<Boolean?>()
        gate.viewModelScope.launch { gate.locked.collect { seen += it } }
        vm.toggleLock(true)
        settle("asks") { ui.step == LockStep.Create }
        assertFalse(call { f.pin.isSet() })

        enter("1234")
        settle("repeat") { ui.step == LockStep.Repeat }
        enter("1235")
        settle("mismatch") { ui.step == LockStep.Create && ui.error == LockError.Mismatch }
        assertFalse("nothing set after a mismatch", call { f.pin.isSet() })
        assertFalse(ui.lockOn)

        enter("1234")
        settle("repeat again") { ui.step == LockStep.Repeat }
        enter("1234")
        settle("on") { ui.lockOn && ui.step == LockStep.None }
        assertEquals(PinResult.Ok, call { f.pin.verify("1234") })
        settle("not locked out of the app it was turned on in") { locked == false }
        assertFalse("the app never flashed to the lock screen", seen.contains(true))
    }

    @Test fun aPinShorterThanFourDigitsIsNotTaken() {
        screen()
        vm.toggleLock(true)
        settle("asks") { ui.step == LockStep.Create }
        enter("123")
        main.scheduler.runCurrent()
        assertEquals(LockStep.Create, ui.step)
        assertEquals("123", ui.digits)
    }

    @Test fun turningOnWithAPinAlreadySetNeedsNoNewOne() {
        call { f.pin.set("4321") } // the child screen uses the same PIN
        screen()
        vm.toggleLock(true)
        settle("on") { ui.lockOn }
        assertEquals(LockStep.None, ui.step)
    }

    @Test fun changingNeedsTheOldPinAndAWrongOneSaysHowManyTriesAreLeft() {
        lockWithPin()
        vm.startChange()
        settle("asks the old one") { ui.step == LockStep.VerifyForChange }
        enter("0000")
        settle("wrong") { ui.error == LockError.Wrong }
        assertEquals(4, ui.triesLeft)
        assertEquals(LockStep.VerifyForChange, ui.step)
        assertEquals(PinResult.Ok, call { f.pin.verify("1234") })

        enter("1234")
        settle("new") { ui.step == LockStep.NewAfterVerify }
        enter("5678")
        settle("repeat") { ui.step == LockStep.RepeatChange }
        enter("5679")
        settle("mismatch") { ui.step == LockStep.NewAfterVerify && ui.error == LockError.Mismatch }
        enter("5678")
        settle("repeat again") { ui.step == LockStep.RepeatChange }
        enter("5678")
        settle("changed") { ui.step == LockStep.None }
        assertEquals(PinResult.Ok, call { f.pin.verify("5678") })
        assertTrue(ui.lockOn)
    }

    @Test fun fiveWrongOldPinsSayWait() {
        lockWithPin()
        vm.startChange()
        settle("asks") { ui.step == LockStep.VerifyForChange }
        repeat(5) {
            enter("0000")
            settle("answered ${it + 1}") { ui.error != null && ui.digits.isEmpty() }
        }
        settle("waiting") { ui.error == LockError.Waiting }
        assertEquals(30L, ui.waitSeconds)
    }

    @Test fun turningOffNeedsTheRightPin() {
        lockWithPin()
        vm.toggleLock(false)
        settle("asks") { ui.step == LockStep.VerifyForOff }
        assertTrue("still on until verified", ui.lockOn)
        enter("0000")
        settle("wrong") { ui.error == LockError.Wrong }
        assertTrue(call { f.settings.current().lockOn })

        enter("1234")
        settle("off") { !ui.lockOn && ui.step == LockStep.None }
        assertTrue("the PIN stays for the child screen", call { f.pin.isSet() })
    }

    @Test fun cancellingChangesNothing() {
        lockWithPin()
        vm.toggleLock(false)
        settle("asks") { ui.step == LockStep.VerifyForOff }
        vm.cancel()
        settle("closed") { ui.step == LockStep.None }
        assertTrue(ui.lockOn)
    }

    @Test fun theFingerprintSwitchIsStored() {
        lockWithPin()
        assertTrue(ui.fingerprint)
        vm.setFingerprint(false)
        settle("off") { !ui.fingerprint }
        assertFalse(call { f.settings.current().lockFingerprint })
    }
}
