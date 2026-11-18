package com.masroufi.app.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.BuildConfig
import com.masroufi.app.R
import com.masroufi.app.core.Payday
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.feature.onboarding.PaydayChoices
import com.masroufi.app.feature.onboarding.PaydayDayChips
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.LocalEndOfMonthMode
import com.masroufi.app.ui.components.LocalOpenPaywall
import com.masroufi.app.ui.components.PlusSettingsRow
import com.masroufi.app.ui.components.PlusSheet
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.Segmented
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.TzToggle
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** One support address for the feedback row; empty until the owner gives one (decision 10), and the row is hidden while it is. */
const val SUPPORT_EMAIL = ""

/** Rows that open another screen; Tasks 55 to 57 fill the stub routes behind lock, backup and notifications. */
class SettingsNav(
    val onBack: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onLock: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onLegal: () -> Unit = {},
    val onLicences: () -> Unit = {},
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, nav: SettingsNav, supportEmail: String = SUPPORT_EMAIL, versionName: String = BuildConfig.VERSION_NAME) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(ui, viewModel, nav, supportEmail, versionName)
}

@Composable
fun SettingsContent(ui: SettingsUi, vm: SettingsActions, nav: SettingsNav, supportEmail: String, versionName: String) {
    val context = LocalContext.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val openPaywall = LocalOpenPaywall.current
    val endOfMonth = LocalEndOfMonthMode.current
    val subject = stringResource(R.string.settings_feedback_subject)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.settings_title), nav.onBack)
        if (ui.loading) {
            repeat(3) { RowSkeleton() }
            return@Column
        }
        ProfileRow(ui, nav.onProfile)
        PlusSettingsRow(
            plan = ui.plan, renewsOn = ui.renewsOn, trialEnds = ui.trialEnds, restore = ui.restore,
            onManage = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.manageUrl()))) } },
            onRestore = vm::restore,
        )

        Group(R.string.settings_group_ticket) {
            ValueRow(R.string.settings_payday, paydayText(ui.payday)) { vm.open(SettingsSheet.Payday) }
            ValueRow(R.string.settings_reminder, hourText(ui.reminderHour)) { vm.open(SettingsSheet.Reminder) }
            ValueRow(R.string.settings_leftover, stringResource(leftoverName(ui.leftover))) { vm.open(SettingsSheet.Leftover) }
            ValueRow(R.string.jar_round_title, roundText(ui.roundUpStep)) {
                when {
                    ui.isPlus -> vm.open(SettingsSheet.RoundUp)
                    endOfMonth -> vm.open(SettingsSheet.PlusInfo)
                    else -> openPaywall(PlusFeature.ROUND_UPS)
                }
            }
            ValueRow(R.string.settings_notifications, null, last = true, onClick = nav.onNotifications)
        }

        Group(R.string.settings_group_feel) {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RowTitle(R.string.settings_language, null)
                Segmented(
                    listOf(stringResource(R.string.settings_lang_ar), stringResource(R.string.settings_lang_en)),
                    if (arabic) 0 else 1,
                    { vm.setLanguage(if (it == 0) "ar" else "en") },
                    Modifier.width(190.dp),
                )
            }
            Divider()
            SwitchRow(R.string.settings_vibration, null, ui.vibration, vm::setVibration)
            SwitchRow(R.string.settings_sounds, R.string.settings_sounds_sub, ui.ticketSounds, vm::setTicketSounds)
            SwitchRow(R.string.settings_hide, R.string.settings_hide_sub, ui.hideAmounts, vm::setHideAmounts, last = true)
        }

        Group(R.string.settings_group_safety) {
            ValueRow(R.string.settings_lock, stringResource(if (ui.lockOn) R.string.settings_lock_on else R.string.settings_lock_off), onClick = nav.onLock)
            ValueRow(R.string.settings_backup, stringResource(R.string.settings_backup_none), onClick = nav.onBackup)
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).alpha(0.6f).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RowTitle(R.string.settings_sms, R.string.settings_sms_sub)
                Text(stringResource(R.string.settings_soon), style = TzType.body.copy(fontSize = 14.sp), color = Tz.text2)
            }
        }

        Group(R.string.settings_group_about) {
            ValueRow(R.string.settings_privacy, null, onClick = nav.onLegal)
            ValueRow(R.string.settings_licences, null, onClick = nav.onLicences)
            if (supportEmail.isNotBlank()) {
                ValueRow(R.string.settings_feedback, null) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$supportEmail")).putExtra(Intent.EXTRA_SUBJECT, subject))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RowTitle(R.string.settings_version, null)
                Text(versionName, style = TzType.body.copy(fontSize = 14.sp), color = Tz.text2)
            }
        }
        Text(stringResource(R.string.settings_footer), Modifier.fillMaxWidth(), color = Tz.text2, style = TzType.caption.copy(textAlign = TextAlign.Center))
    }
    SettingsSheets(ui, vm)
}

