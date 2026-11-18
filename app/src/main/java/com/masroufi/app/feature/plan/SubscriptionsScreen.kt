package com.masroufi.app.feature.plan

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.masroufi.app.R
import com.masroufi.app.data.repo.SubscriptionAnswer
import com.masroufi.app.data.repo.SubscriptionView
import com.masroufi.app.core.formatPounds
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.paper.BlankSheet
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import androidx.compose.ui.unit.IntSize

/** الاشتراكات (Subscriptions.dc.html): monthly total, trial card, question of the month, the list by next renewal. */
@Composable
fun SubscriptionsScreen(viewModel: SubscriptionsViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: (String) -> Unit, onOpenPerson: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val s by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.subscriptions_title), onBack, trailing = {
            CircleButton(TzIcons.add, stringResource(R.string.subs_add_cd)) { onNew("") }
        })
        when {
            s.loading -> repeat(3) { RowSkeleton() }
            s.rows.isEmpty() -> EmptyState(
                { BlankSheet() }, R.string.subs_empty_title, R.string.subs_empty_line, R.string.subs_empty_action, { onNew("") },
                primaryAction = true,
            )
            else -> {
                Hero(s)
                s.trial?.let { t ->
                    TrialCard(t, onRemind = { viewModel.remindAboutTrial(t.view) }, onKeep = { viewModel.keepAfterTrial(t.view) })
                }
                QuestionCard(s.question) { id, a -> viewModel.answer(id, a) }
                ListCard(
                    s.rows, onOpen,
                    // The group's People entry when it exists (what they owe, the reminder there); else the plain share text.
                    onRemindFriends = { v -> scope.launch { viewModel.sharedPersonId(v)?.let(onOpenPerson) ?: shareReminder(context, v) } },
                )
            }
        }
        if (!s.loading && s.chips.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.subs_chips_title), style = TzType.label.copy(fontSize = 13.sp), color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.chips.forEach { name -> DashedChip(name) { haptics.perform(HapticLevel.Tap); onNew(if (name == OTHER_CHIP) "" else name) } }
                }
            }
        }
    }
}

private fun shareReminder(context: android.content.Context, v: SubscriptionView) {
    val text = context.getString(R.string.subs_share_text, v.item.name, v.item.nextDue.dayOfMonth.toString(), formatPounds(v.userShare))
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.subs_share_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The last common name is "something else": it opens the form with no name. */
private val OTHER_CHIP = com.masroufi.app.data.repo.COMMON_SUBSCRIPTIONS.last()

@Composable
private fun DashedChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.height(44.dp).clip(shape).clickable(role = Role.Button, onClick = onClick).drawBehind {
            drawRoundRect(
                Tz.dashed, cornerRadius = CornerRadius(22.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TzType.label.copy(fontSize = 13.sp), color = Tz.text)
    }
}

@Composable
private fun Hero(s: SubscriptionsUi) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.subs_hero_label), style = TzType.label.copy(fontSize = 13.sp), color = Tz.text2)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(poundsText(s.monthlyTotal), style = TzType.hero.copy(fontSize = 64.sp, lineHeight = 64.sp), color = Tz.text)
            Text(stringResource(R.string.paper_currency), style = TzType.titlePushed.copy(fontSize = 20.sp), color = Tz.text2, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.tickets > 0) {
                StatTile(stringResource(R.string.subs_tickets, s.tickets.toString()), stringResource(R.string.subs_tickets_line), Tz.yellow, Tz.night, Tz.night, Modifier.weight(1f))
            }
            StatTile(poundsText(s.perYear), stringResource(R.string.subs_year_line), Tz.night, Tz.text, Tz.text2, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(big: String, small: String, bg: Color, ink: Color, inkSmall: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(big, style = TzType.rowNumber.copy(fontSize = 24.sp), color = ink)
        Text(small, style = TzType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = inkSmall)
    }
}

@Composable
internal fun PillButton(text: String, filled: Boolean, fill: Color, ink: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(44.dp).clip(shape)
            .then(if (filled) Modifier.background(fill) else Modifier.border(1.5.dp, fill, shape))
            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }.padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TzType.label.copy(fontSize = 13.sp, fontWeight = if (filled) FontWeight.Bold else FontWeight.SemiBold), color = if (filled) ink else fill, maxLines = 1)
    }
}

