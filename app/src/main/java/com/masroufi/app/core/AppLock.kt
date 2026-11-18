package com.masroufi.app.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.di.ProcessLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** 11-DATA "Lock": the app locks on a cold start and after this long in the background. */
const val LOCK_AFTER_BACKGROUND_MS = 60_000L

/**
 * Whether the app lock is showing. Starts locked (a cold start is a new process), unlocks when the PIN or fingerprint
 * proved it, and locks again when the app comes back after [LOCK_AFTER_BACKGROUND_MS] or more away ([clock] is injected).
 */
@Singleton
class AppLock @Inject constructor(
    settings: SettingsStore,
    private val clock: Clock,
    @ProcessLifecycle lifecycle: Lifecycle,
) {
    private val unlocked = MutableStateFlow(false)
    private var stoppedAt: Long? = null

    /** The setting: the lock is on. */
    val lockOn: Flow<Boolean> = settings.settings.map { it.lockOn }.distinctUntilChanged()

    /** On and not yet unlocked since the last cold start or long absence. */
    val locked: Flow<Boolean> = combine(lockOn, unlocked) { on, open -> on && !open }

    init {
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> stoppedAt = clock.millis()
                    Lifecycle.Event.ON_START -> {
                        val away = stoppedAt?.let { clock.millis() - it }
                        if (away != null && away >= LOCK_AFTER_BACKGROUND_MS) unlocked.value = false
                        stoppedAt = null
                    }
                    else -> Unit
                }
            },
        )
    }

    /** The PIN or fingerprint proved it, or the user has just turned the lock on from inside the app. */
    fun unlock() { unlocked.value = true }
}
