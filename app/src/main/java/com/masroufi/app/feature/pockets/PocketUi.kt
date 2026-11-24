package com.masroufi.app.feature.pockets

import com.masroufi.app.ui.components.skeletonPulse
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.RowSkeleton
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import com.masroufi.app.R
import com.masroufi.app.core.formatDayDate
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.add.Keypad
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.LocalDate

/** The stored colour as a Compose colour; a pocket made before colours existed (alpha 0) falls back to turquoise. */
internal fun pocketColor(argb: Int): Color = if (argb ushr 24 == 0) Tz.turquoise else Color(argb)

/** "VF" for a Latin name (its first two letters); null for anything else, which gets the kind icon. */
internal fun pocketInitials(name: String): String? {
    val letters = name.filter { it.isLetter() }
    return if (letters.length >= 2 && letters.all { it in 'A'..'Z' || it in 'a'..'z' }) letters.take(2).uppercase() else null
}

internal fun PocketKind.icon(): TzIconDef = when (this) {
    PocketKind.CASH -> TzIcons.withdraw
    PocketKind.MOBILE_WALLET -> TzIcons.wallet
    PocketKind.BANK -> TzIcons.bank
    PocketKind.UNDER_TILE, PocketKind.VAULT -> TzIcons.grid
}

/** "النهارده" / "امبارح" / "الخميس 12 نوفمبر". */
@Composable
internal fun dayWord(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.pocket_day_today)
    today.minusDays(1) -> stringResource(R.string.pocket_day_yesterday)
    else -> formatDayDate(date, LocalConfiguration.current.locales[0])
}

/** The caption under a pocket: when it last moved. */
@Composable
internal fun lastMoveCaption(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> stringResource(R.string.pockets_last_none)
    today -> stringResource(R.string.pockets_last_today)
    today.minusDays(1) -> stringResource(R.string.pockets_last_yesterday)
    else -> stringResource(R.string.pockets_last_date, formatDayDate(date, LocalConfiguration.current.locales[0]))
}

/** 44 dp round button of a pushed screen's header (back, add). */
@Composable
internal fun CircleButton(icon: TzIconDef, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.pressScale(source, PressScale.Chip).minimumInteractiveComponentSize()
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Tz.surface).border(1.dp, Tz.line, CircleShape), contentAlignment = Alignment.Center) {
            TzIcon(icon, 20.dp, description, tint = Tz.text)
        }
    }
}

/** Back button, Lalezar 22 title, an optional round button (or a 44 dp gap) at the other end. */
@Composable
internal fun PushedHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = { Spacer(Modifier.size(48.dp)) }) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        CircleButton(TzIcons.back, stringResource(R.string.close_back), onBack)
        Text(title, style = TzType.titlePushed, color = Tz.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp))
        trailing()
    }
}

/** 44 dp tile in the pocket's colour: Latin initials, else the kind icon. */
@Composable
internal fun PocketTile(name: String, kind: PocketKind, color: Int) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(pocketColor(color)), contentAlignment = Alignment.Center) {
        val initials = pocketInitials(name)
        if (initials != null) Text(initials, style = TzType.button.copy(fontSize = 16.sp), color = Tz.night)
        else TzIcon(kind.icon(), 22.dp, null, tint = Tz.night)
    }
}

/** A 1.5 dp dashed rounded row with a yellow plus and label. */
@Composable
internal fun DashedRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }
            .drawBehind {
                drawRoundRect(
                    Tz.dashed, cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TzIcon(TzIcons.add, 20.dp, null, tint = Tz.yellow)
        Spacer(Modifier.width(8.dp))
        Text(text, style = TzType.label.copy(fontSize = 15.sp), color = Tz.yellow)
    }
}

/** 200 / 100 / 50 steppers and the change field, on Night chips (the cash card and a cash pocket's screen). */
@Composable
internal fun NoteCounter(counter: CashCounter, onStep: (denomination: Int, delta: Int) -> Unit, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val chip = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(200 to counter.n200, 100 to counter.n100, 50 to counter.n50).forEach { (denomination, count) ->
            Column(Modifier.weight(1f).clip(chip).background(Tz.night), horizontalAlignment = Alignment.CenterHorizontally) {
                Stepper(TzIcons.add, stringResource(R.string.pockets_counter_plus, denomination.toString())) { onStep(denomination, 1) }
                Text(denomination.toString(), style = TzType.cardHeading.copy(fontSize = 18.sp), color = Tz.yellow)
                Text("× $count", style = TzType.caption.copy(fontSize = 11.sp), color = Tz.yellow)
                Stepper(TzIcons.minus, stringResource(R.string.pockets_counter_minus, denomination.toString())) { onStep(denomination, -1) }
            }
        }
        Column(Modifier.weight(1f).clip(chip).background(Tz.night), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.pockets_counter_change), style = TzType.cardHeading.copy(fontSize = 18.sp), color = Tz.yellow)
            BasicTextField(
                value = counter.change.text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TzType.caption.copy(fontSize = 13.sp, color = Tz.yellow, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(Tz.yellow),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (counter.change.text.isEmpty()) Text("0", style = TzType.caption.copy(fontSize = 13.sp), color = Tz.yellow.copy(alpha = 0.5f))
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun Stepper(icon: TzIconDef, description: String, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button) { haptics.perform(HapticLevel.Tick); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        TzIcon(icon, 16.dp, description, tint = Tz.yellow)
    }
}

/** A sheet with a keypad for one amount (limit, threshold, counted balance); [onDone] gets piastres. */
@Composable
internal fun AmountSheet(title: String, initial: Long?, confirm: String, onDone: (Long) -> Unit, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    var input by remember { mutableStateOf(AmountInput.of(initial ?: 0)) }
    TzSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = TzType.cardHeading, color = Tz.text)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(input.text.ifEmpty { "0" }, style = TzType.hero, color = Tz.text)
                Text(stringResource(R.string.paper_currency), style = TzType.titlePushed, color = Tz.text2, modifier = Modifier.padding(bottom = 8.dp))
            }
            Keypad({ input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            PrimaryButton(confirm, { haptics.perform(HapticLevel.Confirm); onDone(input.toPiastres()) }, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}

/** Rows of the shape the screen will have, shown only when the load takes over 150 ms (10-PERFORMANCE). */
@Composable
internal fun LoadingRows() {
    var due by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        due = true
    }
    if (due) {
        Column(Modifier.skeletonPulse(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TzCard {
                RowSkeleton(0.5f, 0.3f, 44.dp)
                Spacer(Modifier.height(14.dp))
                RowSkeleton(0.4f, 0.4f, 44.dp)
            }
        }
    }
}
