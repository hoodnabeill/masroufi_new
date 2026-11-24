package com.masroufi.app.feature.pockets

import com.masroufi.app.ui.components.isLargeFont
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.roll.EntryLine
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.TzToggle
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Guilloche
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle

private enum class DetailSheet { Reconcile, Limit, Low, Archive }

/** جيب واحد (WalletDetail.dc.html), a pushed screen. */
@Composable
fun PocketDetailScreen(
    viewModel: PocketDetailViewModel,
    onBack: () -> Unit,
    onOpenRoll: () -> Unit,
    onOpenAddSheet: (AddPrefill?) -> Unit,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.missing) { if (s.missing) onBack() }
    PocketDetailContent(
        s, onBack, onOpenRoll,
        onReceive = { onOpenAddSheet(viewModel.receivePrefill()) },
        onMove = { onOpenAddSheet(viewModel.transferPrefill()) },
        onCashOut = { onOpenAddSheet(viewModel.cashOutPrefill()) },
        onStep = viewModel::step,
        onChange = viewModel::setChange,
        onReconcileCounted = viewModel::reconcileCounted,
        onReconcile = viewModel::reconcile,
        onSetLimit = viewModel::setLimit,
        onSetThreshold = viewModel::setLowBalanceThreshold,
        onCountsInTicket = viewModel::setCountsInTicket,
        onLowAlert = viewModel::setLowBalanceAlert,
        onArchive = viewModel::archive,
    )
}

/** Stateless one-pocket screen. */
@Composable
fun PocketDetailContent(
    s: PocketDetailUi,
    onBack: () -> Unit,
    onOpenRoll: () -> Unit,
    onReceive: () -> Unit,
    onMove: () -> Unit,
    onCashOut: () -> Unit,
    onStep: (Int, Int) -> Unit,
    onChange: (String) -> Unit,
    onReconcileCounted: () -> Unit,
    onReconcile: (Long) -> Unit,
    onSetLimit: (Long?) -> Unit,
    onSetThreshold: (Long) -> Unit,
    onCountsInTicket: (Boolean) -> Unit,
    onLowAlert: (Boolean) -> Unit,
    onArchive: () -> Unit,
) {
    val haptics = LocalHaptics.current
    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(s.name, onBack)
        if (s.loading || s.missing) {
            if (s.loading) LoadingRows()
            return@Column
        }
        Hero(s)
        // Four across; two by two once the font is large.
        val actions = buildList<@Composable (Modifier) -> Unit> {
            add { m -> Action(TzIcons.arrowUp, Tz.turquoise, R.string.pocket_act_receive, m, onReceive) }
            add { m -> Action(TzIcons.swap, Tz.sky, R.string.pocket_act_move, m, onMove) }
            if (!s.isCash) add { m -> Action(TzIcons.withdraw, Tz.yellow, R.string.pocket_act_cash, m, onCashOut) }
            add { m -> Action(TzIcons.refresh, Tz.lilac, R.string.pocket_act_reconcile, m) { sheet = DetailSheet.Reconcile } }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.chunked(if (isLargeFont()) 2 else 4).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { it(Modifier.weight(1f)) }
                }
            }
        }
        s.reconciled?.let {
            val line = if (it == ReconcileResult.Done) R.string.pockets_reconciled else R.string.pockets_reconciled_already
            Text(stringResource(line), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.turquoise, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (s.isCash) {
            CounterCard(s, onStep, onChange, onReconcile = { haptics.perform(HapticLevel.Confirm); onReconcileCounted() })
        } else {
            LimitCard(s, onEdit = { sheet = DetailSheet.Limit })
            FeesCard(s)
        }
        RecentCard(s, onOpenRoll)
        Switches(s, onCountsInTicket, onLowAlert, onEditThreshold = { sheet = DetailSheet.Low })
        TextLink(stringResource(R.string.pocket_archive), { sheet = DetailSheet.Archive }, Modifier.align(Alignment.CenterHorizontally))
    }
    when (sheet) {
        DetailSheet.Reconcile -> AmountSheet(
            stringResource(R.string.pocket_reconcile_sheet), s.balance, stringResource(R.string.pocket_act_reconcile),
            onDone = { onReconcile(it); sheet = null }, onDismiss = { sheet = null },
        )
        DetailSheet.Limit -> AmountSheet(
            stringResource(R.string.pocket_limit_sheet), s.limit, stringResource(R.string.pocket_sheet_done),
            onDone = { onSetLimit(it); sheet = null }, onDismiss = { sheet = null },
        )
        DetailSheet.Low -> AmountSheet(
            stringResource(R.string.pocket_low_sheet), s.lowBalanceThreshold, stringResource(R.string.pocket_sheet_done),
            onDone = { onSetThreshold(it); sheet = null }, onDismiss = { sheet = null },
        )
        DetailSheet.Archive -> TzSheet(onDismiss = { sheet = null }) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.pocket_archive_title, s.name), style = TzType.cardHeading.copy(fontSize = 22.sp), color = Tz.text)
                Text(stringResource(R.string.pocket_archive_line), style = TzType.body.copy(fontSize = 14.sp), color = Tz.text2)
                PrimaryButton(
                    stringResource(R.string.pocket_archive_confirm),
                    { haptics.perform(HapticLevel.Heavy); onArchive(); sheet = null },
                    Modifier.fillMaxWidth(), height = 52.dp,
                )
                TextLink(stringResource(R.string.pocket_archive_cancel), { sheet = null }, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        null -> Unit
    }
}

