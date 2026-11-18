package com.masroufi.app.feature.kids

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.ChildMode
import com.masroufi.app.core.PIN_MAX_DIGITS
import com.masroufi.app.core.PIN_MIN_DIGITS
import com.masroufi.app.core.PIN_TRIES_BEFORE_WAIT
import com.masroufi.app.core.PinResult
import com.masroufi.app.core.PinStore
import com.masroufi.app.data.repo.AboveLeft
import com.masroufi.app.data.repo.AdvanceRefusal
import com.masroufi.app.data.repo.AdvanceRefused
import com.masroufi.app.data.repo.ChildView
import com.masroufi.app.data.repo.KidsRepository
import com.masroufi.app.data.repo.PlusRequired
import com.masroufi.app.ui.haptics.HapticLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class ChildSheet { SPEND, SAVE, ADVANCE }

/** A plain line under the ticket after something was tried. */
sealed interface ChildNotice {
    /** What was asked for is more than what is left: nothing was saved. */
    data class AskParent(val left: Long) : ChildNotice
    data object AdvanceSent : ChildNotice
    data object AdvanceOpen : ChildNotice
    data class AdvanceLimit(val limit: Long) : ChildNotice

    /** The change did not go through (the child is read-only, or something failed). */
    data object NotDone : ChildNotice
}

/**
 * The parent's unlock: [digits] typed so far; [triesLeft] after a wrong PIN until the waits start; [waitMillis] > 0 while
 * the PIN is not checked; [unreadable] when the stored PIN cannot be opened any more; [noBiometric] when that cannot be
 * confirmed with a fingerprint or the device credential either (the child screen then stays).
 */
data class UnlockUi(
    val digits: String = "",
    val triesLeft: Int? = null,
    val waitMillis: Long = 0,
    val unreadable: Boolean = false,
    val noBiometric: Boolean = false,
) {
    val canSubmit: Boolean get() = waitMillis == 0L && !unreadable && digits.length >= PIN_MIN_DIGITS
}

/** What the child screen draws: this child's ticket and jar and nothing of the parent's money. */
data class ChildUi(
    val loading: Boolean = true,
    val child: ChildView? = null,
    val finishedOn: DayOfWeek = DayOfWeek.SATURDAY,
    val handoverDay: DayOfWeek = DayOfWeek.SATURDAY,
    val slots: Int = 5,
    val sheet: ChildSheet? = null,
    val notice: ChildNotice? = null,
    val unlock: UnlockUi? = null,
)

