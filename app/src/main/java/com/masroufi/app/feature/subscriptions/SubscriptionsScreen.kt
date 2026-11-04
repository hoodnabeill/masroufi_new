package com.masroufi.app.feature.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.ui.theme.continuousPercent
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.domain.Frequency
import com.masroufi.app.feature.recurring.frequencyLabel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.FitAmount
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.Service
import com.masroufi.app.ui.components.ServiceTile
import com.masroufi.app.ui.components.Services
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.components.liquidPress
import com.masroufi.app.ui.components.staggerIn
import com.masroufi.app.ui.theme.AmountStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class SubscriptionsState(
    val loading: Boolean = true,
    val items: List<RecurringRule> = emptyList(),
    val currency: String = "EGP",
    /** Monthly-equivalent cost of every active subscription in the primary currency. */
    val monthlyMinor: Long = 0,
)

/** What a rule costs per month on average (daily, weekly and yearly rules converted), rounded to the minor unit. */
internal fun monthlyEquivalent(rule: RecurringRule): Long {
    val perCycle = BigDecimal.valueOf(rule.amountMinor).divide(BigDecimal.valueOf(rule.interval.coerceAtLeast(1).toLong()), 6, RoundingMode.HALF_UP)
    val factor = when (rule.frequency) {
        Frequency.DAILY -> BigDecimal("30.436875")
        Frequency.WEEKLY -> BigDecimal("4.348125")
        Frequency.MONTHLY -> BigDecimal.ONE
        Frequency.YEARLY -> BigDecimal.ONE.divide(BigDecimal(12), 6, RoundingMode.HALF_UP)
    }
    return perCycle.multiply(factor).setScale(0, RoundingMode.HALF_UP).toLong()
}

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(recurring: RecurringRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<SubscriptionsState> = combine(recurring.observeAll(), settings.settings) { rules, s ->
        val subs = rules.filter { it.serviceKey != null && it.type == TransactionType.EXPENSE }
            .sortedWith(compareBy<RecurringRule> { !it.active }.thenBy { it.nextEpochDay ?: Long.MAX_VALUE })
        SubscriptionsState(
            loading = false,
            items = subs,
            currency = s.primaryCurrency,
            monthlyMinor = subs.filter { it.active && it.currency == s.primaryCurrency }.sumOf(::monthlyEquivalent),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsState())
}

/**
 * Every subscription in one place with its logo: what they cost per month and per year, and when each renews.
 * Adding one starts from the service grid, so the name, logo and category are already filled in.
 */
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: (serviceKey: String?) -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { MasroufiTopBar(stringResource(R.string.subs_title), onBack = onBack) }) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).edgeFade(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.items.isEmpty()) {
                item { EmptyState(MIcons.Repeat, stringResource(R.string.subs_empty_title), stringResource(R.string.subs_empty_body)) }
            } else {
                item { TotalCard(state, Modifier.staggerIn(0)) }
                items(state.items, key = { it.id }) { rule -> SubscriptionRow(rule) { onOpen(rule.id) } }
            }
            item { SectionHeader(stringResource(R.string.subs_add)) }
            item { Catalogue(onAdd) }
        }
    }
}

@Composable
private fun TotalCard(state: SubscriptionsState, modifier: Modifier) {
    val formatter = LocalMoneyFormatter.current
    PressableCard(onClick = null, modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
        Text(stringResource(R.string.subs_monthly), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FitAmount(state.monthlyMinor, state.currency, style = AmountStyle)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.subs_yearly, formatter.format(state.monthlyMinor * 12, state.currency)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        // The logos of everything being paid for, at a glance.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.items.filter { it.active }.mapNotNull { Services.of(it.serviceKey) }.distinctBy { it.key }.take(7).forEach { ServiceTile(it, size = 36.dp) }
        }
    }
}

@Composable
private fun SubscriptionRow(rule: RecurringRule, onClick: () -> Unit) {
    val service = Services.of(rule.serviceKey)
    val today = remember { LocalDate.now(Clock.systemDefaultZone()) }
    PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (service != null) ServiceTile(service, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.note.ifBlank { service?.title.orEmpty() }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(renewalLabel(rule, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(rule.amountMinor, rule.currency, style = MaterialTheme.typography.titleMedium)
                Text(frequencyLabel(rule.frequency, rule.interval), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun renewalLabel(rule: RecurringRule, today: LocalDate): String {
    if (!rule.active) return stringResource(R.string.subs_paused)
    val next = rule.nextEpochDay ?: return ""
    val days = (next - today.toEpochDay()).toInt()
    return if (days <= 0) stringResource(R.string.subs_renews_today) else pluralStringResource(R.plurals.subs_renews_in, days, days)
}

@Composable
private fun Catalogue(onAdd: (String?) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        maxItemsInEachRow = 4,
    ) {
        Services.all.forEach { service -> CatalogueItem(service, Modifier.weight(1f)) { onAdd(service.key) } }
        OtherItem(Modifier.weight(1f)) { onAdd(null) }
    }
}

@Composable
private fun CatalogueItem(service: Service, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .liquidPress(interaction, MaterialTheme.shapes.medium, scaleTo = 1.06f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ServiceTile(service, size = 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(service.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OtherItem(modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .liquidPress(interaction, MaterialTheme.shapes.medium, scaleTo = 1.06f)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.width(56.dp).height(56.dp).glass(androidx.compose.foundation.shape.continuousPercent(23)),
            contentAlignment = Alignment.Center,
        ) { Icon(MIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.subs_other), style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
    }
}