@Composable
private fun Hero(s: PocketDetailUi) {
    val ink = Tz.night
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pocketColor(s.color))) {
        Guilloche(Color.Black.copy(alpha = 0.12f), Modifier.align(Alignment.TopEnd).offset(x = 80.dp, y = (-80).dp).size(220.dp))
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pocket_hero_label), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = ink)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Odometer(s.balance, TzType.hero.copy(fontSize = 72.sp, color = ink))
                Text(stringResource(R.string.paper_currency), style = TzType.titlePushed, color = ink, modifier = Modifier.padding(bottom = 10.dp))
            }
            val at = s.smsAt
            if (s.matchesSms && at != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TzIcon(TzIcons.check, 16.dp, null, tint = ink)
                    Text(stringResource(R.string.pocket_sms_match, smsWhen(at, s.today)), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = ink)
                }
            }
        }
    }
}

/** "النهارده 11:20 ص". */
@Composable
private fun smsWhen(at: Long, today: java.time.LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val clock = DateTimeFormatter.ofPattern("h:mm a", locale).withDecimalStyle(DecimalStyle.STANDARD).format(time)
    return dayWord(time.toLocalDate(), today) + " " + clock
}

@Composable
private fun Action(icon: TzIconDef, tint: Color, label: Int, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.heightIn(min = 76.dp).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        TzIcon(icon, 20.dp, null, tint = tint)
        Text(stringResource(label), style = TzType.caption.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), color = Tz.text)
    }
}

@Composable
private fun CounterCard(s: PocketDetailUi, onStep: (Int, Int) -> Unit, onChange: (String) -> Unit, onReconcile: () -> Unit) {
    TzCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pockets_counter_counted, poundsText(s.counter.total)), style = TzType.cardHeading, color = Tz.text)
            NoteCounter(s.counter, onStep, onChange)
            PrimaryButton(stringResource(R.string.pockets_reconcile), onReconcile, Modifier.fillMaxWidth(), height = 48.dp, textStyle = TzType.button.copy(fontSize = 16.sp))
        }
    }
}

