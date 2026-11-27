@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.masroufi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.core.formatDate
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.RestoreOutcome
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.LocalDate

const val PlusManageTag = "plus_row_manage"
const val PlusRestoreTag = "plus_row_restore"

/**
 * Settings' one Plus row (decision 7; Phase 11's Settings screen places it): "مصروفي بلس", the plan with its renewal
 * date (or the trial's end, or "not subscribed"), "إدارة الاشتراك" ([onManage] opens Google Play's page) and
 * "استرجاع الاشتراك" ([onRestore]); [restore] is the plain result line of the last restore.
 */
@Composable
fun PlusSettingsRow(
    plan: PlusPlan?,
    renewsOn: LocalDate?,
    trialEnds: LocalDate?,
    restore: RestoreOutcome?,
    onManage: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val locale = LocalConfiguration.current.locales[0]
    val status = when {
        plan != null && renewsOn != null ->
            stringResource(if (plan == PlusPlan.YEARLY) R.string.plus_row_plan_year else R.string.plus_row_plan_month) + " · " +
                stringResource(R.string.plus_row_renews, formatDate(renewsOn, locale))
        trialEnds != null -> stringResource(R.string.plus_row_trial, formatDate(trialEnds, locale))
        else -> stringResource(R.string.plus_row_none)
    }
    TzCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.plus_row_title), style = TzType.cardHeading, color = Tz.text)
            Text(status, style = TzType.body, color = Tz.text2)
            FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                TextLink(stringResource(R.string.plus_row_manage), { haptics.perform(HapticLevel.Tap); onManage() }, Modifier.testTag(PlusManageTag))
                TextLink(stringResource(R.string.plus_row_restore), { haptics.perform(HapticLevel.Tap); onRestore() }, Modifier.testTag(PlusRestoreTag))
            }
            if (restore != null) {
                Text(
                    stringResource(
                        when (restore) {
                            RestoreOutcome.FOUND -> R.string.plus_restore_found
                            RestoreOutcome.NONE -> R.string.plus_restore_none
                            RestoreOutcome.FAILED -> R.string.plus_restore_failed
                        },
                    ),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = TzType.caption, color = Tz.text2,
                )
            }
        }
    }
}
