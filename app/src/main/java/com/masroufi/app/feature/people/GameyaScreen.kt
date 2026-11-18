package com.masroufi.app.feature.people

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.db.GameyaPlanEntity
import com.masroufi.app.data.repo.GameyaView
import com.masroufi.app.data.repo.SlotState
import com.masroufi.app.data.repo.WheelSlot
import com.masroufi.app.data.repo.gameyaStatement
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
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.BlankSheet
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val CardShape = RoundedCornerShape(24.dp)
private val SheetPad = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
private val PlanColors = listOf(Tz.sky, Tz.yellow, Tz.turquoise, Tz.lilac, Tz.carnelian)
private const val LABEL_MAX = 30

private fun YearMonth.label(locale: Locale): String = month.getDisplayName(TextStyle.FULL, locale)

/** الجمعية (Gameya.dc.html): the wheel, the user's turn, this month's installment, the payout plan, swap and share. */
@Composable
fun GameyaMainScreen(viewModel: GameyaViewModel, onBack: () -> Unit, onNew: () -> Unit, onEdit: (Long) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val current = ui.current
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(
            current?.gameya?.name ?: stringResource(R.string.gameya_title), onBack,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current != null) CircleButton(TzIcons.edit, stringResource(R.string.gameya_edit_cd)) { haptics.perform(HapticLevel.Tap); onEdit(current.gameya.id) }
                    CircleButton(TzIcons.add, stringResource(R.string.gameya_new_cd)) { haptics.perform(HapticLevel.Tap); viewModel.requestNew(onNew) }
                }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                ui.loading -> repeat(3) { RowSkeleton() }
                current == null -> EmptyState({ BlankSheet() }, R.string.empty_gameya_title, R.string.empty_gameya_line, R.string.empty_gameya_action, onNew, primaryAction = true)
                else -> {
                    if (ui.gameyas.size > 1) {
                        ChipRow { ui.gameyas.forEach { g -> TzChip(g.gameya.name, g.gameya.id == current.gameya.id, { viewModel.select(g.gameya.id) }, style = ChipStyle.Filter) } }
                    }
                    GameyaBody(current, viewModel)
                }
            }
            Spacer(Modifier.size(28.dp))
        }
    }
    if (ui.plusSheet) PlusSheet(R.string.gameya_plus_line, PlusFeature.GAMEYA, viewModel::dismissPlus)
    val v = current
    if (v != null) GameyaSheets(ui, v, viewModel)
}

@Composable
private fun GameyaBody(v: GameyaView, vm: GameyaViewModel) {
    val haptics = LocalHaptics.current
    val share = rememberShareText()
    val statementTitle = stringResource(R.string.gameya_share)
    Wheel(v)
    TurnCard(v)
    InstallmentCard(v, vm)
    PlanCard(v, vm)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(TzIcons.swap, Tz.lilac, R.string.gameya_swap, Modifier.weight(1f)) { haptics.perform(HapticLevel.Tap); vm.openSwap() }
        ActionTile(TzIcons.share, Tz.turquoise, R.string.gameya_share, Modifier.weight(1f)) { haptics.perform(HapticLevel.Tap); share(gameyaStatement(v), statementTitle) }
    }
    Text(stringResource(R.string.gameya_records_only), style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 20.sp), color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun ActionTile(icon: TzIconDef, tint: Color, label: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.height(84.dp).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        TzIcon(icon, 22.dp, null, tint = tint)
        Text(stringResource(label), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.text, textAlign = TextAlign.Center)
    }
}

/** The circle: members round a dashed ring, a turquoise arc up to whoever is next; the payout and month in the middle. */
@Composable
private fun Wheel(v: GameyaView) {
    val n = v.wheel.size
    val paidOut = v.wheel.count { it.state == SlotState.PAID_OUT }
    val description = stringResource(R.string.gameya_wheel_cd, n.toString(), paidOut.toString())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Tz.surface).border(1.dp, Tz.line, RoundedCornerShape(28.dp)).padding(top = 10.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(340.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = 130.dp.toPx()
                drawCircle(Tz.line, r, c, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))))
                if (paidOut > 0) {
                    drawArc(Tz.turquoise, -90f, 360f * paidOut / n, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            // Fewer pixels per seat as the circle fills up.
            val base = (46f * min(1f, 10f / n)).coerceAtLeast(30f)
            v.wheel.forEachIndexed { i, slot ->
                val angle = Math.toRadians(-90.0 + 360.0 * i / n)
                Seat(slot, base.dp, Modifier.absoluteOffset((130 * cos(angle)).dp, (130 * sin(angle)).dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.gameya_payout), style = TzType.label.copy(fontSize = 16.sp), color = Tz.text2)
                Text(formatPounds(v.payout), style = TzType.hero.copy(fontSize = 44.sp), color = Tz.text)
                Text(
                    if (v.finished) stringResource(R.string.people_gameya_done) else stringResource(R.string.gameya_month_of, v.monthNumber.coerceAtLeast(1).toString(), v.people.toString()),
                    style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(stringResource(R.string.gameya_leg_out)) { Box(Modifier.size(10.dp).clip(CircleShape).background(Tz.turquoise)) }
            Legend(stringResource(R.string.gameya_leg_now)) { Box(Modifier.size(10.dp).clip(CircleShape).background(Tz.yellow)) }
            Legend(stringResource(R.string.gameya_leg_me)) { Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, Tz.yellow, CircleShape)) }
        }
    }
}

