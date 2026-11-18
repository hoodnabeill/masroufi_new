package com.masroufi.app.feature.kids

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.PIN_MIN_DIGITS
import com.masroufi.app.core.formatPounds
import com.masroufi.app.feature.pockets.AmountSheet
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.util.Locale

/**
 * شاشة الطفل: the app while it is pinned to one child. This child's ticket and jar, "صرفت", saving into the jar and asking
 * for an advance. Nothing of the parent's money is here; "خروج" asks for the parent's PIN, fingerprint or device credential.
 */
@Composable
fun ChildScreen(viewModel: ChildScreenViewModel) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(viewModel) { viewModel.haptics.collect { haptics.perform(it) } }
    val child = ui.child
    Column(
        Modifier.fillMaxSize().background(Tz.night).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.kids_child_title), style = TzType.titlePushed, color = Tz.text)
            TextLink(stringResource(R.string.kids_child_exit), { haptics.perform(HapticLevel.Tap); viewModel.openUnlock() })
        }
        if (child != null) {
            val t = ChildTicketUi(child, ui.finishedOn, ui.slots)
            val advance = child.waitingAdvance
            val jar = child.jar
            KidsTicket(
                t, ui.handoverDay,
                hasBelow = advance != null || jar != null,
                below = { c ->
                    if (advance != null) {
                        Text(stringResource(R.string.kids_child_advance_waiting, formatPounds(advance.amount)), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = c.ink)
                    }
                    if (jar != null) {
                        JarBlock(jar, c, if (jar.matchPer != null && jar.matchAdd != null) stringResource(R.string.kids_child_match, formatPounds(jar.matchPer), formatPounds(jar.matchAdd)) else null)
                    }
                },
            )
            ui.notice?.let { Text(noticeText(it), style = TzType.label.copy(fontSize = 14.sp), color = Tz.text) }
            PrimaryButton(stringResource(R.string.kids_child_spent), { haptics.perform(HapticLevel.Tap); viewModel.openSheet(ChildSheet.SPEND) }, Modifier.fillMaxWidth(), height = 60.dp, textStyle = TzType.button.copy(fontSize = 22.sp))
            if (jar != null) {
                SecondaryButton(stringResource(R.string.kids_child_save), { haptics.perform(HapticLevel.Tap); viewModel.openSheet(ChildSheet.SAVE) }, Modifier.fillMaxWidth())
            }
            SecondaryButton(stringResource(R.string.kids_child_advance), { haptics.perform(HapticLevel.Tap); viewModel.openSheet(ChildSheet.ADVANCE) }, Modifier.fillMaxWidth())
        }
    }
    when (ui.sheet) {
        ChildSheet.SPEND -> AmountSheet(stringResource(R.string.kids_child_spent_q), null, stringResource(R.string.kids_child_ok), viewModel::submit, viewModel::dismissSheet)
        ChildSheet.SAVE -> AmountSheet(stringResource(R.string.kids_child_save_q), null, stringResource(R.string.kids_child_ok), viewModel::submit, viewModel::dismissSheet)
        ChildSheet.ADVANCE -> AmountSheet(stringResource(R.string.kids_child_advance_q), null, stringResource(R.string.kids_child_ok), viewModel::submit, viewModel::dismissSheet)
        null -> Unit
    }
    ui.unlock?.let { UnlockSheet(it, viewModel) }
}

@Composable
private fun noticeText(n: ChildNotice): String = when (n) {
    is ChildNotice.AskParent -> stringResource(R.string.kids_child_ask_parent, formatPounds(n.left))
    ChildNotice.AdvanceSent -> stringResource(R.string.kids_child_advance_sent)
    ChildNotice.AdvanceOpen -> stringResource(R.string.kids_child_advance_open)
    is ChildNotice.AdvanceLimit -> if (n.limit > 0) stringResource(R.string.kids_child_advance_limit, formatPounds(n.limit)) else stringResource(R.string.kids_child_advance_none)
    ChildNotice.NotDone -> stringResource(R.string.kids_child_not_done)
}

/** "خروج": the PIN pad with a fingerprint key; waits and wrong tries are said plainly; a PIN that cannot be read is cleared only after the system prompt. */
@Composable
private fun UnlockSheet(u: UnlockUi, vm: ChildScreenViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.fragmentActivity() }
    val canAuth = remember(context) { canAuthenticateParent(context) }
    val title = stringResource(R.string.kids_unlock_title)
    val cancel = stringResource(R.string.kids_unlock_cancel)
    val prompt = {
        if (activity != null && canAuth) promptParent(activity, title, cancel, vm::biometricOk) else vm.biometricUnavailable()
    }
    TzSheet(vm::closeUnlock) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = TzType.cardHeading, color = Tz.text)
            if (u.unreadable) {
                PinLine(stringResource(R.string.kids_unlock_unreadable))
                if (u.noBiometric || !canAuth) PinLine(stringResource(R.string.kids_unlock_no_biometric), error = true)
                PrimaryButton(stringResource(R.string.kids_unlock_clear), { prompt() }, Modifier.fillMaxWidth(), enabled = canAuth, height = 52.dp)
            } else {
                PinLine(stringResource(R.string.kids_unlock_line))
                if (u.waitMillis > 0) PinLine(stringResource(R.string.kids_unlock_wait, waitText(u.waitMillis)), error = true)
                else if (u.triesLeft != null) PinLine(stringResource(R.string.kids_unlock_wrong, u.triesLeft.toString()), error = true)
                PinPad(
                    u.digits.length, enabled = u.waitMillis == 0L, okEnabled = u.digits.length >= PIN_MIN_DIGITS,
                    onDigit = vm::digit, onBackspace = vm::backspace, onOk = vm::submitPin,
                    fingerprint = if (canAuth) ({ prompt() }) else null,
                )
            }
        }
    }
}

/** 90 000 ms -> "1:30". */
private fun waitText(ms: Long): String {
    val s = (ms + 999) / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
