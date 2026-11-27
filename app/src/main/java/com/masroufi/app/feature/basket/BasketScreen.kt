@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.masroufi.app.feature.basket

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.data.repo.HabitFacts
import com.masroufi.app.feature.pockets.DashedRow
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.pluralText
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import com.masroufi.app.feature.plan.SplitRow
import com.masroufi.app.feature.plan.TwoUp
import com.masroufi.app.feature.pockets.LoadingRows
import com.masroufi.app.ui.components.CappedFontScale
import com.masroufi.app.ui.motion.pressClickable
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import java.time.Month
import java.time.format.TextStyle

/** سلّتك (Basket.dc.html), a pushed screen; Plus. */
@Composable
fun BasketScreen(viewModel: BasketViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    BasketContent(ui, viewModel, onBack)
}

/** The screen with its state and actions passed in, so a test can render it (no behaviour of its own). */
@Composable
fun BasketContent(ui: BasketUi, viewModel: BasketActions, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.basket_title), onBack)
        when {
            ui.loading -> LoadingRows()
            ui.locked -> PlusLocked(R.string.basket_locked_what, PlusFeature.BASKET) { Sample() }
            ui.empty -> {
                EmptyState(
                    { Qersh(QershMood.Sleepy, 96.dp) }, R.string.empty_basket_title, R.string.empty_basket_line,
                    R.string.empty_basket_action, viewModel::openAdd, primaryAction = true,
                )
                if (ui.hasHabits) Habits(ui.habits)
            }
            else -> Body(ui, viewModel)
        }
    }
    if (ui.addSheet) AddItemSheet(viewModel::dismissAdd, viewModel::track)
}

@Composable
private fun Body(ui: BasketUi, vm: BasketActions) {
    ui.hero?.let { Hero(it) }
    ItemsCard(ui.items)
    DashedRow(stringResource(R.string.basket_add), vm::openAdd)
    val suggestion = ui.suggestion
    val raised = ui.raisedTo
    if (suggestion != null) Suggestion(suggestion.current, suggestion.suggested, vm::raise, vm::leave)
    else if (raised != null) Raised(raised)
    if (ui.hasHabits) Habits(ui.habits)
}

/** Blurred stand-in behind the Plus card; no real numbers of the user are behind it. */
@Composable
private fun Sample() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero(HeroUi(9, 148_000, 161_200, null, Trend.UP))
        ItemsCard(listOf(BasketItemUi(1, "—", 2_200, 47, null), BasketItemUi(2, "—", 8_500, 9, null), BasketItemUi(3, "—", 5_000, 0, null)))
    }
}

@Composable
private fun Hero(h: HeroUi) {
    val shape = RoundedCornerShape(TzRadius.hero)
    val color = when (h.trend) {
        Trend.UP -> Tz.carnelianText
        Trend.DOWN -> Tz.turquoise
        Trend.SAME -> Tz.text
    }
    val big = when (h.trend) {
        Trend.UP -> "+${h.percent}%"
        Trend.DOWN -> "${h.percent}%"
        Trend.SAME -> "0%"
    }
    val word = when (h.trend) {
        Trend.UP -> R.string.basket_hero_up
        Trend.DOWN -> R.string.basket_hero_down
        Trend.SAME -> R.string.basket_hero_same
    }
    val locale = LocalConfiguration.current.locales[0]
    val then = poundsText(h.then)
    val now = poundsText(h.now)
    val line = if (h.sinceMonth != null) {
        stringResource(R.string.basket_hero_line, then, Month.of(h.sinceMonth).getDisplayName(TextStyle.FULL_STANDALONE, locale), now)
    } else {
        stringResource(R.string.basket_hero_line_nomonth, then, now)
    }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.basket_hero_label), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2)
        FlowRow(itemVerticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // shortcut: the 72 sp hero number keeps its size at a large system font (it would not fit the card); revisit with a layout that can grow.
            CappedFontScale { Text(big, style = TzType.hero.copy(fontSize = 72.sp, lineHeight = 72.sp), color = color) }
            Text(stringResource(word), style = TzType.titlePushed, color = Tz.text, modifier = Modifier.padding(bottom = 10.dp))
        }
        Text(
            line + " " + stringResource(R.string.basket_hero_honest),
            style = TzType.caption.copy(fontSize = 13.sp, lineHeight = 22.sp), color = Tz.text2,
        )
    }
}

@Composable
private fun ItemsCard(items: List<BasketItemUi>) {
    val shape = RoundedCornerShape(TzRadius.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)) {
        items.forEachIndexed { i, item ->
            ItemRow(item)
            if (i < items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
        }
    }
}

@Composable
private fun ItemRow(item: BasketItemUi) {
    SplitRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        info = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
                val line = lineText(item)
                if (line != null) Text(line, style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
            }
        },
    ) {
        val price = item.lastPrice
        if (price != null) Text(poundsText(price), style = TzType.rowNumber.copy(fontSize = 22.sp), color = Tz.text)
        item.changePercent?.let { Badge(it) }
    }
}

