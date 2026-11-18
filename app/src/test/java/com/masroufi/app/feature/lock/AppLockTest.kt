package com.masroufi.app.feature.lock

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockTest : LockTestBase() {
    @Test fun aColdStartIsLockedWhenTheLockIsOn() {
        lockOn()
        settle("locked") { locked == true }
    }

    @Test fun nothingLocksWhileTheLockIsOff() {
        settle("read") { locked != null }
        assertEquals(false, locked)
    }

    @Test fun unlockingOpensTheApp() {
        lockOn()
        settle("locked") { locked == true }
        appLock.unlock()
        settle("open") { locked == false }
    }

    @Test fun lessThanAMinuteInTheBackgroundStaysOpen() {
        lockOn()
        settle("locked") { locked == true }
        appLock.unlock()
        settle("open") { locked == false }
        stop()
        clock.instant = clock.instant.plusSeconds(59)
        start()
        main.scheduler.runCurrent()
        assertEquals(false, locked)
    }

    @Test fun aMinuteInTheBackgroundLocksAgain() {
        lockOn()
        settle("locked") { locked == true }
        appLock.unlock()
        settle("open") { locked == false }
        stop()
        clock.instant = clock.instant.plusSeconds(60)
        start()
        settle("locked again") { locked == true }
        appLock.unlock()
        settle("open again") { locked == false }
    }

    @Test fun turningTheLockOffOpensTheAppAtOnce() {
        lockOn()
        settle("locked") { locked == true }
        call { f.settings.setLockOn(false) }
        settle("open") { locked == false }
    }
}
