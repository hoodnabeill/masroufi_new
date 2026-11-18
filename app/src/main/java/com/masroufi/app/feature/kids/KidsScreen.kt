package com.masroufi.app.feature.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.core.PIN_MIN_DIGITS
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.ChoreView
import com.masroufi.app.data.repo.KidsView
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.plan.AmountKeypad
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.plan.NameField
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.PlusSheet
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.BlankSheet
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.DayOfWeek

/** مصروف العيال (Kids.dc.html), reached from the People tab. */
@Composable
fun KidsScreen(viewModel: KidsViewModel, onBack: () -> Unit, onNewChild: () -> Unit, onEditChild: (Long) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(viewModel) { viewModel.haptics.collect { haptics.perform(it) } }
    val view = ui.view
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.kids_title), onBack) {
            CircleButton(TzIcons.add, stringResource(R.string.kids_add_cd)) {
                haptics.perform(HapticLevel.Tap)
                if (view == null || view.canAddChild) onNewChild() else viewModel.openPlus()
            }
        }
        if (ui.loading || view == null) return@Column
        if (view.children.isEmpty()) {
            EmptyState({ BlankSheet() }, R.string.kids_empty_title, R.string.kids_empty_line, R.string.kids_empty_action, onNewChild, primaryAction = true)
            return@Column
        }
        Summary(view, viewModel::openHandoverDay)
        ui.tickets.forEach { t -> ParentTicket(t, view.handoverDay, viewModel, onEdit = { onEditChild(t.view.child.id) }) }
        ChoresCard(view, viewModel)
        if (ui.notice == KidsNotice.CHORE_TAKEN) Text(stringResource(R.string.kids_chore_taken), style = TzType.label, color = Tz.text2)
        HintCard(ui.tickets.filter { it.view.editable }, viewModel::openChildScreen)
        PrimaryButton(
            stringResource(R.string.kids_handover, dayName(view.handoverDay), formatPounds(view.total)),
            { viewModel.handOver() },
            Modifier.fillMaxWidth(), enabled = ui.total > 0, height = 60.dp, textStyle = TzType.button.copy(fontSize = 22.sp),
        )
    }
    KidsSheets(ui, viewModel)
}

@Composable
private fun Summary(view: KidsView, onDay: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.kids_month_line, formatPounds(view.monthEstimate)) + (view.envelopeName?.let { " · " + stringResource(R.string.kids_from_envelope) } ?: ""),
            style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2, modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.kids_handover_on, dayName(view.handoverDay, bare = true)),
            style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Tz.turquoise,
            modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onDay).padding(start = 8.dp),
        )
    }
}

@Composable
private fun ParentTicket(t: ChildTicketUi, day: DayOfWeek, vm: KidsViewModel, onEdit: () -> Unit) {
    val v = t.view
    val jar = v.jar
    val advance = v.waitingAdvance
    KidsTicket(
        t, day, onHeader = if (v.editable) onEdit else null,
        hasBelow = jar != null || advance != null || !v.editable,
        below = { c ->
            if (advance != null) {
                Text(stringResource(R.string.kids_advance_ask, formatPounds(advance.amount)), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = c.ink)
                if (v.editable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TicketPill(stringResource(R.string.kids_advance_yes), c, filled = true, modifier = Modifier.weight(1f)) { vm.answerAdvance(advance.id, true) }
                        TicketPill(stringResource(R.string.kids_advance_wait, dayName(day)), c, filled = false, modifier = Modifier.weight(1f)) { vm.answerAdvance(advance.id, false) }
                    }
                }
            }
            if (jar != null) {
                JarBlock(jar, c, if (jar.matchPer != null && jar.matchAdd != null) stringResource(R.string.kids_match_line, formatPounds(jar.matchPer), formatPounds(jar.matchAdd)) else null)
            }
            if (!v.editable) Text(stringResource(R.string.kids_read_only), style = TzType.caption.copy(fontSize = 12.sp), color = c.ink)
        },
    )
}

@Composable
private fun ChoresCard(view: KidsView, vm: KidsViewModel) {
    val haptics = LocalHaptics.current
    TzCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.kids_chores_title), style = TzType.cardHeading, color = Tz.text)
            TextLink(stringResource(R.string.kids_chores_add), { haptics.perform(HapticLevel.Tap); vm.openAddChore() })
        }
        view.chores.forEach { row -> ChoreRow(row, view, onTap = { vm.tapChore(row) }) }
    }
}

