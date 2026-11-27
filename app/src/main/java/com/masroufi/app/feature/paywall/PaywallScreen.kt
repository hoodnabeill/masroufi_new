@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.masroufi.app.feature.paywall

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.FlowRow
import com.masroufi.app.ui.components.isLargeFont
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatDayMonth
import com.masroufi.app.data.repo.FeaturePitch
import com.masroufi.app.data.repo.LossRow
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.TrialState
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.pluralText
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.delay
import java.util.Locale

const val PaywallCloseTag = "paywall_close"
const val PaywallNoThanksTag = "paywall_no_thanks"
const val PaywallButtonTag = "paywall_button"

/** Rows arrive one after another this far apart (05-MOTION / 09-PREMIUM). */
const val ROW_STAGGER_MS = 60L

/** مصروفي بلس (Paywall.dc.html). [onClose] goes back to where the user came from; closing never asks. */
@Composable
fun PaywallScreen(viewModel: PaywallViewModel, onClose: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    PaywallContent(ui, viewModel::select, viewModel::buy, onClose)
}

@Composable
fun PaywallContent(ui: PaywallUi, onSelect: (PlusPlan) -> Unit, onBuy: () -> Unit, onClose: () -> Unit) {
    val haptics = LocalHaptics.current
    val done = ui.purchase == PurchaseState.Done
    val close by rememberUpdatedState(onClose)
    val closeCd = stringResource(R.string.paywall_close)
    LaunchedEffect(done) { if (done) haptics.perform(HapticLevel.Milestone) }
    LaunchedEffect(ui.close) { if (ui.close) close() }
    Column(
        Modifier.fillMaxSize().background(Tz.night).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            val tag = tagText(ui)
            if (tag != null) {
                Text(
                    tag,
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Tz.carnelianDim).padding(horizontal = 12.dp, vertical = 6.dp),
                    style = TzType.label.copy(fontSize = 12.sp), color = Tz.carnelianText,
                )
            } else {
                Box(Modifier)
            }
            Box(
                Modifier.size(48.dp).testTag(PaywallCloseTag).clip(CircleShape).background(Tz.surface).border(1.dp, Tz.line, CircleShape)
                    .semantics { contentDescription = closeCd }
                    .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClose() },
                contentAlignment = Alignment.Center,
            ) { TzIcon(TzIcons.close, 18.dp, null, tint = Tz.text) }
        }
        // Sad while something stops; delighted once bought; otherwise just thinking (nothing is about to stop).
        val mood = when {
            done -> QershMood.Delighted
            ui.stops -> QershMood.Sad
            else -> QershMood.Thinking
        }
        val moodCd = stringResource(if (done) R.string.paywall_qersh_happy else R.string.paywall_qersh_sad)
        val headline = stringResource(headlineRes(ui.headline))
        if (isLargeFont()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Qersh(mood, 92.dp, contentDescription = moodCd)
                Text(headline, style = TzType.hero.copy(fontSize = 36.sp, lineHeight = 1.2.em), color = Tz.text, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Qersh(mood, 92.dp, contentDescription = moodCd)
                Text(headline, style = TzType.hero.copy(fontSize = 36.sp, lineHeight = 1.2.em), color = Tz.text, modifier = Modifier.weight(1f))
            }
        }
        Rows(ui, done)
        Text(
            stringResource(R.string.paywall_data_line),
            Modifier.padding(horizontal = 4.dp),
            style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 1.7.em), color = Tz.text2,
        )
        if (ui.pricesAvailable) {
            val planCards: @Composable (Boolean) -> Unit = { stacked ->
                ui.plans.forEach { p ->
                    PlanCard(
                        p, selected = p.plan == ui.selected, enabled = ui.purchase != PurchaseState.Buying && !done,
                        modifier = if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    ) {
                        if (p.plan != ui.selected) haptics.perform(HapticLevel.Tick)
                        onSelect(p.plan)
                    }
                }
            }
            if (isLargeFont()) Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(18.dp)) { planCards(true) }
            else Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { planCards(false) }
        } else {
            Text(stringResource(R.string.paywall_prices_unavailable), style = TzType.body, color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp))
        }
        PrimaryButton(
            stringResource(R.string.paywall_button),
            { haptics.perform(HapticLevel.Confirm); onBuy() },
            Modifier.fillMaxWidth().testTag(PaywallButtonTag),
            enabled = ui.canBuy,
            height = 62.dp,
        )
        if (ui.purchase == PurchaseState.Failed) {
            Text(
                stringResource(R.string.paywall_failed),
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                style = TzType.body, color = Tz.carnelianText,
            )
        }
        if (done) {
            Text(stringResource(R.string.paywall_done), Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, style = TzType.body, color = Tz.turquoise)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(
                    when {
                        !ui.pricesAvailable -> R.string.paywall_renew_none
                        ui.selected == PlusPlan.YEARLY -> R.string.paywall_renew_year
                        else -> R.string.paywall_renew_month
                    },
                ),
                style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2,
            )
            Box(
                Modifier.testTag(PaywallNoThanksTag).defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.paywall_no_thanks), style = TzType.caption.copy(fontSize = 13.sp, textDecoration = TextDecoration.Underline), color = Tz.text2)
            }
        }
    }
}

