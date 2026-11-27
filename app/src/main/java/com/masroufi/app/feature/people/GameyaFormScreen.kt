package com.masroufi.app.feature.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.feature.plan.AmountKeypad
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.plan.FormBlock
import com.masroufi.app.feature.plan.NameField
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.isLargeFont
import com.masroufi.app.feature.plan.SplitRow
import androidx.compose.ui.text.style.TextAlign
import com.masroufi.app.ui.components.PlusSheet
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.format.TextStyle

/**
 * جمعية جديدة / عدّل الجمعية: a pushed screen, one question per block, yellow save button. Free with one gameya already
 * gets the Plus card instead. Editing changes the name and the monthly amount only.
 */
@Composable
fun GameyaFormScreen(viewModel: GameyaFormViewModel, onBack: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    GameyaFormContent(f, viewModel, onBack)
}

/** The form with its state and actions passed in, so a test can render it (no behaviour of its own). */
@Composable
fun GameyaFormContent(f: GameyaFormState, viewModel: GameyaFormActions, onBack: () -> Unit) {
    val haptics = LocalHaptics.current
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(f.saved) { if (f.saved) onBack() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(if (viewModel.isEdit) R.string.gameya_form_edit else R.string.gameya_form_new), onBack)
        if (!f.blocked) {
            FormBlock(R.string.gameya_name_q) { NameField(f.name, viewModel::setName, R.string.gameya_name_hint) }
            FormBlock(R.string.gameya_monthly_q) { AmountKeypad(f.amount, viewModel::digit, viewModel::point, viewModel::backspace) }
            if (!f.editing) {
                FormBlock(R.string.gameya_members_q) {
                    SplitRow(
                        Modifier.fillMaxWidth(),
                        info = { m -> Box(m) { NameField(f.newMember, viewModel::setNewMember, R.string.gameya_member_hint) } },
                    ) { PrimaryButton(stringResource(R.string.gameya_member_add), { haptics.perform(HapticLevel.Tick); viewModel.addMember() }, enabled = f.newMember.isNotBlank(), height = 52.dp) }
                    (1..f.participants).forEach { turn ->
                        val mine = turn == f.myTurn
                        val name = if (mine) stringResource(R.string.gameya_me) else f.members[if (turn < f.myTurn) turn - 1 else turn - 2]
                        SeatRow(turn, name, mine, f.participants, viewModel)
                    }
                }
                FormBlock(R.string.gameya_start_q) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(TzIcons.minus, stringResource(R.string.gameya_start_less)) { haptics.perform(HapticLevel.Tick); viewModel.shiftStart(-1) }
                        Text(
                            "${f.start.month.getDisplayName(TextStyle.FULL, locale)} ${f.start.year}", style = TzType.cardHeading.copy(fontSize = 24.sp, textAlign = TextAlign.Center), color = Tz.text,
                            modifier = if (isLargeFont()) Modifier.weight(1f) else Modifier,
                        )
                        CircleButton(TzIcons.add, stringResource(R.string.gameya_start_more)) { haptics.perform(HapticLevel.Tick); viewModel.shiftStart(1) }
                    }
                }
                FormBlock(R.string.gameya_pocket_q) {
                    f.pockets.chunked(2).forEach { row ->
                        ChipRow { row.forEach { p -> TzChip(p.name, f.pocketId == p.id, { viewModel.setPocket(p.id) }, style = ChipStyle.Choice) } }
                    }
                }
            }
            if (f.failed) Text(stringResource(R.string.gameya_failed), style = TzType.label, color = Tz.carnelianText)
            PrimaryButton(
                stringResource(R.string.gameya_save), { haptics.perform(HapticLevel.Confirm); viewModel.save() },
                Modifier.fillMaxWidth(), enabled = f.canSave,
            )
            if (viewModel.isEdit) {
                TextLink(stringResource(R.string.gameya_archive), { haptics.perform(HapticLevel.Heavy); viewModel.askArchive() }, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
    if (f.blocked) PlusSheet(R.string.gameya_plus_line, PlusFeature.GAMEYA, onBack)
    if (f.askArchive) {
        TzSheet(viewModel::dismissArchive) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.gameya_archive_title, f.name), style = TzType.cardHeading, color = Tz.text)
                Text(stringResource(R.string.gameya_archive_body), style = TzType.body, color = Tz.text2)
                PrimaryButton(stringResource(R.string.gameya_archive_confirm), { haptics.perform(HapticLevel.Heavy); viewModel.archive() }, Modifier.fillMaxWidth(), height = 52.dp)
                SecondaryButton(stringResource(R.string.gameya_archive_cancel), viewModel::dismissArchive, Modifier.fillMaxWidth(), height = 52.dp)
            }
        }
    }
}

/** One seat of the turn order: its number, the name, and earlier / later (and remove, for the others). */
@Composable
private fun SeatRow(turn: Int, name: String, mine: Boolean, seats: Int, vm: GameyaFormActions) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (mine) Tz.raised else Tz.night).border(1.dp, if (mine) Tz.yellow else Tz.line, shape).padding(start = 14.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.gameya_member_row, turn.toString(), name), style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = if (mine) Tz.yellow else Tz.text, modifier = Modifier.weight(1f),
        )
        if (turn > 1) CircleButton(TzIcons.arrowUp, stringResource(R.string.gameya_member_up_cd, name)) { haptics.perform(HapticLevel.Tick); vm.moveSeat(turn, -1) }
        if (turn < seats) CircleButton(TzIcons.chevronDown, stringResource(R.string.gameya_member_down_cd, name)) { haptics.perform(HapticLevel.Tick); vm.moveSeat(turn, 1) }
        if (!mine) CircleButton(TzIcons.close, stringResource(R.string.gameya_member_remove_cd, name)) { haptics.perform(HapticLevel.Tick); vm.removeSeat(turn) }
    }
}
