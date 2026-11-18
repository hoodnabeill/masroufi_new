package com.masroufi.app.feature.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.PIN_MIN_DIGITS
import com.masroufi.app.feature.kids.PinLine
import com.masroufi.app.feature.kids.PinPad
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.feature.settings.Group
import com.masroufi.app.feature.settings.SwitchRow
import com.masroufi.app.feature.settings.ValueRow
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** القفل والبصمة: the lock switch (a PIN is set first when there is none), change the PIN, the fingerprint switch. */
@Composable
fun LockSettingsScreen(viewModel: LockSettingsViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LockSettingsContent(ui, viewModel, onBack)
}

@Composable
fun LockSettingsContent(ui: LockSettingsUi, vm: LockSettingsViewModel, onBack: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.locks_title), onBack)
        Group(R.string.locks_group) {
            SwitchRow(R.string.locks_switch, R.string.locks_switch_sub, ui.lockOn, vm::toggleLock, last = !ui.lockOn)
            if (ui.lockOn) {
                ValueRow(R.string.locks_change, null, onClick = vm::startChange)
                SwitchRow(R.string.locks_fingerprint, R.string.locks_fingerprint_sub, ui.fingerprint, vm::setFingerprint, last = true)
            }
        }
        Text(stringResource(R.string.locks_when), Modifier.padding(horizontal = 16.dp), style = TzType.caption, color = Tz.text2)
    }
    if (ui.step != LockStep.None) {
        TzSheet(vm::cancel) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.locks_title), style = TzType.cardHeading, color = Tz.text)
                PinLine(stringResource(stepLine(ui.step)))
                errorLine(ui)?.let { PinLine(it, error = true) }
                PinPad(
                    ui.digits.length, enabled = true, okEnabled = ui.digits.length >= PIN_MIN_DIGITS,
                    onDigit = { haptics.perform(HapticLevel.Tick); vm.digit(it) }, onBackspace = vm::backspace, onOk = vm::ok,
                )
            }
        }
    }
}

private fun stepLine(step: LockStep): Int = when (step) {
    LockStep.Create, LockStep.NewAfterVerify -> R.string.locks_new
    LockStep.Repeat, LockStep.RepeatChange -> R.string.locks_repeat
    else -> R.string.locks_current
}

@Composable
private fun errorLine(ui: LockSettingsUi): String? = when (ui.error) {
    LockError.Mismatch -> stringResource(R.string.locks_mismatch)
    LockError.Wrong -> ui.triesLeft?.let { stringResource(R.string.kids_unlock_wrong, it.toString()) } ?: stringResource(R.string.lock_wrong)
    LockError.Waiting -> stringResource(R.string.locks_wait, ui.waitSeconds.toString())
    LockError.Unreadable -> stringResource(R.string.locks_unreadable)
    null -> null
}
