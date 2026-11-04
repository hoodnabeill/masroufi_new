package com.masroufi.app.feature.wallets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.masroufi.app.ui.components.LiquidButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.ui.components.FitAmount
import androidx.compose.ui.unit.sp
import com.masroufi.app.core.Money
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.WalletType
import com.masroufi.app.data.model.WalletWithBalance
import com.masroufi.app.domain.SmsParse
import com.masroufi.app.domain.SmsParser
import com.masroufi.app.domain.SmsSource
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.glassFieldColors
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass

/** "إجمالي فلوسك": every active wallet in [currency] added up, with each wallet's share as a split bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalletsTotal(wallets: List<WalletWithBalance>, currency: String) {
    val glass = LocalGlass.current
    val mine = wallets.filter { !it.wallet.archived && it.wallet.currency == currency }
    val total = mine.sumOf { it.balanceMinor }
    val positive = mine.filter { it.balanceMinor > 0 }
    val shareBase = positive.sumOf { it.balanceMinor }.coerceAtLeast(1)
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(glass.fill).border(1.dp, glass.rim, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.wallets_total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FitAmount(total, currency, style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 44.sp))
        if (positive.size > 1) {
            Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                positive.forEach { w ->
                    Box(Modifier.weight(w.balanceMinor.toFloat() / shareBase).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(categoryColor(w.wallet.colorIndex)))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                positive.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(w.wallet.colorIndex)))
                        Spacer(Modifier.width(6.dp))
                        Text("${w.wallet.name} ${w.balanceMinor * 100 / shareBase}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Picks the wallet a message most likely belongs to: by name first, then by kind (bank vs mobile wallet). */
internal fun guessWallet(source: SmsSource?, wallets: List<WalletWithBalance>): WalletWithBalance? {
    val active = wallets.filter { !it.wallet.archived }
    val names = when (source) {
        SmsSource.VODAFONE_CASH -> listOf("فودافون", "vodafone")
        SmsSource.ETISALAT_CASH -> listOf("اتصالات", "etisalat")
        SmsSource.ORANGE_CASH -> listOf("اورنج", "أورنج", "orange")
        SmsSource.WE_PAY -> listOf("we", "وي")
        SmsSource.INSTAPAY -> listOf("انستا", "إنستا", "insta")
        SmsSource.BANK, null -> emptyList()
    }
    return active.firstOrNull { w -> names.any { w.wallet.name.contains(it, ignoreCase = true) } }
        ?: when (source) {
            null -> null
            SmsSource.BANK -> active.firstOrNull { it.wallet.type == WalletType.BANK }
            else -> active.firstOrNull { it.wallet.type == WalletType.DIGITAL }
        }
}

/**
 * سجّل من الرسالة: the user pastes a wallet or bank SMS, Masroufi reads it on the phone and fills the quick-add sheet,
 * where it is checked and saved. Nothing leaves the device.
 */
@Composable
internal fun SmsLogCard(wallets: List<WalletWithBalance>, onLog: (type: TransactionType, walletId: String?, amount: String, note: String?) -> Unit) {
    val glass = LocalGlass.current
    val clipboard = LocalClipboardManager.current
    val violet = MaterialTheme.colorScheme.secondary
    var text by rememberSaveable { mutableStateOf("") }
    var parsedFor by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val parsed: SmsParse? = parsedFor?.let(SmsParser::parse)
    // The handoff tints this card violet (#1B1830 on #332C55) so the "new" feature stands apart from the plain cards.
    val shape = RoundedCornerShape(26.dp)
    val dash = Color(0xFF4A3F8F)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1B1830)).border(1.dp, Color(0xFF332C55), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(MIcons.Sparkle, violet, size = 30.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sms_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.sms_new),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(stringResource(R.string.sms_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; parsedFor = null; failed = false },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .drawBehind {
                    drawRoundRect(
                        dash,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                    )
                },
            placeholder = { Text(stringResource(R.string.sms_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glass.base,
                unfocusedContainerColor = glass.base,
                focusedBorderColor = violet,
                unfocusedBorderColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        if (parsed == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassSecondaryButton(onClick = { clipboard.getText()?.text?.let { text = it; parsedFor = null; failed = false } }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sms_paste))
                }
                LiquidButton(
                    onClick = { if (SmsParser.parse(text) != null) parsedFor = text else failed = true },
                    modifier = Modifier.weight(1f),
                    enabled = text.isNotBlank(),
                    tint = violet,
                    contentColor = Color(0xFF17132E),
                ) { Text(stringResource(R.string.sms_read)) }
            }
            if (failed) Text(stringResource(R.string.sms_failed), style = MaterialTheme.typography.bodySmall, color = LocalFinanceColors.current.warning)
        }
        AnimatedVisibility(parsed != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val p = parsed ?: return@AnimatedVisibility
            val wallet = guessWallet(p.source, wallets)
            SmsResult(p, wallet, onLog = { onLog(p.type, wallet?.wallet?.id, p.amount, p.counterparty) }, onReset = { text = ""; parsedFor = null })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MIcons.Lock, contentDescription = null, tint = glass.textTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sms_private), style = MaterialTheme.typography.labelSmall, color = glass.textTertiary)
        }
    }
}

@Composable
private fun SmsResult(p: SmsParse, wallet: WalletWithBalance?, onLog: () -> Unit, onReset: () -> Unit) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val currency = wallet?.wallet?.currency ?: Money.DEFAULT_CURRENCY
    val amountMinor = Money.parse(p.amount, currency) ?: 0
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(glass.fillStrong).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Line(
            stringResource(R.string.sms_type),
            stringResource(
                when (p.type) {
                    TransactionType.INCOME -> R.string.type_income
                    TransactionType.TRANSFER -> R.string.sms_type_cash
                    TransactionType.EXPENSE -> R.string.type_expense
                },
            ),
        )
        Line(stringResource(R.string.field_amount), money.format(amountMinor, currency))
        Line(stringResource(R.string.field_wallet), wallet?.wallet?.name ?: stringResource(R.string.sms_wallet_pick))
        p.counterparty?.let { Line(stringResource(R.string.sms_who), it) }
        p.balanceAfter?.let { reported ->
            val reportedMinor = Money.parse(reported, currency)
            val expected = wallet?.let { it.balanceMinor + if (p.type == TransactionType.INCOME) amountMinor else -amountMinor }
            val matches = reportedMinor != null && expected == reportedMinor
            Line(
                stringResource(R.string.sms_balance_after),
                if (matches || expected == null) money.format(reportedMinor ?: 0, currency) + if (matches) " · " + stringResource(R.string.sms_matches) else ""
                else stringResource(R.string.sms_balance_differs, money.format(reportedMinor ?: 0, currency), money.format(expected, currency)),
                highlight = matches,
            )
        }
        HorizontalDivider(color = glass.strokeBottom)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(onClick = onLog, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sms_log)) }
            TextButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sms_another), color = MaterialTheme.colorScheme.secondary) }
        }
    }
}

@Composable
private fun Line(label: String, value: String, highlight: Boolean = false) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textTertiary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
