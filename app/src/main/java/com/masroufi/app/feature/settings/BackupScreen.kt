package com.masroufi.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.backup.BackupPayload
import com.masroufi.app.backup.BackupRepository
import com.masroufi.app.backup.SyncRepository
import com.masroufi.app.backup.SyncStatus
import com.masroufi.app.backup.UnsupportedBackupException
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.prefs.SyncSettings
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.entitlements.PremiumFeature
import com.masroufi.app.security.PayloadCrypto
import com.masroufi.app.ui.components.CollectEffects
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.LabeledRow
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.work.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

data class PendingRestore(val uri: Uri, val payload: BackupPayload)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupRepository,
    private val sync: SyncRepository,
    settings: SettingsRepository,
    entitlements: Entitlements,
) : ViewModel() {
    val syncSettings: StateFlow<SyncSettings> = settings.sync.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSettings())
    val lastBackup: StateFlow<Long?> = settings.settings.map { it.lastBackupAt }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val syncUnlocked: StateFlow<Boolean> = entitlements.isUnlocked(PremiumFeature.WEBDAV_SYNC).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _pending = MutableStateFlow<PendingRestore?>(null)
    val pending: StateFlow<PendingRestore?> = _pending.asStateFlow()
    val messages = EffectChannel<Int>()

    private fun launchTask(block: suspend () -> Int?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: PayloadCrypto.WrongPasswordException) {
                R.string.backup_wrong_password
            } catch (e: PayloadCrypto.NotABackupException) {
                R.string.backup_not_a_backup
            } catch (e: UnsupportedBackupException) {
                R.string.backup_newer_version
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                R.string.backup_failed
            }
            _busy.value = false
            message?.let(messages::send)
        }
    }

    fun export(uri: Uri, password: String) = launchTask {
        backup.exportTo(uri, password.toCharArray())
        R.string.backup_exported
    }

    fun readBackup(uri: Uri, password: String) = launchTask {
        _pending.value = PendingRestore(uri, backup.readFrom(uri, password.toCharArray()))
        null
    }

    fun confirmRestore(replace: Boolean) {
        val p = _pending.value ?: return
        _pending.value = null
        launchTask {
            backup.write(p.payload, replace)
            R.string.backup_restored
        }
    }

    fun cancelRestore() {
        _pending.value = null
    }

    fun configure(url: String, user: String, password: String, passphrase: String) = launchTask {
        when (sync.configure(url, user, password, passphrase)) {
            SyncStatus.INVALID_URL -> R.string.sync_invalid_url
            else -> {
                SyncWorker.schedule(context)
                syncMessage(sync.sync().status)
            }
        }
    }

    fun syncNow() = launchTask { syncMessage(sync.sync().status) }

    fun disconnect() = launchTask {
        sync.disconnect()
        SyncWorker.cancel(context)
        R.string.sync_disconnected
    }

    @StringRes
    private fun syncMessage(status: SyncStatus): Int = syncStatusLabel(status)
}

@StringRes
fun syncStatusLabel(status: SyncStatus): Int = when (status) {
    SyncStatus.OK -> R.string.sync_ok
    SyncStatus.NOT_CONFIGURED -> R.string.sync_not_configured
    SyncStatus.AUTH_FAILED -> R.string.sync_auth_failed
    SyncStatus.WRONG_PASSPHRASE -> R.string.sync_wrong_passphrase
    SyncStatus.NETWORK -> R.string.sync_network
    SyncStatus.INVALID_URL -> R.string.sync_invalid_url
    SyncStatus.REMOTE_CHANGED -> R.string.sync_remote_changed
    SyncStatus.SERVER_ERROR -> R.string.sync_server_error
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun BackupScreen(onBack: () -> Unit, onPremium: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val sync by viewModel.syncSettings.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackup.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val syncUnlocked by viewModel.syncUnlocked.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dates = rememberDateFormatter()
    CollectEffects(viewModel.messages.flow) { snackbar.showSnackbar(context.getString(it)) }

    var password by rememberSaveable { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.export(uri, password)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readBackup(uri, password)
    }
    var url by rememberSaveable { mutableStateOf(sync.url) }
    var user by rememberSaveable { mutableStateOf(sync.username) }
    var davPassword by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { MasroufiTopBar(stringResource(R.string.backup_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionHeader(stringResource(R.string.backup_file_section))
            Text(stringResource(R.string.backup_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lastBackup?.let { Text(stringResource(R.string.backup_last, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(dates)), style = MaterialTheme.typography.bodySmall) }
            PasswordField(password, { password = it }, stringResource(R.string.backup_password))
            Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(onClick = { exporter.launch("masroufi-backup.msrf") }, enabled = !busy && password.length >= MIN_PASSWORD, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, enabled = !busy && password.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_import))
                }
            }

            SectionHeader(stringResource(R.string.sync_title))
            if (!syncUnlocked) {
                TextButton(onClick = onPremium) { Text(stringResource(R.string.premium_required)) }
            } else {
                Text(stringResource(R.string.sync_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (syncUnlocked && sync.isConfigured) {
                PressableCard(onClick = null) {
                    LabeledRow(stringResource(R.string.sync_server)) { Text(sync.url, maxLines = 1) }
                    LabeledRow(stringResource(R.string.sync_last)) {
                        Text(sync.lastSyncAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: stringResource(R.string.sync_never))
                    }
                    sync.lastStatus?.let { status ->
                        val parsed = runCatching { SyncStatus.valueOf(status) }.getOrNull()
                        if (parsed != null) LabeledRow(stringResource(R.string.sync_status)) { Text(stringResource(syncStatusLabel(parsed))) }
                    }
                    if (sync.lastConflicts > 0) Text(stringResource(R.string.sync_conflicts, sync.lastConflicts), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(onClick = viewModel::syncNow, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sync_now)) }
                    OutlinedButton(onClick = viewModel::disconnect, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sync_disconnect)) }
                }
            } else if (syncUnlocked) {
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.sync_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.sync_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PasswordField(davPassword, { davPassword = it }, stringResource(R.string.sync_password))
                PasswordField(passphrase, { passphrase = it }, stringResource(R.string.sync_passphrase))
                Text(stringResource(R.string.sync_passphrase_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GlassButton(
                    onClick = { viewModel.configure(url, user, davPassword, passphrase) },
                    enabled = !busy && url.isNotBlank() && passphrase.length >= MIN_PASSWORD,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.sync_connect)) }
            }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.restore_summary,
                            Instant.ofEpochMilli(p.payload.exportedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(dates),
                            p.payload.transactionCount,
                            p.payload.wallets.count { !it.deleted },
                        ),
                    )
                    Text(stringResource(R.string.restore_choice))
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmRestore(replace = false) }) { Text(stringResource(R.string.restore_merge)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { viewModel.confirmRestore(replace = true) }) {
                        Text(stringResource(R.string.restore_replace), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }
}

private const val MIN_PASSWORD = 8
