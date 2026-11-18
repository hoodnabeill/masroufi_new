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
import androidx.compose.runtime.LaunchedEffect
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
import com.masroufi.app.core.formatPounds
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

const val SelfPayCardTag = "selfpay_card"

/**
 * What the freelancer's self-payday did, said plainly, in the first-week card's slot: the salary landed (Heavy haptic, once)
 * and the payday ritual is offered for it; a short vault says how much it had; an empty one says nothing moved.
 */
@Composable
fun SelfPayCardView(
    card: SelfPayCard,
    onRitual: (Long) -> Unit,
    onOpenVault: () -> Unit,
    onDismiss: () -> Unit,
    onLanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    // Money moved and this is its first sight: one Heavy haptic, then it is remembered.
    LaunchedEffect(card.key, card.paid) {
        if (card.entryId != null && !card.landedSeen) {
            haptics.perform(HapticLevel.Heavy)
            onLanded()
        }
    }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    val close = stringResource(R.string.fw_close)
    val text = when {
        card.entryId == null -> stringResource(R.string.home_selfpay_empty)
        card.short -> stringResource(R.string.home_selfpay_short, formatPounds(card.paid), formatPounds(card.salary))
        else -> stringResource(R.string.home_selfpay_full, formatPounds(card.paid))
    }
    Row(
        modifier.fillMaxWidth().testTag(SelfPayCardTag),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Qersh(if (card.entryId == null) QershMood.Thinking else QershMood.Happy, 52.dp)
        Row(
            Modifier.weight(1f).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
                .padding(start = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text, style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.6.em), color = Tz.text2)
                if (card.entryId != null) {
                    TextLink(stringResource(R.string.payday_roll_link), { haptics.perform(HapticLevel.Confirm); onRitual(card.entryId) })
                } else {
                    TextLink(stringResource(R.string.home_selfpay_open), { haptics.perform(HapticLevel.Tap); onOpenVault() })
                }
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
