package com.masroufi.app.feature.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.Money
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.WalletWithBalance
import com.masroufi.app.data.repo.TodayBudget
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.DateChip
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.glassAssistChipColors
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import kotlin.math.roundToInt

/** The sheet's four kinds: the three transaction types plus سلفة, which hands over to the debt editor. */
private enum class SheetKind(val type: TransactionType?, val label: Int, val save: Int) {
    EXPENSE(TransactionType.EXPENSE, R.string.type_expense, R.string.add_save_expense),
    INCOME(TransactionType.INCOME, R.string.type_income, R.string.add_save_income),
    TRANSFER(TransactionType.TRANSFER, R.string.type_transfer, R.string.add_save_transfer),
    LOAN(null, R.string.add_type_loan, R.string.add_save_loan),
}

/** How many category chips show before "all categories"; the most recently used come first. */
private const val TOP_CATEGORIES = 6

/**
 * Quick add: a bottom sheet with a big amount, a phone keypad and one row of choices, so logging takes a few taps.
 * While typing an expense it says how much of today's number it uses. Edits and extras (tags, receipt, a received
 * amount in another currency) live in the full editor, one tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSheet(
    onClose: () -> Unit,
    onSaved: (TransactionType) -> Unit,
    onLoan: (amount: String) -> Unit,
    onMoreDetails: (EditorUiState) -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val glass = LocalGlass.current
    var loan by rememberSaveable { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var allCategories by rememberSaveable { mutableStateOf(false) }
    val kind = if (loan) SheetKind.LOAN else SheetKind.entries.first { it.type == state.type }

    // Slides up on open; closing slides it back down first and only then leaves the back stack.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun leave(then: () -> Unit) {
        pending = then
        visible.targetState = false
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) pending?.invoke()
    }
    BackHandler(enabled = visible.targetState) { leave(onClose) }
    HandleEditorEffects(viewModel.effects.flow, snackbar) { leave { onSaved(state.type) } }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { leave(onClose) })
        AnimatedVisibility(
            visibleState = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(Motion.spatial(reduce)) { it } + fadeIn(Motion.effects(reduce)),
            exit = slideOutVertically(Motion.snappy(reduce)) { it } + fadeOut(Motion.snappy(reduce)),
        ) {
            val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(glass.fill)
                    .border(1.dp, glass.strokeTop, shape)
                    // Swallows taps so they don't reach the dismiss layer behind.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 5.dp).clip(CircleShape).background(glass.track))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KindSwitch(kind, Modifier.weight(1f)) { picked ->
                        haptics.tick()
                        loan = picked == SheetKind.LOAN
                        picked.type?.let(viewModel::setType)
                    }
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(glass.fillStrong).clickable(role = Role.Button) { leave(onClose) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MIcons.Close, contentDescription = stringResource(R.string.add_close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }

                AmountBlock(state, kind, today)

                when (kind) {
                    SheetKind.EXPENSE, SheetKind.INCOME -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.add_on_what), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                        val all = state.visibleCategories
                        val shown = if (allCategories) all else all.take(TOP_CATEGORIES).let { top ->
                            all.firstOrNull { it.id == state.categoryId && it !in top }?.let { top + it } ?: top
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shown.forEach { category ->
                                CategoryPill(
                                    label = categoryLabel(category),
                                    icon = { Icon(CategoryIcons.of(category.iconKey), contentDescription = null, tint = categoryColor(category.colorIndex), modifier = Modifier.size(16.dp)) },
                                    color = categoryColor(category.colorIndex),
                                    selected = category.id == state.categoryId,
                                ) {
                                    haptics.tick()
                                    viewModel.setCategory(category.id)
                                }
                            }
                            if (!allCategories && all.size > shown.size) {
                                CategoryPill(stringResource(R.string.home_all_categories), icon = null, color = glass.textTertiary, selected = false) { allCategories = true }
                            }
                        }
                    }
                    SheetKind.TRANSFER -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        WalletChip(state.wallets, state.wallet, stringResource(R.string.field_from_wallet), viewModel::setWallet)
                        Icon(MIcons.ChevronForward, contentDescription = null, tint = glass.textTertiary, modifier = Modifier.size(18.dp))
                        WalletChip(state.wallets.filter { it.wallet.id != state.walletId }, state.toWallet, stringResource(R.string.field_to_wallet), viewModel::setToWallet)
                    }
                    SheetKind.LOAN -> Unit
                }

                if (kind != SheetKind.LOAN) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (kind != SheetKind.TRANSFER) WalletChip(state.wallets, state.wallet, null, viewModel::setWallet)
                        DateChip(state.date, viewModel::setDate)
                        AssistChip(
                            colors = glassAssistChipColors(),
                            onClick = { editingNote = !editingNote },
                            label = { Text(state.note.ifBlank { stringResource(R.string.add_note_chip) }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
                            leadingIcon = { Icon(MIcons.Pencil, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        AssistChip(
                            colors = glassAssistChipColors(),
                            onClick = { leave { onMoreDetails(state) } },
                            label = { Text(stringResource(R.string.transaction_more_options)) },
                            leadingIcon = { Icon(MIcons.More, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
                if (editingNote) TextInput(state.note, viewModel::setNote, stringResource(R.string.field_note))

                // The pad steps aside while the note uses the system keyboard.
                if (!editingNote) {
                    SheetKeypad(
                        allowDecimal = Money.fractionDigits(state.currency) > 0,
                        onKey = { key ->
                            haptics.tick()
                            viewModel.setAmount(applyAmountKey(state.amount, key, state.currency))
                        },
                        onClear = { viewModel.setAmount("") },
                    )
                }

                val amountOk = (Money.parse(state.amount, state.currency) ?: 0) > 0
                GlassButton(
                    onClick = {
                        when {
                            loan -> leave { onLoan(state.amount) }
                            // A transfer into another currency needs the received amount, which the full editor asks for.
                            state.needsTargetAmount -> leave { onMoreDetails(state) }
                            else -> viewModel.save()
                        }
                    },
                    enabled = if (loan) amountOk else state.canSave,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(kind.save), style = MaterialTheme.typography.titleMedium)
                    if (!loan) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.home_points, 10),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun KindSwitch(selected: SheetKind, modifier: Modifier, onSelect: (SheetKind) -> Unit) {
    val glass = LocalGlass.current
    Row(
        modifier.height(44.dp).clip(CircleShape).background(glass.base).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SheetKind.entries.forEach { kind ->
            val on = kind == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (on) glass.track else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(kind) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(kind.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurface else glass.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Groups the typed integer part by thousands while keeping exactly what was typed after the point. */
