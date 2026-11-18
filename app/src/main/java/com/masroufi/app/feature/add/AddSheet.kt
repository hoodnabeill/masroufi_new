package com.masroufi.app.feature.add

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.Segmented
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzRow
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.TzToggle
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.launch

/** What the sheet's controls do; [AddViewModel] implements it, tests pass their own. */
interface AddSheetActions {
    fun setType(type: EntryKind)
    fun digit(d: Int)
    fun point()
    fun backspace()
    fun focusFee(on: Boolean)
    fun selectCategory(id: Long)
    fun selectPocket(id: Long)
    fun selectToPocket(id: Long)
    fun setNote(text: String)
    fun setRoundUp(on: Boolean)
    /** The optional item and unit price (spends only). */
    fun setItem(text: String) {}
    fun setUnitPrice(text: String) {}
    /** Edit mode's "امسحها". */
    fun delete() {}
}

private val Types = listOf(EntryKind.SPENT, EntryKind.RECEIVED, EntryKind.MOVED)
private val FieldShape = RoundedCornerShape(16.dp)
private val AmountStyle = TzType.hero.copy(fontSize = 84.sp, lineHeight = 1.em, color = Tz.yellow)

private enum class Slot { From, To }

/**
 * سجّل (AddPunch.dc.html). Opens with the keypad (no system keyboard for the amount). Save: Confirm haptic on the tap's
 * frame, the sheet drops away, and the ViewModel shows the punch on Home before storage has it.
 */
@Composable
fun AddSheet(prefill: AddPrefill?, onDismiss: () -> Unit, viewModel: AddViewModel = hiltViewModel()) {
    LaunchedEffect(viewModel, prefill) { viewModel.open(prefill) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val close = { scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() } }
    TzSheet(onDismiss = onDismiss, sheetState = sheetState) {
        AddSheetContent(
            state = state,
            actions = viewModel,
            onClose = { close() },
            onSave = {
                if (viewModel.save()) {
                    haptics.perform(HapticLevel.Confirm)
                    haptics.sound()
                    close()
                }
            },
        )
    }
}

/**
 * The sheet's body. On a short screen the middle (amount, chips, pockets, round-up) scrolls and the keypad and the
 * button stay in view.
 */
@Composable
fun ColumnScope.AddSheetContent(state: AddUiState, actions: AddSheetActions, onClose: () -> Unit, onSave: () -> Unit) {
    val f = state.form
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val amountPulse = remember { Animatable(1f) }
    val toPulse = remember { Animatable(1f) }
    // The missing field pulses once on a disabled tap (05-MOTION); reduced motion keeps only the Reject haptic.
    val pulseMissing: () -> Unit = {
        val target = if (state.amount > 0 && f.type == EntryKind.MOVED) toPulse else amountPulse
        if (!reduce) scope.launch {
            target.animateTo(1.06f, TzSpring.Snappy.spec())
            target.animateTo(1f, TzSpring.Snappy.spec())
        }
    }
    var picking by remember { mutableStateOf<Slot?>(null) }

    Column(
        Modifier.weight(1f, fill = false).padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(f.type, actions::setType, onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AmountBlock(state, amountPulse.value) { actions.focusFee(false) }
            if (f.type == EntryKind.SPENT && f.categories.isNotEmpty()) CategoryChips(f.categories, f.categoryId, actions::selectCategory)
            PocketFields(state, actions, toPulse.value) { picking = it }
            if (state.roundUpOffer > 0) RoundUpRow(state, actions::setRoundUp)
            if (f.type == EntryKind.SPENT) ItemRow(f.item, f.unitPrice, actions::setItem, actions::setUnitPrice)
        }
        Keypad(actions::digit, actions::point, actions::backspace)
        PrimaryButton(
            text = stringResource(
                when {
                    state.amount <= 0 -> R.string.add_amount_missing
                    f.edit != null -> R.string.add_save_edit
                    f.type == EntryKind.RECEIVED -> R.string.add_income
                    f.type == EntryKind.MOVED -> R.string.add_transfer
                    else -> R.string.add_punch
                },
            ),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
            height = 60.dp,
            textStyle = TzType.button.copy(fontSize = 22.sp),
            icon = TzIcons.punch.takeIf { f.type == EntryKind.SPENT && state.amount > 0 },
            onDisabledClick = { pulseMissing() },
        )
        if (f.edit != null) {
            val haptics = LocalHaptics.current
            TextLink(
                stringResource(R.string.add_delete),
                {
                    haptics.perform(HapticLevel.Tap)
                    actions.delete()
                    onClose()
                },
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    picking?.let { slot ->
        val selected = if (slot == Slot.From) f.pocketId else f.toPocketId
        val choices = if (slot == Slot.To) f.pockets.filter { it.id != f.pocketId } else f.pockets
        PocketPicker(choices, selected, onDismiss = { picking = null }) { id ->
            if (slot == Slot.From) actions.selectPocket(id) else actions.selectToPocket(id)
            picking = null
        }
    }
}

@Composable
private fun Header(type: EntryKind, onType: (EntryKind) -> Unit, onClose: () -> Unit) {
    val haptics = LocalHaptics.current
    val typesLabel = stringResource(R.string.add_types_label)
    val closeLabel = stringResource(R.string.add_close)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Segmented(
            options = listOf(
                stringResource(R.string.add_type_spent),
                stringResource(R.string.add_type_received),
                stringResource(R.string.add_type_moved),
            ),
            selected = Types.indexOf(type),
            onSelect = { onType(Types[it]) },
            modifier = Modifier.weight(1f).semantics { contentDescription = typesLabel },
            compact = true,
        )
        Box(
            Modifier.minimumInteractiveComponentSize()
                .clickable(role = Role.Button) {
                    haptics.perform(HapticLevel.Tap)
                    onClose()
                }
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Tz.raised), contentAlignment = Alignment.Center) {
                TzIcon(TzIcons.close, 18.dp, null, tint = Tz.text)
            }
        }
    }
}