private fun headlineRes(h: Headline) = when (h) {
    Headline.Tomorrow -> R.string.paywall_h_tomorrow
    Headline.Today -> R.string.paywall_h_today
    Headline.Later -> R.string.paywall_h_later
    Headline.Ended -> R.string.paywall_h_ended
    Headline.Adds -> R.string.paywall_h_adds
    Headline.Plain -> R.string.paywall_h_plain
}

/** The trial-ending tag: only while a trial is running. */
@Composable
private fun tagText(ui: PaywallUi): String? {
    if (ui.trialState != TrialState.ACTIVE) return null
    val end = ui.endDay
    val locale = LocalConfiguration.current.locales[0]
    return when {
        ui.daysLeft == 1 -> stringResource(R.string.paywall_tag_tomorrow)
        ui.daysLeft == 0 -> stringResource(R.string.paywall_tag_today)
        end != null -> stringResource(R.string.paywall_tag_on, formatDayMonth(end, locale))
        else -> null
    }
}

/** The rows, arriving [ROW_STAGGER_MS] apart; under reduce motion they are all there at once. */
@Composable
private fun Rows(ui: PaywallUi, done: Boolean) {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.rows.size, reduce) {
        if (reduce) {
            shown = ui.rows.size
        } else {
            for (i in 1..ui.rows.size) {
                shown = i
                delay(ROW_STAGGER_MS)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ui.rows.forEachIndexed { i, row ->
            val alpha by animateFloatAsState(if (i < shown) 1f else 0f, tween(if (reduce) 0 else 200), label = "row")
            val outline by animateColorAsState(
                when {
                    done -> Tz.turquoise
                    ui.stops -> Tz.carnelian
                    else -> Tz.line
                },
                tween(if (reduce) 0 else 300), label = "outline",
            )
            LossRowView(
                row, outline, dashed = ui.stops && !done,
                Modifier.graphicsLayer { this.alpha = alpha; translationY = (1f - alpha) * 12.dp.toPx() },
                stopping = ui.stops,
            )
        }
    }
}

@Composable
private fun LossRowView(row: PaywallRow, outline: Color, dashed: Boolean, modifier: Modifier, stopping: Boolean) {
    val (value, title, sub) = rowTexts(row, stopping)
    val radius = 18.dp
    val rowModifier = modifier.fillMaxWidth().testTag("paywall_row_${row.feature.name}").clip(RoundedCornerShape(radius)).background(Tz.surface)
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    outline, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null),
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    val texts: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = TzType.label.copy(fontSize = 14.sp), color = Tz.text)
            if (sub.isNotEmpty()) Text(sub, style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
    }
    if (isLargeFont()) {
        // the number gets its own line: at this size it would squeeze the words beside it
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (value != null) Text(value, style = TzType.rowNumber.copy(fontSize = 22.sp), color = Tz.yellow)
            texts(Modifier.fillMaxWidth())
        }
    } else {
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (value != null) Text(value, Modifier.width(64.dp), style = TzType.rowNumber.copy(fontSize = 22.sp), color = Tz.yellow)
            texts(Modifier.weight(1f))
        }
    }
}

