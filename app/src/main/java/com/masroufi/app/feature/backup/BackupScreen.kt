package com.masroufi.app.feature.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatDate
import com.masroufi.app.core.formatDayMonth
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.feature.profile.restartApp
import com.masroufi.app.feature.settings.SwitchRow
import com.masroufi.app.feature.settings.ValueRow
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import androidx.compose.foundation.layout.heightIn
import com.masroufi.app.ui.components.CappedFontScale
import com.masroufi.app.ui.motion.pressClickable
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** النسخة الاحتياطية (Backup.dc.html): opens the system pickers the view model asks for. */
@Composable
fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { viewModel.created(it) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { viewModel.exported(it) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.opened(it) }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.treePicked(it) }
    LaunchedEffect(ui.launch) {
        val l = ui.launch ?: return@LaunchedEffect
        viewModel.launched()
        try {
            when (l.kind) {
                BackupLaunch.Kind.Create -> create.launch(l.name)
                BackupLaunch.Kind.Export -> export.launch(l.name)
                BackupLaunch.Kind.Open -> open.launch(arrayOf("*/*"))
                BackupLaunch.Kind.Tree -> tree.launch(null)
            }
        } catch (e: ActivityNotFoundException) {
            // No file picker on this phone: nothing opens and nothing changes.
        }
    }
    LaunchedEffect(ui.restarted) { if (ui.restarted) restartApp(context) }
    BackupContent(ui, viewModel, onBack)
}

@Composable
fun BackupContent(ui: BackupUi, actions: BackupActions, onBack: () -> Unit) {
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.settings_backup), onBack)
        Hero(ui)
        if (ui.sheet == null) ui.message?.let { MessageLine(it) }
        if (ui.progress != null) {
            WritingBar(ui.progress)
        } else {
            PrimaryButton(
                stringResource(R.string.backup_now), { haptics.perform(HapticLevel.Confirm); actions.backupNow() },
                Modifier.fillMaxWidth(), height = 60.dp, textStyle = TzType.button.copy(fontSize = 22.sp),
            )
        }
        YellowOutlineButton(stringResource(R.string.backup_restore)) { haptics.perform(HapticLevel.Tap); actions.restoreTapped() }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(TzRadius.card)).background(Tz.surface).border(1.dp, Tz.line, RoundedCornerShape(TzRadius.card))) {
            SwitchRow(R.string.backup_auto, R.string.backup_auto_sub, ui.auto, actions::setAuto)
            ValueRow(R.string.backup_where, ui.folder ?: stringResource(R.string.backup_where_pick), onClick = actions::pickFolder)
            ValueRow(
                R.string.backup_password, stringResource(if (ui.hasPassword) R.string.backup_password_change else R.string.backup_password_set),
                last = true, onClick = actions::changePassword,
            )
        }
        Text(stringResource(R.string.backup_key_note), Modifier.padding(horizontal = 16.dp), style = TzType.caption, color = Tz.text2)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(TzRadius.card)).background(Tz.yellowDim).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Qersh(QershMood.Thinking, 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.backup_warn_title), style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.yellow)
                Text(stringResource(R.string.backup_warn_body), style = TzType.caption.copy(lineHeight = 20.sp), color = WarnText)
            }
        }
        Row(
            Modifier.pressClickable { haptics.perform(HapticLevel.Tap); actions.exportTapped() }.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
                .clip(RoundedCornerShape(TzRadius.card)).background(Tz.surface).border(1.dp, Tz.line, RoundedCornerShape(TzRadius.card))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.backup_export), style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = Tz.text)
                Text(stringResource(R.string.backup_export_sub), style = TzType.caption, color = Tz.text2)
            }
            TzIcon(TzIcons.download, 20.dp, null, tint = Tz.yellow)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(TzRadius.card)).background(Tz.surface)
                .border(1.dp, Tz.line, RoundedCornerShape(TzRadius.card)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_phone_title), style = TzType.cardHeading, color = Tz.text)
            listOf(R.string.backup_phone_1, R.string.backup_phone_2, R.string.backup_phone_3).forEachIndexed { i, text ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Tz.yellow), contentAlignment = Alignment.Center) {
                        // shortcut: the step number sits in a fixed 32 dp disc, so it keeps the normal font size.
                        CappedFontScale { Text((i + 1).toString(), style = TzType.cardHeading.copy(fontSize = 18.sp), color = Tz.night) }
                    }
                    Text(stringResource(text), Modifier.weight(1f), style = TzType.body.copy(fontSize = 14.sp), color = Tz.text)
                }
            }
        }
        Text(stringResource(R.string.backup_footer), Modifier.fillMaxWidth(), style = TzType.caption.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = Tz.text2)
    }
    when (ui.sheet) {
        BackupSheet.SetPassword -> PasswordSheet(ui, actions)
        BackupSheet.Restore -> RestoreSheet(ui, actions)
        BackupSheet.Export -> ExportSheet(actions)
        null -> {}
    }
}