@Composable
private fun Legend(label: String, dot: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        dot()
        Text(label, style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
    }
}

/** One member of the wheel: paid out is turquoise, this month's turn is yellow (and bigger), the user's seat is dashed yellow. */
@Composable
private fun Seat(slot: WheelSlot, base: Dp, modifier: Modifier) {
    val now = slot.state == SlotState.THIS_MONTH
    val out = slot.state == SlotState.PAID_OUT
    val diameter = if (now || slot.isMe) base * 1.2f else base
    val fill: Color
    val ink: Color
    val rim: Color
    when {
        now -> { fill = Tz.yellow; ink = Tz.night; rim = Tz.night }
        out -> { fill = Tz.turquoiseDim; ink = Tz.turquoise; rim = Tz.turquoise }
        slot.isMe -> { fill = Tz.night; ink = Tz.yellow; rim = Tz.yellow }
        else -> { fill = Tz.raised; ink = Tz.text; rim = Tz.dashed }
    }
    val dashedMe = slot.isMe && slot.state == SlotState.WAITING
    val name = slot.member?.name?.trim()?.substringBefore(' ') ?: stringResource(R.string.gameya_me)
    Box(
        modifier.size(diameter).clip(CircleShape).background(fill)
            .then(
                if (dashedMe) Modifier.drawBehind {
                    drawCircle(Tz.yellow, diameter.toPx() / 2 - 1.5.dp.toPx(), style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                } else Modifier.border(if (now) 3.dp else 2.dp, rim, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name, style = TzType.cardHeading.copy(fontSize = (base.value * 0.36f).sp), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}

/** "دورك التامن · مارس" over how far off it is, and what has been paid in so far. */
@Composable
private fun TurnCard(v: GameyaView) {
    val locale = LocalConfiguration.current.locales[0]
    val me = v.wheel.firstOrNull { it.isMe }
    val big = when {
        me?.state == SlotState.PAID_OUT -> stringResource(R.string.gameya_turn_done)
        me?.state == SlotState.THIS_MONTH || v.monthsUntil == 0L -> stringResource(R.string.gameya_in_now)
        v.monthsUntil == 1L -> stringResource(R.string.gameya_in_one)
        v.monthsUntil == 2L -> stringResource(R.string.gameya_in_two)
        else -> stringResource(R.string.gameya_in_n, v.monthsUntil.toString())
    }
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Tz.yellow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.gameya_turn_title, v.gameya.myTurn.toString(), v.myTurnMonth.label(locale)),
            style = TzType.label.copy(fontWeight = FontWeight.Bold), color = Tz.night,
        )
        Text(big, style = TzType.hero.copy(fontSize = 52.sp, lineHeight = 52.sp), color = Tz.night)
        Text(stringResource(R.string.gameya_saved_line, formatPounds(v.myPaidSoFar), formatPounds(v.payout)), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.yellowInk)
    }
}

/** This month's installment: the user's own (paid pill or button), who else paid, "فكّرهم", and the ticks. */
@Composable
private fun InstallmentCard(v: GameyaView, vm: GameyaViewModel) {
    val haptics = LocalHaptics.current
    val locale = LocalConfiguration.current.locales[0]
    val share = rememberShareText()
    val month = v.thisMonth.label(locale)
    val meName = stringResource(R.string.gameya_me)
    val unpaid = v.unpaidMembers.map { it.name } + if (v.myPaidThisMonth) emptyList() else listOf(meName)
    val remindText = stringResource(R.string.gameya_remind_text, v.gameya.name, formatPounds(v.gameya.monthly), v.unpaidMembers.joinToString("، ") { it.name })
    val remindTitle = stringResource(R.string.gameya_remind)
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Tz.surface).border(1.dp, Tz.line, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gameya_installment, month, formatPounds(v.gameya.monthly)), style = TzType.cardHeading, color = Tz.text, modifier = Modifier.weight(1f))
            if (v.myPaidThisMonth) {
                Text(
                    stringResource(R.string.gameya_i_paid), style = TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.turquoise,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Tz.turquoiseDim).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            } else {
                PrimaryButton(stringResource(R.string.gameya_pay_mine), { haptics.perform(HapticLevel.Confirm); vm.openPay() }, height = 44.dp, textStyle = TzType.button.copy(fontSize = 16.sp))
            }
        }
        Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (v.paidCount > 0) Box(Modifier.weight(v.paidCount.toFloat()).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Tz.turquoise))
            if (v.people > v.paidCount) Box(Modifier.weight((v.people - v.paidCount).toFloat()).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Tz.line))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.gameya_paid_count, v.paidCount.toString(), v.people.toString()))
                    if (unpaid.isNotEmpty()) {
                        append(" " + stringResource(R.string.gameya_still) + " ")
                        withStyle(SpanStyle(color = Tz.text, fontWeight = FontWeight.SemiBold)) { append(unpaid.joinToString("، ")) }
                    }
                },
                style = TzType.caption.copy(fontSize = 13.sp, lineHeight = 22.sp), color = Tz.text2, modifier = Modifier.weight(1f),
            )
            if (v.unpaidMembers.isNotEmpty()) {
                val shape = RoundedCornerShape(22.dp)
                Box(
                    Modifier.height(44.dp).clip(shape).border(1.dp, Tz.yellow, shape)
                        .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); share(remindText, remindTitle) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.gameya_remind), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.yellow) }
            }
        }
        Text(stringResource(R.string.gameya_tick_hint), style = TzType.caption, color = Tz.text2)
        ChipRow {
            val paid = v.paidMembers.map { it.id }.toSet()
            (v.paidMembers + v.unpaidMembers).sortedBy { it.turn }.forEach { m ->
                TzChip(m.name, m.id in paid, { vm.togglePaid(m.id) }, style = ChipStyle.Filter)
            }
        }
    }
}

