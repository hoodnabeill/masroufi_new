package com.masroufi.app.feature.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.repo.GiftRow
import com.masroufi.app.data.repo.OccasionView
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.plan.AmountKeypad
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.plan.NameField
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.Segmented
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.BlankSheet
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

private val CardShape = RoundedCornerShape(24.dp)
private val SheetPad = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)

/** دفتر الواجب (pushed from the lilac card): the next occasion, then my occasions and other people's with their gifts. Plus-locked. */
@Composable
fun DutyLedgerScreen(viewModel: DutyLedgerViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(
            stringResource(R.string.people_duty_title), onBack,
            trailing = {
                if (ui.plus) CircleButton(TzIcons.add, stringResource(R.string.duty_add_cd)) { haptics.perform(HapticLevel.Tap); viewModel.openOccasion() }
                else Spacer(Modifier.size(44.dp))
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val content: @Composable () -> Unit = { DutyContent(ui, viewModel) }
            if (ui.plus) content() else PlusLocked(R.string.people_duty_plus, PlusFeature.DUTY, content)
            Spacer(Modifier.size(28.dp))
        }
    }
    if (ui.plus) DutySheets(ui, viewModel)
}

@Composable
private fun DutyContent(ui: DutyUi, vm: DutyLedgerViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            ui.loading -> repeat(3) { RowSkeleton() }
            ui.empty -> EmptyState({ BlankSheet() }, R.string.duty_empty_title, R.string.duty_empty_line, R.string.duty_empty_action, { vm.openOccasion() }, primaryAction = true)
            else -> {
                ui.upcoming?.let { Column(Modifier.fillMaxWidth().clip(CardShape).background(Tz.lilac).padding(12.dp)) { DutyUpcoming(it) } }
                Group(R.string.duty_mine, ui.mine, vm)
                Group(R.string.duty_theirs, ui.theirs, vm)
            }
        }
    }
}

@Composable
private fun Group(title: Int, list: List<OccasionView>, vm: DutyLedgerViewModel) {
    if (list.isEmpty()) return
    Text(stringResource(title), style = TzType.cardHeading, color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp))
    list.forEach { OccasionCard(it, vm) }
}

@Composable
private fun OccasionCard(v: OccasionView, vm: DutyLedgerViewModel) {
    val haptics = LocalHaptics.current
    val o = v.occasion
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Tz.surface).border(1.dp, Tz.line, CardShape).padding(vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); vm.openOccasion(v) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(o.name, style = TzType.cardHeading, color = Tz.text)
            Text(listOfNotNull(formatShortDate(o.date), v.person?.name).joinToString(" · "), style = TzType.caption, color = Tz.text2)
            if (o.whose == OccasionOwner.MINE && v.givers > 0) {
                Text(
                    stringResource(R.string.people_duty_own, formatPounds(v.receivedTotal), v.givers.toString()),
                    style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.turquoise,
                )
            }
        }
        v.gifts.forEach { g -> GiftLine(g) { haptics.perform(HapticLevel.Tap); vm.openGift(o.id, g) } }
        TextLink(stringResource(R.string.duty_add_gift), { vm.openGift(o.id) }, Modifier.padding(horizontal = 12.dp))
    }
}

@Composable
private fun GiftLine(g: GiftRow, onClick: () -> Unit) {
    val received = g.gift.direction == GiftDirection.RECEIVED
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(g.person?.name.orEmpty(), style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text, maxLines = 1)
            Text(stringResource(if (received) R.string.duty_received else R.string.duty_given), style = TzType.caption, color = Tz.text2)
        }
        Text(formatPounds(g.gift.amount), style = TzType.rowNumber, color = if (received) Tz.turquoise else Tz.text)
    }
}

@Composable
private fun DutySheets(ui: DutyUi, vm: DutyLedgerViewModel) {
    when (val s = ui.sheet) {
        null -> Unit
        is DutySheet.Occasion -> OccasionSheet(ui, s, vm)
        is DutySheet.Gift -> GiftSheet(ui, s, vm)
        is DutySheet.ConfirmDelete -> TzSheet(vm::dismissSheet) {
            val haptics = LocalHaptics.current
            Column(SheetPad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.duty_delete_title, s.name), style = TzType.cardHeading, color = Tz.text)
                Text(stringResource(R.string.duty_delete_body, s.gifts.toString()), style = TzType.body, color = Tz.text2)
                PrimaryButton(stringResource(R.string.duty_delete_confirm), { haptics.perform(HapticLevel.Heavy); vm.confirmDeleteOccasion(s.occasionId) }, Modifier.fillMaxWidth(), height = 52.dp)
                SecondaryButton(stringResource(R.string.duty_delete_cancel), vm::dismissSheet, Modifier.fillMaxWidth(), height = 52.dp)
            }
        }
    }
}

private const val NAME_MAX = 40

