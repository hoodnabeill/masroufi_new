package com.masroufi.app.feature.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.AppLock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What AppRoot needs of the lock: whether it is on (the window is secured) and whether it is showing (null until the stored value is read). */
@HiltViewModel
class LockGateViewModel @Inject constructor(appLock: AppLock) : ViewModel() {
    val lockOn: StateFlow<Boolean> = appLock.lockOn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val locked: StateFlow<Boolean?> = appLock.locked.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** 11-DATA "Lock": while the lock is on the window is FLAG_SECURE, so recent apps and screenshots show nothing. */
@Composable
fun SecureWindow(on: Boolean) {
    val window = LocalContext.current.activity()?.window
    DisposableEffect(window, on) {
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (on) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
