package com.masroufi.app.feature.lock

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.AppLock
import com.masroufi.app.core.PIN_MAX_DIGITS
import com.masroufi.app.core.PIN_MIN_DIGITS
import com.masroufi.app.core.PinResult
import com.masroufi.app.core.PinStore
import com.masroufi.app.data.DataWipe
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.ui.haptics.HapticLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * The lock screen. [digits] typed; [pinLength] dots; [wrong] after a wrong PIN until the next digit; [shake] counts wrong tries
 * (the dots shake once per change); [waitMillis] > 0 while the keys are quiet; [unreadable] when the stored PIN cannot be opened.
 */
data class LockUi(
    val digits: String = "",
    val pinLength: Int = DEFAULT_LENGTH,
    val wrong: Boolean = false,
    val shake: Int = 0,
    val waitMillis: Long = 0,
    val unreadable: Boolean = false,
    /** The fingerprint key is offered (the setting is on). An unreadable PIN offers it whatever the setting says. */
    val fingerprint: Boolean = false,
    /** The PIN length and settings are read: the dots and the key are final. */
    val loaded: Boolean = false,
    val forgot: Boolean = false,
    /** The data is erased: the screen relaunches the app. */
    val wiped: Boolean = false,
) {
    companion object { const val DEFAULT_LENGTH = 4 }
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val pin: PinStore,
    private val settings: SettingsStore,
    private val appLock: AppLock,
    private val dataWipe: DataWipe,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(LockUi())
    val state: StateFlow<LockUi> = _state
    private val haptic = Channel<HapticLevel>(Channel.BUFFERED)
    private var ticker: Job? = null

    /** Tick per digit, Tap on unlock. */
    val haptics: Flow<HapticLevel> = haptic.receiveAsFlow()

    /** Each time the lock screen appears: a clean pad, the PIN's length and the fingerprint setting read again, a running wait picked up. */
    fun start() {
        ticker?.cancel()
        _state.value = LockUi()
        launchSafe {
            val s = settings.current()
            _state.update { it.copy(pinLength = (pin.length() ?: LockUi.DEFAULT_LENGTH).coerceIn(PIN_MIN_DIGITS, PIN_MAX_DIGITS), fingerprint = s.lockFingerprint, loaded = true) }
            val wait = pin.waitRemaining()
            if (wait > 0) countdown(clock.millis() + wait)
        }
    }

    fun digit(d: Int) {
        val u = _state.value
        if (!u.loaded || u.waitMillis > 0 || u.digits.length >= u.pinLength || u.forgot) return
        haptic.trySend(HapticLevel.Tick)
        val next = u.digits + d
        _state.update { it.copy(digits = next, wrong = false) }
        if (next.length == u.pinLength) submit(next)
    }

    fun backspace() = _state.update { if (it.waitMillis > 0) it else it.copy(digits = it.digits.dropLast(1), wrong = false) }

    private fun submit(entered: String) = launchSafe {
        when (val r = pin.verify(entered)) {
            PinResult.Ok -> open()
            is PinResult.Wrong -> {
                if (r.waitMillis > 0) countdown(clock.millis() + r.waitMillis)
                _state.update { it.copy(digits = "", wrong = r.waitMillis == 0L, shake = it.shake + 1) }
            }
            is PinResult.Waiting -> {
                _state.update { it.copy(digits = "") }
                countdown(clock.millis() + r.remainingMillis)
            }
            // No PIN to ask for any more: nothing to guard, so the lock lets go instead of trapping the owner.
            PinResult.NotSet -> {
                settings.setLockOn(false)
                open()
            }
            PinResult.Unreadable -> _state.update { it.copy(digits = "", unreadable = true) }
        }
    }

    /** The system prompt (fingerprint or device credential) succeeded. A PIN that cannot be read is forgotten and the lock turns off. */
    fun biometricOk() = launchSafe {
        if (_state.value.unreadable) {
            pin.clear()
            settings.setLockOn(false)
        }
        open()
    }

    fun openForgot() = _state.update { it.copy(forgot = true) }
    fun closeForgot() = _state.update { it.copy(forgot = false) }

    /** Decision 2: erase this phone's data; the screen then relaunches the app (restoring a backup is Task 56). */
    fun confirmWipe() {
        if (_state.value.wiped) return
        launchSafe {
            ticker?.cancel()
            dataWipe.wipe()
            _state.update { it.copy(forgot = false, wiped = true) }
        }
    }

    private fun open() {
        haptic.trySend(HapticLevel.Tap)
        appLock.unlock()
    }

    private fun countdown(until: Long) {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val left = (until - clock.millis()).coerceAtLeast(0)
                _state.update { it.copy(waitMillis = left, wrong = false) }
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
            } catch (e: Exception) {
                Log.e("LockViewModel", "lock action failed", e)
            }
        }
    }
}
