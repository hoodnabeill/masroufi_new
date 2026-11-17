package com.masroufi.app.feature.pockets

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.PRIMARY_CURRENCY
import com.masroufi.app.core.formatPounds
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.LocalDate

/** الجيوب (Wallets.dc.html), a pushed screen. */
@Composable
fun PocketsScreen(
    viewModel: PocketsViewModel,
    onBack: () -> Unit,
    onOpenPocket: (Long) -> Unit,
    onNewPocket: () -> Unit,
    onOpenAddSheet: (AddPrefill?) -> Unit,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.pockets_title), onBack) {
            CircleButton(TzIcons.add, stringResource(R.string.pockets_new_cd), onNewPocket)
        }
        if (s.loading) return@Column
        Total(s)
        s.cash?.let { cash ->
            CashCard(
                cash, s,
                onStep = viewModel::step,
                onChange = viewModel::setChange,
                onReconcile = { haptics.perform(HapticLevel.Confirm); viewModel.reconcile() },
            )
        }
        if (s.others.isNotEmpty()) {
            TzCard(padding = 0.dp) {
                s.others.forEachIndexed { i, r ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
                    PocketRow(r, s.today) { onOpenPocket(r.view.pocket.id) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(TzIcons.swap, Tz.sky, R.string.pockets_quick_transfer, Modifier.weight(1f)) { onOpenAddSheet(viewModel.transferPrefill()) }
            QuickAction(TzIcons.withdraw, Tz.turquoise, R.string.pockets_quick_atm, Modifier.weight(1f)) { onOpenAddSheet(viewModel.atmPrefill()) }
            QuickAction(TzIcons.list, Tz.yellow, R.string.pockets_quick_topup, Modifier.weight(1f)) { onOpenAddSheet(viewModel.topUpPrefill()) }
        }
        SmsCard(
            s.sms,
            onPaste = { haptics.perform(HapticLevel.Tap); viewModel.pasteSms(readClipboard(context)) },
            onLog = {
                haptics.perform(HapticLevel.Confirm)
                viewModel.smsPrefill()?.let { onOpenAddSheet(it) }
                viewModel.clearSms()
            },
        )
        DashedRow(stringResource(R.string.pockets_new_row), onNewPocket)
    }
}

/** The clipboard's first item as text; null when there is nothing. */
private fun readClipboard(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

@Composable
private fun Total(s: PocketsUi) {
    val egp = s.rows.filter { it.view.pocket.currency == PRIMARY_CURRENCY && it.view.balance > 0 }
    val positive = egp.sumOf { it.view.balance }.toFloat()
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.pockets_total_label), style = TzType.label, color = Tz.text2)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Odometer(s.total, TzType.hero.copy(fontSize = 60.sp, color = Tz.text))
            Text(stringResource(R.string.paper_currency), style = TzType.titlePushed.copy(fontSize = 20.sp), color = Tz.text2, modifier = Modifier.padding(bottom = 8.dp))
        }
        TzProgressBar(
            parts = if (positive > 0f) egp.map { it.view.balance / positive to pocketColor(it.view.pocket.color) } else emptyList(),
            modifier = Modifier.padding(top = 10.dp),
            height = 12.dp,
        )
    }
}

@Composable
private fun CashCard(cash: PocketRowUi, s: PocketsUi, onStep: (Int, Int) -> Unit, onChange: (String) -> Unit, onReconcile: () -> Unit) {
    val pill = RoundedCornerShape(percent = 50)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.yellow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cash.view.pocket.name, style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.night)
                Text(lastMoveCaption(cash.lastDate, s.today), style = TzType.caption, color = Tz.yellowInk2)
            }
            Column(horizontalAlignment = Alignment.End) {
                Odometer(cash.view.balance, TzType.card.copy(fontSize = 34.sp, color = Tz.night))
                Text(stringResource(R.string.pockets_counter_counted, formatPounds(s.counter.total)), style = TzType.caption, color = Tz.yellowInk2)
            }
        }
        NoteCounter(s.counter, onStep, onChange)
        Box(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).clip(pill).border(2.dp, Tz.night, pill)
                .clickable(role = Role.Button, onClick = onReconcile).padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.pockets_reconcile), style = TzType.button.copy(fontSize = 16.sp, textAlign = TextAlign.Center), color = Tz.night)
        }
        s.reconciled?.let {
            val line = if (it == ReconcileResult.Done) R.string.pockets_reconciled else R.string.pockets_reconciled_already
            Text(stringResource(line), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.yellowInk, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun PocketRow(r: PocketRowUi, today: LocalDate, onClick: () -> Unit) {
    val p = r.view.pocket
    val haptics = LocalHaptics.current
    val caption = if (p.kind == com.masroufi.app.data.db.PocketKind.UNDER_TILE) stringResource(R.string.pockets_under_tile) else lastMoveCaption(r.lastDate, today)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PocketTile(p.name, p.kind, p.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
            Text(caption, style = TzType.caption, color = Tz.text2)
        }
        Text(formatPounds(r.view.balance), style = TzType.rowNumber, color = Tz.text)
    }
}

@Composable
private fun QuickAction(icon: TzIconDef, tint: androidx.compose.ui.graphics.Color, label: Int, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.height(84.dp).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        TzIcon(icon, 22.dp, null, tint = tint)
        Text(stringResource(label), style = TzType.caption.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center), color = Tz.text)
    }
}

@Composable
private fun SmsCard(sms: SmsState, onPaste: () -> Unit, onLog: () -> Unit) {
    TzCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.pockets_sms_title), style = TzType.cardHeading, color = Tz.text)
            when (sms) {
                SmsState.Idle -> Unit
                SmsState.Unreadable -> Text(stringResource(R.string.pockets_sms_unreadable), style = TzType.caption.copy(fontSize = 13.sp, lineHeight = 1.7.em), color = Tz.text2)
                is SmsState.Parsed -> SmsPreviewLines(sms.preview)
            }
            if (sms is SmsState.Parsed) {
                PrimaryButton(stringResource(R.string.pockets_sms_save), onLog, Modifier.fillMaxWidth(), height = 48.dp)
                TextLink(stringResource(R.string.pockets_sms_paste), onPaste, Modifier.align(Alignment.CenterHorizontally))
            } else {
                PrimaryButton(stringResource(R.string.pockets_sms_paste), onPaste, Modifier.fillMaxWidth(), height = 48.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TzIcon(TzIcons.lock, 14.dp, null, tint = Tz.text2)
                Text(stringResource(R.string.pockets_privacy), style = TzType.caption, color = Tz.text2)
            }
        }
    }
}

@Composable
private fun ColumnScope.SmsPreviewLines(p: SmsPreview) {
    val parts = listOfNotNull(p.categoryName ?: p.parsed.party, formatPounds(p.parsed.amount), p.pocket?.name).joinToString(" · ")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.pockets_sms_understood) + " ")
                withStyle(SpanStyle(color = Tz.text, fontWeight = FontWeight.SemiBold)) { append(parts) }
            },
            style = TzType.label.copy(fontWeight = FontWeight.Normal),
            color = Tz.text2,
            modifier = Modifier.weight(1f),
        )
        if (p.matches) {
            Text(
                stringResource(R.string.pockets_sms_matches),
                style = TzType.caption.copy(fontWeight = FontWeight.Bold),
                color = Tz.turquoise,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Tz.turquoiseDim).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}
