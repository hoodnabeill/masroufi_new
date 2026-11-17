package com.masroufi.app.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** حاجة ثابتة جديدة / عدّل الحاجة: a pushed screen, one question per block, yellow save button. */
@Composable
fun FixedCostFormScreen(viewModel: FixedCostFormViewModel, onBack: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    val gone by viewModel.gone.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(f.saved, gone) { if (f.saved || gone) onBack() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(if (viewModel.isEdit) R.string.fixed_edit_title else R.string.fixed_new_title), onBack)

        FormBlock(R.string.fixed_name_q) { NameField(f.name, viewModel::setName, R.string.fixed_name_hint) }

        FormBlock(R.string.fixed_amount_q) { AmountKeypad(f.amount, viewModel::digit, viewModel::point, viewModel::backspace) }

        FormBlock(R.string.fixed_day_q) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(TzIcons.minus, stringResource(R.string.fixed_day_minus)) { haptics.perform(HapticLevel.Tick); viewModel.setDay(f.day - 1) }
                Text(f.day.toString(), style = TzType.hero, color = Tz.text)
                CircleButton(TzIcons.add, stringResource(R.string.fixed_day_plus)) { haptics.perform(HapticLevel.Tick); viewModel.setDay(f.day + 1) }
            }
        }

        FormBlock(R.string.fixed_pocket_q) {
            f.pockets.chunked(2).forEach { row ->
                ChipRow { row.forEach { p -> TzChip(p.name, f.pocketId == p.id, { viewModel.setPocket(p.id) }, style = ChipStyle.Choice) } }
            }
        }

        FormBlock(R.string.fixed_kind_q) {
            ChipRow {
                FIXED_COST_KINDS.forEach { kind ->
                    val label = when (kind) {
                        RecurringKind.INSTALLMENT -> R.string.fixed_kind_installment
                        RecurringKind.SUBSCRIPTION -> R.string.fixed_kind_subscription
                        else -> R.string.fixed_kind_bill
                    }
                    TzChip(stringResource(label), f.kind == kind, { viewModel.setKind(kind) }, style = ChipStyle.Choice)
                }
            }
        }

        if (f.failed) Text(stringResource(R.string.fixed_failed), style = TzType.label, color = Tz.carnelianText)
        PrimaryButton(
            stringResource(R.string.fixed_save),
            { haptics.perform(HapticLevel.Confirm); viewModel.save() },
            Modifier.fillMaxWidth(),
            enabled = f.canSave,
        )
        if (viewModel.isEdit) TextLink(stringResource(R.string.fixed_delete), { haptics.perform(HapticLevel.Heavy); viewModel.delete() }, Modifier.align(Alignment.CenterHorizontally))
    }
}
