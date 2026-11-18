package com.masroufi.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.add.Keypad
import com.masroufi.app.feature.pockets.pocketColor
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.paper.EnvelopeCard
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** The envelope card with the texts of its honest [CardLine] (Envelopes.dc.html). */
@Composable
fun PlanEnvelopeCard(ui: EnvelopeCardUi, modifier: Modifier = Modifier, amountModifier: Modifier = Modifier) {
    val v = ui.view
    val l = ui.line
    val sealed = l is CardLine.Sealed
    val line = if (l is CardLine.Sealed) stringResource(R.string.env_sealed_amount, poundsText(l.amount), l.day.toString())
    else stringResource(R.string.env_left_of, poundsText(v.monthly))
    val status = when (l) {
        is CardLine.Sealed -> stringResource(R.string.env_sealed_note)
        is CardLine.RunsOut -> stringResource(R.string.env_runs_out, l.day.toString())
        CardLine.OnPace -> stringResource(R.string.env_on_pace)
        CardLine.BetterThanLast -> stringResource(R.string.env_better)
        null -> null
    }
    EnvelopeCard(v.name, pocketColor(v.color), v.left, v.bar, line, sealed, status, modifier = modifier, amountModifier = amountModifier)
}

/** One question of a pushed form: Surface card, Lalezar 20 question, the answer below. */
@Composable
fun FormBlock(question: Int, content: @Composable () -> Unit) {
    TzCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(question), style = TzType.cardHeading, color = Tz.text)
            content()
        }
    }
}

@Composable
fun NameField(value: String, onValue: (String) -> Unit, hint: Int) {
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TzType.body.copy(fontSize = 17.sp, color = Tz.text),
        cursorBrush = SolidColor(Tz.yellow),
        modifier = Modifier.fillMaxWidth().background(Tz.night, shape).border(1.dp, Tz.line, shape).padding(horizontal = 14.dp, vertical = 14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(stringResource(hint), style = TzType.body.copy(fontSize = 17.sp), color = Tz.text3)
            inner()
        },
    )
}

/** The typed amount as a hero number with the keypad under it. */
@Composable
fun AmountKeypad(input: AmountInput, onDigit: (Int) -> Unit, onPoint: () -> Unit, onBackspace: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(input.text.ifEmpty { "0" }, style = TzType.hero, color = Tz.text)
        Text(stringResource(R.string.paper_currency), style = TzType.titlePushed, color = Tz.text2, modifier = Modifier.padding(bottom = 8.dp))
    }
    Keypad(onDigit, onPoint, onBackspace)
}

/**
 * "انقل {amount}": pick both envelopes (preselected by a drop or the tip) and type the amount, capped at what the first
 * has left. [onConfirm] gets piastres.
 */
@Composable
fun MoveSheet(sheet: EnvelopeSheet.Move, cards: List<EnvelopeCardUi>, onConfirm: (from: Long, to: Long, amount: Long) -> Unit, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    var from by remember { mutableStateOf(sheet.fromId) }
    var to by remember { mutableStateOf(sheet.toId) }
    val cap = cards.firstOrNull { it.view.id == from }?.view?.left ?: 0L
    var input by remember { mutableStateOf(AmountInput.of(minOf(sheet.initial, cap))) }
    val amount = input.toPiastres()
    TzSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.env_move_title, input.text.ifEmpty { "0" }), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.env_move_from), style = TzType.caption, color = Tz.text2)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.filter { it.line !is CardLine.Sealed && it.view.left > 0 }.forEach {
                    TzChip(it.view.name, from == it.view.id, { from = it.view.id; if (to == it.view.id) to = null }, style = ChipStyle.Filter)
                }
            }
            Text(stringResource(R.string.env_move_to), style = TzType.caption, color = Tz.text2)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.filter { it.view.id != from }.forEach {
                    TzChip(it.view.name, to == it.view.id, { to = it.view.id }, style = ChipStyle.Filter)
                }
            }
            Keypad({ input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            PrimaryButton(
                stringResource(R.string.env_move_confirm),
                { haptics.perform(HapticLevel.Confirm); onConfirm(checkNotNull(from), checkNotNull(to), amount) },
                Modifier.fillMaxWidth(),
                enabled = from != null && to != null && amount in 1..cap,
                height = 52.dp,
            )
        }
    }
}

/** A row of chips that scrolls sideways instead of overflowing. */
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** A round colour swatch; the selected one carries a Text ring. */
@Composable
fun Swatch(color: androidx.compose.ui.graphics.Color, selected: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(color).then(if (selected) Modifier.border(3.dp, Tz.text, CircleShape) else Modifier)
            .semantics { contentDescription = description }
            .clickable(role = Role.RadioButton, onClick = onClick),
    )
}
