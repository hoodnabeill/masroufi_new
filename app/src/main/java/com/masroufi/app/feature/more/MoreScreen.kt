@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.masroufi.app.feature.more

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.ui.theme.continuous
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.pressClick
import com.masroufi.app.ui.components.GroupedRow
import com.masroufi.app.ui.components.GroupedSection
import com.masroufi.app.data.model.DebtDirection
import com.masroufi.app.data.model.DebtWithPaid
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.WalletWithBalance
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.DebtRepository
import com.masroufi.app.data.repo.GameyaRepository
import com.masroufi.app.data.repo.ProgressRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.domain.DebtMath
import com.masroufi.app.domain.GameyaMath
import com.masroufi.app.domain.ProgressSnapshot
import com.masroufi.app.domain.Schedule
import com.masroufi.app.feature.analytics.InsightsViewModel
import com.masroufi.app.feature.progress.levelTitle
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.staggerIn
import com.masroufi.app.ui.navigation.DockClearance
import com.masroufi.app.ui.navigation.TabInsets
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.format.TextStyle
import javax.inject.Inject

data class MoreState(
    val progress: ProgressSnapshot? = null,
    val wallets: List<WalletWithBalance> = emptyList(),
    val rules: List<RecurringRule> = emptyList(),
    val debts: List<DebtWithPaid> = emptyList(),
    /** The next turn the user cashes out in any gameya, if they have one coming. */
    val nextTurn: LocalDate? = null,
    /** True once the data has a copy somewhere (an exported backup or WebDAV sync). */
    val backedUp: Boolean = true,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    progress: ProgressRepository,
    wallets: WalletRepository,
    recurring: RecurringRepository,
    debts: DebtRepository,
    gameyas: GameyaRepository,
    settings: SettingsRepository,
    clock: Clock,
) : ViewModel() {
    private val nextTurn = combine(gameyas.observeAll(), gameyas.observeAllMembers(), gameyas.observeAllPayments(), gameyas.observeAllPayouts()) { g, m, p, o ->
        val today = LocalDate.now(clock)
        g.mapNotNull { gameya ->
            GameyaMath.summarize(gameya, m.filter { it.gameyaId == gameya.id }, p.filter { it.gameyaId == gameya.id }, o.filter { it.gameyaId == gameya.id }, today)
                .myPayoutRound?.takeIf { it.payout == null }?.date
        }.minOrNull()
    }

    val state: StateFlow<MoreState> = combine(
        combine(progress.observe(), wallets.observeWallets(), ::Pair),
        recurring.observeAll(),
        debts.observeDebts(),
        nextTurn,
        combine(settings.settings, settings.sync, ::Pair),
    ) { (p, w), r, d, turn, (s, sync) ->
        MoreState(p, w.filter { !it.wallet.archived }, r, d, turn, backedUp = s.lastBackupAt != null || sync.isConfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreState())
}

/**
 * المزيد (handoff More.dc.html): your profile and progress, the monthly recap, a backup nudge until the data has a
 * copy somewhere, the money tools as a grid with live summaries, then the app's own settings.
 */
@Composable
fun MoreScreen(
    onWallets: () -> Unit,
    onRecurring: () -> Unit,
    onGameya: () -> Unit,
    onDebts: () -> Unit,
    onSplit: () -> Unit,
    onAnalytics: () -> Unit,
    onCategories: () -> Unit,
    onBackup: () -> Unit,
    onSecurity: () -> Unit,
    onSettings: () -> Unit,
    onRecap: () -> Unit,
    onAchievements: () -> Unit,
    onLegal: (String) -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
    insights: InsightsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val health by insights.state.collectAsStateWithLifecycle()
    val money = LocalMoneyFormatter.current
    val finance = LocalFinanceColors.current
    val locale = LocalConfiguration.current.locales[0]
    val score = health?.factors?.score

    val fixed = s.rules.filter { it.active && it.nextEpochDay != null && it.type == TransactionType.EXPENSE }
        .groupBy { it.currency }
        .map { (c, list) -> money.format(list.sumOf { Schedule.monthlyMinor(it.amountMinor, it.frequency, it.interval) }, c) }
    val open = s.debts.filter { it.paidMinor < it.debt.originalMinor }
    fun debtTotal(direction: DebtDirection) = open.filter { it.debt.direction == direction }.groupBy { it.debt.currency }
        .map { (c, list) -> money.format(list.sumOf { DebtMath.remaining(it.debt.originalMinor, it.paidMinor) }, c) }
        .ifEmpty { listOf("0") }.joinToString(" · ")

    val tools = listOf(
        Tool(MIcons.Wallet, finance.categories[5], R.string.wallets_title, s.wallets.joinToString(" · ") { it.wallet.name }.ifBlank { stringResource(R.string.more_wallets_sub) }, onWallets),
        Tool(MIcons.Repeat, MaterialTheme.colorScheme.secondary, R.string.more_recurring_title, if (fixed.isEmpty()) stringResource(R.string.more_recurring_sub) else stringResource(R.string.more_fixed_monthly, fixed.joinToString(" · ")), onRecurring),
        Tool(MIcons.Insights, finance.transfer, R.string.analytics_title, score?.let { stringResource(R.string.more_health, it) } ?: stringResource(R.string.more_analytics_sub), onAnalytics),
        Tool(
            MIcons.Gameya, finance.categories[3], R.string.gameya_title,
            s.nextTurn?.let { stringResource(R.string.more_gameya_turn, it.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)) } ?: stringResource(R.string.more_gameya_sub),
            onGameya,
        ),
        Tool(MIcons.Handshake, finance.categories[2], R.string.debts_title, stringResource(R.string.more_debts_line, debtTotal(DebtDirection.OWED_TO_ME), debtTotal(DebtDirection.I_OWE)), onDebts),
        Tool(MIcons.Split, finance.categories[4], R.string.split_title, stringResource(R.string.more_split_line), onSplit),
    )

    val largeTitle = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(largeTitle.nestedScrollConnection), contentWindowInsets = TabInsets, topBar = { MasroufiTopBar(stringResource(R.string.tab_more), scrollBehavior = largeTitle) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).edgeFade(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = DockClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            s.progress?.let { p -> item(key = "profile") { Profile(p, score, onAchievements, Modifier.staggerIn(0)) } }
            item(key = "recap") { RecapEntry(onRecap, Modifier.staggerIn(1)) }
            if (!s.backedUp) item(key = "backup") { BackupNudge(onBackup) }
            item(key = "tools-h") { Header(R.string.more_money) }
            items(tools.chunked(2).size) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tools.chunked(2)[row].forEach { ToolTile(it, Modifier.weight(1f)) }
                }
            }
            item(key = "app-h") { Header(R.string.more_app) }
            item(key = "app") {
                AppList(
                    listOf(
                        AppEntry(MIcons.Lock, 0, R.string.security_title, R.string.more_security_line, onSecurity),
                        AppEntry(MIcons.Cloud, 1, R.string.backup_title, if (s.backedUp) R.string.more_backup_on else R.string.more_backup_off, onBackup),
                        AppEntry(MIcons.Shapes, 5, R.string.categories_title, R.string.more_categories_line, onCategories),
                        AppEntry(MIcons.Gear, null, R.string.settings_title, R.string.more_settings_line, onSettings),
                        AppEntry(MIcons.Eye, 7, R.string.legal_privacy, R.string.legal_privacy_sub) { onLegal("privacy") },
                        AppEntry(MIcons.Clip, null, R.string.legal_terms, R.string.legal_terms_sub) { onLegal("terms") },
                    ),
                )
            }
        }
    }
}

