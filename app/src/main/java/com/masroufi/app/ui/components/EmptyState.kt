package com.masroufi.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/**
 * EmptyStates.dc.html: Surface card, radius 28, 22 x 18 padding, illustration, Lalezar 26 title, a 280 dp body line
 * in Text2, then the action. [primaryAction] = the yellow pill (52 dp); otherwise a yellow [TextLink] (the mock's
 * outlined pill is not a system component). No [action] = no button.
 */
@Composable
fun EmptyState(
    illustration: @Composable () -> Unit,
    @StringRes title: Int,
    @StringRes line: Int,
    @StringRes action: Int? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    primaryAction: Boolean = false,
) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        illustration()
        Text(stringResource(title), style = TzType.titleTab.copy(fontSize = 26.sp, textAlign = TextAlign.Center), color = Tz.text)
        Text(
            stringResource(line),
            style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.7.em, textAlign = TextAlign.Center),
            color = Tz.text2,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        if (action != null && onAction != null) {
            val go = { haptics.perform(HapticLevel.Tap); onAction() }
            if (primaryAction) {
                PrimaryButton(stringResource(action), go, Modifier.padding(top = 4.dp), height = 52.dp, textStyle = TzType.button.copy(fontSize = 18.sp))
            } else {
                TextLink(stringResource(action), go, Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The "all caught up" row (EmptyStates.dc.html): turquoise-dim band, a turquoise tick disc, two lines. */
@Composable
fun AllCaughtUpRow(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.turquoiseDim)
            .padding(horizontal = 18.dp, vertical = 16.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Tz.turquoise), contentAlignment = Alignment.Center) {
            TzIcon(TzIcons.check, 22.dp, null, tint = Tz.turquoiseInk)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.empty_caught_title), style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = Tz.text)
            Text(stringResource(R.string.empty_caught_line), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.turquoiseDimText)
        }
    }
}