@Composable
private fun TrialCard(t: TrialCardUi, onRemind: () -> Unit, onKeep: () -> Unit) {
    val v = t.view
    val end = checkNotNull(v.item.trialEnd)
    val days = checkNotNull(v.trialDaysLeft)
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.carnelianDim).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (days == 0L) stringResource(R.string.subs_trial_today) else stringResource(R.string.subs_trial_title, days.toString()),
                    style = TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.carnelianText,
                )
                Text(v.item.name, style = TzType.cardHeading, color = Tz.text)
            }
            Text(stringResource(R.string.subs_trial_price, poundsText(v.item.amount)), style = TzType.cardHeading.copy(fontSize = 22.sp), color = Tz.carnelianText)
        }
        Text(
            stringResource(R.string.subs_trial_body, end.dayOfMonth.toString()),
            style = TzType.body.copy(fontSize = 13.sp, lineHeight = 22.sp), color = Color(0xFFF4C9BF),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val remindDay = end.minusDays(1).dayOfMonth.toString()
            PillButton(
                stringResource(if (t.reminded) R.string.subs_trial_reminded else R.string.subs_trial_remind, remindDay),
                filled = !t.reminded, fill = Tz.carnelian, ink = Tz.carnelianInk, onClick = onRemind, modifier = Modifier.weight(1f),
            )
            PillButton(stringResource(R.string.subs_trial_keep), filled = false, fill = Tz.carnelian, ink = Tz.carnelianInk, onClick = onKeep, modifier = Modifier.weight(1f))
        }
    }
}

/** The yellow question card; answering collapses it away with the Snappy spring. */
@Composable
private fun QuestionCard(question: QuestionCardUi?, onAnswer: (Long, SubscriptionAnswer) -> Unit) {
    var last by remember { mutableStateOf(question) }
    if (question != null) last = question
    AnimatedVisibility(
        visible = question != null,
        exit = if (LocalReduceMotion.current) fadeOut(tween(120)) else shrinkVertically(TzSpring.Snappy.spec<IntSize>()) + fadeOut(TzSpring.Snappy.spec<Float>()),
    ) {
        val q = last ?: return@AnimatedVisibility
        val v = q.view
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Tz.yellowDim).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.subs_q_title), style = TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.yellow)
                Text(stringResource(R.string.subs_q_text, v.item.name), style = TzType.cardHeading, color = Tz.text)
            }
            v.stopSaving?.let { save ->
                Text(
                    if (q.grams != null) stringResource(R.string.subs_q_save_gold, poundsText(save), q.grams.toString())
                    else stringResource(R.string.subs_q_save_plain, poundsText(save)),
                    style = TzType.body.copy(fontSize = 13.sp, lineHeight = 22.sp), color = Color(0xFFF0E3B5),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.subs_a_yes), false, Tz.yellow, Tz.night, { onAnswer(v.item.id, SubscriptionAnswer.YES) }, Modifier.weight(1f))
                PillButton(stringResource(R.string.subs_a_meh), false, Tz.yellow, Tz.night, { onAnswer(v.item.id, SubscriptionAnswer.NOT_REALLY) }, Modifier.weight(1f))
                PillButton(stringResource(R.string.subs_a_stop), true, Tz.yellow, Tz.night, { onAnswer(v.item.id, SubscriptionAnswer.STOPPED) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ListCard(rows: List<SubRowUi>, onOpen: (Long) -> Unit, onRemindFriends: (SubscriptionView) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.subs_next), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.subs_count, rows.size.toString()), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
            SubscriptionRow(row, { onOpen(row.view.item.id) }, { onRemindFriends(row.view) })
        }
    }
}

private val TileColors = listOf(
    Tz.sky to Color(0xFF06173D), Tz.carnelian to Tz.carnelianInk, Tz.turquoise to Tz.turquoiseInk,
    Tz.lilac to Color(0xFF2A0E38), Tz.yellow to Tz.night,
)

/** One list row: tile, name, when it renews (+ the yearly hint), amount; shared ones add the "owed" band with "remind them". */
@Composable
internal fun SubscriptionRow(row: SubRowUi, onOpen: () -> Unit, onRemindFriends: () -> Unit) {
    val v = row.view
    val item = v.item
    val day = item.nextDue.dayOfMonth.toString()
    val shared = item.sharedWith > 1
    val soon = row.daysTo in 0..TRIAL_WARNING_DAYS
    var sub = when {
        shared -> stringResource(R.string.subs_shared_day, day, item.sharedWith.toString())
        row.daysTo == 0L -> stringResource(R.string.subs_today, day)
        soon -> stringResource(R.string.subs_after, row.daysTo.toString(), day)
        else -> stringResource(R.string.subs_day, day)
    }
    v.yearlySaving?.let { sub = stringResource(R.string.subs_yearly_cheaper, sub, poundsText(it)) }
    val (bg, ink) = TileColors[(item.id % TileColors.size).toInt()]
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(item.name.trim().take(2), style = TzType.button.copy(fontSize = 16.sp), color = ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text, maxLines = 1)
                Text(sub, style = TzType.caption.copy(fontSize = 12.sp), color = if (soon && !shared) Tz.yellow else Tz.text2, maxLines = 2)
            }
            Text(poundsText(item.amount), style = TzType.rowNumber, color = Tz.text)
        }
        if (shared) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tz.turquoiseDim).clickable(role = Role.Button, onClick = onRemindFriends)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.subs_share_note, poundsText(v.userShare), poundsText(v.othersOwe), (item.sharedWith - 1).toString()),
                    style = TzType.caption.copy(fontSize = 12.sp), color = Tz.turquoiseDimText, modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.subs_remind_them),
                    style = TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.turquoise,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