/** "الـ 20,000 هيروحوا فين": a bar and the rows (tap one to edit), then "ضيف بند". */
@Composable
private fun PlanCard(v: GameyaView, vm: GameyaViewModel) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Tz.surface).border(1.dp, Tz.line, CardShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.gameya_plan_title, formatPounds(v.payout)), style = TzType.cardHeading, color = Tz.text)
        if (v.plan.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                v.plan.forEachIndexed { i, row ->
                    Box(Modifier.weight(row.amount.toFloat().coerceAtLeast(1f)).height(14.dp).clip(RoundedCornerShape(7.dp)).background(PlanColors[i % PlanColors.size]))
                }
                if (v.unplanned > 0) Box(Modifier.weight(v.unplanned.toFloat()).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Tz.line))
            }
            v.plan.forEachIndexed { i, row ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); vm.openPlan(row) }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(PlanColors[i % PlanColors.size]))
                    Text(row.label, style = TzType.body.copy(fontSize = 14.sp), color = Tz.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatPounds(row.amount), style = TzType.rowNumber.copy(fontSize = 18.sp), color = Tz.text)
                }
            }
            if (v.unplanned > 0) Text(stringResource(R.string.gameya_plan_left, formatPounds(v.unplanned)), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        }
        Text(stringResource(R.string.gameya_plan_hint), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
        TextLink(stringResource(R.string.gameya_plan_add), { vm.openPlan() })
    }
}

@Composable
private fun GameyaSheets(ui: GameyaUi, v: GameyaView, vm: GameyaViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    when (val s = ui.sheet) {
        null -> Unit
        GameyaSheet.Pay -> {
            val haptics = LocalHaptics.current
            var pocket by remember { mutableStateOf(ui.pockets.firstOrNull()?.id) }
            TzSheet(vm::dismissSheet) {
                Column(SheetPad.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.gameya_pay_title, v.thisMonth.label(locale)), style = TzType.cardHeading, color = Tz.text)
                    PocketPicker(ui.pockets, pocket, false) { pocket = it }
                    PrimaryButton(
                        stringResource(R.string.gameya_pay_confirm), { haptics.perform(HapticLevel.Confirm); pocket?.let(vm::payMine) },
                        Modifier.fillMaxWidth(), enabled = pocket != null, height = 52.dp,
                    )
                }
            }
        }
        GameyaSheet.Swap -> TzSheet(vm::dismissSheet) {
            val haptics = LocalHaptics.current
            Column(SheetPad.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.gameya_swap_title), style = TzType.cardHeading, color = Tz.text)
                v.wheel.filter { !it.isMe && it.state != SlotState.PAID_OUT }.forEach { slot ->
                    val m = checkNotNull(slot.member)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tz.raised)
                            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Confirm); vm.swapWith(m.id) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.gameya_swap_row, m.name, slot.turn.toString()), style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
                    }
                }
            }
        }
        is GameyaSheet.Plan -> PlanSheet(s.editing, vm)
    }
}

@Composable
private fun PlanSheet(editing: GameyaPlanEntity?, vm: GameyaViewModel) {
    val haptics = LocalHaptics.current
    var label by remember { mutableStateOf(editing?.label.orEmpty()) }
    var input by remember { mutableStateOf(editing?.let { AmountInput.of(it.amount) } ?: AmountInput()) }
    val amount = input.toPiastres()
    TzSheet(vm::dismissSheet) {
        Column(SheetPad.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (editing == null) R.string.gameya_plan_new else R.string.gameya_plan_edit), style = TzType.cardHeading, color = Tz.text)
            NameField(label, { label = it.take(LABEL_MAX) }, R.string.gameya_plan_label_hint)
            AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            PrimaryButton(
                stringResource(R.string.gameya_save), { haptics.perform(HapticLevel.Confirm); vm.savePlan(editing, label, amount) },
                Modifier.fillMaxWidth(), enabled = amount > 0 && label.isNotBlank(), height = 52.dp,
            )
            if (editing != null) SecondaryButton(stringResource(R.string.gameya_plan_delete), { haptics.perform(HapticLevel.Heavy); vm.deletePlan(editing.id) }, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}
