package com.masroufi.app.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.data.prefs.AppSettings
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.prefs.ThemeMode
import com.masroufi.app.entitlements.EntitlementSource
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.entitlements.PremiumFeature
import com.masroufi.app.feature.security.PIN_LENGTH
import com.masroufi.app.feature.security.PinPad
import com.masroufi.app.feature.security.canUseBiometric
import com.masroufi.app.feature.security.showBiometricPrompt
import com.masroufi.app.security.AppLockManager
import com.masroufi.app.ui.components.ChipChoice
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.rememberHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val repo: SettingsRepository,
    private val lockManager: AppLockManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun launch(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }

    fun enableLock(pin: String) = launch {
        this.enableLock(AppLockManager.createPinRecord(pin))
    }

    fun disableLock() = launch {
        this.disableLock()
        lockManager.disabled()
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val language = LocalConfiguration.current.locales[0].language
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.launch { setNotifications(granted) }
    }
    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.settings_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            SegmentedChoice(
                listOf("ar", "en"),
                if (language == "ar") "ar" else "en",
                { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
                { if (it == "ar") "العربية" else "English" },
            )
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            SegmentedChoice(
                ThemeMode.entries.toList(), s.themeMode, { m -> viewModel.launch { setTheme(m) } },
                {
                    stringResource(
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        },
                    )
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_sub)) },
                    trailingContent = { Switch(s.dynamicColor, { v -> viewModel.launch { setDynamicColor(v) } }) },
                )
            }
            CurrencyField(s.primaryCurrency, { c -> viewModel.launch { setPrimaryCurrency(c) } })
            Text(stringResource(R.string.settings_currency_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_notifications_sub)) },
                trailingContent = {
                    Switch(s.notificationsEnabled, { v ->
                        if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.launch { setNotifications(v) }
                        }
                    })
                },
            )
            SectionHeader(stringResource(R.string.settings_privacy))
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SecurityScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val activity = LocalContext.current as FragmentActivity
    var settingPin by rememberSaveable { mutableStateOf(false) }
    var confirmDisable by rememberSaveable { mutableStateOf(false) }
    val biometricTitle = stringResource(R.string.lock_biometric_title)
    val cancel = stringResource(R.string.action_cancel)
    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.security_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.security_app_lock)) },
                supportingContent = { Text(stringResource(R.string.security_app_lock_sub)) },
                trailingContent = { Switch(s.lockEnabled, { v -> if (v) settingPin = true else confirmDisable = true }) },
            )
            if (s.lockEnabled) {
                val available = canUseBiometric(activity)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.security_biometric)) },
                    supportingContent = { Text(stringResource(if (available) R.string.security_biometric_sub else R.string.security_biometric_unavailable)) },
                    trailingContent = {
                        Switch(s.biometricEnabled, { v ->
                            // Confirm the sensor works before relying on it, so the user can't lock themselves into a broken path.
                            if (v) showBiometricPrompt(activity, biometricTitle, cancel) { viewModel.launch { setBiometric(true) } } else viewModel.launch { setBiometric(false) }
                        }, enabled = available)
                    },
                )
                Text(stringResource(R.string.security_timeout), style = MaterialTheme.typography.titleSmall)
                ChipChoice(
                    listOf(0, 30, 60, 300), s.lockTimeoutSeconds, { t -> viewModel.launch { setLockTimeout(t) } },
                    { if (it == 0) stringResource(R.string.security_timeout_immediate) else stringResource(R.string.security_timeout_seconds, it) },
                )
                OutlinedButton(onClick = { settingPin = true }) { Text(stringResource(R.string.security_change_pin)) }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.security_hide_recents)) },
                supportingContent = { Text(stringResource(R.string.security_hide_recents_sub)) },
                trailingContent = { Switch(s.hideInRecents, { v -> viewModel.launch { setHideInRecents(v) } }) },
            )
            PressableCard(onClick = null) {
                Text(stringResource(R.string.security_model_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.security_model_body), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (settingPin) {
        SetPinDialog(onDismiss = { settingPin = false }, onSet = { viewModel.enableLock(it); settingPin = false })
    }
    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(stringResource(R.string.security_disable_title)) },
            text = { Text(stringResource(R.string.security_disable_body)) },
            confirmButton = { TextButton(onClick = { viewModel.disableLock(); confirmDisable = false }) { Text(stringResource(R.string.action_disable)) } },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(stringResource(if (first == null) R.string.security_enter_pin else R.string.security_confirm_pin)) },
        text = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("●".repeat(pin.length) + "○".repeat(PIN_LENGTH - pin.length), style = MaterialTheme.typography.headlineSmall)
                if (mismatch) Text(stringResource(R.string.security_pin_mismatch), color = MaterialTheme.colorScheme.error)
                PinPad(
                    onDigit = { d ->
                        if (pin.length >= PIN_LENGTH) return@PinPad
                        haptics.tick()
                        pin += d
                        if (pin.length == PIN_LENGTH) {
                            val firstPin = first
                            when {
                                firstPin == null -> { first = pin; pin = ""; mismatch = false }
                                firstPin == pin -> onSet(pin)
                                else -> { haptics.reject(); first = null; pin = ""; mismatch = true }
                            }
                        }
                    },
                    onBackspace = { pin = pin.dropLast(1) },
                    extra = null,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@HiltViewModel
class PremiumViewModel @Inject constructor(entitlements: Entitlements) : ViewModel() {
    val state = entitlements.state
}

@Composable
fun PremiumScreen(onBack: () -> Unit, viewModel: PremiumViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.premium_title), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyState(
                Icons.Outlined.WorkspacePremium,
                stringResource(R.string.premium_title),
                stringResource(if (state.source == EntitlementSource.EARLY_ACCESS) R.string.premium_early_access else R.string.premium_active),
            )
            PremiumFeature.entries.forEach { feature ->
                ListItem(
                    headlineContent = { Text(stringResource(premiumLabel(feature))) },
                    trailingContent = { Text(stringResource(if (feature in state.unlocked) R.string.premium_unlocked else R.string.premium_locked)) },
                )
            }
            Text(stringResource(R.string.premium_promise), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun premiumLabel(feature: PremiumFeature): Int = when (feature) {
    PremiumFeature.ADVANCED_ANALYTICS -> R.string.premium_feature_analytics
    PremiumFeature.AUTO_SPLIT -> R.string.split_title
    PremiumFeature.WEBDAV_SYNC -> R.string.premium_feature_sync
    PremiumFeature.RECURRING_AUTOMATION -> R.string.recurring_title
    PremiumFeature.GAMEYA_PLUS -> R.string.gameya_title
    PremiumFeature.CUSTOMIZATION -> R.string.premium_feature_customization
}