@Composable
private fun ChoreRow(row: ChoreView, view: KidsView, onTap: () -> Unit) {
    val done = row.tickedBy != null
    val who = row.chore.childId ?: row.tickedBy
    val whoName = view.children.firstOrNull { it.child.id == who }?.child?.name
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox, onClick = onTap),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Tick(done)
        Column(Modifier.weight(1f)) {
            Text(row.chore.label, style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
            if (whoName != null) Text(whoName, style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
        Text(stringResource(R.string.kids_chore_bonus, formatPounds(row.chore.bonus)), style = TzType.cardHeading.copy(fontSize = 18.sp), color = if (done) Tz.turquoise else Tz.text2)
    }
}

@Composable
private fun Tick(on: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(22.dp).clip(shape).background(if (on) Tz.turquoise else Tz.night).then(if (on) Modifier else Modifier.border(2.dp, Tz.dashed, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) TzIcon(TzIcons.check, 16.dp, null, tint = Tz.turquoiseInk)
    }
}

@Composable
private fun HintCard(editable: List<ChildTicketUi>, onOpen: (Long) -> Unit) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.yellowDim).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Qersh(QershMood.Happy, 52.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.kids_hint_title), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.yellow)
                Text(stringResource(R.string.kids_hint_line), style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = Tz.text2)
            }
        }
        if (editable.isNotEmpty()) ChipRow {
            editable.forEach { t ->
                TzChip(stringResource(R.string.kids_open_child, t.view.child.name), false, { haptics.perform(HapticLevel.Tap); onOpen(t.view.child.id) }, style = ChipStyle.Choice)
            }
        }
    }
}

@Composable
private fun KidsSheets(ui: KidsUi, vm: KidsViewModel) {
    val view = ui.view ?: return
    when (val s = ui.sheet) {
        KidsSheet.HandoverDay -> TzSheet(vm::dismissSheet) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.kids_day_q), style = TzType.cardHeading, color = Tz.text)
                // Saturday first, as the week runs.
                listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).chunked(4).forEach { row ->
                    ChipRow { row.forEach { d -> TzChip(dayName(d, bare = true), view.handoverDay == d, { vm.setHandoverDay(d) }, style = ChipStyle.Choice) } }
                }
            }
        }
        KidsSheet.AddChore -> AddChoreSheet(view, vm)
        KidsSheet.Plus -> PlusSheet(R.string.kids_plus_line, PlusFeature.KIDS, vm::dismissSheet)
        is KidsSheet.ChooseChild -> TzSheet(vm::dismissSheet) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.kids_chore_who), style = TzType.cardHeading, color = Tz.text)
                ChipRow { view.children.filter { it.editable }.forEach { c -> TzChip(c.child.name, false, { vm.tick(s.choreId, c.child.id) }, style = ChipStyle.Choice) } }
            }
        }
        null -> Unit
    }
    ui.pinSetup?.let { PinSetupSheet(it, vm) }
}

/** "ضيف": label, bonus and for which child (or any). */
@Composable
private fun AddChoreSheet(view: KidsView, vm: KidsViewModel) {
    val haptics = LocalHaptics.current
    var label by remember { mutableStateOf("") }
    var bonus by remember { mutableStateOf(AmountInput()) }
    var childId by remember { mutableStateOf<Long?>(null) }
    TzSheet(vm::dismissSheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.kids_chore_new), style = TzType.cardHeading, color = Tz.text)
            NameField(label, { label = it.take(30) }, R.string.kids_chore_hint)
            Text(stringResource(R.string.kids_chore_bonus_q), style = TzType.caption, color = Tz.text2)
            AmountKeypad(bonus, { bonus = bonus.digit(it) }, { bonus = bonus.point() }, { bonus = bonus.backspace() })
            ChipRow {
                TzChip(stringResource(R.string.kids_chore_any), childId == null, { childId = null }, style = ChipStyle.Choice)
                view.children.filter { it.editable }.forEach { c -> TzChip(c.child.name, childId == c.child.id, { childId = c.child.id }, style = ChipStyle.Choice) }
            }
            PrimaryButton(
                stringResource(R.string.kids_chore_save),
                { haptics.perform(HapticLevel.Confirm); vm.addChore(label, bonus.toPiastres(), childId) },
                Modifier.fillMaxWidth(), enabled = label.isNotBlank() && bonus.toPiastres() > 0, height = 52.dp,
            )
        }
    }
}

/** The first child screen: the parent types a PIN and then types it again (4 to 6 digits). */
@Composable
private fun PinSetupSheet(s: PinSetup, vm: KidsViewModel) {
    TzSheet(vm::cancelPinSetup) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.kids_pin_set_title), style = TzType.cardHeading, color = Tz.text)
            PinLine(stringResource(if (s.stage == PinStage.ENTER) R.string.kids_pin_set_enter else R.string.kids_pin_set_repeat))
            if (s.mismatch) PinLine(stringResource(R.string.kids_pin_mismatch), error = true)
            PinPad(s.digits.length, enabled = true, okEnabled = s.digits.length >= PIN_MIN_DIGITS, onDigit = vm::setupDigit, onBackspace = vm::setupBackspace, onOk = vm::setupNext)
        }
    }
}
