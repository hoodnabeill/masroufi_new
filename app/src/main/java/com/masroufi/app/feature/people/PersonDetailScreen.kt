package com.masroufi.app.feature.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.repo.PersonRow
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.plan.NameField
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
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

/** Person detail (04-SCREENS "Screens not drawn"): the person's two totals, "فكّره بلُطف" / settle the tab, the history, edit and delete. */
@Composable
fun PersonDetailScreen(viewModel: PersonDetailViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val remind = rememberReminder(viewModel::reminded)
    LaunchedEffect(ui.missing) { if (ui.missing) onBack() }
    val row = ui.row
    if (row == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            PushedHeader(row.person.name, onBack, trailing = {
                CircleButton(TzIcons.edit, stringResource(R.string.people_edit_cd)) { haptics.perform(HapticLevel.Tap); viewModel.openEdit() }
            })
        }
        if (ui.notice != null) item(key = "notice") { NoticeLine(ui.notice, viewModel::clearNotice) }
        item(key = "totals") { DetailTotals(row) }
        if (row.owedToMe > 0 || row.onTab > 0) item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.onTab > 0) PrimaryButton(stringResource(R.string.people_act_settle_tab), viewModel::openSettle, Modifier.fillMaxWidth(), height = 52.dp)
                if (row.owedToMe > 0) SecondaryButton(
                    stringResource(R.string.people_act_remind), { remind(row.person.name, row.owedToMe) }, Modifier.fillMaxWidth(), height = 52.dp,
                )
            }
        }
        item(key = "history-title") { Text(stringResource(R.string.people_history), style = TzType.cardHeading, color = Tz.text, modifier = Modifier.padding(horizontal = 4.dp)) }
        if (ui.history.isEmpty()) {
            item(key = "history-empty") { Text(stringResource(R.string.people_history_empty), style = TzType.body, color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp)) }
        } else {
            items(ui.history, key = { it.key }) { h ->
                val i = ui.history.indexOf(h)
                HistoryRow(h, first = i == 0, last = i == ui.history.lastIndex, onMoney = viewModel::openMoney)
            }
        }
        item(key = "delete") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextLink(stringResource(R.string.people_delete, row.person.name), viewModel::openDelete)
            }
        }
    }
    when (val s = ui.sheet) {
        null -> Unit
        DetailSheet.Settle -> PocketPaySheet(
            title = stringResource(R.string.people_settle_title, row.person.name), amount = row.onTab, editable = false,
            pockets = ui.pockets, pocketQuestion = R.string.people_settle_pocket, confirm = R.string.people_settle_confirm, allowNone = false,
            onDismiss = viewModel::dismissSheet,
        ) { _, pocket ->
            haptics.perform(HapticLevel.Heavy)
            viewModel.settleTab(checkNotNull(pocket))
        }
        is DetailSheet.Money -> {
            val theyOwe = s.debt.direction == DebtDirection.THEY_OWE
            PocketPaySheet(
                title = stringResource(if (theyOwe) R.string.people_received_title else R.string.people_repay_title, row.person.name),
                amount = s.debt.amount, editable = true, pockets = ui.pockets,
                pocketQuestion = if (theyOwe) R.string.people_received_pocket else R.string.people_settle_pocket,
                confirm = if (theyOwe) R.string.people_received_confirm else R.string.people_settle_confirm, allowNone = true,
                onDismiss = viewModel::dismissSheet,
            ) { amount, pocket ->
                haptics.perform(HapticLevel.Heavy)
                viewModel.settleDebt(s.debt, amount, pocket)
            }
        }
        DetailSheet.Edit -> EditSheet(row, viewModel)
        DetailSheet.Delete -> DeleteSheet(row, ui.history, viewModel)
    }
}

@Composable
private fun DetailTotals(row: PersonRow) {
    val shape = RoundedCornerShape(24.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).clip(shape).background(Tz.turquoise).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.people_detail_owed), style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Tz.turquoiseInk)
            Text(formatPounds(row.owedToMe), style = TzType.hero.copy(fontSize = 40.sp, lineHeight = 42.sp), color = Tz.turquoiseInk)
        }
        Column(Modifier.weight(1f).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.people_detail_owe), style = TzType.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Tz.text2)
            Text(formatPounds(row.iOweLoans + row.onTab), style = TzType.hero.copy(fontSize = 40.sp, lineHeight = 42.sp), color = Tz.text)
        }
    }
}

