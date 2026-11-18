package com.masroufi.app.feature.buy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.core.PIASTRES
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.WishView
import com.masroufi.app.data.db.WishAnswer
import com.masroufi.app.domain.BeforeBuy
import com.masroufi.app.domain.Installment
import com.masroufi.app.domain.Tenths
import com.masroufi.app.domain.gramsText
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.add.Keypad
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.pluralText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.launch

/** "1800" -> "1,800", "12.5" -> "12.5"; empty is "0". */
internal fun typedText(input: AmountInput): String {
    val whole = input.text.substringBefore('.').ifEmpty { "0" }
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return if (input.hasPoint) grouped + "." + input.text.substringAfter('.') else grouped
}

/**
 * قبل ما تشتري (BeforeBuy.dc.html): what you want and its price (typed on the app's keypad), what that equals, cash
 * against installments (Plus), Qersh's 48 hours, and the "نِفسي فيه" list.
 */
@Composable
fun BeforeBuyScreen(viewModel: BeforeBuyViewModel, onBack: () -> Unit, onOpenAddSheet: (AddPrefill?) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val ask by viewModel.ask.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val reduce = LocalReduceMotion.current
    val openSheet by rememberUpdatedState(onOpenAddSheet)
    LaunchedEffect(viewModel) { viewModel.prefill.collect { openSheet(it) } }

    val focus = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 05-MOTION "Parking an item for 48 h": the item's cards shrink away as the list gains its row.
    val shrink = remember { Animatable(1f) }
    LaunchedEffect(ui.parked) {
        if (ui.parked > 0 && !reduce) {
            shrink.animateTo(0.92f, TzSpring.Move.spec())
            shrink.animateTo(1f, TzSpring.Move.spec())
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PushedHeader(stringResource(R.string.buy_title), onBack)
            Column(
                Modifier.graphicsLayer { scaleX = shrink.value; scaleY = shrink.value; alpha = 0.4f + 0.6f * ((shrink.value - 0.92f) / 0.08f).coerceIn(0f, 1f) },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                WantCard(
                    ui, nameFocus,
                    onName = viewModel::setName,
                    onNameFocused = { viewModel.focus(null) },
                    onPriceTap = { focus.clearFocus(); viewModel.focus(BuyField.PRICE) },
                    onDone = { focus.clearFocus(); viewModel.focus(BuyField.PRICE) },
                )
                ui.result?.let { r -> if (ui.tiles.isNotEmpty()) Tiles(r, ui.tiles) }
                if (ui.priceValue > 0) {
                    InstallmentCard(ui, viewModel::focus)
                }
                ParkCard(enabled = ui.canPark, onPark = { if (viewModel.park()) haptics.perform(HapticLevel.Confirm) })
            }
            WishList(
                ui, reduce,
                onChangedMind = { viewModel.changedMind(it.wish) },
                onBought = { viewModel.bought(it.wish.id) },
                onWrite = {
                    scope.launch { scroll.animateScrollTo(0) }
                    nameFocus.requestFocus()
                },
            )
        }
        if (ui.field != null) {
            Box(Modifier.fillMaxWidth().background(Tz.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Keypad(viewModel::digit, viewModel::point, viewModel::backspace)
            }
        }
    }
    ask?.let { JarAskSheet(it, onAnswer = viewModel::jarAnswer, onDismiss = viewModel::askDismissed) }
}

@Composable
private fun WantCard(
    ui: BeforeBuyUi,
    nameFocus: FocusRequester,
    onName: (String) -> Unit,
    onNameFocused: () -> Unit,
    onPriceTap: () -> Unit,
    onDone: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val label = stringResource(R.string.buy_want_label)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = TzType.label.copy(fontSize = 13.sp), color = Tz.text2)
        val fieldShape = RoundedCornerShape(16.dp)
        BasicTextField(
            value = ui.name,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(fieldShape).background(Tz.night).border(1.dp, Tz.line, fieldShape)
                .focusRequester(nameFocus).onFocusChanged { if (it.isFocused) onNameFocused() }.semantics { contentDescription = label },
            textStyle = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tz.text),
            singleLine = true,
            cursorBrush = SolidColor(Tz.yellow),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (ui.name.isEmpty()) Text(stringResource(R.string.buy_name_hint), style = TzType.body, color = Tz.text3)
                    inner()
                }
            },
        )
        val focused = ui.field == BuyField.PRICE
        val ring = RoundedCornerShape(16.dp)
        Row(
            Modifier.clip(ring).border(1.5.dp, if (focused) Tz.yellow else Color.Transparent, ring)
                .clickable(role = Role.Button, onClick = onPriceTap).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                typedText(ui.price),
                style = TzType.hero.copy(fontSize = 68.sp, lineHeight = 68.sp),
                color = if (ui.price.text.isEmpty()) Tz.text3 else Tz.yellow,
            )
            Text(stringResource(R.string.paper_currency), style = TzType.titlePushed, color = Tz.text2, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun Tiles(r: BeforeBuy, tiles: List<BuyTile>) {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.buy_equals), style = TzType.cardHeading, color = Tz.text, modifier = Modifier.padding(horizontal = 4.dp))
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { i, tile ->
                    val first = row == 0 && i == 0
                    val (big, caption) = when (tile) {
                        BuyTile.TICKETS -> r.tickets!!.let { (if (arabic) it.spoken() else it.plain()) } to stringResource(R.string.buy_tickets_caption)
                        BuyTile.WORK_DAYS -> workDaysText(r.workDays!!) to stringResource(R.string.buy_work_caption)
                        BuyTile.GOLD -> gramsText(r.goldGramsHundredths!!) to stringResource(R.string.buy_gold_caption)
                        BuyTile.SEASON -> "${r.seasonSharePercent}%" to
                            stringResource(R.string.buy_season_caption, r.seasonName ?: stringResource(R.string.buy_season_fallback))
                    }
                    val color = when {
                        first -> Tz.night
                        tile == BuyTile.GOLD -> Tz.turquoise
                        tile == BuyTile.SEASON -> Tz.carnelianText
                        else -> Tz.text
                    }
                    TileBox(big, caption, color, first, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun workDaysText(days: Tenths): String = when {
    days.value == 10L -> stringResource(R.string.buy_work_one)
    days.value == 20L -> stringResource(R.string.buy_work_two)
    days.value % 10 == 0L && days.value / 10 in 3..10 -> stringResource(R.string.buy_work_few, days.plain())
    else -> stringResource(R.string.buy_work_other, days.plain())
}

@Composable
private fun TileBox(big: String, caption: String, numberColor: Color, highlighted: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape).then(if (highlighted) Modifier.background(Tz.yellow) else Modifier.background(Tz.surface).border(1.dp, Tz.line, shape))
            .padding(14.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(big, style = TzType.card.copy(fontSize = 34.sp, lineHeight = 1.05.em), color = numberColor)
        Text(
            caption,
            style = TzType.caption.copy(fontSize = 12.sp, fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal),
            color = if (highlighted) Tz.night else Tz.text2,
        )
    }
}

@Composable
private fun InstallmentCard(ui: BeforeBuyUi, onFocus: (BuyField?) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.buy_inst_title), style = TzType.cardHeading, color = Tz.text)
        if (ui.plus) InstallmentBody(ui, onFocus)
        else PlusLocked(R.string.buy_inst_locked, PlusFeature.INSTALLMENT_COMPARE) { InstallmentBody(ui, onFocus) }
    }
}

