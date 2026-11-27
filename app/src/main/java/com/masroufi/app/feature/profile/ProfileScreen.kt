@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.masroufi.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.domain.MainGoal
import com.masroufi.app.domain.WorkKind
import com.masroufi.app.feature.people.rememberShareText
import com.masroufi.app.feature.plan.AmountKeypad
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.minimumInteractiveComponentSize
import com.masroufi.app.feature.plan.SplitRow
import com.masroufi.app.feature.plan.TwoUp
import com.masroufi.app.feature.pockets.LoadingRows
import com.masroufi.app.ui.components.isLargeFont
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressClickable
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle

/** ملفك (Profile.dc.html), a pushed screen; every field is optional and saved as it changes. */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onBack: () -> Unit, onOpenFreelance: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val share = rememberShareText()
    // The data is gone: a clean relaunch, not just a trip to onboarding.
    LaunchedEffect(ui.wiped) { if (ui.wiped) restartApp(context) }
    ProfileContent(ui, viewModel, { share(profileShareText(context, ui), it) }, onBack, onOpenFreelance, onOpenSettings)
}

/** The screen with its state and actions passed in, so a test can render it (no behaviour of its own). [share] gets the chooser's title. */
@Composable
fun ProfileContent(ui: ProfileUi, viewModel: ProfileActions, share: (title: String) -> Unit, onBack: () -> Unit, onOpenFreelance: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.profile_title), onBack) {
            CircleButton(TzIcons.settings, stringResource(R.string.album_settings_cd), onOpenSettings)
        }
        if (ui.loading) {
            LoadingRows()
        } else {
            val shareTitle = stringResource(R.string.profile_share)
            Identity(ui) { share(shareTitle) }
            Fields(ui, viewModel, onOpenFreelance)
            ui.gift?.let { Gift(it) { viewModel.open(ProfileSheet.Target) } }
            Household(ui, viewModel)
            MainGoalCard(ui, viewModel)
            Privacy { viewModel.open(ProfileSheet.Wipe) }
        }
    }
    when (ui.sheet) {
        ProfileSheet.Birthday -> BirthdayPicker(ui.birthday, viewModel::setBirthday, viewModel::closeSheet)
        ProfileSheet.Target -> TargetSheet(ui, viewModel)
        ProfileSheet.Wipe -> WipeSheet(viewModel::closeSheet, viewModel::wipe)
        null -> {}
    }
}

@Composable
private fun Identity(ui: ProfileUi, onShare: () -> Unit) {
    val shape = RoundedCornerShape(TzRadius.hero)
    val ring = Tz.yellowRule2
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.yellow)
            .drawBehind {
                // the mock's concentric rings, bottom-left
                val c = Offset(40.dp.toPx(), size.height - 20.dp.toPx())
                listOf(40, 56, 72, 88, 104).forEach { r -> drawCircle(ring, r.dp.toPx(), c, style = Stroke(1.2.dp.toPx())) }
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Tz.night), contentAlignment = Alignment.Center) {
                Text(ui.initial, style = TzType.titleTab.copy(fontSize = 38.sp), color = Tz.yellow)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    ui.name.ifBlank { stringResource(R.string.profile_name_blank) },
                    style = TzType.titleTab.copy(lineHeight = 33.sp), color = Tz.night,
                )
                Text(
                    stringResource(R.string.profile_level_line, ui.level.toString(), ui.levelName, ui.ticketsClosed.toString()),
                    style = TzType.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Tz.yellowInk,
                )
            }
        }
        if (isLargeFont()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(ui.streak.toString(), R.string.profile_stat_streak, Modifier.fillMaxWidth())
                Stat(ui.proverbs.toString(), R.string.profile_stat_proverbs, Modifier.fillMaxWidth())
                Stat("${ui.wonOf} / ${ui.ofLast}", R.string.profile_stat_matches, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(ui.streak.toString(), R.string.profile_stat_streak, Modifier.weight(1f))
                Stat(ui.proverbs.toString(), R.string.profile_stat_proverbs, Modifier.weight(1f))
                Stat("${ui.wonOf} / ${ui.ofLast}", R.string.profile_stat_matches, Modifier.weight(1f))
            }
        }
        val pill = RoundedCornerShape(percent = 50)
        val haptics = LocalHaptics.current
        Box(
            Modifier.pressClickable { haptics.perform(HapticLevel.Tap); onShare() }.fillMaxWidth().heightIn(min = 48.dp).clip(pill).border(2.dp, Tz.night, pill).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.profile_share), style = TzType.button.copy(fontSize = 16.sp), color = Tz.night) }
    }
}

@Composable
private fun Stat(value: String, label: Int, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Tz.night).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = TzType.rowNumber.copy(fontSize = 22.sp), color = Tz.yellow)
        Text(stringResource(label), style = TzType.caption.copy(fontSize = 10.sp), color = Tz.qershOnYellow)
    }
}

