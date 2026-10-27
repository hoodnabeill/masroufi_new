package com.masroufi.app.security

import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.masroufi.app.data.prefs.PinRecord
import com.masroufi.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class LockState { UNKNOWN, LOCKED, UNLOCKED }

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class Throttled(val retryAtMillis: Long) : PinResult
}

@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val clock: Clock,
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(LockState.UNKNOWN)
    val state: StateFlow<LockState> = _state.asStateFlow()
    private var backgroundedAt: Long? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            _state.value = if (settings.current().lockEnabled) LockState.LOCKED else LockState.UNLOCKED
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = clock.millis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        scope.launch {
            val current = settings.current()
            if (current.lockEnabled && clock.millis() - since >= current.lockTimeoutSeconds * 1000L) {
                _state.value = LockState.LOCKED
            }
        }
    }

    fun unlockedByBiometric() {
        _state.value = LockState.UNLOCKED
    }

    fun lockNow() {
        _state.value = LockState.LOCKED
    }

    fun disabled() {
        _state.value = LockState.UNLOCKED
    }

    suspend fun verifyPin(pin: String): PinResult {
        val now = clock.millis()
        val (failures, lockedUntil) = settings.pinThrottle()
        if (lockedUntil > now) return PinResult.Throttled(lockedUntil)
        val record = settings.pin() ?: return PinResult.Success.also { _state.value = LockState.UNLOCKED }
        val ok = withContext(Dispatchers.Default) {
            PinHasher.verify(pin, Base64.decode(record.salt, Base64.NO_WRAP), Base64.decode(record.hash, Base64.NO_WRAP))
        }
        if (ok) {
            settings.setPinThrottle(0, 0)
            _state.value = LockState.UNLOCKED
            return PinResult.Success
        }
        val newFailures = failures + 1
        return if (newFailures >= MAX_ATTEMPTS) {
            // Back off exponentially: 30s, 60s, 120s ... capped at 1h.
            val delay = (30_000L shl (newFailures - MAX_ATTEMPTS).coerceAtMost(7)).coerceAtMost(3_600_000L)
            settings.setPinThrottle(newFailures, now + delay)
            PinResult.Throttled(now + delay)
        } else {
            settings.setPinThrottle(newFailures, 0)
            PinResult.Wrong(MAX_ATTEMPTS - newFailures)
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5

        fun createPinRecord(pin: String): PinRecord {
            val salt = PinHasher.newSalt()
            return PinRecord(
                hash = Base64.encodeToString(PinHasher.hash(pin, salt), Base64.NO_WRAP),
                salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            )
        }
    }
}
