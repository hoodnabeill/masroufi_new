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

/** اشتراك جديد / عدّل الاشتراك: one question per block, like the fixed-cost form. */
@Composable
fun SubscriptionFormScreen(viewModel: SubscriptionFormViewModel, onBack: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    val gone by viewModel.gone.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(f.saved, gone) { if (f.saved || gone) onBack() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(if (viewModel.isEdit) R.string.subs_edit_title else R.string.subs_new_title), onBack)

        FormBlock(R.string.subs_name_q) { NameField(f.name, viewModel::setName, R.string.subs_name_hint) }

        FormBlock(R.string.subs_period_q) {
            ChipRow {
                TzChip(stringResource(R.string.subs_monthly), !f.yearly, { viewModel.setYearly(false) }, style = ChipStyle.Choice)
                TzChip(stringResource(R.string.subs_yearly), f.yearly, { viewModel.setYearly(true) }, style = ChipStyle.Choice)
            }
        }

        FormBlock(if (f.yearly) R.string.subs_price_year_q else R.string.subs_price_q) {
            AmountKeypad(f.amount, viewModel::digit, viewModel::point, viewModel::backspace)
        }

        if (!f.yearly) {
            FormBlock(R.string.subs_yearly_price_q) {
                AmountKeypad(f.yearlyPrice, viewModel::yearlyDigit, viewModel::yearlyPoint, viewModel::yearlyBackspace)
            }
        }

        FormBlock(R.string.subs_day_q) {
            Stepper(f.day.toString(), R.string.fixed_day_minus, R.string.fixed_day_plus, { viewModel.setDay(f.day - 1) }, { viewModel.setDay(f.day + 1) })
        }

        FormBlock(R.string.subs_people_q) {
            Stepper(f.sharedWith.toString(), R.string.subs_people_minus, R.string.subs_people_plus, { viewModel.stepPeople(-1) }, { viewModel.stepPeople(1) })
        }

        FormBlock(R.string.subs_trial_q) {
            ChipRow {
                TzChip(stringResource(R.string.subs_trial_no), f.trialDays == null, { viewModel.setTrial(false) }, style = ChipStyle.Choice)
                TzChip(stringResource(R.string.subs_trial_yes), f.trialDays != null, { viewModel.setTrial(true) }, style = ChipStyle.Choice)
            }
            f.trialDays?.let { days ->
                Stepper(days.toString(), R.string.subs_trial_minus, R.string.subs_trial_plus, { viewModel.stepTrial(-1) }, { viewModel.stepTrial(1) })
                Text(stringResource(R.string.subs_trial_ends, days.toString()), style = TzType.caption, color = Tz.text2)
            }
        }

        FormBlock(R.string.subs_pocket_q) {
            f.pockets.chunked(2).forEach { row ->
                ChipRow { row.forEach { p -> TzChip(p.name, f.pocketId == p.id, { viewModel.setPocket(p.id) }, style = ChipStyle.Choice) } }
            }
        }

        if (f.failed) Text(stringResource(R.string.subs_failed), style = TzType.label, color = Tz.carnelianText)
        PrimaryButton(
            stringResource(R.string.subs_save),
            { haptics.perform(HapticLevel.Confirm); viewModel.save() },
            Modifier.fillMaxWidth(),
            enabled = f.canSave,
        )
        if (viewModel.isEdit) TextLink(stringResource(R.string.subs_delete), { haptics.perform(HapticLevel.Heavy); viewModel.delete() }, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
internal fun Stepper(value: String, minusCd: Int, plusCd: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(TzIcons.minus, stringResource(minusCd)) { haptics.perform(HapticLevel.Tick); onMinus() }
        Text(value, style = TzType.hero, color = Tz.text)
        CircleButton(TzIcons.add, stringResource(plusCd)) { haptics.perform(HapticLevel.Tick); onPlus() }
    }
}