/** Name, whose, the person it is about (optional) and the date as month chips with day and year steppers. */
@Composable
private fun OccasionSheet(ui: DutyUi, s: DutySheet.Occasion, vm: DutyLedgerViewModel) {
    val haptics = LocalHaptics.current
    val locale = LocalConfiguration.current.locales[0]
    val e = s.editing
    val start = e?.occasion?.date ?: ui.today
    var name by remember { mutableStateOf(e?.occasion?.name.orEmpty()) }
    var whose by remember { mutableStateOf(e?.occasion?.whose ?: OccasionOwner.THEIRS) }
    var personId by remember { mutableStateOf(e?.occasion?.personId) }
    var year by remember { mutableStateOf(start.year) }
    var month by remember { mutableStateOf(start.monthValue) }
    var day by remember { mutableStateOf(start.dayOfMonth) }
    val people = ui.persons.filter { it.kind == PersonKind.PERSON }
    TzSheet(vm::dismissSheet) {
        Column(SheetPad.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (e == null) R.string.duty_occ_new else R.string.duty_occ_edit), style = TzType.cardHeading, color = Tz.text)
            NameField(name, { name = it.take(NAME_MAX) }, R.string.duty_occ_name_hint)
            Segmented(
                listOf(stringResource(R.string.duty_whose_mine), stringResource(R.string.duty_whose_theirs)),
                whose.ordinal, { whose = OccasionOwner.values()[it] }, compact = true,
            )
            Text(stringResource(R.string.duty_occ_who), style = TzType.caption, color = Tz.text2)
            ChipRow {
                TzChip(stringResource(R.string.duty_none), personId == null, { personId = null }, style = ChipStyle.Filter)
                people.forEach { p -> TzChip(p.name, personId == p.id, { personId = p.id }, style = ChipStyle.Filter) }
            }
            ChipRow {
                (1..12).forEach { m -> TzChip(Month.of(m).getDisplayName(TextStyle.FULL, locale), month == m, { month = m }, style = ChipStyle.Choice) }
            }
            Stepper(day.toString(), stringResource(R.string.season_day_less), stringResource(R.string.season_day_more), { day = (day - 1).coerceAtLeast(1) }, { day = (day + 1).coerceAtMost(31) })
            Stepper(year.toString(), stringResource(R.string.duty_year_less), stringResource(R.string.duty_year_more), { year-- }, { year++ })
            PrimaryButton(
                stringResource(R.string.duty_save),
                {
                    haptics.perform(HapticLevel.Confirm)
                    val ym = YearMonth.of(year, month)
                    vm.saveOccasion(e?.occasion, name, ym.atDay(minOf(day, ym.lengthOfMonth())), whose, personId)
                },
                Modifier.fillMaxWidth(), enabled = name.isNotBlank(), height = 52.dp,
            )
            if (e != null) SecondaryButton(stringResource(R.string.duty_delete), { haptics.perform(HapticLevel.Heavy); vm.requestDeleteOccasion(e) }, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}

@Composable
private fun Stepper(value: String, lessCd: String, moreCd: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(TzIcons.minus, lessCd, onLess)
        Text(value, style = TzType.hero.copy(fontSize = 32.sp), color = Tz.text, modifier = Modifier.defaultMinSize(minWidth = 64.dp))
        CircleButton(TzIcons.add, moreCd, onMore)
    }
}

/** Who (picked or new), how much, and which way it went. */
@Composable
private fun GiftSheet(ui: DutyUi, s: DutySheet.Gift, vm: DutyLedgerViewModel) {
    val haptics = LocalHaptics.current
    val e = s.editing
    val people = ui.persons.filter { it.kind == PersonKind.PERSON }
    var personId by remember { mutableStateOf(e?.gift?.personId) }
    var creating by remember { mutableStateOf(e == null && people.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    var input by remember { mutableStateOf(e?.let { AmountInput.of(it.gift.amount) } ?: AmountInput()) }
    var direction by remember { mutableStateOf(e?.gift?.direction ?: GiftDirection.RECEIVED) }
    val amount = input.toPiastres()
    val named = if (creating) newName.isNotBlank() else personId != null
    TzSheet(vm::dismissSheet) {
        Column(SheetPad.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (e == null) R.string.duty_gift_new else R.string.duty_gift_edit), style = TzType.cardHeading, color = Tz.text)
            Segmented(
                listOf(stringResource(R.string.duty_received), stringResource(R.string.duty_given)),
                direction.ordinal, { direction = GiftDirection.values()[it] }, compact = true,
            )
            Text(stringResource(R.string.duty_gift_who), style = TzType.caption, color = Tz.text2)
            ChipRow {
                people.forEach { p -> TzChip(p.name, !creating && personId == p.id, { personId = p.id; creating = false }, style = ChipStyle.Filter) }
                TzChip(stringResource(R.string.duty_gift_new_person), creating, { creating = true; personId = null }, style = ChipStyle.Filter)
            }
            if (creating) NameField(newName, { newName = it.take(NAME_MAX) }, R.string.people_add_name_hint)
            AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            PrimaryButton(
                stringResource(R.string.duty_save),
                { haptics.perform(HapticLevel.Confirm); vm.saveGift(s.occasionId, e?.gift, if (creating) null else personId, newName.takeIf { creating }, amount, direction) },
                Modifier.fillMaxWidth(), enabled = amount > 0 && named, height = 52.dp,
            )
            if (e != null) SecondaryButton(stringResource(R.string.duty_delete), { haptics.perform(HapticLevel.Heavy); vm.deleteGift(e.gift.id) }, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}
