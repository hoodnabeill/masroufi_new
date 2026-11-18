package com.masroufi.app.feature.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.plan.AmountKeypad
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.plan.NameField
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.Segmented
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

private val SheetPadding = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)

/** The tab's sheets: the new record, the quick split, and the tab or loans to settle. */
@Composable
internal fun PeopleSheets(ui: PeopleUi, vm: PeopleViewModel) {
    val haptics = LocalHaptics.current
    when (val s = ui.sheet) {
        null -> Unit
        PeopleSheet.Add -> AddRecordSheet(ui, vm)
        PeopleSheet.Split -> SplitSheet(ui, vm)
        is PeopleSheet.SettleTab -> PocketPaySheet(
            title = stringResource(R.string.people_settle_title, s.name), amount = s.amount, editable = false,
            pockets = ui.pockets, pocketQuestion = R.string.people_settle_pocket, confirm = R.string.people_settle_confirm, allowNone = false,
            onDismiss = vm::dismissSheet,
        ) { _, pocket ->
            haptics.perform(HapticLevel.Heavy)
            vm.settleTab(s.personId, s.name, checkNotNull(pocket))
        }
        is PeopleSheet.Repay -> PocketPaySheet(
            title = stringResource(R.string.people_repay_title, s.name), amount = s.amount, editable = false,
            pockets = ui.pockets, pocketQuestion = R.string.people_settle_pocket, confirm = R.string.people_settle_confirm, allowNone = true,
            onDismiss = vm::dismissSheet,
        ) { _, pocket ->
            haptics.perform(HapticLevel.Heavy)
            vm.repayAll(s.personId, pocket)
        }
    }
}

/**
 * Pick the pocket that pays (or gets paid) and confirm. [editable] shows a keypad to type part of [amount]; otherwise the
 * whole of it is shown and confirmed. [onConfirm] gets piastres and the pocket (null = record only, when [allowNone]).
 */
@Composable
internal fun PocketPaySheet(
    title: String,
    amount: Long,
    editable: Boolean,
    pockets: List<PocketEntity>,
    pocketQuestion: Int,
    confirm: Int,
    allowNone: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, pocketId: Long?) -> Unit,
) {
    val haptics = LocalHaptics.current
    var input by remember { mutableStateOf(AmountInput.of(amount)) }
    var pocket by remember { mutableStateOf(if (allowNone && pockets.isEmpty()) null else pockets.firstOrNull()?.id) }
    val typed = if (editable) input.toPiastres() else amount
    TzSheet(onDismiss) {
        Column(SheetPadding.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = TzType.cardHeading, color = Tz.text)
            if (editable) {
                AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            } else {
                Text(poundsText(amount) + " " + stringResource(R.string.paper_currency), style = TzType.hero, color = Tz.text)
            }
            Text(stringResource(pocketQuestion), style = TzType.caption, color = Tz.text2)
            PocketPicker(pockets, pocket, allowNone) { pocket = it }
            PrimaryButton(
                stringResource(confirm), { onConfirm(typed, pocket) }, Modifier.fillMaxWidth(),
                enabled = typed in 1..amount && (allowNone || pocket != null), height = 52.dp,
            )
        }
    }
}

private val DueChoices = listOf(null to R.string.people_due_none, 7 to R.string.people_due_week, 14 to R.string.people_due_2weeks, 30 to R.string.people_due_month)

/** "+" : they owe me / I owe / a tab item, for a person picked or new. */
@Composable
private fun AddRecordSheet(ui: PeopleUi, vm: PeopleViewModel) {
    val haptics = LocalHaptics.current
    var mode by remember { mutableStateOf(AddMode.THEY_OWE) }
    var personId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(ui.persons.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    var input by remember { mutableStateOf(AmountInput()) }
    var text by remember { mutableStateOf("") }
    var pocket by remember { mutableStateOf(ui.pockets.firstOrNull()?.id) }
    var dueDays by remember { mutableStateOf<Int?>(null) }
    val tab = mode == AddMode.TAB
    val candidates = ui.persons.filter { (it.kind == PersonKind.SHOP) == tab }
    val amount = input.toPiastres()
    val named = if (creating) newName.isNotBlank() else candidates.any { it.id == personId }
    val canSave = amount > 0 && named && (!tab || text.isNotBlank())
    TzSheet(vm::dismissSheet) {
        Column(SheetPadding.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.people_add_title), style = TzType.cardHeading, color = Tz.text)
            Segmented(
                listOf(stringResource(R.string.people_mode_they_owe), stringResource(R.string.people_mode_i_owe), stringResource(R.string.people_mode_tab)),
                mode.ordinal, { mode = AddMode.values()[it]; personId = null; creating = false }, compact = true,
            )
            Text(stringResource(R.string.people_add_who), style = TzType.caption, color = Tz.text2)
            ChipRow {
                candidates.forEach { p -> TzChip(p.name, !creating && personId == p.id, { personId = p.id; creating = false }, style = ChipStyle.Filter) }
                TzChip(
                    stringResource(if (tab) R.string.people_add_new_shop else R.string.people_add_new_person), creating,
                    { creating = true; personId = null }, style = ChipStyle.Filter,
                )
            }
            if (creating) NameField(newName, { newName = it.take(NAME_MAX) }, R.string.people_add_name_hint)
            NameField(text, { text = it.take(NOTE_MAX) }, if (tab) R.string.people_add_item_hint else R.string.people_add_reason_hint)
            AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            if (!tab) {
                Text(stringResource(R.string.people_add_pocket), style = TzType.caption, color = Tz.text2)
                PocketPicker(ui.pockets, pocket, true) { pocket = it }
                Text(stringResource(R.string.people_add_due), style = TzType.caption, color = Tz.text2)
                ChipRow { DueChoices.forEach { (days, label) -> TzChip(stringResource(label), dueDays == days, { dueDays = days }, style = ChipStyle.Filter) } }
            }
            PrimaryButton(
                stringResource(R.string.people_add_save),
                {
                    haptics.perform(HapticLevel.Confirm)
                    vm.add(
                        mode, if (creating) null else personId, newName.takeIf { creating }, amount, text, if (tab) null else pocket,
                        dueDays,
                    )
                },
                Modifier.fillMaxWidth(), enabled = canSave, height = 52.dp,
            )
        }
    }
}

