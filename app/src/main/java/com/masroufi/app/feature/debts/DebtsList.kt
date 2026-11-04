package com.masroufi.app.feature.debts

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.model.DebtDirection
import com.masroufi.app.data.model.DebtWithPaid
import com.masroufi.app.domain.DebtMath
import com.masroufi.app.domain.DebtStatus
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.ui.components.GlassSheet
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class DebtFilter(val label: Int, val direction: DebtDirection?) {
    ALL(R.string.filter_all, null),
    OWED(R.string.debts_owed_short, DebtDirection.OWED_TO_ME),
    OWE(R.string.debts_owe_short, DebtDirection.I_OWE),
}

/** How a reminder sounds; the user picks before anything is sent, and WhatsApp still asks before sending. */
private enum class Tone(val label: Int) { FRIENDLY(R.string.tone_friendly), NORMAL(R.string.tone_normal), FIRM(R.string.tone_firm) }

/**
 * الديون والسلف: what people owe the user against what the user owes, one list with a filter, and for money owed to
 * the user a gentle reminder that opens WhatsApp with the text ready (never sent automatically).
 */
@Composable
fun DebtsScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: DebtsViewModel = hiltViewModel()) {
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(DebtFilter.ALL) }
    var remindId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(R.string.debts_title), onBack = onBack) { GlassIconButton(onClick = onAdd) { Icon(MIcons.Plus, stringResource(R.string.debt_add)) } } },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        val list = debts ?: return@Scaffold
        val today = viewModel.today
        val open = list.filter { it.paidMinor < it.debt.originalMinor }
        if (list.isEmpty()) {
            EmptyState(MIcons.Handshake, stringResource(R.string.debts_empty_title), stringResource(R.string.debts_empty_body), Modifier.padding(padding), stringResource(R.string.debt_add), onAdd)
            return@Scaffold
        }
        val visible = list.filter { filter.direction == null || it.debt.direction == filter.direction }
            .sortedWith(compareBy<DebtWithPaid> { it.paidMinor >= it.debt.originalMinor }.thenBy { it.debt.dueEpochDay ?: Long.MAX_VALUE })
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).edgeFade(),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "totals") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TotalTile(stringResource(R.string.debts_owed_short), open.filter { it.debt.direction == DebtDirection.OWED_TO_ME }, LocalFinanceColors.current.income, Modifier.weight(1f))
                    TotalTile(stringResource(R.string.debts_owe_short), open.filter { it.debt.direction == DebtDirection.I_OWE }, LocalFinanceColors.current.over, Modifier.weight(1f))
                }
            }
            item(key = "filter") {
                SegmentedChoice(DebtFilter.entries.toList(), filter, { filter = it }, { stringResource(it.label) })
            }
            item(key = "list") {
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(LocalGlass.current.fill).border(1.dp, LocalGlass.current.rim, MaterialTheme.shapes.large).padding(vertical = 4.dp),
                ) {
                    visible.forEach { item -> DebtRow(item, today, onOpen = { onOpen(item.debt.id) }, onRemind = { remindId = item.debt.id }) }
                }
            }
        }
        list.firstOrNull { it.debt.id == remindId }?.let { item ->
            ReminderSheet(item, today, onDismiss = { remindId = null }, onCopied = { /* the sheet confirms with its own label */ })
        }
    }
}

/** Open totals per currency, joined, so currencies are never added together. */
@Composable
private fun TotalTile(label: String, items: List<DebtWithPaid>, color: Color, modifier: Modifier) {
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val totals = items.groupBy { it.debt.currency }.map { (currency, rows) -> money.format(rows.sumOf { DebtMath.remaining(it.debt.originalMinor, it.paidMinor) }, currency) }
    val people = items.map { it.debt.personName }.distinct().size
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        Text(totals.ifEmpty { listOf("0") }.joinToString(" · "), style = MaterialTheme.typography.headlineSmall, color = if (items.isEmpty()) MaterialTheme.colorScheme.onSurface else color, maxLines = 1)
        Text(
            if (people == 0) stringResource(R.string.debts_nobody) else androidx.compose.ui.res.pluralStringResource(R.plurals.debts_people, people, people),
            style = MaterialTheme.typography.labelSmall,
            color = glass.textTertiary,
        )
    }
}