@Composable
private fun LimitCard(s: PocketDetailUi, onEdit: () -> Unit) {
    val limit = s.limit
    if (limit == null) {
        DashedRow(stringResource(R.string.pocket_limit_add), onEdit)
        return
    }
    TzCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pocket_limit_title), style = TzType.cardHeading, color = Tz.text)
                TextLink(stringResource(R.string.pocket_limit_edit), onEdit)
            }
            TzProgressBar(listOf((s.moved.toFloat() / limit).coerceIn(0f, 1f) to Tz.yellow), height = 12.dp)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.pocket_limit_moved, poundsText(s.moved)), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
                Text(stringResource(R.string.pocket_limit_of, poundsText(limit)), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.text)
            }
            val room = s.warningRoom
            if (room != null) {
                val first = if (room >= 0) stringResource(R.string.pocket_limit_warning, poundsText(room)) else stringResource(R.string.pocket_limit_over, poundsText(-room))
                // The mock's second sentence names a specific payment: only said when there is one.
                val second = s.upcoming?.let { stringResource(R.string.pocket_limit_upcoming, it.name, poundsText(it.amount)) }
                Text(listOfNotNull(first, second).joinToString(" "), style = TzType.caption.copy(lineHeight = 1.7.em), color = Tz.yellow)
            }
            Text(stringResource(R.string.pocket_limit_note), style = TzType.caption.copy(fontSize = 11.sp), color = Tz.text3)
        }
    }
}

@Composable
private fun FeesCard(s: PocketDetailUi) {
    TzCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pocket_fees_title), style = TzType.cardHeading, color = Tz.text)
            val f = s.fees
            if (f.total == 0L) {
                Text(stringResource(R.string.pocket_fees_none), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(poundsText(f.total), style = TzType.hero.copy(fontSize = 40.sp, lineHeight = 1.em), color = Tz.carnelianText)
                Text(
                    stringResource(R.string.pocket_fees_line, (f.cashOut.first + f.transfer.first).toString()),
                    style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            FeeLine(stringResource(R.string.pocket_fees_cash, f.cashOut.first.toString()), f.cashOut.second)
            FeeLine(stringResource(R.string.pocket_fees_transfer, f.transfer.first.toString()), f.transfer.second)
            if (s.feeTip) {
                Text(
                    stringResource(R.string.pocket_fees_tip),
                    style = TzType.caption.copy(lineHeight = 1.7.em), color = Tz.turquoiseDimText,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tz.turquoiseDim).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun FeeLine(label: String, amount: Long) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
        Text(poundsText(amount), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.text)
    }
}

@Composable
private fun RecentCard(s: PocketDetailUi, onOpenRoll: () -> Unit) {
    TzCard(padding = 0.dp) {
        FlowRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pocket_recent_title), style = TzType.cardHeading, color = Tz.text)
            TextLink(stringResource(R.string.pocket_recent_all), onOpenRoll)
        }
        if (s.recent.isEmpty()) {
            Text(stringResource(R.string.pocket_recent_empty), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                s.recent.forEachIndexed { i, e -> EntryLine(e, s.categories, false, "", first = i == 0, onEntry = { onOpenRoll() }) }
            }
        }
    }
}

@Composable
private fun Switches(s: PocketDetailUi, onCountsInTicket: (Boolean) -> Unit, onLowAlert: (Boolean) -> Unit, onEditThreshold: () -> Unit) {
    TzCard {
        Column(Modifier.fillMaxWidth()) {
            val countsTitle = stringResource(R.string.pocket_counts_title)
            SwitchRow(countsTitle, stringResource(R.string.pocket_counts_caption), s.countsInTicket, onCountsInTicket)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
            val lowTitle = stringResource(R.string.pocket_low_title, poundsText(s.lowBalanceThreshold))
            SwitchRow(lowTitle, stringResource(R.string.pocket_low_caption), s.lowBalanceAlert, onLowAlert) {
                TextLink(stringResource(R.string.pocket_low_edit), onEditThreshold)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, caption: String, checked: Boolean, onChecked: (Boolean) -> Unit, extra: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
            Text(caption, style = TzType.caption, color = Tz.text2)
        }
        extra()
        TzToggle(checked, onChecked, title)
    }
}
