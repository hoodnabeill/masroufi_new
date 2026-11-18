package com.masroufi.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.feature.kids.KidsVmTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The app's start: a child mode that is set beats everything, whatever else was stored (cold start, back and recents all land on it). */
@RunWith(AndroidJUnit4::class)
class AppRootViewModelTest : KidsVmTestBase() {
    private fun root(): AppRootViewModel = make(AppRootViewModel::class, { it.mode }) { AppRootViewModel(f.settings, f.eomMode) }

    @Test fun nothingShowsUntilTheStoredValuesAreRead() {
        val vm = root()
        assertNull(vm.mode.value)
        settle("read") { vm.mode.value != null }
        assertEquals(RootMode.Onboarding, vm.mode.value)
    }

    @Test fun onboardedStartsTheApp() {
        call { f.settings.setOnboarded(true) }
        val vm = root()
        settle("app") { vm.mode.value == RootMode.App }
    }

    @Test fun aSetChildModeStartsOnTheChildScreenEvenBeforeOnboardingIsDone() {
        val child = call { f.kids.addChild("ملك", 2017, 3, 7_000) }
        call {
            f.pin.set("1234")
            f.childMode.enter(child)
        }
        val vm = root()
        settle("child") { vm.mode.value == RootMode.Child(child) }
    }

    @Test fun leavingWithThePinGoesBackToTheApp() {
        call { f.settings.setOnboarded(true) }
        val child = call { f.kids.addChild("ملك", 2017, 3, 7_000) }
        call {
            f.pin.set("1234")
            f.childMode.enter(child)
        }
        val vm = root()
        settle("child") { vm.mode.value == RootMode.Child(child) }
        call { f.childMode.leaveWithPin("0000") }
        assertEquals("a wrong PIN keeps the child screen", RootMode.Child(child), vm.mode.value)
        call { f.childMode.leaveWithPin("1234") }
        settle("app again") { vm.mode.value == RootMode.App }
    }
}
