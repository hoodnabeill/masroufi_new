package com.masroufi.app.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.SectionHeader
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.components.staggerIn
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import com.masroufi.app.ui.navigation.DockClearance
import com.masroufi.app.ui.navigation.TabInsets

private data class Entry(val icon: ImageVector, val title: Int, val subtitle: Int, val onClick: () -> Unit)

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
    onPremium: () -> Unit,
) {
    val money = listOf(
        Entry(Icons.Outlined.AccountBalanceWallet, R.string.wallets_title, R.string.more_wallets_sub, onWallets),
        Entry(Icons.Outlined.Repeat, R.string.recurring_title, R.string.more_recurring_sub, onRecurring),
        Entry(Icons.Outlined.Groups, R.string.gameya_title, R.string.more_gameya_sub, onGameya),
        Entry(Icons.Outlined.Handshake, R.string.debts_title, R.string.more_debts_sub, onDebts),
        Entry(Icons.Outlined.CallSplit, R.string.split_title, R.string.more_split_sub, onSplit),
        Entry(Icons.Outlined.Insights, R.string.analytics_title, R.string.more_analytics_sub, onAnalytics),
        Entry(Icons.Outlined.Category, R.string.categories_title, R.string.more_categories_sub, onCategories),
    )
    val app = listOf(
        Entry(Icons.Outlined.Backup, R.string.backup_title, R.string.more_backup_sub, onBackup),
        Entry(Icons.Outlined.Lock, R.string.security_title, R.string.more_security_sub, onSecurity),
        Entry(Icons.Outlined.Settings, R.string.settings_title, R.string.more_settings_sub, onSettings),
        Entry(Icons.Outlined.WorkspacePremium, R.string.premium_title, R.string.more_premium_sub, onPremium),
    )
    Scaffold(contentWindowInsets = TabInsets, topBar = { MasroufiTopBar(stringResource(R.string.tab_more)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = DockClearance)) {
            item { SectionHeader(stringResource(R.string.more_money), Modifier.padding(horizontal = 16.dp)) }
            item { Group(money, Modifier.staggerIn(0)) }
            item { SectionHeader(stringResource(R.string.more_app), Modifier.padding(horizontal = 16.dp)) }
            item { Group(app, Modifier.staggerIn(1)) }
        }
    }
}

@Composable
private fun Row(entry: Entry) {
    ListItem(
        modifier = Modifier.clickable(onClick = entry.onClick),
        leadingContent = { IconBadge(entry.icon, MaterialTheme.colorScheme.primary, size = 40.dp) },
        headlineContent = { Text(stringResource(entry.title)) },
        supportingContent = { Text(stringResource(entry.subtitle)) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}

/** iOS-style grouped list: one glass slab per section with inset hairlines between rows. */
@Composable
private fun Group(entries: List<Entry>, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp).glass(MaterialTheme.shapes.large).padding(vertical = 4.dp)) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(entry)
        }
    }
}