@Composable
private fun AmountBlock(state: AddUiState, scale: Float, onTap: () -> Unit) {
    val f = state.form
    // A transfer's fee takes the keypad while it is focused; tapping the amount gives it back.
    val tap = if (f.type == EntryKind.MOVED && f.feeFocused) Modifier.clickable(interactionSource = null, indication = null, onClick = onTap) else Modifier
    Column(
        Modifier.fillMaxWidth().then(tap).padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Odometer(state.amount, AmountStyle, showPiastres = f.amount.hasPoint)
            // Lalezar's baseline sits about a sixth of the 84 sp line above its bottom.
            Text(
                stringResource(R.string.add_unit),
                style = TzType.button.copy(fontSize = 22.sp),
                color = Tz.text2,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text(liveLine(state), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2, maxLines = 1)
    }
}

/** The line under the amount; empty (but still one line tall) when there is nothing to say. */
@Composable
private fun liveLine(state: AddUiState): AnnotatedString = when (state.form.type) {
    EntryKind.SPENT -> state.leftAfter?.let { left ->
        val n = formatPounds(left.coerceAtLeast(0))
        highlighted(stringResource(R.string.add_left, n), n, SpanStyle(color = Tz.text, fontWeight = FontWeight.SemiBold))
    }
    EntryKind.RECEIVED -> AnnotatedString(stringResource(R.string.add_income_line))
    EntryKind.MOVED -> {
        val from = state.pocket
        val to = state.toPocket
        if (from != null && to != null && state.transferValid) AnnotatedString(stringResource(R.string.add_transfer_line, from.name, to.name)) else null
    }
    EntryKind.ADJUST, EntryKind.DEBT -> null
} ?: AnnotatedString("")

/** [line] with the last [part] in [style]. */
private fun highlighted(line: String, part: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(line)
    val at = line.lastIndexOf(part)
    if (at >= 0) addStyle(style, at, at + part.length)
}

@Composable
private fun CategoryChips(categories: List<CategoryEntity>, selected: Long?, onSelect: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { c -> CategoryChip(c, c.id == selected) { onSelect(c.id) } }
    }
}

