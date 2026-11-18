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

/** What the app shows: the child screen while a child mode is set (phase 9b decision 6), else onboarding or the app. */
sealed interface RootMode {
    data object Onboarding : RootMode
    data object App : RootMode
    data class Child(val childId: Long) : RootMode
}

@HiltViewModel
class AppRootViewModel @Inject constructor(settings: SettingsStore, endOfMonthMode: EndOfMonthMode) : ViewModel() {
    /** Whether onboarding is done; null until the stored value is read, so nothing shows before it is known. */
    val onboarded: StateFlow<Boolean?> =
        settings.settings.map<_, Boolean?> { it.onboarded }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null until the stored values are read. A set child mode beats everything: cold start, back and recents all land on that child. */
    val mode: StateFlow<RootMode?> = settings.settings.map<_, RootMode?> {
        val child = it.childModeId
        when {
            child != null -> RootMode.Child(child)
            it.onboarded -> RootMode.App
            else -> RootMode.Onboarding
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** End-of-month mode is on: Plus-locked cards leave out "جرّب بلس" (plan decision 8). */
    val endOfMonth: StateFlow<Boolean> = endOfMonthMode.isOn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