/** value line (null = none), title, one-line subtext. Every number comes from the user's data; the words are fixed. */
@Composable
private fun rowTexts(row: PaywallRow, stopping: Boolean): Triple<String?, String, String> = when (row) {
    is PaywallRow.Loss -> {
        val r = row.row
        val pitch = row.pitch?.let { pitchLine(it) }
        when (r) {
            is LossRow.Gold -> Triple(
                stringResource(R.string.paywall_val_grams, grams(r.value)), stringResource(R.string.loss_title_gold),
                pitch ?: stringResource(if (stopping) R.string.loss_sub_gold_loss else R.string.loss_sub_gold),
            )
            is LossRow.RoundUps -> Triple(
                stringResource(R.string.paywall_val_pounds, poundsText(r.value)), stringResource(R.string.loss_title_roundups),
                pitch ?: stringResource(R.string.loss_sub_roundups),
            )
            is LossRow.Seasons -> Triple(
                pluralText(R.plurals.paywall_val_seasons, r.value.toInt()), stringResource(R.string.loss_title_seasons),
                pitch ?: seasonsSub(r),
            )
            is LossRow.Duty -> Triple(
                pluralText(R.plurals.paywall_val_names, r.value.toInt()), stringResource(R.string.loss_title_duty), pitch ?: dutySub(r),
            )
            is LossRow.SafetyTicket -> Triple(
                pluralText(R.plurals.paywall_val_days, r.value.toInt()), stringResource(R.string.loss_title_safety),
                pitch ?: stringResource(R.string.loss_sub_safety),
            )
            is LossRow.Basket -> Triple(
                pluralText(R.plurals.paywall_val_items, r.value.toInt()), stringResource(R.string.loss_title_basket),
                pitch ?: stringResource(R.string.loss_sub_basket),
            )
            is LossRow.Envelopes -> Triple(
                pluralText(R.plurals.paywall_val_envelopes, r.value.toInt()), stringResource(R.string.loss_title_envelopes),
                pitch ?: stringResource(R.string.loss_sub_envelopes),
            )
        }
    }
    is PaywallRow.Pitch -> Triple(null, stringResource(pitchTitle(row.feature)), pitchLine(row.pitch))
}

/** Hundredths of a gram: 37 -> "0.37". */
internal fun grams(hundredths: Long): String = String.format(Locale.US, "%d.%02d", hundredths / 100, hundredths % 100)

@Composable
private fun seasonsSub(r: LossRow.Seasons): String {
    val base = if (r.nearestDaysUntil <= 0) stringResource(R.string.loss_sub_seasons_today, r.nearestName)
    else stringResource(R.string.loss_sub_seasons, r.nearestName, r.nearestDaysUntil.toInt())
    return if (r.nearestSaved > 0) base + " " + stringResource(R.string.loss_sub_seasons_saved, poundsText(r.nearestSaved)) else base
}

@Composable
private fun dutySub(r: LossRow.Duty): String {
    val name = r.nextName
    val days = r.nextDaysUntil
    return when {
        name == null || days == null -> stringResource(R.string.loss_sub_duty_none)
        days <= 0 -> stringResource(R.string.loss_sub_duty_today, name)
        else -> stringResource(R.string.loss_sub_duty, name, days.toInt())
    }
}

private fun pitchTitle(f: PlusFeature) = when (f) {
    PlusFeature.GOLD -> R.string.loss_title_gold
    PlusFeature.ROUND_UPS -> R.string.loss_title_roundups
    PlusFeature.SEASONS -> R.string.loss_title_seasons
    PlusFeature.ZAKAT -> R.string.loss_title_zakat
    PlusFeature.DUTY -> R.string.loss_title_duty
    PlusFeature.BASKET -> R.string.loss_title_basket
    PlusFeature.ENVELOPES -> R.string.loss_title_envelopes
    PlusFeature.GAMEYA -> R.string.loss_title_gameya
    PlusFeature.KIDS -> R.string.loss_title_kids
    PlusFeature.MATCH_HISTORY -> R.string.loss_title_match
    PlusFeature.INSTALLMENT_COMPARE -> R.string.loss_title_installment
    PlusFeature.SAFETY_TICKET -> R.string.loss_title_safety
}

