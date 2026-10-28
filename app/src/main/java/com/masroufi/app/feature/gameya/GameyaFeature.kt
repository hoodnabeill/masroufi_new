@file:OptIn(ExperimentalLayoutApi::class)

package com.masroufi.app.feature.gameya

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.masroufi.app.R
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.GlassSnackbarHost
import com.masroufi.app.ui.components.GlassSecondaryButton
import com.masroufi.app.ui.components.GlassSwitch
import com.masroufi.app.ui.components.FitAmount
import com.masroufi.app.ui.components.glassChipColors
import com.masroufi.app.ui.components.glassFieldColors
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.data.model.Gameya
import com.masroufi.app.data.model.GameyaMember
import com.masroufi.app.data.repo.MemberDraft
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.GameyaRepository
import com.masroufi.app.domain.ContributionStatus
import com.masroufi.app.domain.GameyaMath
import com.masroufi.app.domain.GameyaRound
import com.masroufi.app.domain.GameyaSummary
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.AnimatedProgressBar
import com.masroufi.app.ui.components.ConfirmDialog
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.DateChip
import com.masroufi.app.ui.components.EditorEffect
import com.masroufi.app.ui.components.EffectChannel
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.HandleEditorEffects
import com.masroufi.app.ui.components.LabeledRow
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.TextInput
import com.masroufi.app.ui.components.attempt
import com.masroufi.app.ui.components.rememberDateFormatter
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.navigation.GameyaDetailRoute
import com.masroufi.app.ui.navigation.GameyaEditorRoute
import com.masroufi.app.ui.theme.AmountStyle
import com.masroufi.app.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class GameyaOverview(val gameya: Gameya, val summary: GameyaSummary)

@HiltViewModel
class GameyaListViewModel @Inject constructor(repo: GameyaRepository, clock: Clock) : ViewModel() {
    val items: StateFlow<List<GameyaOverview>?> = combine(
        repo.observeAll(),
        repo.observeAllMembers(),
        repo.observeAllPayments(),
        repo.observeAllPayouts(),
    ) { groups, members, payments, payouts ->
        val today = LocalDate.now(clock)
        groups.map { g ->
            GameyaOverview(g, GameyaMath.summarize(g, members.filter { it.gameyaId == g.id }, payments.filter { it.gameyaId == g.id }, payouts.filter { it.gameyaId == g.id }, today))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun GameyaListScreen(onBack: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit, viewModel: GameyaListViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val money = LocalMoneyFormatter.current
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(R.string.gameya_title), onBack = onBack) { GlassIconButton(onClick = onAdd) { Icon(MIcons.Plus, stringResource(R.string.gameya_add)) } } },
    ) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(MIcons.Gameya, stringResource(R.string.gameya_empty_title), stringResource(R.string.gameya_empty_body), Modifier.padding(padding), stringResource(R.string.gameya_add), onAdd)
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.gameya.id }) { item ->
                val s = item.summary
                PressableCard(onClick = { onOpen(item.gameya.id) }, modifier = Modifier.animateItem()) {
                    Text(item.gameya.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.gameya_summary_line, money.format(item.gameya.contributionMinor, item.gameya.currency), s.rounds.size, money.format(s.potMinor, item.gameya.currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    AnimatedProgressBar(if (s.rounds.isEmpty()) 0f else s.completedRounds.toFloat() / s.rounds.size, MaterialTheme.colorScheme.tertiary)
                    Text(stringResource(R.string.gameya_rounds_done, s.completedRounds, s.rounds.size), style = MaterialTheme.typography.bodySmall)
                    if (s.missedContributions > 0) {
                        Text(stringResource(R.string.gameya_missed, s.missedContributions), style = MaterialTheme.typography.bodySmall, color = LocalFinanceColors.current.expense)
                    }
                }
            }
        }
    }
}

data class GameyaDetailState(val gameya: Gameya? = null, val members: List<GameyaMember> = emptyList(), val summary: GameyaSummary? = null)

