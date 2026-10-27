package com.masroufi.app.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.masroufi.app.R
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LabeledRow
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.components.typeColor
import com.masroufi.app.ui.theme.AmountStyle

@Composable
fun TransactionDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val row by viewModel.row.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var viewingReceipt by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val dates = rememberDateFormatter()
    val money = LocalMoneyFormatter.current
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone = { if (deleting) onBack() })

    Scaffold(
        topBar = {
            MasroufiTopBar(stringResource(R.string.transaction_details), onBack = onBack) {
                row?.let { current ->
                    IconButton(onClick = { viewModel.duplicateNow() }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_duplicate)) }
                    IconButton(onClick = { onEdit(current.transaction.id) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = row ?: return@Scaffold
        val tx = current.transaction
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(
                    if (tx.type == TransactionType.TRANSFER) CategoryIcons.walletGeneric else CategoryIcons.of(current.categoryIcon),
                    if (tx.type == TransactionType.TRANSFER) typeColor(tx.type) else categoryColor(current.categoryColor),
                    size = 64.dp,
                )
                AnimatedAmount(tx.amountMinor, tx.currency, style = AmountStyle, color = typeColor(tx.type), modifier = Modifier.padding(top = 8.dp))
                Text(typeLabel(tx.type), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PressableCard(onClick = null) {
                if (tx.type != TransactionType.TRANSFER) {
                    LabeledRow(stringResource(R.string.field_category)) { Text(categoryLabel(current.categoryName, current.categorySystemKey)) }
                    LabeledRow(stringResource(R.string.field_wallet)) { Text(current.walletName.orEmpty()) }
                } else {
                    LabeledRow(stringResource(R.string.field_from_wallet)) { Text(current.walletName.orEmpty()) }
                    LabeledRow(stringResource(R.string.field_to_wallet)) { Text(current.toWalletName.orEmpty()) }
                    tx.toAmountMinor?.let { received ->
                        LabeledRow(stringResource(R.string.field_received_amount)) { Text(money.format(received, current.toWalletCurrency ?: tx.currency)) }
                    }
                }
                LabeledRow(stringResource(R.string.field_date)) { Text(tx.occurredAt.toLocalDate().format(dates)) }
                if (tx.note.isNotBlank()) LabeledRow(stringResource(R.string.field_note)) { Text(tx.note) }
                if (tx.tags.isNotBlank()) LabeledRow(stringResource(R.string.field_tags)) { Text(tx.tags.replace(",", " · ")) }
                if (tx.recurringId != null) LabeledRow(stringResource(R.string.recurring_title)) { Text(stringResource(R.string.transaction_from_recurring)) }
            }
            tx.receiptPath?.let { name ->
                val file = viewModel.receiptFile(name)
                PressableCard(onClick = null) {
                    Text(stringResource(R.string.receipt_title), style = MaterialTheme.typography.titleMedium)
                    if (file == null) {
                        Text(stringResource(R.string.receipt_missing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        AsyncImage(
                            model = file,
                            contentDescription = stringResource(R.string.receipt_title),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(top = 8.dp).clip(MaterialTheme.shapes.medium).clickable { viewingReceipt = true },
                        )
                    }
                    Row { TextButton(onClick = viewModel::removeReceipt) { Text(stringResource(R.string.receipt_remove)) } }
                }
            }
        }
        if (viewingReceipt) {
            val file = tx.receiptPath?.let(viewModel::receiptFile)
            Dialog(onDismissRequest = { viewingReceipt = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().clickable { viewingReceipt = false }, contentAlignment = Alignment.Center) {
                    AsyncImage(model = file, contentDescription = stringResource(R.string.receipt_title), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.transaction_delete_title),
            body = stringResource(R.string.transaction_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { deleting = true; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