private const val NAME_MAX = 40
private const val NOTE_MAX = 60

/** Quick split: the amount, the pocket, who is in (the user too) and each share, equal until typed over. */
@Composable
private fun SplitSheet(ui: PeopleUi, vm: PeopleViewModel) {
    val haptics = LocalHaptics.current
    val d = ui.split
    val shares = d.shares
    var newName by remember { mutableStateOf("") }
    TzSheet(vm::dismissSheet) {
        Column(SheetPadding.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.people_split_title), style = TzType.cardHeading, color = Tz.text)
            AmountKeypad(d.amount, vm::splitDigit, vm::splitPoint, vm::splitBackspace)
            Text(stringResource(R.string.people_split_pocket), style = TzType.caption, color = Tz.text2)
            ChipRow { ui.pockets.forEach { p -> TzChip(p.name, d.pocketId == p.id, { vm.splitPocket(p.id) }, style = ChipStyle.Filter) } }
            Text(stringResource(R.string.people_split_who), style = TzType.caption, color = Tz.text2)
            ParticipantRow(ME, stringResource(R.string.people_split_me), d, shares, vm)
            ui.persons.filter { it.kind == PersonKind.PERSON }.forEach { p -> ParticipantRow(p.id, p.name, d, shares, vm) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { NameField(newName, { newName = it.take(NAME_MAX) }, R.string.people_split_new_hint) }
                SmallPill(stringResource(R.string.people_split_add), true, { vm.addSplitPerson(newName); newName = "" })
            }
            if (shares == null && d.total > 0) Text(stringResource(R.string.people_split_mismatch), style = TzType.caption, color = Tz.carnelianText)
            PrimaryButton(
                stringResource(R.string.people_split_save), { haptics.perform(HapticLevel.Confirm); vm.confirmSplit() },
                Modifier.fillMaxWidth(), enabled = d.canSave, height = 52.dp,
            )
        }
    }
}

/** One person of the split: a tick disc, the name and, when in, the share as a field to type over. */
@Composable
private fun ParticipantRow(id: Long, name: String, d: SplitDraft, shares: Map<Long, Long>?, vm: PeopleViewModel) {
    val haptics = LocalHaptics.current
    val on = id in d.participants
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (on) Tz.raised else Tz.night).border(1.dp, if (on) Tz.yellow else Tz.line, shape)
            .clickable(role = Role.Checkbox) { haptics.perform(HapticLevel.Tick); vm.toggleParticipant(id) }.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) Tz.yellow else Tz.line), contentAlignment = Alignment.Center) {
            if (on) TzIcon(TzIcons.check, 14.dp, null, tint = Tz.night)
        }
        Text(name, style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text, modifier = Modifier.weight(1f), maxLines = 1)
        if (on) {
            val shown = d.edited[id] ?: shares?.get(id)?.let { AmountInput.of(it).text.ifEmpty { "0" } } ?: ""
            val label = stringResource(R.string.people_split_share_cd, name)
            BasicTextField(
                value = shown,
                onValueChange = { vm.editShare(id, it) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TzType.rowNumber.copy(fontSize = 18.sp, color = Tz.text, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Tz.yellow),
                modifier = Modifier.width(88.dp).clip(RoundedCornerShape(12.dp)).background(Tz.night).padding(horizontal = 8.dp, vertical = 8.dp)
                    .semantics { contentDescription = label },
            )
        }
    }
}
