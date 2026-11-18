package com.masroufi.app.feature.buy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.db.WishEntity
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

const val WishCardTag = "wish_card"

/** The two answers of a due wish (BeforeBuy.dc.html): the turquoise "صرفت نظر" and the outlined "اشتريتها", 44 dp. */
@Composable
fun WishButtons(onChangedMind: () -> Unit, onBought: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(stringResource(R.string.buy_changed_mind), Modifier.weight(1f), filled = true) { haptics.perform(HapticLevel.Tap); onChangedMind() }
        Pill(stringResource(R.string.buy_bought), Modifier, filled = false) { haptics.perform(HapticLevel.Tap); onBought() }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.height(44.dp).clip(shape)
            .then(if (filled) Modifier.background(Tz.turquoise) else Modifier.border(1.dp, Tz.line, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = TzType.label.copy(fontSize = 13.sp, fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal),
            color = if (filled) Tz.turquoiseInk else Tz.text,
        )
    }
}

/** Home's 48-hour question: Qersh's bubble with the 07-COPY line and the two answers. */
@Composable
fun WishQuestionCard(wish: WishEntity, onChangedMind: () -> Unit, onBought: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Row(
        modifier.fillMaxWidth().testTag(WishCardTag),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Qersh(QershMood.Thinking, 52.dp)
        Column(
            Modifier.weight(1f).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.home_wish_line, wish.name),
                style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.6.em),
                color = Tz.text2,
            )
            WishButtons(onChangedMind, onBought)
        }
    }
}

/** "تحوّل X للحصّالة؟": X is the price, or less when that is all that is spendable (then the sheet says so). */
@Composable
fun JarAskSheet(ask: WishAsk, onAnswer: (toJar: Boolean) -> Unit, onDismiss: () -> Unit) {
    val haptics = LocalHaptics.current
    TzSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.wish_jar_title, ask.name), style = TzType.cardHeading, color = Tz.text)
            Text(
                if (ask.offer < ask.price) stringResource(R.string.wish_jar_body_capped, formatPounds(ask.offer), formatPounds(ask.price))
                else stringResource(R.string.wish_jar_body, formatPounds(ask.offer)),
                style = TzType.body, color = Tz.text2,
            )
            PrimaryButton(stringResource(R.string.wish_jar_move), { haptics.perform(HapticLevel.Confirm); onAnswer(true) }, Modifier.fillMaxWidth(), height = 52.dp)
            SecondaryButton(stringResource(R.string.wish_jar_skip), { haptics.perform(HapticLevel.Tap); onAnswer(false) }, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}
