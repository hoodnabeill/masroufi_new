package com.masroufi.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.MoneyFormatter
import com.masroufi.app.data.prefs.AppSettings
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.feature.onboarding.OnboardingScreen
import com.masroufi.app.feature.security.LockScreen
import com.masroufi.app.security.AppLockManager
import com.masroufi.app.security.LockState
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.navigation.MasroufiNavHost
import com.masroufi.app.ui.theme.MasroufiTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
    lockManager: AppLockManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settings.settings
        .map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lock: StateFlow<LockState> = lockManager.state
}

private enum class RootContent { LOADING, ONBOARDING, LOCKED, APP }

@Composable
fun MasroufiRoot(viewModel: RootViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lock by viewModel.lock.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { MoneyFormatter(locale) }

    MasroufiTheme(mode = current.themeMode, dynamicColor = current.dynamicColor) {
        CompositionLocalProvider(LocalMoneyFormatter provides formatter) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val content = when {
                    settings == null || lock == LockState.UNKNOWN -> RootContent.LOADING
                    !current.onboardingDone -> RootContent.ONBOARDING
                    lock == LockState.LOCKED -> RootContent.LOCKED
                    else -> RootContent.APP
                }
                // The app shell stays composed underneath the lock screen so navigation state survives locking.
                Box(Modifier.fillMaxSize()) {
                    if (content == RootContent.APP || content == RootContent.LOCKED) MasroufiNavHost()
                    AnimatedContent(
                        targetState = content,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "root",
                    ) { state ->
                        when (state) {
                            RootContent.LOADING -> Box(Modifier.fillMaxSize())
                            RootContent.ONBOARDING -> OnboardingScreen()
                            RootContent.LOCKED -> LockScreen(biometricEnabled = current.biometricEnabled)
                            RootContent.APP -> Unit
                        }
                    }
                }
            }
        }
    }
}