/** AddPunch.dc.html chip: 48 dp, icon + name; selected = 2 dp turquoise border on TurquoiseDim. */
@Composable
private fun CategoryChip(c: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.pressScale(source, PressScale.Chip, release = TzSpring.Snappy)
            .height(48.dp)
            .clip(shape)
            .background(if (selected) Tz.turquoiseDim else Tz.raised)
            .border(if (selected) 2.dp else 1.dp, if (selected) Tz.turquoise else Tz.line, shape)
            .selectable(selected = selected, interactionSource = source, indication = null, role = Role.RadioButton) {
                haptics.perform(HapticLevel.Tick)
                onClick()
            }
            .padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TzIcon(TzIcons.all.firstOrNull { it.name == c.icon } ?: TzIcons.grid, 18.dp, null, tint = if (selected) Tz.turquoise else Tz.text2)
        Text(
            c.name,
            style = TzType.label.copy(fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = Tz.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun PocketFields(state: AddUiState, actions: AddSheetActions, toScale: Float, onPick: (Slot) -> Unit) {
    val f = state.form
    if (f.type == EntryKind.MOVED) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketButton(stringResource(R.string.add_from), state.pocket, Modifier.weight(1f)) { onPick(Slot.From) }
                PocketButton(
                    stringResource(R.string.add_to),
                    state.toPocket,
                    Modifier.weight(1f).graphicsLayer { scaleX = toScale; scaleY = toScale },
                ) { onPick(Slot.To) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeeField(f.fee, f.feeFocused, Modifier.weight(1f)) { actions.focusFee(true) }
                NoteField(f.note, actions::setNote, Modifier.weight(1f))
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val label = stringResource(if (f.type == EntryKind.RECEIVED) R.string.add_in else R.string.add_from)
            PocketButton(label, state.pocket, Modifier.weight(1f)) { onPick(Slot.From) }
            NoteField(f.note, actions::setNote, Modifier.weight(1f))
        }
    }
}

private fun Modifier.field(borderColor: Color = Tz.line) =
    height(48.dp).clip(FieldShape).background(Tz.night).border(1.dp, borderColor, FieldShape)

@Composable
private fun PocketButton(label: String, pocket: PocketEntity?, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier.field()
            .clickable(role = Role.Button) {
                haptics.perform(HapticLevel.Tap)
                onClick()
            }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
        Text(pocket?.name.orEmpty(), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.text, maxLines = 1)
    }
}

/** The transfer's optional fee: tapping it gives it the keypad (yellow border while it has it). */
@Composable
private fun FeeField(fee: AmountInput, focused: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier.field(if (focused) Tz.yellow else Tz.line)
            .clickable(role = Role.Button) {
                haptics.perform(HapticLevel.Tap)
                onFocus()
            }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.add_fee), style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2)
        Text(fee.text.ifEmpty { "0" }, style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.text, maxLines = 1)
    }
}

/** Inline note, at most 120 chars; the system keyboard opens only when it is tapped. */
@Composable
private fun NoteField(note: String, onNote: (String) -> Unit, modifier: Modifier) {
    val hint = stringResource(R.string.add_note)
    BasicTextField(
        value = note,
        onValueChange = onNote,
        modifier = modifier.field().semantics { contentDescription = hint },
        textStyle = TzType.label.copy(fontWeight = FontWeight.Normal, color = Tz.text),
        singleLine = true,
        cursorBrush = SolidColor(Tz.yellow),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (note.isEmpty()) Text(hint, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2, maxLines = 1)
                inner()
            }
        },
    )
}

/** Collapsed by default: the item bought and what one unit cost, so the basket can follow its price. */
@Composable
private fun ItemRow(item: String, unitPrice: String, onItem: (String) -> Unit, onUnitPrice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(item.isNotEmpty() || unitPrice.isNotEmpty()) }
    if (!expanded) {
        TextLink(stringResource(R.string.add_item_open), { expanded = true })
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemField(item, stringResource(R.string.add_item_name), KeyboardType.Text, onItem, Modifier.weight(1f))
        ItemField(unitPrice, stringResource(R.string.add_item_price), KeyboardType.Decimal, onUnitPrice, Modifier.weight(1f))
    }
}

@Composable
private fun ItemField(value: String, hint: String, keyboard: KeyboardType, onValue: (String) -> Unit, modifier: Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.field().semantics { contentDescription = hint },
        textStyle = TzType.label.copy(fontWeight = FontWeight.Normal, color = Tz.text),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        cursorBrush = SolidColor(Tz.yellow),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, style = TzType.label.copy(fontWeight = FontWeight.Normal), color = Tz.text2, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
private fun RoundUpRow(state: AddUiState, onRoundUp: (Boolean) -> Unit) {
    val offer = state.roundUpOffer
    val change = stringResource(R.string.add_round_up_change, formatPounds(offer))
    val line = stringResource(R.string.add_round_up, formatPounds(state.amount + offer), change)
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(FieldShape).background(Tz.turquoiseDim).padding(start = 14.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            highlighted(line, change, SpanStyle(color = Tz.turquoise, fontWeight = FontWeight.SemiBold)),
            style = TzType.label.copy(fontWeight = FontWeight.Normal),
            color = Tz.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        TzToggle(state.form.roundUpOn, onRoundUp, contentDescription = line)
    }
}

@Composable
private fun PocketPicker(pockets: List<PocketEntity>, selected: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    TzSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            pockets.forEach { p ->
                TzRow(
                    leading = { TzIcon(TzIcons.wallet, 20.dp, null) },
                    title = p.name,
                    caption = null,
                    trailing = { if (p.id == selected) TzIcon(TzIcons.check, 20.dp, null, tint = Tz.yellow) },
                    onClick = { onPick(p.id) },
                )
            }
        }
    }
}
