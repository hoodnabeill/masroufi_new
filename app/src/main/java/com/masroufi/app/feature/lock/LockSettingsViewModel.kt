package com.masroufi.app.feature.lock

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.AppLock
import com.masroufi.app.core.PIN_MIN_DIGITS
import com.masroufi.app.core.PIN_MAX_DIGITS
import com.masroufi.app.core.PIN_TRIES_BEFORE_WAIT
import com.masroufi.app.core.PinResult
import com.masroufi.app.core.PinStore
import com.masroufi.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which PIN question the pad is asking: enter and repeat a new one, or prove the old one before a change or turning off. */
enum class LockStep { None, Create, Repeat, VerifyForOn, VerifyForOff, VerifyForChange, NewAfterVerify, RepeatChange }

enum class LockError { Mismatch, Wrong, Waiting, Unreadable }

data class LockSettingsUi(
    val loading: Boolean = true,
    val lockOn: Boolean = false,
    val fingerprint: Boolean = true,
    val step: LockStep = LockStep.None,
    val digits: String = "",
    val error: LockError? = null,
    /** Wrong tries left before the first wait, after a wrong old PIN. */
    val triesLeft: Int? = null,
    val waitSeconds: Long = 0,
)

/** What the lock settings screen calls on the view model (a stateless screen takes this, so a test can pass a no-op). */
interface LockSettingsActions {
    fun toggleLock(on: Boolean) {}
    fun startChange() {}
    fun setFingerprint(on: Boolean) {}
    fun cancel() {}
    fun digit(d: Int) {}
    fun backspace() {}
    fun ok() {}
}

/** Turn the lock on (set a PIN if none: enter + repeat), change the PIN (verify the old first), turn it off (verify first), fingerprint on/off. */
@HiltViewModel
class LockSettingsViewModel @Inject constructor(
    private val pin: PinStore,
    private val settings: SettingsStore,
    private val appLock: AppLock,
) : ViewModel(), LockSettingsActions {
    private class Local(
        val step: LockStep = LockStep.None,
        val digits: String = "",
        val error: LockError? = null,
        val triesLeft: Int? = null,
        val waitSeconds: Long = 0,
    )

    private val local = MutableStateFlow(Local())

    /** The PIN being replaced, or the first of two entries; kept off the screen state. */
    private var verifiedOld = ""
    private var first = ""

    val state: StateFlow<LockSettingsUi> = combine(settings.settings, local) { s, l ->
        LockSettingsUi(false, s.lockOn, s.lockFingerprint, l.step, l.digits, l.error, l.triesLeft, l.waitSeconds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockSettingsUi())

    override fun toggleLock(on: Boolean) = act {
        if (on) {
            when {
                !pin.isSet() -> go(LockStep.Create)
                pin.length() == null -> go(LockStep.VerifyForOn) // set before the length was kept: one verify records it
                else -> turnOn()
            }
        } else if (pin.isSet()) go(LockStep.VerifyForOff) else settings.setLockOn(false)
    }

    override fun startChange() = act { go(LockStep.VerifyForChange) }
    override fun setFingerprint(on: Boolean) = act { settings.setLockFingerprint(on) }
    override fun cancel() = local.update { Local() }

    override fun digit(d: Int) = local.update { if (it.step == LockStep.None || it.digits.length >= PIN_MAX_DIGITS) it else Local(it.step, it.digits + d) }
    override fun backspace() = local.update { Local(it.step, it.digits.dropLast(1)) }

    /** The check mark of the pad. */
    override fun ok() {
        val l = local.value
        if (l.digits.length < PIN_MIN_DIGITS) return
        act {
            when (l.step) {
                LockStep.None -> Unit
                LockStep.Create -> { first = l.digits; go(LockStep.Repeat) }
                LockStep.NewAfterVerify -> { first = l.digits; go(LockStep.RepeatChange) }
                LockStep.Repeat -> if (l.digits == first) { pin.set(first); turnOn() } else mismatch(LockStep.Create)
                LockStep.RepeatChange -> if (l.digits == first) changeTo(first) else mismatch(LockStep.NewAfterVerify)
                LockStep.VerifyForOn -> verify(l.digits) { turnOn() }
                LockStep.VerifyForOff -> verify(l.digits) { settings.setLockOn(false); done() }
                LockStep.VerifyForChange -> verify(l.digits) { verifiedOld = l.digits; go(LockStep.NewAfterVerify) }
            }
        }
    }

    private suspend fun verify(entered: String, onOk: suspend () -> Unit) {
        when (val r = pin.verify(entered)) {
            PinResult.Ok, PinResult.NotSet -> onOk()
            is PinResult.Wrong ->
                if (r.waitMillis > 0) fail(LockError.Waiting, wait = r.waitMillis)
                else fail(LockError.Wrong, triesLeft = (PIN_TRIES_BEFORE_WAIT - r.failures).coerceAtLeast(0))
            is PinResult.Waiting -> fail(LockError.Waiting, wait = r.remainingMillis)
            PinResult.Unreadable -> fail(LockError.Unreadable)
        }
    }

    private suspend fun changeTo(new: String) {
        when (pin.change(verifiedOld, new)) {
            PinResult.Ok -> done()
            // The old PIN stopped being right between the steps (a wait began): back to asking for it.
            else -> go(LockStep.VerifyForChange)
        }
    }

    private suspend fun turnOn() {
        appLock.unlock() // first: turned on from inside the app, it locks at the next cold start or long absence, not now
        settings.setLockOn(true)
        done()
    }

    private fun go(step: LockStep) = local.update { Local(step) }
    private fun done() { verifiedOld = ""; first = ""; local.update { Local() } }
    private fun mismatch(back: LockStep) = local.update { Local(back, error = LockError.Mismatch) }
    private fun fail(error: LockError, triesLeft: Int? = null, wait: Long = 0) =
        local.update { Local(it.step, error = error, triesLeft = triesLeft, waitSeconds = (wait + 999) / 1000) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LockSettings", "lock settings action failed", e)
            }
        }
    }
}