@Composable
private fun DebtRow(item: DebtWithPaid, today: LocalDate, onOpen: () -> Unit, onRemind: () -> Unit) {
    val glass = LocalGlass.current
    val finance = LocalFinanceColors.current
    val money = LocalMoneyFormatter.current
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }
    val debt = item.debt
    val owedToMe = debt.direction == DebtDirection.OWED_TO_ME
    val remaining = DebtMath.remaining(debt.originalMinor, item.paidMinor)
    val status = DebtMath.status(debt.originalMinor, item.paidMinor, debt.dueEpochDay, today)
    val avatar = categoryColor(kotlin.math.abs(debt.personName.hashCode()) % 12)
    val (dueText, dueColor) = when {
        status == DebtStatus.SETTLED -> stringResource(R.string.debt_status_settled) to finance.income
        status == DebtStatus.OVERDUE -> {
            val days = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(debt.dueEpochDay ?: today.toEpochDay()), today).toInt()
            androidx.compose.ui.res.pluralStringResource(R.plurals.debt_overdue_days, days, days) to finance.over
        }
        debt.dueEpochDay != null -> stringResource(R.string.debt_due_on, LocalDate.ofEpochDay(debt.dueEpochDay).format(dates)) to glass.textTertiary
        else -> "" to glass.textTertiary
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(avatar.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(debt.personName.take(1), style = MaterialTheme.typography.titleMedium, color = avatar)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(debt.personName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                Text(
                    if (status == DebtStatus.SETTLED) stringResource(R.string.debt_status_settled)
                    else (if (owedToMe) "+" else "−") + money.format(remaining, debt.currency),
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        status == DebtStatus.SETTLED -> glass.textTertiary
                        owedToMe -> finance.income
                        else -> finance.over
                    },
                )
            }
            val note = listOf(debt.note.ifBlank { stringResource(if (owedToMe) R.string.debt_lent else R.string.debt_borrowed) }, debt.createdAt.toLocalDate().format(dates)).joinToString(" · ")
            Text(note, style = MaterialTheme.typography.bodySmall, color = glass.textTertiary, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dueText, style = MaterialTheme.typography.labelMedium, color = dueColor, modifier = Modifier.weight(1f))
                if (owedToMe && status != DebtStatus.SETTLED) {
                    val lime = MaterialTheme.colorScheme.primary
                    Text(
                        stringResource(R.string.debt_nudge),
                        style = MaterialTheme.typography.labelLarge,
                        color = lime,
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(CircleShape)
                            .background(lime.copy(alpha = 0.12f))
                            .border(1.dp, lime.copy(alpha = 0.3f), CircleShape)
                            .clickable(role = Role.Button, onClick = onRemind)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** فكّره بلُطف: three tones of the same reminder, previewed, then handed to WhatsApp (or copied) for the user to send. */
@Composable
private fun ReminderSheet(item: DebtWithPaid, today: LocalDate, onDismiss: () -> Unit, onCopied: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val money = LocalMoneyFormatter.current
    val glass = LocalGlass.current
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }
    var tone by rememberSaveable { mutableStateOf(Tone.FRIENDLY) }
    var copied by remember { mutableStateOf(false) }
    val debt = item.debt
    val name = debt.personName
    val amount = money.format(DebtMath.remaining(debt.originalMinor, item.paidMinor), debt.currency)
    val since = debt.createdAt.toLocalDate().format(dates)
    val by = (debt.dueEpochDay?.let(LocalDate::ofEpochDay)?.takeIf { it.isAfter(today) } ?: today.plusDays(7)).format(dates)
    val message = when (tone) {
        Tone.FRIENDLY -> stringResource(R.string.debt_msg_friendly, name, amount, since)
        Tone.NORMAL -> stringResource(R.string.debt_msg_normal, name, amount, since)
        Tone.FIRM -> stringResource(R.string.debt_msg_firm, name, amount, since, by)
    }
    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.debt_remind_title, name), style = MaterialTheme.typography.titleLarge)
            Text("$amount · $since", style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tone.entries.forEach { t ->
                    val on = t == tone
                    val lime = MaterialTheme.colorScheme.primary
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(CircleShape)
                            .background(if (on) lime.copy(alpha = 0.12f) else Color.Transparent)
                            .border(1.dp, if (on) lime else glass.strokeBottom, CircleShape)
                            .clickable(role = Role.RadioButton) { tone = t; copied = false }
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(t.label), style = MaterialTheme.typography.labelLarge, color = if (on) lime else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(glass.fillStrong).padding(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    onClick = {
                        // wa.me with no number lets the user pick the chat; WhatsApp shows the text and the user sends it.
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode(message)))
                        try {
                            context.startActivity(intent)
                            onDismiss()
                        } catch (e: ActivityNotFoundException) {
                            clipboard.setText(AnnotatedString(message))
                            copied = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.debt_send_whatsapp)) }
                GlassSecondaryButton(
                    onClick = { clipboard.setText(AnnotatedString(message)); copied = true; onCopied() },
                    modifier = Modifier.height(52.dp),
                ) { Text(stringResource(if (copied) R.string.debt_copied else R.string.debt_copy)) }
            }
            Text(stringResource(R.string.debt_remind_note), style = MaterialTheme.typography.labelSmall, color = glass.textTertiary)
        }
    }
}