private data class Tool(val icon: ImageVector, val color: Color, val title: Int, val sub: String, val onClick: () -> Unit)

@Composable
private fun Header(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalGlass.current.textSecondary,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp).semantics { heading() },
    )
}

/** ملفك: level, the way to the next one, and three numbers (streak, health, badges). */
@Composable
private fun Profile(p: ProgressSnapshot, score: Int?, onAchievements: () -> Unit, modifier: Modifier) {
    val glass = LocalGlass.current
    val violet = MaterialTheme.colorScheme.secondary
    val level = p.level
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(violet.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(MIcons.Person, contentDescription = null, tint = violet, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.more_profile), style = MaterialTheme.typography.titleMedium)
                Text(
                    level.nextXp?.let { stringResource(R.string.more_level_line, level.number, levelTitle(level), level.xp, it) }
                        ?: stringResource(R.string.level_label, level.number, levelTitle(level)),
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.textSecondary,
                )
            }
            Row(
                Modifier.heightIn(min = 40.dp).clip(CircleShape).background(violet.copy(alpha = 0.18f)).clickable(role = Role.Button, onClick = onAchievements).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.achievements_title), style = MaterialTheme.typography.labelLarge, color = violet)
                Icon(MIcons.ChevronForward, contentDescription = null, tint = violet, modifier = Modifier.size(16.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(glass.track)) {
            Box(Modifier.fillMaxWidth(level.progress.coerceAtLeast(0.02f)).height(6.dp).clip(CircleShape).background(violet))
        }
        HorizontalDivider(color = glass.strokeBottom)
        Row {
            Stat(stringResource(R.string.more_streak), androidx.compose.ui.res.pluralStringResource(R.plurals.streak_days_short, p.streak.current, p.streak.current), LocalFinanceColors.current.streak, Modifier.weight(1f), divider = false)
            Stat(stringResource(R.string.health_title), score?.toString() ?: "—", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Stat(stringResource(R.string.achievements_badges), stringResource(R.string.badges_count, p.badges.count { it.earned }, p.badges.size), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier, divider: Boolean = true) {
    val glass = LocalGlass.current
    Row(modifier) {
        if (divider) Box(Modifier.width(1.dp).height(40.dp).background(glass.strokeBottom))
        Column(Modifier.padding(start = if (divider) 12.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = glass.textTertiary)
            Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun RecapEntry(onClick: () -> Unit, modifier: Modifier) {
    val violet = MaterialTheme.colorScheme.secondary
    val ink = MaterialTheme.colorScheme.onSecondary
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(violet).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(continuous(14)).background(Color.Black.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(MIcons.Sparkle, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.recap_entry), style = MaterialTheme.typography.titleMedium, color = ink)
            Text(stringResource(R.string.more_recap_line), style = MaterialTheme.typography.bodySmall, color = ink)
        }
        Icon(MIcons.ChevronForward, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
    }
}

/** Shown until the data has a copy outside the phone. */
@Composable
private fun BackupNudge(onBackup: () -> Unit) {
    val coral = LocalFinanceColors.current.over
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(coral.copy(alpha = 0.10f)).border(1.dp, coral.copy(alpha = 0.3f), MaterialTheme.shapes.large).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(continuous(13)).background(coral.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(MIcons.Cloud, contentDescription = null, tint = coral, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.more_backup_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.more_backup_body), style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.more_backup_cta),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.heightIn(min = 40.dp).clip(CircleShape).background(coral).clickable(role = Role.Button, onClick = onBackup).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ToolTile(tool: Tool, modifier: Modifier) {
    val glass = LocalGlass.current
    Column(
        modifier
            .pressClick(MaterialTheme.shapes.medium, onClick = tool.onClick)
            .heightIn(min = 104.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(glass.fill)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        IconBadge(tool.icon, tool.color, size = 36.dp)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(tool.sub, style = MaterialTheme.typography.labelSmall, color = glass.textTertiary, maxLines = 1)
    }
}

private data class AppEntry(val icon: ImageVector, val color: Int?, val title: Int, val sub: Int, val onClick: () -> Unit)

/** The app rows as an inset-grouped list with Settings-style icon tiles (grey for the plain ones). */
@Composable
private fun AppList(rows: List<AppEntry>) {
    GroupedSection(
        rows = rows.map { e ->
            @Composable {
                GroupedRow(
                    title = stringResource(e.title),
                    subtitle = stringResource(e.sub),
                    icon = e.icon,
                    iconTint = e.color?.let { categoryColor(it) } ?: Color(0xFF8E8E93),
                    onClick = e.onClick,
                )
            }
        },
        inset = 58.dp,
    )
}

