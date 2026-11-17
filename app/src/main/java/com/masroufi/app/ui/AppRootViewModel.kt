package com.masroufi.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppRootViewModel @Inject constructor(settings: SettingsStore) : ViewModel() {
    /** Whether onboarding is done; null until the stored value is read, so nothing shows before it is known. */
    val onboarded: StateFlow<Boolean?> =
        settings.settings.map<_, Boolean?> { it.onboarded }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