@Composable
private fun InstallmentBody(ui: BeforeBuyUi, onFocus: (BuyField?) -> Unit) {
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(R.string.buy_inst_monthly, typedText(ui.monthly), ui.monthly.text.isEmpty(), ui.field == BuyField.MONTHLY, Modifier.weight(1f)) {
                focus.clearFocus(); onFocus(BuyField.MONTHLY)
            }
            NumberField(R.string.buy_inst_months, typedText(ui.months), ui.months.text.isEmpty(), ui.field == BuyField.MONTHS, Modifier.weight(1f)) {
                focus.clearFocus(); onFocus(BuyField.MONTHS)
            }
        }
        val inst = ui.installment
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CashBox(ui.priceValue, ui.result?.cashDays, Modifier.weight(1f))
            if (inst != null) InstallmentBox(ui.monthly.toPiastres(), inst, Modifier.weight(1f))
        }
        Text(stringResource(R.string.buy_inst_note), style = TzType.caption.copy(fontSize = 12.sp, lineHeight = 1.7.em), color = Tz.text2)
    }
}

@Composable
private fun NumberField(label: Int, value: String, empty: Boolean, focused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(Tz.night).border(if (focused) 1.5.dp else 1.dp, if (focused) Tz.yellow else Tz.line, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(label), style = TzType.caption.copy(fontSize = 11.sp), color = Tz.text2)
        Text(value, style = TzType.rowNumber.copy(fontSize = 22.sp), color = if (empty) Tz.text3 else Tz.text)
    }
}

@Composable
private fun CashBox(price: Long, days: Int?, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(Tz.turquoiseDim).border(2.dp, Tz.turquoise, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.buy_inst_cash), style = TzType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.turquoise)
        Text(formatPounds(price), style = TzType.card.copy(fontSize = 28.sp, lineHeight = 1.1.em), color = Tz.text)
        if (days != null) {
            Text(pluralText(R.plurals.buy_cash_days, days), style = TzType.caption.copy(fontSize = 11.sp, lineHeight = 1.5.em), color = Tz.turquoiseDimText)
        }
    }
}

