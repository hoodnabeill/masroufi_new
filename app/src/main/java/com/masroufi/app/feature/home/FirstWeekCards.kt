package com.masroufi.app.feature.home

import androidx.annotation.StringRes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.add.Keypad
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.LocalDate

const val FirstWeekCardTag = "first_week_card"

/** 08-ONBOARDING.md "After onboarding". Declared in priority order: the first one that applies shows. */
enum class FirstWeekCard(val id: String, @StringRes val text: Int) {
    Money("money", R.string.ob_money_bubble),
    FirstEntry("first_entry", R.string.fw_first_entry),
    FirstEvening("first_evening", R.string.fw_first_evening),
    Day2("day2", R.string.fw_day2),
    Day3("day3", R.string.fw_day3),
    Day5("day5", R.string.fw_day5),
}

private const val EVENING_HOUR = 18
private const val MONEY_CARD_MIN_ENTRIES = 3

/**
 * The one card Home shows now, or null. Days count from [firstOpenDay] (day 1 = that day). A card is gone for good once
 * dismissed, and none shows while the day is end-of-month. Day 7 (the Plus trial) is not a card: Plus has no trial.
 */
fun firstWeekCard(
    entryCount: Int,
    moneyUnknown: Boolean,
    dismissed: Set<String>,
    firstOpenDay: Long?,
    today: LocalDate,
    hour: Int,
    rowOpen: Boolean,
    endOfMonth: Boolean,
): FirstWeekCard? {
    if (endOfMonth) return null
    val day = firstOpenDay?.let { today.toEpochDay() - it + 1 } ?: 0L
    return FirstWeekCard.values().firstOrNull { c ->
        c.id !in dismissed && when (c) {
            FirstWeekCard.Money -> moneyUnknown && entryCount >= MONEY_CARD_MIN_ENTRIES
            FirstWeekCard.FirstEntry -> entryCount >= 1
            FirstWeekCard.FirstEvening -> day == 1L && hour >= EVENING_HOUR && rowOpen
            FirstWeekCard.Day2 -> day >= 2
            FirstWeekCard.Day3 -> day >= 3
            FirstWeekCard.Day5 -> day >= 5
        }
    }
}

/** A Qersh bubble with a close at its end; the money card adds its question above the line and a button below. */
@Composable
fun FirstWeekCardView(card: FirstWeekCard, onDismiss: () -> Unit, onMoney: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    val close = stringResource(R.string.fw_close)
    Row(
        modifier.fillMaxWidth().testTag(FirstWeekCardTag),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Qersh(if (card == FirstWeekCard.Money) QershMood.Thinking else QershMood.Happy, 52.dp)
        Row(
            Modifier.weight(1f).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
                .padding(start = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (card == FirstWeekCard.Money) {
                    Text(stringResource(R.string.ob_money_title), style = TzType.cardHeading, color = Tz.text)
                }
                Text(stringResource(card.text), style = TzType.body.copy(fontSize = 14.sp, lineHeight = 1.6.em), color = Tz.text2)
                if (card == FirstWeekCard.Money) {
                    TextLink(stringResource(R.string.fw_money_button), { onMoney() })
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

/** "How much do you have now?" in a small sheet: the amount, the keypad, one button. */
@Composable
fun MoneySheet(onDone: (Long) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(AmountInput()) }
    TzSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.ob_money_title), style = TzType.cardHeading, color = Tz.text)
            Text(
                (if (input.text.isEmpty()) "0" else input.text) + " " + stringResource(R.string.paper_currency),
                style = TzType.hero.copy(fontSize = 48.sp, color = Tz.yellow, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            Keypad({ input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            PrimaryButton(
                stringResource(R.string.fw_money_button), { onDone(input.toPiastres()) },
                Modifier.fillMaxWidth(), enabled = input.text.isNotEmpty(),
            )
        }
    }
}