private val WarnText = Color(0xFFF0E3B5)

/** The status hero: the last backup, or the yellow-dim "no backup yet" state; a failed automatic run adds one quiet line. */
@Composable
private fun Hero(ui: BackupUi) {
    val never = ui.lastDay == null
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(TzRadius.hero)).background(if (never) Tz.yellowDim else Tz.turquoiseDim).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(if (never) Tz.yellow else Tz.turquoise), contentAlignment = Alignment.Center) {
            TzIcon(if (never) TzIcons.lock else TzIcons.check, 32.dp, null, tint = if (never) Tz.night else Tz.turquoiseInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (never) {
                Text(stringResource(R.string.backup_none), style = TzType.cardHeading.copy(lineHeight = 28.sp), color = Tz.text)
            } else {
                Text(stringResource(R.string.backup_last_kicker), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.turquoiseDimText)
                Text(backupWhen(ui.lastDay!!, ui.lastTime ?: LocalTime.MIDNIGHT, ui.today, arabic), style = TzType.titleTab, color = Tz.text)
                Text(
                    stringResource(R.string.backup_stats, ui.tickets.toString(), sizeText(ui.bytes)),
                    style = TzType.caption, color = Tz.turquoiseDimText,
                )
            }
            if (ui.failed) Text(stringResource(R.string.backup_failed), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.carnelianText)
        }
    }
}

/** "امبارح 9:12 م": today and yesterday by name, older days by date. Western digits. */
@Composable
internal fun backupWhen(day: LocalDate, time: LocalTime, today: LocalDate, arabic: Boolean): String {
    val h12 = if (time.hour % 12 == 0) 12 else time.hour % 12
    val clock = String.format(Locale.US, "%d:%02d", h12, time.minute)
    val at = stringResource(if (time.hour < 12) R.string.backup_time_am else R.string.backup_time_pm, clock)
    return when (day) {
        today -> stringResource(R.string.backup_when_today, at)
        today.minusDays(1) -> stringResource(R.string.backup_when_yesterday, at)
        else -> stringResource(R.string.backup_when_date, formatDayMonth(day, Locale(if (arabic) "ar" else "en")), at)
    }
}

@Composable
private fun sizeText(bytes: Long): String =
    if (bytes >= 100_000) stringResource(R.string.backup_size_mb, String.format(Locale.US, "%.1f", bytes / 1_000_000.0))
    else stringResource(R.string.backup_size_kb, ((bytes + 999) / 1000).toString())

/** While the file is written the main button is a bar that fills. */
@Composable
private fun WritingBar(progress: Float) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "backupProgress")
    val shape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().height(60.dp).clip(shape).background(Tz.yellowDim).progressSemantics(p)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceAtLeast(0.02f)).background(Tz.yellow))
        Text(
            stringResource(R.string.backup_writing), Modifier.align(Alignment.Center),
            style = TzType.button.copy(fontSize = 20.sp), color = if (p > 0.5f) Tz.night else Tz.text,
        )
    }
}