@Composable
private fun InstallmentBox(monthly: Long, inst: Installment, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    val perDay = formatPounds(inst.perDay.coerceAtLeast(PIASTRES))
    val forHowLong = if (inst.months == 12) stringResource(R.string.buy_inst_year) else pluralText(R.plurals.buy_months, inst.months)
    Column(
        modifier.clip(shape).background(Tz.night).border(1.dp, Tz.line, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.buy_inst_label, pluralText(R.plurals.buy_months, inst.months), formatPounds(monthly)),
            style = TzType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = Tz.text2,
        )
        Text(formatPounds(inst.total), style = TzType.card.copy(fontSize = 28.sp, lineHeight = 1.1.em), color = Tz.text)
        Text(
            if (inst.extra > 0) stringResource(R.string.buy_inst_effect, formatPounds(inst.extra), perDay, forHowLong)
            else stringResource(R.string.buy_inst_effect_free, perDay, forHowLong),
            style = TzType.caption.copy(fontSize = 11.sp, lineHeight = 1.5.em), color = Tz.carnelianText,
        )
    }
}

@Composable
private fun ParkCard(enabled: Boolean, onPark: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val line: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Tz.yellow)) { append(stringResource(R.string.buy_qersh_bold)) }
        withStyle(SpanStyle(color = Color(0xFFF0E3B5))) { append(stringResource(R.string.buy_qersh_rest)) }
    }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.yellowDim).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Qersh(QershMood.Happy, 52.dp)
            Text(line, style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.7.em), modifier = Modifier.weight(1f))
        }
        PrimaryButton(
            stringResource(R.string.buy_park), onPark, Modifier.fillMaxWidth(), enabled = enabled, height = 52.dp,
            textStyle = TzType.button.copy(fontSize = 18.sp),
        )
    }
}

/** What the list's rows looked like when it first showed: only rows added later play the "gains a row" motion. */
private class Baseline { var ids: Set<Long>? = null }

@Composable
private fun WishList(
    ui: BeforeBuyUi,
    reduce: Boolean,
    onChangedMind: (WishView) -> Unit,
    onBought: (WishView) -> Unit,
    onWrite: () -> Unit,
) {
    if (ui.loading) return
    val baseline = remember { Baseline() }
    if (baseline.ids == null) baseline.ids = ui.wishes.map { it.wish.id }.toSet()
    if (ui.wishes.isEmpty()) {
        EmptyState({ Qersh(QershMood.Thinking, 96.dp) }, R.string.buy_empty_title, R.string.buy_empty_line, R.string.buy_empty_action, onWrite)
        return
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.buy_list_title), style = TzType.cardHeading, color = Tz.text)
            if (ui.changedMindTotal > 0) {
                Text(
                    stringResource(R.string.buy_wish_total, formatPounds(ui.changedMindTotal)),
                    style = TzType.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = Tz.turquoise,
                )
            }
        }
        ui.wishes.forEachIndexed { i, v ->
            key(v.wish.id) {
                val isNew = baseline.ids?.contains(v.wish.id) == false
                val visible = remember { MutableTransitionState(!isNew).apply { targetState = true } }
                val enter: EnterTransition = if (reduce) fadeIn(tween(120)) else fadeIn(tween(200)) + expandVertically() + scaleIn(initialScale = 0.85f)
                AnimatedVisibility(visible, enter = enter) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
                        WishRow(v, { onChangedMind(v) }, { onBought(v) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WishRow(v: WishView, onChangedMind: () -> Unit, onBought: () -> Unit) {
    val w = v.wish
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.buy_row_name, w.name, formatPounds(w.price)),
                    style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 1.4.em), color = Tz.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                when {
                    v.due -> Text(stringResource(R.string.buy_due), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.yellow)
                    w.answer == WishAnswer.WAITING ->
                        Text(pluralText(R.plurals.buy_hours_left, v.hoursLeft.toInt()), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
                    w.answer == WishAnswer.BOUGHT ->
                        Text(stringResource(R.string.buy_bought), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
                    else -> {
                        // Only what really moved: a capped answer shows the capped amount, never the price.
                        val moved = w.movedToJar
                        Text(
                            if (moved != null && moved > 0) stringResource(R.string.buy_row_changed_jar, formatPounds(moved)) else stringResource(R.string.buy_changed_mind),
                            style = TzType.caption.copy(fontSize = 12.sp), color = Tz.turquoise,
                        )
                    }
                }
            }
            if (w.answer == WishAnswer.WAITING && !v.due) {
                TzProgressBar(listOf(((WISH_HOURS_TOTAL - v.hoursLeft).toFloat() / WISH_HOURS_TOTAL) to Tz.yellow), Modifier.width(96.dp))
            }
        }
        if (v.due) WishButtons(onChangedMind, onBought)
    }
}

private const val WISH_HOURS_TOTAL = com.masroufi.app.data.repo.WISH_HOURS