@HiltViewModel
class GameyaDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: GameyaRepository,
    clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<GameyaDetailRoute>().id
    val state: StateFlow<GameyaDetailState> = combine(
        repo.observe(id), repo.observeMembers(id), repo.observePayments(id), repo.observePayouts(id),
    ) { g, m, p, o ->
        GameyaDetailState(g, m, g?.let { GameyaMath.summarize(it, m, p, o, LocalDate.now(clock)) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameyaDetailState())
    val closeEffects = EffectChannel<EditorEffect>()

    fun togglePaid(memberId: String, round: Int, paid: Boolean) {
        val g = state.value.gameya ?: return
        viewModelScope.launch { repo.setPaid(g, memberId, round, paid) }
    }

    fun togglePayout(round: GameyaRound, received: Boolean) {
        val g = state.value.gameya ?: return
        viewModelScope.launch { repo.setPayoutReceived(g, round.round, round.potMinor, received) }
    }

    fun delete() {
        viewModelScope.launch { closeEffects.attempt { repo.delete(id) } }
    }
}

@Composable
fun GameyaDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, viewModel: GameyaDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    HandleEditorEffects(viewModel.closeEffects.flow, snackbar, onDone = onBack)
    val gameya = state.gameya
    val summary = state.summary
    val money = LocalMoneyFormatter.current
    val dates = rememberDateFormatter()
    Scaffold(
        topBar = {
            MasroufiTopBar(gameya?.name.orEmpty(), onBack = onBack) {
                if (gameya != null) {
                    GlassIconButton(onClick = { onEdit(gameya.id) }) { Icon(MIcons.Pencil, stringResource(R.string.action_edit)) }
                    GlassIconButton(onClick = { confirmDelete = true }) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (gameya == null || summary == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PressableCard(onClick = null, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(stringResource(R.string.gameya_pot), style = MaterialTheme.typography.labelLarge)
                    FitAmount(summary.potMinor, gameya.currency, style = AmountStyle)
                    LabeledRow(stringResource(R.string.gameya_contribution)) { MoneyText(gameya.contributionMinor, gameya.currency) }
                    LabeledRow(stringResource(R.string.gameya_frequency)) {
                        Text(if (gameya.monthly) stringResource(R.string.gameya_monthly) else stringResource(R.string.gameya_every_days, gameya.intervalDays))
                    }
                    if (gameya.organizer.isNotBlank()) LabeledRow(stringResource(R.string.gameya_organizer)) { Text(gameya.organizer) }
                    if (summary.myTotalObligationMinor > 0) {
                        LabeledRow(stringResource(R.string.gameya_my_total)) { MoneyText(summary.myTotalObligationMinor, gameya.currency) }
                        LabeledRow(stringResource(R.string.gameya_my_paid)) { MoneyText(summary.myPaidMinor, gameya.currency) }
                        LabeledRow(stringResource(R.string.gameya_my_remaining)) { MoneyText(summary.myRemainingMinor, gameya.currency) }
                    }
                    summary.myPayoutRound?.let { r ->
                        Text(
                            if (r.payout != null) stringResource(R.string.gameya_my_payout_received, r.round)
                            else stringResource(R.string.gameya_my_payout, r.round, r.date.format(dates)),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.gameya_rounds)) }
            items(summary.rounds, key = { it.round }) { round ->
                RoundCard(round, state.members, gameya, onTogglePaid = viewModel::togglePaid, onTogglePayout = viewModel::togglePayout)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.gameya_delete_title), stringResource(R.string.gameya_delete_body), stringResource(R.string.action_delete), viewModel::delete, { confirmDelete = false })
    }
}

@Composable
private fun RoundCard(
    round: GameyaRound,
    members: List<GameyaMember>,
    gameya: Gameya,
    onTogglePaid: (String, Int, Boolean) -> Unit,
    onTogglePayout: (GameyaRound, Boolean) -> Unit,
) {
    val dates = rememberDateFormatter()
    val finance = LocalFinanceColors.current
    val haptics = rememberHaptics()
    val today = LocalDate.now()
    val overdue = round.date.isBefore(today) && round.paidMemberIds.size < members.size
    PressableCard(onClick = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gameya_round_n, round.round), style = MaterialTheme.typography.titleMedium)
                Text(round.date.format(dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.gameya_paid_count, round.paidMemberIds.size, members.size),
                style = MaterialTheme.typography.labelLarge,
                color = if (overdue) finance.expense else MaterialTheme.colorScheme.onSurface,
            )
        }
        round.recipient?.let { recipient ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MIcons.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.gameya_recipient, recipient.name),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 6.dp).weight(1f),
                )
                Text(stringResource(R.string.gameya_payout_received), style = MaterialTheme.typography.labelMedium)
                GlassSwitch(
                    checked = round.payout != null,
                    onCheckedChange = { haptics.confirm(); onTogglePayout(round, it) },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        round.myStatus?.let { status ->
            val (text, color) = when (status) {
                ContributionStatus.PAID -> stringResource(R.string.gameya_status_paid) to finance.income
                ContributionStatus.OVERDUE -> stringResource(R.string.gameya_status_overdue) to finance.expense
                ContributionStatus.DUE_TODAY -> stringResource(R.string.gameya_status_due_today) to finance.warning
                ContributionStatus.SCHEDULED -> stringResource(R.string.gameya_status_scheduled) to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(stringResource(R.string.gameya_my_status, text), color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            members.forEach { member ->
                val paid = member.id in round.paidMemberIds
                FilterChip(colors = glassChipColors(),
                    selected = paid,
                    onClick = { haptics.tick(); onTogglePaid(member.id, round.round, !paid) },
                    label = { Text(if (member.isMe) stringResource(R.string.gameya_me_suffix, member.name) else member.name, maxLines = 1) },
                    leadingIcon = { Icon(if (paid) MIcons.Check else MIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        Text(
            stringResource(R.string.gameya_each_pays, LocalMoneyFormatter.current.format(gameya.contributionMinor, gameya.currency)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

data class GameyaForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val contribution: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
    val monthly: Boolean = true,
    val intervalDays: String = "30",
    val start: LocalDate = LocalDate.now(),
    val organizer: String = "",
    val members: List<MemberDraft> = listOf(MemberDraft(), MemberDraft()),
    val meIndex: Int? = 0,
)

@HiltViewModel
class GameyaEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: GameyaRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = savedState.toRoute<GameyaEditorRoute>().id
    private var original: Gameya? = null
    private val _form = MutableStateFlow(GameyaForm())
    val form: StateFlow<GameyaForm> = _form.asStateFlow()
    val effects = EffectChannel<EditorEffect>()

    init {
        viewModelScope.launch {
            val existing = id?.let { repo.get(it) }
            original = existing
            _form.value = if (existing != null) {
                val members = repo.members(existing.id)
                GameyaForm(
                    loaded = true, isNew = false, name = existing.name,
                    contribution = Money.toPlainString(existing.contributionMinor, existing.currency), currency = existing.currency,
                    monthly = existing.monthly, intervalDays = existing.intervalDays.toString(), start = LocalDate.ofEpochDay(existing.startEpochDay),
                    organizer = existing.organizer, members = members.map { MemberDraft(it.id, it.name) }, meIndex = members.indexOfFirst { it.isMe }.takeIf { it >= 0 },
                )
            } else {
                GameyaForm(loaded = true, currency = settings.current().primaryCurrency, start = LocalDate.now(clock))
            }
        }
    }

    fun update(block: (GameyaForm) -> GameyaForm) = _form.update(block)

    fun move(index: Int, delta: Int) = _form.update { f ->
        val target = index + delta
        if (target !in f.members.indices) return@update f
        val list = f.members.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        val me = when (f.meIndex) {
            index -> target
            target -> index
            else -> f.meIndex
        }
        f.copy(members = list, meIndex = me)
    }

    fun remove(index: Int) = _form.update { f ->
        val me = when {
            f.meIndex == index -> null
            f.meIndex != null && f.meIndex > index -> f.meIndex - 1
            else -> f.meIndex
        }
        f.copy(members = f.members.filterIndexed { i, _ -> i != index }, meIndex = me)
    }

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            effects.attempt {
                repo.save(
                    Gameya(
                        id = original?.id ?: newId(),
                        name = f.name,
                        contributionMinor = Money.parse(f.contribution, f.currency) ?: 0,
                        currency = f.currency,
                        intervalDays = f.intervalDays.toIntOrNull()?.coerceIn(1, 365) ?: 30,
                        monthly = f.monthly,
                        startEpochDay = f.start.toEpochDay(),
                        organizer = f.organizer.trim(),
                        note = original?.note.orEmpty(),
                        updatedAt = clock.millis(),
                    ),
                    f.members,
                    f.meIndex,
                )
            }
        }
    }
}

@Composable
fun GameyaEditorScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: GameyaEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleEditorEffects(viewModel.effects.flow, snackbar, onDone)
    Scaffold(
        topBar = { MasroufiTopBar(stringResource(if (form.isNew) R.string.gameya_add else R.string.gameya_edit), onBack = onBack) },
        snackbarHost = { GlassSnackbarHost(snackbar) },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextInput(form.name, { v -> viewModel.update { it.copy(name = v) } }, stringResource(R.string.field_name))
            AmountField(form.contribution, { v -> viewModel.update { it.copy(contribution = v) } }, form.currency, label = stringResource(R.string.gameya_contribution))
            CurrencyField(form.currency, { c -> viewModel.update { it.copy(currency = c) } })
            SegmentedChoice(
                listOf(true, false), form.monthly, { m -> viewModel.update { it.copy(monthly = m) } },
                { stringResource(if (it) R.string.gameya_monthly else R.string.gameya_custom_interval) },
            )
            if (!form.monthly) {
                TextInput(
                    form.intervalDays,
                    { v -> viewModel.update { it.copy(intervalDays = v.filter(Char::isDigit).take(3)) } },
                    stringResource(R.string.gameya_interval_days),
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                )
            }
            DateChip(form.start, { d -> viewModel.update { it.copy(start = d) } }, label = stringResource(R.string.gameya_first_round))
            TextInput(form.organizer, { v -> viewModel.update { it.copy(organizer = v) } }, stringResource(R.string.gameya_organizer_optional))

            Text(stringResource(R.string.gameya_members_order), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.gameya_members_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            form.members.forEachIndexed { index, draft ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = form.meIndex == index, onClick = { viewModel.update { it.copy(meIndex = index) } })
                    OutlinedTextField(colors = glassFieldColors(), shape = MaterialTheme.shapes.medium,
                        value = draft.name,
                        onValueChange = { v -> viewModel.update { f -> f.copy(members = f.members.mapIndexed { i, old -> if (i == index) old.copy(name = v) else old }) } },
                        label = { Text(stringResource(R.string.gameya_member_n, index + 1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) { Icon(MIcons.ArrowUp, stringResource(R.string.action_move_up)) }
                    IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < form.members.lastIndex) { Icon(MIcons.ArrowDown, stringResource(R.string.action_move_down)) }
                    IconButton(onClick = { viewModel.remove(index) }, enabled = form.members.size > 2) { Icon(MIcons.Trash, stringResource(R.string.action_delete)) }
                }
            }
            GlassSecondaryButton(onClick = { viewModel.update { it.copy(members = it.members + MemberDraft()) } }) {
                Icon(MIcons.Plus, contentDescription = null)
                Text(stringResource(R.string.gameya_add_member), modifier = Modifier.padding(start = 8.dp))
            }
            Text(stringResource(R.string.gameya_me_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GlassButton(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.action_save)) }
        }
    }
}
