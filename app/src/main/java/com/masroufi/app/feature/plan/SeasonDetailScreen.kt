package com.masroufi.app.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.db.SeasonKind
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.Month
import java.time.format.TextStyle

/** A season's page, or a new personal occasion: one question per block. */
@Composable
fun SeasonDetailScreen(viewModel: SeasonDetailViewModel, onBack: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val free by viewModel.free.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(f.saved) { if (f.saved) onBack() }
    val season = view?.season
    val personal = viewModel.isNew || season?.kind == SeasonKind.PERSONAL
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(season?.name ?: stringResource(R.string.season_new_title), onBack)
        if (!f.loaded) return@Column
        if (personal) FormBlock(R.string.season_q_name) { NameField(f.name, viewModel::setName, R.string.season_name_hint) }
        FormBlock(R.string.season_q_date) {
            if (season?.hijriMonth != null) {
                Text(
                    stringResource(R.string.season_hijri_date, season.hijriDay ?: 1, stringArrayResource(R.array.hijri_months)[season.hijriMonth - 1]),
                    style = TzType.body.copy(fontSize = 17.sp), color = Tz.text,
                )
            } else {
                ChipRow {
                    (1..12).forEach { m ->
                        TzChip(Month.of(m).getDisplayName(TextStyle.FULL, locale), f.month == m, { viewModel.setMonth(m) }, style = ChipStyle.Choice)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(TzIcons.minus, stringResource(R.string.season_day_less)) { viewModel.stepDay(-1) }
                    Text(f.day.toString(), style = TzType.hero.copy(fontSize = 36.sp), color = Tz.text, modifier = Modifier.defaultMinSize(minWidth = 48.dp))
                    CircleButton(TzIcons.add, stringResource(R.string.season_day_more)) { viewModel.stepDay(1) }
                }
            }
        }
        FormBlock(R.string.season_q_target) { AmountKeypad(f.target, viewModel::digit, viewModel::point, viewModel::backspace) }
        val live = view
        if (live != null) {
            FormBlock(R.string.season_q_items) {
                live.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.label, style = TzType.body.copy(fontSize = 15.sp), color = Tz.text, modifier = Modifier.weight(1f))
                        Text(formatPounds(item.amount), style = TzType.rowNumber, color = Tz.text)
                        Box(
                            Modifier.size(44.dp).clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); viewModel.removeItem(item.id) },
                            contentAlignment = Alignment.Center,
                        ) { TzIcon(TzIcons.close, 18.dp, stringResource(R.string.season_item_remove, item.label)) }
                    }
                }
                val sum = live.items.sumOf { it.amount }
                if (sum > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.season_items_total, formatPounds(sum)), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.text2)
                        if (sum != f.target.toPiastres()) TextLink(stringResource(R.string.season_items_as_target), viewModel::targetFromChecklist)
                    }
                }
                TextLink(stringResource(R.string.season_item_add), { viewModel.open(SeasonSheet.Item) })
            }
            FormBlock(R.string.season_q_saved) {
                Text(
                    stringResource(R.string.jar_goal_amounts, formatPounds(live.saved), formatPounds(live.season.target)),
                    style = TzType.hero.copy(fontSize = 32.sp), color = Tz.text,
                )
                SecondaryButton(stringResource(R.string.season_put), { viewModel.open(SeasonSheet.Money) }, Modifier.fillMaxWidth(), height = 52.dp)
            }
        }
        PrimaryButton(
            stringResource(R.string.jar_save),
            { haptics.perform(HapticLevel.Confirm); viewModel.save() },
            Modifier.fillMaxWidth(),
            enabled = viewModel.canSave(f),
        )
    }
    when (sheet) {
        SeasonSheet.Item -> ItemSheet(viewModel::dismiss) { label, amount -> viewModel.addItem(label, amount); viewModel.dismiss() }
        SeasonSheet.Money -> {
            val live = view
            if (live != null) AmountSheet(live.season.name, viewModel::dismiss) { amount ->
                if (amount > free) Text(stringResource(R.string.jar_free_only, formatPounds(free)), style = TzType.label, color = Tz.carnelianText)
                PrimaryButton(stringResource(R.string.season_put), { haptics.perform(HapticLevel.Confirm); viewModel.earmark(amount) }, Modifier.fillMaxWidth(), enabled = amount in 1..free, height = 52.dp)
                if (live.saved > 0) SecondaryButton(
                    stringResource(R.string.season_release),
                    { if (amount in 1..live.saved) { haptics.perform(HapticLevel.Confirm); viewModel.release(amount) } else haptics.perform(HapticLevel.Reject) },
                    Modifier.fillMaxWidth(), height = 52.dp,
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun ItemSheet(onDismiss: () -> Unit, onAdd: (String, Long) -> Unit) {
    var label by remember { mutableStateOf("") }
    var input by remember { mutableStateOf(AmountInput()) }
    TzSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NameField(label, { label = it.take(30) }, R.string.season_item_name_hint)
            AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            PrimaryButton(
                stringResource(R.string.season_item_save), { onAdd(label, input.toPiastres()) },
                Modifier.fillMaxWidth(), enabled = label.isNotBlank() && input.toPiastres() > 0, height = 52.dp,
            )
        }
    }
}
