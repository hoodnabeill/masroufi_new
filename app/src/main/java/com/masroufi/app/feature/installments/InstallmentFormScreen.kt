package com.masroufi.app.feature.installments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.masroufi.app.feature.plan.AmountKeypad
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.plan.FormBlock
import com.masroufi.app.feature.plan.NameField
import com.masroufi.app.feature.plan.Stepper
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** قسط جديد / عدّل القسط: name, monthly amount, day of month, how many payments, how many already paid, the pocket. */
@Composable
fun InstallmentFormScreen(viewModel: InstallmentFormViewModel, onBack: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    val gone by viewModel.gone.collectAsStateWithLifecycle()
    LaunchedEffect(f.saved, gone) { if (f.saved || gone) onBack() }
    InstallmentFormContent(
        f, viewModel.isEdit, onBack,
        InstallmentFormActions(
            viewModel::setName, viewModel::digit, viewModel::point, viewModel::backspace, viewModel::setDay, viewModel::setOpen,
            viewModel::stepTotal, viewModel::stepPaid, viewModel::setPocket, viewModel::save, viewModel::delete,
        ),
    )
}

/** What the installment form can do; the view model's functions in the app, no-ops in the audit. */
internal class InstallmentFormActions(
    val setName: (String) -> Unit,
    val digit: (Int) -> Unit,
    val point: () -> Unit,
    val backspace: () -> Unit,
    val setDay: (Int) -> Unit,
    val setOpen: (Boolean) -> Unit,
    val stepTotal: (Int) -> Unit,
    val stepPaid: (Int) -> Unit,
    val setPocket: (Long) -> Unit,
    val save: () -> Unit,
    val delete: () -> Unit,
) {
    companion object { val None = InstallmentFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Composable
internal fun InstallmentFormContent(f: InstallmentForm, isEdit: Boolean, onBack: () -> Unit, vm: InstallmentFormActions) {
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(if (isEdit) R.string.inst_edit_title else R.string.inst_new_title), onBack)

        FormBlock(R.string.inst_name_q) { NameField(f.name, vm.setName, R.string.inst_name_hint) }

        FormBlock(R.string.inst_amount_q) { AmountKeypad(f.amount, vm.digit, vm.point, vm.backspace) }

        FormBlock(R.string.fixed_day_q) {
            Stepper(f.day.toString(), R.string.fixed_day_minus, R.string.fixed_day_plus, { vm.setDay(f.day - 1) }, { vm.setDay(f.day + 1) })
        }

        FormBlock(R.string.inst_total_q) {
            ChipRow {
                TzChip(stringResource(R.string.inst_total_fixed), f.total != null, { vm.setOpen(false) }, style = ChipStyle.Choice)
                TzChip(stringResource(R.string.inst_total_open), f.total == null, { vm.setOpen(true) }, style = ChipStyle.Choice)
            }
            f.total?.let { total ->
                Stepper(total.toString(), R.string.inst_total_minus, R.string.inst_total_plus, { vm.stepTotal(-1) }, { vm.stepTotal(1) })
            }
        }

        if (f.total != null) {
            FormBlock(R.string.inst_paid_q) {
                Stepper(f.paidBefore.toString(), R.string.inst_paid_minus, R.string.inst_paid_plus, { vm.stepPaid(-1) }, { vm.stepPaid(1) })
                Text(stringResource(R.string.inst_paid_hint), style = TzType.caption, color = Tz.text2)
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
        if (isEdit) TextLink(stringResource(R.string.inst_delete), { haptics.perform(HapticLevel.Heavy); vm.delete() }, Modifier.align(Alignment.CenterHorizontally))
    }
}