private const val TAG = "ChildScreen"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChildScreenViewModel @Inject constructor(
    private val kids: KidsRepository,
    private val childMode: ChildMode,
    private val pin: PinStore,
    private val clock: Clock,
) : ViewModel() {
    private val sheet = MutableStateFlow<ChildSheet?>(null)
    private val notice = MutableStateFlow<ChildNotice?>(null)
    private val unlock = MutableStateFlow<UnlockUi?>(null)
    private val haptic = Channel<HapticLevel>(Channel.BUFFERED)
    private var ticker: Job? = null

    /** Tick per PIN digit, Tap on unlock, Milestone when a saving reaches the goal, Confirm for an advance. */
    val haptics: Flow<HapticLevel> = haptic.receiveAsFlow()

    private val childView: Flow<Pair<ChildView?, DayOfWeek>> = childMode.activeChildId.flatMapLatest { id ->
        if (id == null) flowOf(null to DayOfWeek.SATURDAY) else kids.observe().map { v -> v.children.firstOrNull { it.child.id == id } to v.handoverDay }
    }

    val state: StateFlow<ChildUi> = combine(childView, sheet, notice, unlock) { (c, handover), s, n, u ->
        val day = c?.lastSpendAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone).dayOfWeek } ?: LocalDate.now(clock).dayOfWeek
        ChildUi(false, c, day, handover, (c?.punches ?: 0).coerceIn(5, 10), s, n, u)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChildUi())

    fun openSheet(kind: ChildSheet) {
        notice.value = null
        sheet.value = kind
    }

    fun dismissSheet() { sheet.value = null }
    fun clearNotice() { notice.value = null }

    /** The keypad sheet's amount (piastres) for whichever sheet is open. */
    fun submit(amount: Long) {
        val kind = sheet.value ?: return
        if (amount <= 0) return
        launchSafe {
            val c = state.value.child ?: return@launchSafe
            val id = c.child.id
            when (kind) {
                ChildSheet.SPEND -> kids.spend(id, amount)
                ChildSheet.SAVE -> {
                    kids.saveFromLeft(id, amount)
                    // Reaching the goal is the child's big moment.
                    val after = kids.view().children.firstOrNull { it.child.id == id }
                    if (after?.jar?.goalReached == true && c.jar?.goalReached != true) haptic.trySend(HapticLevel.Milestone)
                }
                ChildSheet.ADVANCE -> {
                    kids.requestAdvance(id, amount)
                    haptic.trySend(HapticLevel.Confirm)
                    notice.value = ChildNotice.AdvanceSent
                }
            }
            sheet.value = null
        }
    }

    // -- leaving: the parent's PIN, fingerprint or device credential

    fun openUnlock() {
        unlock.value = UnlockUi()
        launchSafe {
            val wait = pin.waitRemaining()
            if (wait > 0) countdown(clock.millis() + wait)
        }
    }

    fun closeUnlock() {
        ticker?.cancel()
        unlock.value = null
    }

    fun digit(d: Int) {
        haptic.trySend(HapticLevel.Tick)
        unlock.update { u -> if (u != null && u.waitMillis == 0L && u.digits.length < PIN_MAX_DIGITS) u.copy(digits = u.digits + d, triesLeft = null) else u }
    }

    fun backspace() = unlock.update { it?.copy(digits = it.digits.dropLast(1)) }

    /** The check mark: the PIN is verified; only Ok leaves. */
    fun submitPin() {
        val u = unlock.value ?: return
        if (!u.canSubmit) return
        launchSafe {
            when (val r = childMode.leaveWithPin(u.digits)) {
                PinResult.Ok -> {
                    haptic.trySend(HapticLevel.Tap)
                    unlock.value = null
                }
                is PinResult.Wrong -> {
                    if (r.waitMillis > 0) countdown(clock.millis() + r.waitMillis)
                    unlock.update { it?.copy(digits = "", triesLeft = (PIN_TRIES_BEFORE_WAIT - r.failures).coerceAtLeast(0)) }
                }
                is PinResult.Waiting -> {
                    unlock.update { it?.copy(digits = "") }
                    countdown(clock.millis() + r.remainingMillis)
                }
                // Nothing to guard any more (the PIN was never set or is gone): the screen is let go rather than locked for good.
                PinResult.NotSet -> {
                    childMode.leaveAfterBiometric()
                    unlock.value = null
                }
                PinResult.Unreadable -> unlock.update { it?.copy(digits = "", unreadable = true) }
            }
        }
    }

    /** The system prompt (fingerprint or device credential) succeeded. With a PIN that cannot be read, it forgets that PIN. */
    fun biometricOk() {
        launchSafe {
            haptic.trySend(HapticLevel.Tap)
            if (unlock.value?.unreadable == true) childMode.resetPinAfterBiometric() else childMode.leaveAfterBiometric()
            unlock.value = null
        }
    }

    /** The parent has neither a fingerprint nor a device credential: the child screen stays, and says so. */
    fun biometricUnavailable() = unlock.update { it?.copy(noBiometric = true) }

    private fun countdown(until: Long) {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val left = (until - clock.millis()).coerceAtLeast(0)
                unlock.update { it?.copy(waitMillis = left, triesLeft = if (left > 0) null else it.triesLeft) }
                if (left == 0L) break
                delay(1_000)
            }
        }
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AboveLeft) {
                sheet.value = null
                notice.value = ChildNotice.AskParent(e.left)
            } catch (e: AdvanceRefused) {
                sheet.value = null
                notice.value = if (e.reason == AdvanceRefusal.OPEN_EXISTS) ChildNotice.AdvanceOpen else ChildNotice.AdvanceLimit(e.limit)
            } catch (e: PlusRequired) {
                sheet.value = null
                notice.value = ChildNotice.NotDone
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
                sheet.value = null
                notice.value = ChildNotice.NotDone
            }
        }
    }
}