/** A debt, a repayment or a tab item; open debts carry their own "سدّد" / "استلمت". Rows overlap by their borders like the list on the tab. */
@Composable
private fun HistoryRow(h: HistoryItem, first: Boolean, last: Boolean, onMoney: (com.masroufi.app.data.db.DebtEntity) -> Unit) {
    val top = if (first) 24.dp else 0.dp
    val bottom = if (last) 24.dp else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val title: String
    val sub: String
    when (h) {
        is HistoryItem.Loan -> {
            val d = h.debt
            title = stringResource(if (d.direction == DebtDirection.THEY_OWE) R.string.people_h_lent else R.string.people_h_borrowed, formatPounds(d.amount))
            sub = listOfNotNull(d.reason, formatShortDate(d.date), d.dueDate?.let { stringResource(R.string.people_ctx_due, formatShortDate(it)) }).joinToString(" · ")
        }
        is HistoryItem.Repayment -> {
            title = stringResource(if (h.toMe) R.string.people_h_repaid_to_me else R.string.people_h_repaid_by_me, formatPounds(h.amount))
            sub = formatShortDate(h.date)
        }
        is HistoryItem.TabLine -> {
            title = h.item.label
            sub = formatShortDate(h.item.date)
        }
    }
    Row(
        Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.offset(y = (-1).dp)).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
            Text(sub, style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
        when (h) {
            is HistoryItem.Loan -> if (h.debt.settled) {
                Text(stringResource(R.string.people_h_done), style = TzType.caption, color = Tz.text2)
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.people_h_left, formatPounds(h.debt.amount)), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
                    val theyOwe = h.debt.direction == DebtDirection.THEY_OWE
                    SmallPill(stringResource(if (theyOwe) R.string.people_act_received else R.string.people_act_repay), !theyOwe, { onMoney(h.debt) })
                }
            }
            is HistoryItem.TabLine -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatPounds(h.item.amount), style = TzType.rowNumber, color = Tz.text)
                    if (h.item.settled) Text(stringResource(R.string.people_h_tab_done), style = TzType.caption, color = Tz.text2)
                }
            }
            is HistoryItem.Repayment -> Unit
        }
    }
}

@Composable
private fun EditSheet(row: PersonRow, vm: PersonDetailViewModel) {
    val haptics = LocalHaptics.current
    var name by remember { mutableStateOf(row.person.name) }
    var phone by remember { mutableStateOf(row.person.phone.orEmpty()) }
    var kind by remember { mutableStateOf(row.person.kind) }
    TzSheet(vm::dismissSheet) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.people_edit_title), style = TzType.cardHeading, color = Tz.text)
            NameField(name, { name = it.take(40) }, R.string.people_add_name_hint)
            NameField(phone, { phone = it.take(20) }, R.string.people_edit_phone_hint)
            ChipRow {
                TzChip(stringResource(R.string.people_kind_person), kind == PersonKind.PERSON, { kind = PersonKind.PERSON }, style = ChipStyle.Choice)
                TzChip(stringResource(R.string.people_kind_shop), kind == PersonKind.SHOP, { kind = PersonKind.SHOP }, style = ChipStyle.Choice)
            }
            PrimaryButton(
                stringResource(R.string.people_edit_save), { haptics.perform(HapticLevel.Confirm); vm.saveEdit(name, phone, kind) },
                Modifier.fillMaxWidth(), enabled = name.isNotBlank(), height = 52.dp,
            )
        }
    }
}

/** Asks first and says what goes with the person. */
@Composable
private fun DeleteSheet(row: PersonRow, history: List<HistoryItem>, vm: PersonDetailViewModel) {
    val haptics = LocalHaptics.current
    val loans = history.count { it is HistoryItem.Loan }
    val items = history.count { it is HistoryItem.TabLine }
    TzSheet(vm::dismissSheet) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.people_delete_title, row.person.name), style = TzType.cardHeading, color = Tz.text)
            Text(
                stringResource(R.string.people_delete_body, loans.toString(), items.toString()),
                style = TzType.body.copy(textAlign = TextAlign.Start), color = Tz.text2,
            )
            PrimaryButton(stringResource(R.string.people_delete_confirm), { haptics.perform(HapticLevel.Heavy); vm.delete() }, Modifier.fillMaxWidth(), height = 52.dp)
            SecondaryButton(stringResource(R.string.people_delete_cancel), vm::dismissSheet, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}