internal fun groupTyped(raw: String): String {
    val shown = raw.ifEmpty { "0" }
    val whole = shown.substringBefore('.').reversed().chunked(3).joinToString(",").reversed()
    return if ('.' in shown) whole + "." + shown.substringAfter('.') else whole
}

@Composable
private fun AmountBlock(state: EditorUiState, kind: SheetKind, today: TodayBudget?) {
    val finance = LocalFinanceColors.current
    val glass = LocalGlass.current
    val formatter = LocalMoneyFormatter.current
    val amountMinor = Money.parse(state.amount, state.currency) ?: 0
    val color = when (kind) {
        SheetKind.EXPENSE -> MaterialTheme.colorScheme.onSurface
        SheetKind.INCOME -> MaterialTheme.colorScheme.primary
        SheetKind.TRANSFER -> finance.transfer
        SheetKind.LOAN -> finance.streak
    }
    // Today's number is in the primary currency, so the comparison only applies to amounts in that currency.
    val budget = today?.takeIf { it.currency == state.currency }
    var bar: Float? = null
    var barColor = MaterialTheme.colorScheme.primary
    var noteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val note = when {
        amountMinor == 0L -> {
            noteColor = glass.textTertiary
            stringResource(R.string.add_note_empty)
        }
        kind == SheetKind.EXPENSE && budget != null -> {
            val left = budget.remainingMinor
            if (left > 0 && amountMinor <= left) {
                val pct = (amountMinor * 100.0 / left).roundToInt()
                bar = pct / 100f
                if (pct > 80) barColor = finance.warning
                stringResource(R.string.add_note_pct, pct)
            } else {
                bar = 1f
                barColor = finance.over
                noteColor = finance.over
                stringResource(R.string.add_note_over, formatter.format(amountMinor - left.coerceAtLeast(0), state.currency))
            }
        }
        kind == SheetKind.INCOME && budget != null -> {
            noteColor = MaterialTheme.colorScheme.primary
            stringResource(R.string.add_note_income, formatter.format(budget.afterIncome(amountMinor), state.currency))
        }
        kind == SheetKind.TRANSFER && state.wallet != null && state.toWallet != null ->
            stringResource(R.string.add_note_transfer, state.wallet!!.wallet.name, state.toWallet!!.wallet.name)
        kind == SheetKind.LOAN -> stringResource(R.string.add_note_loan)
        else -> ""
    }
    val fill by animateFloatAsState(bar ?: 0f, Motion.spatial(LocalReduceMotion.current), label = "todayShare")
    val shown = groupTyped(state.amount)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$shown ${state.currency}" },
        ) {
            // Amount digits stay left-to-right in both languages; the currency follows the reading direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    shown,
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum", letterSpacing = (-2).sp),
                    color = if (state.amount.isEmpty()) glass.textTertiary else color,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                formatter.symbol(state.currency),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(note, style = MaterialTheme.typography.bodySmall, color = noteColor)
        if (bar != null) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(160.dp).height(4.dp).clip(CircleShape).background(glass.track)) {
                Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(barColor))
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, icon: (@Composable () -> Unit)?, color: Color, selected: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.18f) else glass.fillStrong)
            .border(1.dp, if (selected) color else glass.strokeTop, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.invoke()
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun WalletChip(wallets: List<WalletWithBalance>, current: WalletWithBalance?, prefix: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        val name = current?.wallet?.name ?: "—"
        AssistChip(
            colors = glassAssistChipColors(),
            onClick = { open = true },
            label = { Text(prefix?.let { "$it: $name" } ?: name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(current?.let { CategoryIcons.wallet(it.wallet.type) } ?: MIcons.Wallet, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            wallets.forEach { item ->
                DropdownMenuItem(
                    text = { Text("${item.wallet.name} · ${item.wallet.currency}") },
                    leadingIcon = { Icon(CategoryIcons.wallet(item.wallet.type), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        onSelect(item.wallet.id)
                    },
                )
            }
        }
    }
}

/** Phone-style pad, always LTR as on every dial pad: flat keys that light up while pressed. Long-press ⌫ clears. */
@Composable
private fun SheetKeypad(allowDecimal: Boolean, onKey: (Char) -> Unit, onClear: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("123", "456", "789", ".0<").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        val description = when (key) {
                            '<' -> stringResource(R.string.action_delete)
                            '.' -> stringResource(R.string.add_decimal)
                            else -> null
                        }
                        PadKey(
                            Modifier.weight(1f),
                            enabled = key != '.' || allowDecimal,
                            description = description,
                            onClick = { onKey(key) },
                            onLongClick = if (key == '<') onClear else null,
                        ) {
                            if (key == '<') {
                                Icon(MIcons.Backspace, contentDescription = null, modifier = Modifier.size(26.dp))
                            } else {
                                Text(key.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PadKey(
    modifier: Modifier,
    enabled: Boolean,
    description: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .height(52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (pressed) LocalGlass.current.track else Color.Transparent)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides if (enabled) MaterialTheme.colorScheme.onSurface else LocalGlass.current.textTertiary) {
            content()
        }
    }
}