@Composable
private fun YellowOutlineButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.pressClickable(onClick = onClick).fillMaxWidth().heightIn(min = 52.dp).clip(shape).border(1.5.dp, Tz.yellow, shape).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = TzType.button.copy(fontSize = 18.sp), color = Tz.yellow) }
}

@Composable
private fun MessageLine(m: BackupMessage) {
    Text(
        stringResource(messageText(m)), Modifier.padding(horizontal = 16.dp),
        style = TzType.caption.copy(fontSize = 13.sp), color = if (m == BackupMessage.ExportSaved) Tz.turquoise else Tz.carnelianText,
    )
}

private fun messageText(m: BackupMessage): Int = when (m) {
    BackupMessage.SaveFailed -> R.string.backup_msg_save_failed
    BackupMessage.ExportSaved -> R.string.backup_msg_export_saved
    BackupMessage.NotABackup -> R.string.backup_msg_not_backup
    BackupMessage.WrongPassword -> R.string.backup_msg_wrong
    BackupMessage.Damaged -> R.string.backup_msg_damaged
    BackupMessage.TooNew -> R.string.backup_msg_too_new
    BackupMessage.PasswordShort -> R.string.backup_pw_short
    BackupMessage.PasswordMismatch -> R.string.backup_pw_mismatch
}

@Composable
private fun PasswordSheet(ui: BackupUi, actions: BackupActions) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val haptics = LocalHaptics.current
    TzSheet(actions::closeSheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_password), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.backup_pw_warn), style = TzType.caption.copy(fontSize = 13.sp, lineHeight = 21.sp), color = Tz.yellow)
            if (ui.hasPassword) Text(stringResource(R.string.backup_pw_old), style = TzType.caption, color = Tz.text2)
            PasswordField(pw, { pw = it }, stringResource(R.string.backup_pw_new))
            PasswordField(again, { again = it }, stringResource(R.string.backup_pw_repeat))
            ui.message?.let { MessageLine(it) }
            PrimaryButton(
                stringResource(R.string.backup_pw_save), { haptics.perform(HapticLevel.Confirm); actions.submitPassword(pw, again) },
                Modifier.fillMaxWidth(), enabled = !ui.busy && pw.isNotEmpty(), height = 52.dp,
            )
        }
    }
}

@Composable
private fun RestoreSheet(ui: BackupUi, actions: BackupActions) {
    var pw by remember { mutableStateOf("") }
    val haptics = LocalHaptics.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val info = ui.restore
    TzSheet(actions::closeSheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_restore), style = TzType.cardHeading, color = Tz.text)
            if (info != null) {
                Text(
                    stringResource(R.string.backup_restore_line, formatDate(info.day, Locale(if (arabic) "ar" else "en")), info.tickets.toString()),
                    style = TzType.body.copy(fontSize = 14.sp), color = Tz.text,
                )
            }
            PasswordField(pw, { pw = it }, stringResource(R.string.backup_restore_pw))
            ui.message?.let { MessageLine(it) }
            PrimaryButton(
                stringResource(if (ui.busy) R.string.backup_restore_busy else R.string.backup_restore_go),
                { haptics.perform(HapticLevel.Heavy); actions.confirmRestore(pw) },
                Modifier.fillMaxWidth(), enabled = !ui.busy && pw.isNotEmpty(), height = 52.dp,
            )
        }
    }
}

@Composable
private fun ExportSheet(actions: BackupActions) {
    val haptics = LocalHaptics.current
    TzSheet(actions::closeSheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_export), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.backup_export_warn), style = TzType.body.copy(fontSize = 14.sp), color = Tz.yellow)
            PrimaryButton(
                stringResource(R.string.backup_export_go), { haptics.perform(HapticLevel.Confirm); actions.confirmExport() },
                Modifier.fillMaxWidth(), height = 52.dp,
            )
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, hint: String) {
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(64)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(Tz.night).border(1.dp, Tz.line, shape),
        textStyle = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tz.text),
        singleLine = true,
        cursorBrush = SolidColor(Tz.yellow),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, style = TzType.body, color = Tz.text3)
                inner()
            }
        },
    )
}
