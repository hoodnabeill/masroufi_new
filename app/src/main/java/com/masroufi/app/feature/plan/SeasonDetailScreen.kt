package com.masroufi.app.feature.plan

import com.masroufi.app.data.repo.SeasonView
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.minimumInteractiveComponentSize
import com.masroufi.app.ui.components.CappedFontScale
import com.masroufi.app.ui.components.LARGE_FONT_SCALE
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
import com.masroufi.app.ui.components.poundsText
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
    LaunchedEffect(f.saved) { if (f.saved) onBack() }
    SeasonDetailContent(
        f, view, free, sheet, viewModel.isNew, viewModel.canSave(f), onBack,
        SeasonDetailActions(
            viewModel::setName, viewModel::setMonth, viewModel::stepDay, viewModel::digit, viewModel::point, viewModel::backspace,
            viewModel::removeItem, viewModel::targetFromChecklist, viewModel::open, viewModel::dismiss, viewModel::addItem,
            viewModel::earmark, viewModel::release, viewModel::save,
        ),
    )
}

/** What the season page can do; the view model's functions in the app, no-ops in the audit. */
internal class SeasonDetailActions(
    val setName: (String) -> Unit,
    val setMonth: (Int) -> Unit,
    val stepDay: (Int) -> Unit,
    val digit: (Int) -> Unit,
    val point: () -> Unit,
    val backspace: () -> Unit,
    val removeItem: (Long) -> Unit,
    val targetFromChecklist: () -> Unit,
    val open: (SeasonSheet) -> Unit,
    val dismiss: () -> Unit,
    val addItem: (String, Long) -> Unit,
    val earmark: (Long) -> Unit,
    val release: (Long) -> Unit,
    val save: () -> Unit,
) {
    companion object { val None = SeasonDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeasonDetailContent(
    f: SeasonForm,
    view: SeasonView?,
    free: Long,
    sheet: SeasonSheet?,
    isNew: Boolean,
    canSave: Boolean,
    onBack: () -> Unit,
    vm: SeasonDetailActions,
) {
    val haptics = LocalHaptics.current
    val locale = LocalConfiguration.current.locales[0]
    val season = view?.season
    val personal = isNew || season?.kind == SeasonKind.PERSONAL
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(season?.name ?: stringResource(R.string.season_new_title), onBack)
        if (!f.loaded) return@Column
        if (personal) FormBlock(R.string.season_q_name) { NameField(f.name, vm.setName, R.string.season_name_hint) }
        FormBlock(R.string.season_q_date) {
            if (season?.hijriMonth != null) {
                Text(
                    stringResource(R.string.season_hijri_date, season.hijriDay ?: 1, stringArrayResource(R.array.hijri_months)[season.hijriMonth - 1]),
                    style = TzType.body.copy(fontSize = 17.sp), color = Tz.text,
                )
            } else {
                ChipRow {
                    (1..12).forEach { m ->
                        TzChip(Month.of(m).getDisplayName(TextStyle.FULL, locale), f.month == m, { vm.setMonth(m) }, style = ChipStyle.Choice)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(TzIcons.minus, stringResource(R.string.season_day_less)) { vm.stepDay(-1) }
                    Text(f.day.toString(), style = TzType.hero.copy(fontSize = 36.sp), color = Tz.text, modifier = Modifier.defaultMinSize(minWidth = 48.dp))
                    CircleButton(TzIcons.add, stringResource(R.string.season_day_more)) { vm.stepDay(1) }
                }
            }
        }
        FormBlock(R.string.season_q_target) { AmountKeypad(f.target, vm.digit, vm.point, vm.backspace) }
        val live = view
        if (live != null) {
            FormBlock(R.string.season_q_items) {
                live.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.label, style = TzType.body.copy(fontSize = 15.sp), color = Tz.text, modifier = Modifier.weight(1f))
                        Text(poundsText(item.amount), style = TzType.rowNumber, color = Tz.text)
                        Box(
                            Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); vm.removeItem(item.id) },
                            contentAlignment = Alignment.Center,
                        ) { TzIcon(TzIcons.close, 18.dp, stringResource(R.string.season_item_remove, item.label)) }
                    }
                }
                val sum = live.items.sumOf { it.amount }
                if (sum > 0) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.season_items_total, poundsText(sum)), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.text2)
                        if (sum != f.target.toPiastres()) TextLink(stringResource(R.string.season_items_as_target), vm.targetFromChecklist)
                    }
                }
                TextLink(stringResource(R.string.season_item_add), { vm.open(SeasonSheet.Item) })
            }
            FormBlock(R.string.season_q_saved) {
                CappedFontScale(LARGE_FONT_SCALE) {
                    Text(
                        stringResource(R.string.jar_goal_amounts, poundsText(live.saved), poundsText(live.season.target)),
                        style = TzType.hero.copy(fontSize = 32.sp), color = Tz.text,
                    )
                }
                SecondaryButton(stringResource(R.string.season_put), { vm.open(SeasonSheet.Money) }, Modifier.fillMaxWidth(), height = 52.dp)
            }
        }
        PrimaryButton(
            stringResource(R.string.jar_save),
            { haptics.perform(HapticLevel.Confirm); vm.save() },
            Modifier.fillMaxWidth(),
            enabled = canSave,
        )
    }
    when (sheet) {
        SeasonSheet.Item -> ItemSheet(vm.dismiss) { label, amount -> vm.addItem(label, amount); vm.dismiss() }
        SeasonSheet.Money -> {
            val live = view
            if (live != null) AmountSheet(live.season.name, vm.dismiss) { amount ->
                if (amount > free) Text(stringResource(R.string.jar_free_only, poundsText(free)), style = TzType.label, color = Tz.carnelianText)
                PrimaryButton(stringResource(R.string.season_put), { haptics.perform(HapticLevel.Confirm); vm.earmark(amount) }, Modifier.fillMaxWidth(), enabled = amount in 1..free, height = 52.dp)
                if (live.saved > 0) SecondaryButton(
                    stringResource(R.string.season_release),
                    { if (amount in 1..live.saved) { haptics.perform(HapticLevel.Confirm); vm.release(amount) } else haptics.perform(HapticLevel.Reject) },
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