@Composable
private fun Fields(ui: ProfileUi, vm: ProfileActions, onOpenFreelance: () -> Unit) {
    val shape = RoundedCornerShape(TzRadius.card)
    val locale = LocalConfiguration.current.locales[0]
    val nameLabel = stringResource(R.string.profile_name_label)
    // The field owns its text (the saved value arrives a frame late and would eat fast typing); Fields composes after loading.
    var name by rememberSaveable { mutableStateOf(ui.name) }
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(nameLabel, style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(NAME_MAX); vm.setName(it) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = nameLabel },
                textStyle = TzType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tz.text),
                singleLine = true,
                cursorBrush = SolidColor(Tz.yellow),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                decorationBox = { inner -> Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { inner() } },
            )
        }
        Divider()
        FlowRow(
            Modifier.pressClickable { vm.open(ProfileSheet.Birthday) }.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.profile_birthday_label), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
                Text(
                    ui.birthday?.let { fullDate(it, locale) } ?: stringResource(R.string.profile_birthday_unset),
                    style = TzType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = if (ui.birthday == null) Tz.text3 else Tz.text,
                )
            }
            ui.gift?.let {
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Tz.turquoiseDim).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.profile_birthday_in, it.days.toString()),
                        style = TzType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.turquoise,
                    )
                }
            }
        }
        Divider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_work_label), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkKind.entries.forEach { k ->
                    TzChip(stringResource(workLabel(k)), ui.workKind == k, { vm.setWorkKind(k) })
                }
            }
            // The freelancer's vault and self-salary live on their own screen.
            if (ui.workKind == WorkKind.FREELANCE) {
                val haptics = LocalHaptics.current
                Row(
                    Modifier.pressClickable { haptics.perform(HapticLevel.Tap); onOpenFreelance() }.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.profile_free_link), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.yellow, modifier = Modifier.weight(1f))
                    TzIcon(TzIcons.chevron, 18.dp, null)
                }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))

private fun workLabel(k: WorkKind) = when (k) {
    WorkKind.SALARIED -> R.string.profile_work_salaried
    WorkKind.FREELANCE -> R.string.profile_work_freelance
    WorkKind.STUDENT -> R.string.profile_work_student
    WorkKind.RUNS_HOUSE -> R.string.profile_work_house
}

private fun fullDate(d: LocalDate, locale: java.util.Locale): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", locale).withDecimalStyle(DecimalStyle.STANDARD).format(d)

/** "عيدية منك ليك": whole pounds a day, rounded up; tap to change the target. */
@Composable
private fun Gift(g: GiftUi, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(TzRadius.card)
    Row(
        Modifier.pressClickable(onClick = onEdit).fillMaxWidth().clip(shape).background(Tz.turquoiseDim).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Qersh(QershMood.Happy, 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.profile_gift_title), style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.text)
            Text(
                stringResource(R.string.profile_gift_line, g.perDayPounds.toString(), poundsText(g.target)),
                style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = Tz.turquoiseDimText,
            )
        }
    }
}

@Composable
private fun Household(ui: ProfileUi, vm: ProfileActions) {
    val shape = RoundedCornerShape(TzRadius.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)) {
        Text(
            stringResource(R.string.profile_house_title), style = TzType.cardHeading, color = Tz.text,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        )
        Stepper(
            R.string.profile_house_size, stringResource(R.string.profile_house_size_sub), ui.householdSize,
            onMinus = { vm.stepSize(-1) }, onPlus = { vm.stepSize(1) },
        )
        Divider()
        Stepper(
            R.string.profile_house_school,
            if (ui.inSchool > 0) stringResource(R.string.profile_house_school_sub, ui.inSchool.toString()) else stringResource(R.string.profile_house_school_none),
            ui.inSchool, onMinus = { vm.stepSchool(-1) }, onPlus = { vm.stepSchool(1) },
        )
    }
}

@Composable
private fun Stepper(title: Int, sub: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    SplitRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        info = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
                Text(sub, style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton(TzIcons.minus, R.string.profile_less, onMinus)
            Text(value.toString(), Modifier.widthIn(min = 28.dp), style = TzType.titleTab.copy(fontSize = 24.sp), color = Tz.text)
            StepButton(TzIcons.add, R.string.profile_more, onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: TzIconDef, description: Int, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    // drawn 44 dp, a 48 dp touch target
    Box(Modifier.minimumInteractiveComponentSize().pressClickable(PressScale.Chip) { haptics.perform(HapticLevel.Tick); onClick() }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Tz.raised).border(1.dp, Tz.line, CircleShape), contentAlignment = Alignment.Center) {
            TzIcon(icon, 16.dp, stringResource(description), tint = Tz.text)
        }
    }
}

