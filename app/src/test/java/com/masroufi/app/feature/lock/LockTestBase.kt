package com.masroufi.app.feature.lock

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.masroufi.app.core.AppLock
import com.masroufi.app.feature.kids.KidsVmTestBase
import org.junit.Before

/** The lock over the fixture's settings and PIN store, with a process lifecycle the test drives by hand. */
abstract class LockTestBase : KidsVmTestBase() {
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    protected lateinit var appLock: AppLock
    protected lateinit var gate: LockGateViewModel

    @Before fun openLock() {
        appLock = AppLock(f.settings, clock, owner.lifecycle)
        gate = make(LockGateViewModel::class, { it.locked }) { LockGateViewModel(appLock) }
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    protected fun stop() = owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    protected fun start() = owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

    protected val locked: Boolean? get() = gate.locked.value

    protected fun lockOn() = call { f.settings.setLockOn(true) }
}
