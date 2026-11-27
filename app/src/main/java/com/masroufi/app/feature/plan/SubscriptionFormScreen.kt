package com.masroufi.app.feature.plan

import com.masroufi.app.ui.components.CappedFontScale
import com.masroufi.app.ui.components.LARGE_FONT_SCALE
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
    LaunchedEffect(f.saved, gone) { if (f.saved || gone) onBack() }
    SubscriptionFormContent(
        f, viewModel.isEdit, onBack,
        SubscriptionFormActions(
            viewModel::setName, viewModel::setYearly, viewModel::digit, viewModel::point, viewModel::backspace,
            viewModel::yearlyDigit, viewModel::yearlyPoint, viewModel::yearlyBackspace, viewModel::setDay, viewModel::stepPeople,
            viewModel::setTrial, viewModel::stepTrial, viewModel::setPocket, viewModel::save, viewModel::delete,
        ),
    )
}

/** What the subscription form can do; the view model's functions in the app, no-ops in the audit. */
internal class SubscriptionFormActions(
    val setName: (String) -> Unit,
    val setYearly: (Boolean) -> Unit,
    val digit: (Int) -> Unit,
    val point: () -> Unit,
    val backspace: () -> Unit,
    val yearlyDigit: (Int) -> Unit,
    val yearlyPoint: () -> Unit,
    val yearlyBackspace: () -> Unit,
    val setDay: (Int) -> Unit,
    val stepPeople: (Int) -> Unit,
    val setTrial: (Boolean) -> Unit,
    val stepTrial: (Int) -> Unit,
    val setPocket: (Long) -> Unit,
    val save: () -> Unit,
    val delete: () -> Unit,
) {
    companion object { val None = SubscriptionFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Composable
internal fun SubscriptionFormContent(f: SubscriptionForm, isEdit: Boolean, onBack: () -> Unit, vm: SubscriptionFormActions) {
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(if (isEdit) R.string.subs_edit_title else R.string.subs_new_title), onBack)

        FormBlock(R.string.subs_name_q) { NameField(f.name, vm.setName, R.string.subs_name_hint) }

        FormBlock(R.string.subs_period_q) {
            ChipRow {
                TzChip(stringResource(R.string.subs_monthly), !f.yearly, { vm.setYearly(false) }, style = ChipStyle.Choice)
                TzChip(stringResource(R.string.subs_yearly), f.yearly, { vm.setYearly(true) }, style = ChipStyle.Choice)
            }
        }

        FormBlock(if (f.yearly) R.string.subs_price_year_q else R.string.subs_price_q) {
            AmountKeypad(f.amount, vm.digit, vm.point, vm.backspace)
        }

        if (!f.yearly) {
            FormBlock(R.string.subs_yearly_price_q) {
                AmountKeypad(f.yearlyPrice, vm.yearlyDigit, vm.yearlyPoint, vm.yearlyBackspace)
            }
        }

        FormBlock(R.string.subs_day_q) {
            Stepper(f.day.toString(), R.string.fixed_day_minus, R.string.fixed_day_plus, { vm.setDay(f.day - 1) }, { vm.setDay(f.day + 1) })
        }

        FormBlock(R.string.subs_people_q) {
            Stepper(f.sharedWith.toString(), R.string.subs_people_minus, R.string.subs_people_plus, { vm.stepPeople(-1) }, { vm.stepPeople(1) })
        }

        FormBlock(R.string.subs_trial_q) {
            ChipRow {
                TzChip(stringResource(R.string.subs_trial_no), f.trialDays == null, { vm.setTrial(false) }, style = ChipStyle.Choice)
                TzChip(stringResource(R.string.subs_trial_yes), f.trialDays != null, { vm.setTrial(true) }, style = ChipStyle.Choice)
            }
            f.trialDays?.let { days ->
                Stepper(days.toString(), R.string.subs_trial_minus, R.string.subs_trial_plus, { vm.stepTrial(-1) }, { vm.stepTrial(1) })
                Text(stringResource(R.string.subs_trial_ends, days.toString()), style = TzType.caption, color = Tz.text2)
            }
        }

        FormBlock(R.string.subs_pocket_q) {
            f.pockets.chunked(2).forEach { row ->
                ChipRow { row.forEach { p -> TzChip(p.name, f.pocketId == p.id, { vm.setPocket(p.id) }, style = ChipStyle.Choice) } }
            }
        }

        if (f.failed) Text(stringResource(R.string.subs_failed), style = TzType.label, color = Tz.carnelianText)
        PrimaryButton(
            stringResource(R.string.subs_save),
            { haptics.perform(HapticLevel.Confirm); vm.save() },
            Modifier.fillMaxWidth(),
            enabled = f.canSave,
        )
        if (isEdit) TextLink(stringResource(R.string.subs_delete), { haptics.perform(HapticLevel.Tap); vm.delete() }, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
internal fun Stepper(value: String, minusCd: Int, plusCd: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(TzIcons.minus, stringResource(minusCd)) { haptics.perform(HapticLevel.Tick); onMinus() }
        CappedFontScale(LARGE_FONT_SCALE) { Text(value, style = TzType.hero, color = Tz.text) }
        CircleButton(TzIcons.add, stringResource(plusCd)) { haptics.perform(HapticLevel.Tick); onPlus() }
    }
}
