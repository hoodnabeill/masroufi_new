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
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.GroupedContent
import com.masroufi.app.ui.components.GroupedRow
import com.masroufi.app.ui.components.GroupedSection
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import com.masroufi.app.core.applyAppLanguage
import com.masroufi.app.ui.components.DropdownField
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.GlassDialog
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.ui.components.GlassSwitch
import com.masroufi.app.ui.components.MIcons
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
fun SettingsScreen(onBack: () -> Unit, onPremium: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val language = LocalConfiguration.current.locales[0].language
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.launch { setNotifications(granted) }
    }
    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.settings_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Reading money SMS is opt-in: the switch asks for RECEIVE_SMS, and only turns on once it is granted.
            val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                viewModel.launch { setSmsAutoLog(granted) }
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            GroupedSection(
                rows = listOf<@Composable () -> Unit>(
                    {
                        GroupedRow(
                            title = stringResource(R.string.premium_title),
                            subtitle = stringResource(R.string.more_premium_sub),
                            icon = MIcons.Crown,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            onClick = onPremium,
                        )
                    },
                ),
                inset = 58.dp,
            )
            GroupedSection(
                header = stringResource(R.string.settings_language),
                rows = listOf<@Composable () -> Unit>(
                    {
                        GroupedContent {
                            SegmentedChoice(
                                listOf("ar", "en"),
                                if (language == "ar") "ar" else "en",
                                { applyAppLanguage(it) },
                                { if (it == "ar") "العربية" else "English" },
                            )
                        }
                    },
                ),
            )
            GroupedSection(
                header = stringResource(R.string.settings_theme),
                rows = listOf<@Composable () -> Unit>(
                    {
                        GroupedContent {
                            SegmentedChoice(
                                ThemeMode.entries.toList(),
                                s.themeMode,
                                { m -> viewModel.launch { setTheme(m) } },
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
                        }
                    },
                    {
                        GroupedRow(
                            title = stringResource(R.string.settings_solid_glass),
                            subtitle = stringResource(R.string.settings_solid_glass_sub),
                            trailing = { GlassSwitch(s.solidGlass, { v -> viewModel.launch { setSolidGlass(v) } }) },
                        )
                    },
                ),
            )
            GroupedSection(
                footer = stringResource(R.string.settings_payday_sub) + "\n" + stringResource(R.string.settings_currency_note),
                rows = listOf<@Composable () -> Unit>(
                    {
                        GroupedContent {
                            DropdownField(
                                label = stringResource(R.string.settings_payday),
                                options = (0..31).toList(),
                                selected = s.paydayDay,
                                onSelect = { d -> viewModel.launch { setPayday(d) } },
                                optionLabel = { d -> if (d == 0) stringResource(R.string.payday_none) else d.toString() },
                            )
                        }
                    },
                    { GroupedContent { CurrencyField(s.primaryCurrency, { c -> viewModel.launch { setPrimaryCurrency(c) } }) } },
                ),
            )
            GroupedSection(
                header = stringResource(R.string.settings_privacy),
                footer = stringResource(R.string.settings_privacy_body),
                rows = listOf<@Composable () -> Unit>(
                    {
                        GroupedRow(
                            title = stringResource(R.string.settings_sms),
                            subtitle = stringResource(R.string.settings_sms_sub),
                            trailing = {
                                GlassSwitch(s.smsAutoLog, { v ->
                                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (v && !granted) smsPermission.launch(android.Manifest.permission.RECEIVE_SMS) else viewModel.launch { setSmsAutoLog(v) }
                                })
                            },
                        )
                    },
                    {
                        GroupedRow(
                            title = stringResource(R.string.settings_notifications),
                            subtitle = stringResource(R.string.settings_notifications_sub),
                            trailing = {
                                GlassSwitch(s.notificationsEnabled, { v ->
                                    if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.launch { setNotifications(v) }
                                    }
                                })
                            },
                        )
                    },
                ),
            )
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
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val available = canUseBiometric(activity)
            GroupedSection(
                footer = stringResource(R.string.security_app_lock_sub),
                rows = buildList<@Composable () -> Unit> {
                    add {
                        GroupedRow(
                            title = stringResource(R.string.security_app_lock),
                            icon = MIcons.Lock,
                            iconTint = categoryColor(0),
                            trailing = { GlassSwitch(s.lockEnabled, { v -> if (v) settingPin = true else confirmDisable = true }) },
                        )
                    }
                    if (s.lockEnabled) {
                        add {
                            GroupedRow(
                                title = stringResource(R.string.security_biometric),
                                subtitle = stringResource(if (available) R.string.security_biometric_sub else R.string.security_biometric_unavailable),
                                icon = MIcons.Fingerprint,
                                iconTint = categoryColor(6),
                                trailing = {
                                    GlassSwitch(s.biometricEnabled, { v ->
                                        // Confirm the sensor works before relying on it, so the user can't lock themselves into a broken path.
                                        if (v) showBiometricPrompt(activity, biometricTitle, cancel) { viewModel.launch { setBiometric(true) } } else viewModel.launch { setBiometric(false) }
                                    }, enabled = available)
                                },
                            )
                        }
                        add {
                            GroupedRow(
                                title = stringResource(R.string.security_change_pin),
                                icon = MIcons.Pencil,
                                iconTint = categoryColor(1),
                                titleColor = MaterialTheme.colorScheme.secondary,
                                onClick = { settingPin = true },
                            )
                        }
                    }
                },
                inset = 58.dp,
            )
            if (s.lockEnabled) {
                GroupedSection(
                    header = stringResource(R.string.security_timeout),
                    rows = listOf<@Composable () -> Unit>(
                        {
                            GroupedContent {
                                ChipChoice(
                                    listOf(0, 30, 60, 300), s.lockTimeoutSeconds, { t -> viewModel.launch { setLockTimeout(t) } },
                                    { if (it == 0) stringResource(R.string.security_timeout_immediate) else stringResource(R.string.security_timeout_seconds, it) },
                                )
                            }
                        },
                    ),
                )
            }
            GroupedSection(
                footer = stringResource(R.string.security_hide_recents_sub),
                rows = listOf<@Composable () -> Unit>(
                    {
                        GroupedRow(
                            title = stringResource(R.string.security_hide_recents),
                            trailing = { GlassSwitch(s.hideInRecents, { v -> viewModel.launch { setHideInRecents(v) } }) },
                        )
                    },
                ),
            )
            GroupedSection(
                header = stringResource(R.string.security_model_title),
                rows = listOf<@Composable () -> Unit>(
                    {
                        Text(
                            stringResource(R.string.security_model_body),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    },
                ),
            )
        }
    }
    if (settingPin) {
        SetPinDialog(onDismiss = { settingPin = false }, onSet = { viewModel.enableLock(it); settingPin = false })
    }
    if (confirmDisable) {
        GlassDialog(
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
    GlassDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(MIcons.Lock, contentDescription = null) },
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
        Column(Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyState(
                MIcons.Crown,
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

