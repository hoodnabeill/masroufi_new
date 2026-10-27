@file:OptIn(ExperimentalLayoutApi::class)

package com.masroufi.app.feature.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.masroufi.app.R
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
import com.masroufi.app.ui.components.rememberHaptics

@Composable
fun typeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.INCOME -> R.string.type_income
        TransactionType.EXPENSE -> R.string.type_expense
        TransactionType.TRANSFER -> R.string.type_transfer
    },
)

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
    val amountFocus = remember { FocusRequester() }
    val pickReceipt = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachReceipt(uri)
    }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)

    LaunchedEffect(state.loaded) {
        if (state.loaded && state.isNew) runCatching { amountFocus.requestFocus() }
    }

    Scaffold(
        topBar = {
            MasroufiTopBar(
                title = stringResource(if (state.isNew) R.string.transaction_new else R.string.transaction_edit),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).height(56.dp),
            ) { Text(stringResource(R.string.action_save)) }
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
                onSelect = { haptics.tick(); viewModel.setType(it) },
                label = { typeLabel(it) },
            )
            AmountField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                currency = state.currency,
                modifier = Modifier.focusRequester(amountFocus),
                textStyle = MaterialTheme.typography.displaySmall,
            )

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
                Text(stringResource(R.string.field_category), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.visibleCategories.forEach { category ->
                        FilterChip(
                            selected = category.id == state.categoryId,
                            onClick = { haptics.tick(); viewModel.setCategory(category.id) },
                            label = { Text(categoryLabel(category), maxLines = 1) },
                            leadingIcon = {
                                Icon(CategoryIcons.of(category.iconKey), contentDescription = null, tint = categoryColor(category.colorIndex), modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
                Text(stringResource(R.string.field_wallet), style = MaterialTheme.typography.titleSmall)
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WalletChips(wallets: List<WalletWithBalance>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        wallets.forEach { item ->
            FilterChip(
                selected = item.wallet.id == selected,
                onClick = { onSelect(item.wallet.id) },
                label = { Text("${item.wallet.name} · ${item.wallet.currency}", maxLines = 1) },
                leadingIcon = { Icon(CategoryIcons.wallet(item.wallet.type), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}
