package com.masroufi.app.feature.kids

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.haptics.HapticLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChildScreenViewModelTest : KidsVmTestBase() {
    private lateinit var vm: ChildScreenViewModel
    private lateinit var haptics: MutableList<HapticLevel>
    private var kid = 0L

    private val ui get() = vm.state.value
    private val pinned get() = call { f.settings.current().childModeId }

    /** One child with a 10 pound jar goal, a PIN (1234) and the child screen on. */
    private fun pinnedChild() {
        kid = call { f.kids.addChild("ملك", 2017, 3, 7_000, "العجلة", 1_000) }
        call {
            f.pin.set("1234")
            f.childMode.enter(kid)
        }
        vm = make(ChildScreenViewModel::class, { it.state }) { ChildScreenViewModel(f.kids, f.childMode, f.pin, clock) }
        haptics = vm.record(vm.haptics)
        settle("the child") { ui.child != null }
    }

    private fun type(pin: String) = pin.forEach { vm.digit(it.digitToInt()) }

    private fun wrongTry() {
        type("0000")
        vm.submitPin()
        settle("a wrong try answered") { ui.unlock?.digits == "" }
    }

    @Test fun spendingRecordsAPunchAndLowersWhatIsLeft() {
        pinnedChild()
        vm.openSheet(ChildSheet.SPEND)
        vm.submit(2_500)
        settle("spent") { ui.child!!.left == 4_500L && ui.sheet == null }
        assertEquals(1, ui.child!!.punches)
        assertNull(ui.notice)
    }

    @Test fun spendingMoreThanIsLeftRecordsNothingAndTellsThemToAskAParent() {
        pinnedChild()
        vm.openSheet(ChildSheet.SPEND)
        vm.submit(9_900)
        settle("refused") { ui.notice == ChildNotice.AskParent(7_000L) }
        assertEquals(listOf<Any?>(7_000L, 0, null), listOf(ui.child!!.left, ui.child!!.punches, ui.sheet))
    }

    @Test fun anAdvanceIsAskedOnceAndHeldToHalfTheAllowance() {
        pinnedChild()
        vm.openSheet(ChildSheet.ADVANCE)
        vm.submit(2_000)
        settle("sent") { ui.notice == ChildNotice.AdvanceSent && ui.child!!.waitingAdvance != null }
        assertTrue(haptics.contains(HapticLevel.Confirm))

        vm.openSheet(ChildSheet.ADVANCE)
        vm.submit(500)
        settle("one open") { ui.notice == ChildNotice.AdvanceOpen }
        assertEquals(2_000L, ui.child!!.waitingAdvance!!.amount)

        call { f.kids.answerAdvance(ui.child!!.waitingAdvance!!.id, false) }
        settle("answered") { ui.child!!.waitingAdvance == null }
        vm.openSheet(ChildSheet.ADVANCE)
        vm.submit(9_000)
        settle("above half") { ui.notice == ChildNotice.AdvanceLimit(3_500L) }
    }

    @Test fun savingMovesLeftIntoTheJarAndReachingTheGoalIsAMilestone() {
        pinnedChild()
        vm.openSheet(ChildSheet.SAVE)
        vm.submit(600)
        settle("saved") { ui.child!!.jar!!.saved == 600L && ui.sheet == null }
        assertEquals(listOf<Any?>(6_400L, 0), listOf(ui.child!!.left, ui.child!!.punches))
        assertFalse(haptics.contains(HapticLevel.Milestone))
        vm.openSheet(ChildSheet.SAVE)
        vm.submit(400)
        settle("goal") { ui.child!!.jar!!.goalReached }
        settle("milestone") { haptics.contains(HapticLevel.Milestone) }
    }

    @Test fun savingMoreThanIsLeftIsRefusedToo() {
        pinnedChild()
        vm.openSheet(ChildSheet.SAVE)
        vm.submit(8_000)
        settle("refused") { ui.notice == ChildNotice.AskParent(7_000L) }
        assertEquals(0L, ui.child!!.jar!!.saved)
    }

    @Test fun theRightPinLeavesWithATickPerDigitAndATap() {
        pinnedChild()
        vm.openUnlock()
        settle("open") { ui.unlock != null }
        type("1234")
        vm.submitPin()
        settle("left") { pinned == null }
        assertEquals(listOf(HapticLevel.Tick, HapticLevel.Tick, HapticLevel.Tick, HapticLevel.Tick, HapticLevel.Tap), haptics.toList())
        settle("unlock closed") { ui.unlock == null }
    }

    @Test fun aWrongPinSaysHowManyTriesAreLeftAndKeepsTheScreen() {
        pinnedChild()
        vm.openUnlock()
        settle("open") { ui.unlock != null }
        wrongTry()
        assertEquals(4, ui.unlock!!.triesLeft)
        assertEquals(kid, pinned)
        wrongTry()
        assertEquals(3, ui.unlock!!.triesLeft)
    }

    @Test fun fiveWrongTriesWaitAndTheCountdownEndsOnTheClock() {
        pinnedChild()
        vm.openUnlock()
        settle("open") { ui.unlock != null }
        repeat(5) { wrongTry() }
        settle("waiting") { ui.unlock!!.waitMillis == 30_000L }
        assertNull(ui.unlock!!.triesLeft)
        vm.digit(1)
        assertEquals("no digits while waiting", "", ui.unlock!!.digits)
        assertFalse(ui.unlock!!.canSubmit)

        // Reopened during the wait it still waits (the PIN store remembers).
        vm.closeUnlock()
        settle("closed") { ui.unlock == null }
        vm.openUnlock()
        settle("still waiting") { ui.unlock?.waitMillis?.let { it > 0 } == true }

        clock.instant = clock.instant.plusSeconds(31)
        main.scheduler.advanceTimeBy(1_001)
        main.scheduler.runCurrent()
        settle("wait over") { ui.unlock!!.waitMillis == 0L }
        type("1234")
        vm.submitPin()
        settle("left") { pinned == null }
    }

    @Test fun aPinThatCannotBeReadIsClearedOnlyAfterTheSystemPrompt() {
        pinnedChild()
        f.cipher.broken = true
        vm.openUnlock()
        settle("open") { ui.unlock != null }
        type("1234")
        vm.submitPin()
        settle("unreadable") { ui.unlock?.unreadable == true }
        assertEquals(kid, pinned)

        vm.biometricUnavailable() // neither a fingerprint nor a device credential: no way out
        settle("said so") { ui.unlock?.noBiometric == true }
        assertEquals(kid, pinned)
        assertTrue(call { f.pin.isSet() })

        vm.biometricOk()
        settle("left") { pinned == null }
        assertFalse("the unreadable PIN is forgotten", call { f.pin.isSet() })
    }

    @Test fun theFingerprintLeavesWhenThePinIsFine() {
        pinnedChild()
        vm.openUnlock()
        settle("open") { ui.unlock != null }
        vm.biometricOk()
        settle("left") { pinned == null }
        assertTrue("the PIN stays", call { f.pin.isSet() })
        assertNotNull(ui)
    }
}
