package com.masroufi.app.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.components.MIcons
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
        Entry(MIcons.Wallet, R.string.wallets_title, R.string.more_wallets_sub, onWallets),
        Entry(MIcons.Insights, R.string.analytics_title, R.string.more_analytics_sub, onAnalytics),
        Entry(MIcons.Repeat, R.string.recurring_title, R.string.more_recurring_sub, onRecurring),
        Entry(MIcons.Gameya, R.string.gameya_title, R.string.more_gameya_sub, onGameya),
        Entry(MIcons.Handshake, R.string.debts_title, R.string.more_debts_sub, onDebts),
        Entry(MIcons.Split, R.string.split_title, R.string.more_split_sub, onSplit),
        Entry(MIcons.Shapes, R.string.categories_title, R.string.more_categories_sub, onCategories),
    )
    val app = listOf(
        Entry(MIcons.Cloud, R.string.backup_title, R.string.more_backup_sub, onBackup),
        Entry(MIcons.Lock, R.string.security_title, R.string.more_security_sub, onSecurity),
        Entry(MIcons.Gear, R.string.settings_title, R.string.more_settings_sub, onSettings),
        Entry(MIcons.Crown, R.string.premium_title, R.string.more_premium_sub, onPremium),
    )
    Scaffold(contentWindowInsets = TabInsets, topBar = { MasroufiTopBar(stringResource(R.string.tab_more)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).edgeFade(), contentPadding = PaddingValues(top = 16.dp, bottom = DockClearance)) {
            item { SectionHeader(stringResource(R.string.more_money), Modifier.padding(horizontal = 16.dp)) }
            item { Group(money, Modifier.staggerIn(0)) }
            item { SectionHeader(stringResource(R.string.more_app), Modifier.padding(horizontal = 16.dp)) }
            item { Group(app, Modifier.staggerIn(1), colorOffset = 7) }
        }
    }
}

@Composable
private fun Row(entry: Entry, tint: Int) {
    ListItem(
        modifier = Modifier.clickable(onClick = entry.onClick),
        leadingContent = { IconBadge(entry.icon, categoryColor(tint), size = 40.dp) },
        headlineContent = { Text(stringResource(entry.title)) },
        supportingContent = { Text(stringResource(entry.subtitle)) },
        trailingContent = { Icon(MIcons.ChevronForward, contentDescription = null) },
    )
}

/** iOS-style grouped list: one glass slab per section with inset hairlines between rows. */
@Composable
private fun Group(entries: List<Entry>, modifier: Modifier, colorOffset: Int = 0) {
    Column(modifier.padding(horizontal = 16.dp).glass(MaterialTheme.shapes.large).padding(vertical = 4.dp)) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(entry, colorOffset + index)
        }
    }
}
