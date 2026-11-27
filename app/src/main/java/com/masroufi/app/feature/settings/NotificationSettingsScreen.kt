package com.masroufi.app.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.NotifKind
import com.masroufi.app.feature.onboarding.DEFAULT_REMINDER_HOUR
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.notify.channelName
import com.masroufi.app.feature.pockets.LoadingRows
import com.masroufi.app.ui.motion.pressClickable
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationSettingsUi(
    val loading: Boolean = true,
    /** The switch of every kind; the evening one is on while a reminder hour is set. */
    val on: Map<NotifKind, Boolean> = emptyMap(),
    val reminderHour: Int? = null,
    val sheet: Boolean = false,
    /** The system refused the permission when a switch was turned on: the switch stayed off. */
    val denied: Boolean = false,
)

interface NotificationActions {
    fun setKind(kind: NotifKind, on: Boolean)

    /** The answer to the system's request; [kind] is the switch that asked (null = the reminder time was picked). */
    fun permissionResult(kind: NotifKind?, granted: Boolean)
    fun openReminder()
    fun closeReminder()
    fun setReminderHour(hour: Int?)
}

private data class NotifLocal(val sheet: Boolean = false, val denied: Boolean = false)

/** التنبيهات: a switch per kind, saved at once; turning one on asks for the permission first (decision 7). */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel(), NotificationActions {
    private val local = MutableStateFlow(NotifLocal())

    val state: StateFlow<NotificationSettingsUi> = combine(settings.settings, local) { s, l ->
        NotificationSettingsUi(
            loading = false,
            on = NotifKind.entries.associateWith(s::notifEnabled),
            reminderHour = s.reminderHour, sheet = l.sheet, denied = l.denied,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettingsUi())

    override fun setKind(kind: NotifKind, on: Boolean) = act {
        local.update { it.copy(denied = false) }
        if (kind == NotifKind.EVENING) settings.setReminderHour(if (on) DEFAULT_REMINDER_HOUR else null) else settings.setNotifKind(kind, on)
    }

    override fun permissionResult(kind: NotifKind?, granted: Boolean) {
        if (granted) {
            if (kind != null) setKind(kind, true) else local.update { it.copy(denied = false) }
        } else {
            local.update { it.copy(denied = true) }
        }
    }

    override fun openReminder() = local.update { it.copy(sheet = true) }
    override fun closeReminder() = local.update { it.copy(sheet = false) }
    override fun setReminderHour(hour: Int?) {
        closeReminder()
        act { settings.setReminderHour(hour) }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("NotificationSettings", "settings action failed", e)
            }
        }
    }
}

internal fun needsPermission(context: Context) =
    Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

@Composable
fun NotificationSettingsScreen(viewModel: NotificationSettingsViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // What asked for the permission: a kind's switch, or the reminder time (null).
    var asking by remember { mutableStateOf<NotifKind?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> viewModel.permissionResult(asking, granted) }
    var systemOff by remember { mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    // Back from the system's notification settings: read the switch again.
    LifecycleResumeEffect(Unit) {
        systemOff = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    val actions = object : NotificationActions by viewModel {
        override fun setKind(kind: NotifKind, on: Boolean) {
            if (on && needsPermission(context)) {
                asking = kind
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setKind(kind, on)
            }
        }

        override fun setReminderHour(hour: Int?) {
            viewModel.setReminderHour(hour)
            if (hour != null && needsPermission(context)) {
                asking = null
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val blocked = ui.denied || (systemOff && ui.on.values.any { it })
    val openSystem = {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Unit
    }
    NotificationSettingsContent(ui, actions, blocked, openSystem, onBack)
}

@Composable
fun NotificationSettingsContent(ui: NotificationSettingsUi, vm: NotificationActions, blocked: Boolean, onOpenSystem: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.settings_notifications), onBack)
        if (ui.loading) {
            LoadingRows()
            return@Column
        }
        Group(R.string.notif_group) {
            ValueRow(R.string.notif_reminder_time, hourText(ui.reminderHour)) { vm.openReminder() }
            val kinds = NotifKind.entries
            kinds.forEachIndexed { i, kind ->
                // A switch shows on only once the permission is there: a refused request leaves it off.
                SwitchRow(channelName(kind), null, ui.on[kind] == true, { vm.setKind(kind, it) }, last = i == kinds.lastIndex)
            }
        }
        Text(stringResource(R.string.notif_cap_line), Modifier.padding(horizontal = 16.dp), style = TzType.caption, color = Tz.text2)
        if (blocked) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.notif_blocked), style = TzType.caption, color = Tz.text)
                Text(
                    stringResource(R.string.notif_open_system),
                    Modifier.pressClickable(onClick = onOpenSystem).fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    style = TzType.body.copy(fontWeight = FontWeight.SemiBold), color = Tz.yellow,
                )
            }
        }
    }
    if (ui.sheet) ReminderSheet(ui.reminderHour, vm::setReminderHour, vm::closeReminder)
}