private fun goalLabel(g: MainGoal) = when (g) {
    MainGoal.LAST_THE_MONTH -> R.string.profile_goal_month
    MainGoal.SAVE -> R.string.profile_goal_save
    MainGoal.PAY_OFF -> R.string.profile_goal_debts
    MainGoal.OCCASION -> R.string.profile_goal_occasion
}

@Composable
private fun MainGoalCard(ui: ProfileUi, vm: ProfileActions) {
    val shape = RoundedCornerShape(TzRadius.card)
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.profile_goal_title), style = TzType.cardHeading, color = Tz.text)
        MainGoal.entries.chunked(2).forEach { pair ->
            TwoUp(
                { m -> GoalBox(pair[0], ui.mainGoal == pair[0], m) { if (ui.mainGoal != pair[0]) haptics.perform(HapticLevel.Tick); vm.setMainGoal(pair[0]) } },
                { m -> if (pair.size > 1) GoalBox(pair[1], ui.mainGoal == pair[1], m) { if (ui.mainGoal != pair[1]) haptics.perform(HapticLevel.Tick); vm.setMainGoal(pair[1]) } },
            )
        }
        Text(stringResource(R.string.profile_goal_note), style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = Tz.text2)
    }
}

@Composable
private fun GoalBox(g: MainGoal, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val box = RoundedCornerShape(18.dp)
    Box(
        modifier.pressClickable(role = Role.RadioButton, onClick = onClick).heightIn(min = 56.dp).clip(box)
            .background(if (on) Tz.yellowDim else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (on) 2.dp else 1.dp, if (on) Tz.yellow else Tz.line, box)
            .semantics { selected = on }.padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(goalLabel(g)),
            style = TzType.caption.copy(fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
            color = if (on) Tz.yellow else Tz.text,
        )
    }
}

@Composable
private fun Privacy(onWipe: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).border(1.5.dp, Tz.dashed, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TzIcon(TzIcons.lock, 16.dp, null, tint = Tz.text)
            Text(stringResource(R.string.profile_privacy_title), style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
        }
        Text(stringResource(R.string.profile_privacy_body), style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = Tz.text2)
        Box(Modifier.pressClickable(onClick = onWipe).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.profile_wipe), style = TzType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Tz.carnelianText)
        }
    }
}

/** Material's picker in the app's colours; "بدون" clears the date. */
@Composable
private fun BirthdayPicker(current: LocalDate?, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val initial = (current ?: LocalDate.of(2000, 1, 1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial, yearRange = 1920..LocalDate.now().year)
    val colors = DatePickerDefaults.colors(
        containerColor = Tz.surface, titleContentColor = Tz.text2, headlineContentColor = Tz.text, weekdayContentColor = Tz.text2,
        dayContentColor = Tz.text, selectedDayContainerColor = Tz.yellow, selectedDayContentColor = Tz.night,
        todayContentColor = Tz.yellow, todayDateBorderColor = Tz.yellow, yearContentColor = Tz.text,
        selectedYearContainerColor = Tz.yellow, selectedYearContentColor = Tz.night,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ onPick(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }) }) {
                Text(stringResource(R.string.profile_birthday_ok), color = Tz.yellow)
            }
        },
        dismissButton = { TextButton({ onPick(null) }) { Text(stringResource(R.string.profile_birthday_clear), color = Tz.text2) } },
        colors = colors,
    ) { DatePicker(state, colors = colors) }
}

@Composable
internal fun TargetSheet(ui: ProfileUi, vm: ProfileActions) {
    val haptics = LocalHaptics.current
    TzSheet(vm::closeSheet) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.profile_target_title), style = TzType.cardHeading, color = Tz.text)
            AmountKeypad(ui.target, vm::digit, vm::point, vm::backspace)
            PrimaryButton(stringResource(R.string.profile_target_save), { haptics.perform(HapticLevel.Confirm); vm.saveTarget() }, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}

/** The one confirmation (decision 6): what goes, that there is no way back; no backup offer until Phase 11. */
@Composable
internal fun WipeSheet(onCancel: () -> Unit, onWipe: () -> Unit) {
    val haptics = LocalHaptics.current
    TzSheet(onCancel) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Qersh(QershMood.Sleepy, 96.dp)
            Text(stringResource(R.string.profile_wipe_title), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.profile_wipe_body), style = TzType.body.copy(fontSize = 14.sp, lineHeight = 24.sp), color = Tz.text2)
            val pill = RoundedCornerShape(percent = 50)
            Box(
                Modifier.pressClickable { haptics.perform(HapticLevel.Heavy); onWipe() }.fillMaxWidth().heightIn(min = 52.dp).clip(pill).background(Tz.carnelian).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.profile_wipe_go), style = TzType.button, color = Tz.carnelianInk) }
            TextLink(stringResource(R.string.profile_wipe_cancel), onCancel)
        }
    }
}