/** What the feature would do with the user's data; with nothing to say, the feature's own plain line. */
@Composable
private fun pitchLine(p: FeaturePitch): String {
    val amount = p.amount?.let { poundsText(it) }
    val count = p.count
    return when (p.feature) {
        PlusFeature.GOLD ->
            if (amount != null && p.gramsHundredths != null && p.gramsHundredths > 0) stringResource(R.string.loss_pitch_gold, amount, grams(p.gramsHundredths))
            else stringResource(R.string.jar_lock_gold)
        PlusFeature.ZAKAT -> if (amount != null) stringResource(R.string.loss_pitch_zakat, amount) else stringResource(R.string.zakat_lock)
        PlusFeature.ROUND_UPS -> if (amount != null) stringResource(R.string.loss_pitch_roundups, amount) else stringResource(R.string.jar_lock_round)
        PlusFeature.SEASONS ->
            if (count != null) stringResource(R.string.loss_pitch_seasons, pluralText(R.plurals.paywall_val_seasons, count)) else stringResource(R.string.seasons_intro)
        PlusFeature.DUTY ->
            if (count != null) stringResource(R.string.loss_pitch_duty, pluralText(R.plurals.paywall_val_names, count)) else stringResource(R.string.people_duty_plus)
        PlusFeature.BASKET ->
            if (count != null) stringResource(R.string.loss_pitch_basket, pluralText(R.plurals.paywall_val_items, count)) else stringResource(R.string.basket_locked_what)
        PlusFeature.ENVELOPES ->
            if (count != null) stringResource(R.string.loss_pitch_envelopes, pluralText(R.plurals.paywall_val_envelopes, count)) else stringResource(R.string.env_plus_line)
        PlusFeature.GAMEYA -> stringResource(R.string.gameya_plus_line)
        PlusFeature.KIDS -> stringResource(R.string.kids_plus_line)
        PlusFeature.MATCH_HISTORY ->
            if (count != null) stringResource(R.string.loss_pitch_match, pluralText(R.plurals.paywall_val_days, count)) else stringResource(R.string.match_locked_what)
        PlusFeature.INSTALLMENT_COMPARE -> stringResource(R.string.buy_inst_locked)
        PlusFeature.SAFETY_TICKET ->
            if (count != null) stringResource(R.string.loss_pitch_safety, pluralText(R.plurals.paywall_val_days, count)) else stringResource(R.string.loss_sub_safety)
    }
}

@Composable
private fun PlanCard(p: PlanUi, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val name = stringResource(if (p.plan == PlusPlan.YEARLY) R.string.paywall_plan_year else R.string.paywall_plan_month)
    val color = if (selected) Tz.yellow else Tz.text
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().testTag("paywall_plan_${p.plan.name}").clip(shape)
                .background(if (selected) Tz.yellowDim else Tz.surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) Tz.yellow else Tz.line, shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = TzType.label.copy(fontSize = 14.sp), color = Tz.text, modifier = Modifier.weight(1f))
                Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) Tz.yellow else Tz.text3, CircleShape), contentAlignment = Alignment.Center) {
                    if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Tz.yellow))
                }
            }
            // The store's own text, as given (the stub's debug prices carry "(debug)").
            Text(p.priceText, style = TzType.card.copy(fontSize = 26.sp, lineHeight = 1.15.em), color = color)
            val sub = when {
                p.perMonth != null ->
                    if (p.currency == "EGP") stringResource(R.string.paywall_per_month, p.perMonth.toString())
                    else stringResource(R.string.paywall_per_month_cur, p.perMonth.toString(), p.currency)
                else -> stringResource(R.string.paywall_month_renews)
            }
            Text(sub, style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
        if (p.savePercent != null) {
            Text(
                stringResource(R.string.paywall_save, p.savePercent),
                Modifier.align(Alignment.TopStart).offset(x = 14.dp, y = (-11).dp).clip(RoundedCornerShape(10.dp)).background(Tz.yellow).padding(horizontal = 10.dp, vertical = 3.dp),
                style = TzType.label.copy(fontSize = 11.sp), color = Tz.night,
            )
        }
    }
}