@Composable
private fun lineText(item: BasketItemUi): String? = when (val l = item.line) {
    is ItemLine.LastPlace -> {
        val was = l.was
        if (was != null) stringResource(R.string.basket_line_place_was, l.place, poundsText(was)) else stringResource(R.string.basket_line_place, l.place)
    }
    is ItemLine.Cheapest -> stringResource(R.string.basket_line_cheapest, l.place, poundsText(l.price))
    is ItemLine.Times -> stringResource(R.string.basket_line_times, pluralText(R.plurals.basket_times_count, l.perMonth))
    ItemLine.Fell -> stringResource(R.string.basket_line_fell)
    ItemLine.Flat -> stringResource(R.string.basket_line_flat)
    null -> if (item.lastPrice == null) stringResource(R.string.basket_line_waiting) else null
}

@Composable
private fun Badge(percent: Int) {
    val same = stringResource(R.string.basket_same)
    val (bg, fg, text) = when {
        percent > 0 -> Triple(Tz.carnelianDim, Tz.carnelianText, "+$percent%")
        percent < 0 -> Triple(Tz.turquoiseDim, Tz.turquoise, "$percent%")
        else -> Triple(Tz.raised, Tz.text2, same)
    }
    Box(Modifier.widthIn(min = 64.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TzType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), color = fg)
    }
}

@Composable
private fun Suggestion(current: Long, suggested: Long, onRaise: () -> Unit, onLeave: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(TzRadius.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.yellowDim).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.basket_sugg_title), style = TzType.cardHeading, color = Tz.yellow)
        Text(
            stringResource(R.string.basket_sugg_body, poundsText(suggested), poundsText(current)),
            style = TzType.caption.copy(fontSize = 13.sp, lineHeight = 22.sp), color = Tz.qershOnYellow,
        )
        Box(Modifier.padding(top = 4.dp)) {
            TwoUp(
                { m ->
                    PrimaryButton(
                        stringResource(R.string.basket_sugg_raise), { haptics.perform(HapticLevel.Confirm); onRaise() },
                        m, height = 48.dp, textStyle = TzType.button.copy(fontSize = 16.sp),
                    )
                },
                { m ->
                    val pill = RoundedCornerShape(percent = 50)
                    Box(
                        m.pressClickable { haptics.perform(HapticLevel.Tap); onLeave() }.heightIn(min = 48.dp).clip(pill).border(1.5.dp, Tz.yellow, pill).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.basket_sugg_leave), style = TzType.button.copy(fontSize = 16.sp), color = Tz.yellow) }
                },
            )
        }
    }
}

@Composable
private fun Raised(amount: Long) {
    val shape = RoundedCornerShape(TzRadius.card)
    Box(Modifier.fillMaxWidth().clip(shape).background(Tz.turquoiseDim).padding(16.dp)) {
        Text(stringResource(R.string.basket_raised, poundsText(amount)), style = TzType.body.copy(fontSize = 14.sp), color = Tz.turquoiseDimText)
    }
}

@Composable
private fun Habits(h: HabitFacts) {
    val shape = RoundedCornerShape(TzRadius.card)
    val locale = LocalConfiguration.current.locales[0]
    val facts = ArrayList<Triple<String, Color, String>>()
    h.weekday?.let {
        facts += Triple(
            it.day.getDisplayName(TextStyle.FULL_STANDALONE, locale), Tz.yellow,
            stringResource(R.string.basket_habit_weekday, poundsText(it.average), poundsText(it.restAverage)),
        )
    }
    h.lateNight?.let {
        facts += Triple(
            stringResource(R.string.basket_habit_late_tag), Tz.turquoise,
            stringResource(R.string.basket_habit_late, pluralText(R.plurals.basket_times_count, it.count), poundsText(it.sum)),
        )
    }
    h.cashVsWalletPercent?.takeIf { it != 0 }?.let {
        val text = if (it < 0) stringResource(R.string.basket_habit_cash_less, (-it).toString()) else stringResource(R.string.basket_habit_cash_more, it.toString())
        facts += Triple(stringResource(R.string.basket_habit_cash_tag), Tz.lilac, text)
    }
    if (facts.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.basket_habits_title), style = TzType.cardHeading, color = Tz.text)
        facts.forEachIndexed { i, (tag, color, text) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tag, Modifier.widthIn(min = 52.dp), style = TzType.rowNumber.copy(fontSize = 22.sp), color = color)
                Text(text, style = TzType.caption.copy(fontSize = 13.sp, lineHeight = 21.sp), color = Tz.text2, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "تابع سعر حاجة تانية": an item name; it is tracked at once, prices come from later entries. */
@Composable
internal fun AddItemSheet(onDismiss: () -> Unit, onTrack: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(16.dp)
    val haptics = LocalHaptics.current
    TzSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.basket_add_title), style = TzType.cardHeading, color = Tz.text)
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(Tz.night).border(1.dp, Tz.line, shape),
                textStyle = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tz.text),
                singleLine = true,
                cursorBrush = SolidColor(Tz.yellow),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onTrack(name) }),
                decorationBox = { inner ->
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) Text(stringResource(R.string.basket_add_hint), style = TzType.body, color = Tz.text3)
                        inner()
                    }
                },
            )
            PrimaryButton(
                stringResource(R.string.basket_add_go), { haptics.perform(HapticLevel.Confirm); onTrack(name) },
                Modifier.fillMaxWidth(), enabled = name.isNotBlank(), height = 52.dp,
            )
        }
    }
}
