package com.masroufi.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatDayDate
import com.masroufi.app.core.formatPounds
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.pockets.DashedRow
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.AllCaughtUpRow
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** الثوابت (decision 4): recurring fixed costs with their due state, "ادفعها" (the add sheet), add and edit. */
@Composable
fun FixedCostsScreen(
    viewModel: FixedCostsViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onOpenAddSheet: (AddPrefill?) -> Unit,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.fixed_title), onBack)
        when {
            s.loading -> repeat(3) { RowSkeleton() }
            s.items.isEmpty() -> AllCaughtUpRow()
            else -> TzCard(padding = 0.dp) {
                s.items.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
                    FixedCostRow(item, onOpen = { onOpen(item.row.id) }, onPay = { onOpenAddSheet(viewModel.payPrefill(item.row)) })
                }
            }
        }
        if (!s.loading) DashedRow(stringResource(R.string.fixed_add), onNew)
    }
}

@Composable
private fun FixedCostRow(item: FixedCostUi, onOpen: () -> Unit, onPay: () -> Unit) {
    val haptics = LocalHaptics.current
    val row = item.row
    val locale = LocalConfiguration.current.locales[0]
    val (label, color) = when (val d = item.due) {
        DueState.Paid -> stringResource(R.string.fixed_paid) to Tz.turquoise
        is DueState.Due -> stringResource(R.string.fixed_due, formatDayDate(d.date, locale)) to Tz.text2
        DueState.Overdue -> stringResource(R.string.fixed_overdue) to Tz.carnelianText
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpen() }.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, style = TzType.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text, maxLines = 1)
            Text(
                row.pocketName + " · " + stringResource(R.string.fixed_day, row.nextDue.dayOfMonth.toString()),
                style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPounds(row.amount), style = TzType.titleTab.copy(fontSize = 18.sp), color = Tz.text)
            Text(label, style = TzType.caption.copy(fontSize = 12.sp), color = color)
        }
        if (item.due != DueState.Paid) TextLink(stringResource(R.string.fixed_pay), { haptics.perform(HapticLevel.Tap); onPay() })
    }
}
