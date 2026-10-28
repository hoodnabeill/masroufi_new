package com.masroufi.app.feature.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.masroufi.app.R
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.core.Money
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.WalletWithBalance
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.DateChip
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.components.typeColor
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion

@Composable
fun typeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.INCOME -> R.string.type_income
        TransactionType.EXPENSE -> R.string.type_expense
        TransactionType.TRANSFER -> R.string.type_transfer
    },
)

/** Applies one keypad press to the typed amount, refusing input the currency can't represent. */
internal fun applyAmountKey(current: String, key: Char, currency: String): String {
    val digits = Money.fractionDigits(currency)
    return when (key) {
        '<' -> current.dropLast(1)
        '.' -> if (digits == 0 || '.' in current) current else (current.ifEmpty { "0" } + ".")
        else -> {
            val fraction = current.substringAfter('.', "")
            when {
                '.' in current && fraction.length >= digits -> current
                current == "0" -> key.toString()
                current.substringBefore('.').length >= 12 && '.' !in current -> current
                else -> current + key
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    var showMore by rememberSaveable { mutableStateOf(false) }
    val pickReceipt = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachReceipt(uri)
    }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)

    Scaffold(
        topBar = {
            MasroufiTopBar(
                title = stringResource(if (state.isNew) R.string.transaction_new else R.string.transaction_edit),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                // The pad steps aside while note/tags use the system keyboard.
                AnimatedVisibility(!showMore, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Keypad(
                        onKey = { key ->
                            haptics.tick()
                            viewModel.setAmount(applyAmountKey(state.amount, key, state.currency))
                        },
                        onClear = { viewModel.setAmount("") },
                        allowDecimal = Money.fractionDigits(state.currency) > 0,
                    )
                }
                Spacer(Modifier.height(12.dp))
                GlassButton(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SegmentedChoice(
                options = TransactionType.entries.toList(),
                selected = state.type,
                onSelect = viewModel::setType,
                label = { typeLabel(it) },
            )
            AmountDisplay(state.amount, state.currency, typeColor(state.type))

            if (state.type == TransactionType.TRANSFER) {
                Text(stringResource(R.string.field_from_wallet), style = MaterialTheme.typography.titleSmall)
                WalletChips(state.wallets, state.walletId, viewModel::setWallet)
                Text(stringResource(R.string.field_to_wallet), style = MaterialTheme.typography.titleSmall)
                WalletChips(state.wallets.filter { it.wallet.id != state.walletId }, state.toWalletId, viewModel::setToWallet)
                AnimatedVisibility(state.needsTargetAmount) {
                    AmountField(
                        value = state.toAmount,
                        onValueChange = viewModel::setToAmount,
                        currency = state.toWallet?.wallet?.currency ?: state.currency,
                        label = stringResource(R.string.field_received_amount),
                    )
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.visibleCategories.forEach { category ->
                        FilterChip(
                            selected = category.id == state.categoryId,
                            onClick = { haptics.tick(); viewModel.setCategory(category.id) },
                            label = { Text(categoryLabel(category), maxLines = 1) },
                            leadingIcon = {
                                Icon(CategoryIcons.of(category.iconKey), contentDescription = null, tint = categoryColor(category.colorIndex), modifier = Modifier.size(18.dp))
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                        )
                    }
                }
                WalletChips(state.wallets, state.walletId, viewModel::setWallet)
            }

            DateChip(state.date, viewModel::setDate)

            TextButton(onClick = { showMore = !showMore }) {
                Icon(if (showMore) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                Text(stringResource(R.string.transaction_more_options))
            }
            AnimatedVisibility(showMore || state.note.isNotEmpty() || state.receipt != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextInput(state.note, viewModel::setNote, stringResource(R.string.field_note), singleLine = false)
                    TextInput(state.tags, viewModel::setTags, stringResource(R.string.field_tags), supportingText = stringResource(R.string.field_tags_hint))
                    val receipt = state.receipt
                    if (receipt == null) {
                        OutlinedButton(onClick = { pickReceipt.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Outlined.AttachFile, contentDescription = null)
                            Text(stringResource(R.string.receipt_attach), modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = viewModel.receiptFile(receipt),
                                contentDescription = stringResource(R.string.receipt_title),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small),
                            )
                            TextButton(onClick = viewModel::removeReceipt) {
                                Icon(Icons.Outlined.Close, contentDescription = null)
                                Text(stringResource(R.string.receipt_remove))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** The typed amount, huge and centred. Each new character pops in from below; the currency sits quietly beside it. */
@Composable
private fun AmountDisplay(amount: String, currency: String, color: androidx.compose.ui.graphics.Color) {
    val reduce = LocalReduceMotion.current
    val shown = amount.ifEmpty { "0" }
    val description = "$shown $currency"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row {
                shown.forEachIndexed { index, ch ->
                    androidx.compose.runtime.key(index) {
                        AnimatedContent(
                            targetState = ch,
                            transitionSpec = { (scaleIn(Motion.bouncy(reduce), initialScale = 0.4f) + fadeIn(Motion.snappy(reduce))) togetherWith fadeOut(Motion.snappy(reduce)) },
                            label = "amountChar",
                        ) { c ->
                            Text(
                                c.toString(),
                                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                                color = if (amount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else color,
                            )
                        }
                    }
                }
            }
        }
        Text(
            currency,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Phone-style pad, always LTR as on every dial pad. Long-press backspace clears. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(onKey: (Char) -> Unit, onClear: () -> Unit, allowDecimal: Boolean) {
    val rows = listOf("123", "456", "789")
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { d -> Key(Modifier.weight(1f), onClick = { onKey(d) }) { KeyText(d.toString()) } } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key(Modifier.weight(1f), enabled = allowDecimal, onClick = { onKey('.') }) { KeyText(".") }
                Key(Modifier.weight(1f), onClick = { onKey('0') }) { KeyText("0") }
                Key(Modifier.weight(1f), onClick = { onKey('<') }, onLongClick = onClear, description = stringResource(R.string.action_delete)) {
                    Icon(Icons.Outlined.Backspace, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun KeyText(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed && !reduce) 0.92f else 1f, Motion.snappy(reduce), label = "key")
    Box(
        modifier
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.3f }
            .glass(MaterialTheme.shapes.medium, strong = pressed)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletChips(wallets: List<WalletWithBalance>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        wallets.forEach { item ->
            FilterChip(
                selected = item.wallet.id == selected,
                onClick = { onSelect(item.wallet.id) },
                label = { Text("${item.wallet.name} · ${item.wallet.currency}", maxLines = 1) },
                leadingIcon = { Icon(CategoryIcons.wallet(item.wallet.type), contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = MaterialTheme.shapes.extraLarge,
            )
        }
    }
}
