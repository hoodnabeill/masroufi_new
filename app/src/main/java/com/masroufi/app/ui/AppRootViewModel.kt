package com.masroufi.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.EndOfMonthMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppRootViewModel @Inject constructor(settings: SettingsStore, endOfMonthMode: EndOfMonthMode) : ViewModel() {
    /** Whether onboarding is done; null until the stored value is read, so nothing shows before it is known. */
    val onboarded: StateFlow<Boolean?> =
        settings.settings.map<_, Boolean?> { it.onboarded }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** End-of-month mode is on: Plus-locked cards leave out "جرّب بلس" (plan decision 8). */
    val endOfMonth: StateFlow<Boolean> = endOfMonthMode.isOn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
