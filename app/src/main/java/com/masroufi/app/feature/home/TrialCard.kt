package com.masroufi.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.core.formatDayMonth
import com.masroufi.app.ui.components.LocalEndOfMonthMode
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.pluralText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

const val TrialCardTag = "trial_card"
const val TrialEndsSoonTag = "trial_ends_soon"

/**
 * Qersh's honest trial offer in the first-week card's slot (decision 3): what Plus holds, the date the 14 days would end,
 * no payment method, one tap ("ابدأ التجربة") to start. It has no close: it goes when the trial starts.
 */
@Composable
fun TrialCard(offer: TrialOffer, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val locale = LocalConfiguration.current.locales[0]
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Row(modifier.fillMaxWidth().testTag(TrialCardTag), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        Qersh(QershMood.Happy, 52.dp)
        Column(
            Modifier.weight(1f).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.home_trial_title), style = TzType.cardHeading, color = Tz.text)
            Text(
                stringResource(R.string.home_trial_body, formatDayMonth(offer.endsOn, locale)),
                style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.6.em), color = Tz.text2,
            )
            TextLink(stringResource(R.string.home_trial_start), { haptics.perform(HapticLevel.Confirm); onStart() })
        }
    }
}

/**
 * The quiet line of the last three days. Tapping it opens the paywall, except in end-of-month mode (the paywall never
 * shows there, so the line stays plain text).
 */
@Composable
fun TrialEndsSoonLine(soon: TrialEndsSoon, onOpenPaywall: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val inMode = LocalEndOfMonthMode.current
    val dayWord = when (soon.daysLeft) {
        0 -> stringResource(R.string.home_trial_today)
        1 -> stringResource(R.string.home_trial_tomorrow)
        else -> stringResource(R.string.home_trial_after, pluralText(R.plurals.paywall_val_days, soon.daysLeft))
    }
    val tap = if (inMode) Modifier else Modifier.clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpenPaywall() }
    Text(
        stringResource(R.string.home_trial_soon, dayWord),
        modifier.fillMaxWidth().testTag(TrialEndsSoonTag).defaultMinSize(minHeight = 48.dp).then(tap).padding(vertical = 14.dp),
        style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2,
    )
}