@Composable
private fun SettingsSheets(ui: SettingsUi, vm: SettingsActions) {
    when (ui.sheet) {
        SettingsSheet.Payday -> TzSheet(vm::closeSheet) {
            var other by remember { mutableStateOf(false) }
            Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ob_payday_pick), style = TzType.cardHeading.copy(color = Tz.text))
                if (other) PaydayDayChips(ui.payday, vm::setPayday) else PaydayChoices(ui.payday, vm::setPayday, onOther = { other = true })
            }
        }
        SettingsSheet.Reminder -> TzSheet(vm::closeSheet) {
            Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ob_reminder_title), style = TzType.cardHeading.copy(color = Tz.text))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The hours onboarding offers; a stored hour outside them shows in the row and no chip is on.
                    listOf<Pair<Int?, Int>>(20 to R.string.ob_reminder_8, 21 to R.string.ob_reminder_9, 22 to R.string.ob_reminder_10, null to R.string.ob_reminder_none).forEach { (h, label) ->
                        TzChip(stringResource(label), ui.reminderHour == h, { vm.setReminderHour(h) }, Modifier.weight(1f), style = ChipStyle.Choice)
                    }
                }
            }
        }
        SettingsSheet.Leftover -> TzSheet(vm::closeSheet) {
            Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_leftover_title), style = TzType.cardHeading.copy(color = Tz.text))
                LeftoverDestination.values().forEach { d ->
                    TzChip(stringResource(leftoverName(d)), ui.leftover == d, { vm.setLeftover(d) }, Modifier.fillMaxWidth(), style = ChipStyle.Choice)
                }
            }
        }
        SettingsSheet.RoundUp -> TzSheet(vm::closeSheet) {
            Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.jar_round_title), style = TzType.cardHeading.copy(color = Tz.text))
                Text(stringResource(R.string.jar_round_line, if (ui.roundUpStep > 0) ui.roundUpStep else 5), style = TzType.caption, color = Tz.text2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TzChip(stringResource(R.string.settings_off), ui.roundUpStep == 0, { vm.setRoundUpStep(0) }, Modifier.weight(1f), style = ChipStyle.Choice)
                    listOf(5, 10, 50).forEach { n ->
                        TzChip(stringResource(R.string.jar_round_to, n), ui.roundUpStep == n, { vm.setRoundUpStep(n) }, Modifier.weight(1f), style = ChipStyle.Choice)
                    }
                }
            }
        }
        SettingsSheet.PlusInfo -> PlusSheet(R.string.jar_lock_round, PlusFeature.ROUND_UPS, vm::closeSheet)
        null -> {}
    }
}

private fun leftoverName(d: LeftoverDestination) = when (d) {
    LeftoverDestination.JAR -> R.string.close_dest_jar
    LeftoverDestination.TOMORROW -> R.string.close_dest_tomorrow
    LeftoverDestination.EMERGENCY -> R.string.close_dest_emergency
}

@Composable
private fun paydayText(p: Payday): String = when (p) {
    is Payday.Day -> stringResource(R.string.settings_payday_day, p.day.toString())
    Payday.LastDay -> stringResource(R.string.ob_payday_last)
    Payday.NotFixed -> stringResource(R.string.ob_payday_notfixed)
}

@Composable
private fun hourText(h: Int?): String =
    if (h == null) stringResource(R.string.ob_reminder_none)
    else stringResource(if (h < 12) R.string.settings_time_am else R.string.settings_time_pm, (if (h % 12 == 0) 12 else h % 12).toString())

@Composable
private fun roundText(step: Int): String =
    if (step > 0) stringResource(R.string.jar_round_to, step.toString()) else stringResource(R.string.settings_off)

@Composable
private fun ProfileRow(ui: SettingsUi, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.surface)
            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Tz.yellow), contentAlignment = Alignment.Center) {
            Text(ui.initial, style = TzType.titleTab.copy(fontSize = 26.sp), color = Tz.night)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ui.name.ifBlank { stringResource(R.string.profile_name_blank) }, style = TzType.cardHeading.copy(fontSize = 22.sp), color = Tz.text)
            Text(stringResource(R.string.settings_profile_sub, ui.level.toString(), ui.levelName), style = TzType.caption, color = Tz.text2)
        }
        TzIcon(TzIcons.chevron, 18.dp, null, tint = Tz.text2)
    }
}

@Composable
internal fun Group(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), Modifier.padding(horizontal = 16.dp), style = TzType.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Tz.text2)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.surface)) { content() }
    }
}

@Composable
internal fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))

@Composable
internal fun RowTitle(title: Int, sub: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = Tz.text)
        if (sub != null) Text(stringResource(sub), style = TzType.caption, color = Tz.text2)
    }
}

/** A row that opens something: the current [value] in yellow on the far side, or a chevron when it has none. */
@Composable
internal fun ValueRow(title: Int, value: String?, last: Boolean = false, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RowTitle(title, null)
        if (value != null) Text(value, style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.yellow)
        else TzIcon(TzIcons.chevron, 18.dp, null, tint = Tz.text2)
    }
    if (!last) Divider()
}

@Composable
internal fun SwitchRow(title: Int, sub: Int?, checked: Boolean, onChange: (Boolean) -> Unit, last: Boolean = false) {
    val label = stringResource(title)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RowTitle(title, sub)
        TzToggle(checked, onChange, label)
    }
    if (!last) Divider()
}
