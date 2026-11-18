package com.masroufi.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

const val OfferCardTag = "eom_offer_card"

/**
 * Qersh's offer of end-of-month mode, in the first-week card's slot (04-SCREENS "Main" 7 and the plan's decision 5):
 * "شغّله" switches it on, the close hides it until the next payday.
 */
@Composable
fun EndOfMonthOfferCard(onSwitchOn: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    val close = stringResource(R.string.fw_close)
    Row(
        modifier.fillMaxWidth().testTag(OfferCardTag),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Qersh(QershMood.Thinking, 52.dp)
        Row(
            Modifier.weight(1f).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
                .padding(start = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.eom_offer_text), style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.6.em), color = Tz.text2)
                TextLink(stringResource(R.string.eom_offer_on), { haptics.perform(HapticLevel.Confirm); onSwitchOn() })
            }
            Box(
                Modifier.size(48.dp).semantics { contentDescription = close }
                    .clickable(role = Role.Button) {
                        haptics.perform(HapticLevel.Tap)
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) { TzIcon(TzIcons.close, 18.dp, null, tint = Tz.text2) }
        }
    }
}
