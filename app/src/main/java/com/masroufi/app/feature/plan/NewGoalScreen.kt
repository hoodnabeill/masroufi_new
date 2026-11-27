package com.masroufi.app.feature.plan

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics

/** هدف جديد: a pushed screen (name, target, optional date). */
@Composable
fun NewGoalScreen(viewModel: NewGoalViewModel, onBack: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(f.saved) { if (f.saved) onBack() }
    NewGoalContent(f, onBack, viewModel::setName, viewModel::digit, viewModel::point, viewModel::backspace, viewModel::setMonths, viewModel::save)
}

@Composable
internal fun NewGoalContent(
    f: GoalForm,
    onBack: () -> Unit,
    setName: (String) -> Unit,
    digit: (Int) -> Unit,
    point: () -> Unit,
    backspace: () -> Unit,
    setMonths: (Int?) -> Unit,
    save: () -> Unit,
) {
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.jar_goal_new), onBack)
        FormBlock(R.string.goal_name_q) { NameField(f.name, setName, R.string.goal_name_hint) }
        FormBlock(R.string.goal_target_q) { AmountKeypad(f.target, digit, point, backspace) }
        FormBlock(R.string.goal_date_q) {
            ChipRow {
                TzChip(stringResource(R.string.goal_date_none), f.months == null, { setMonths(null) }, style = ChipStyle.Choice)
                GOAL_MONTHS.forEach { m ->
                    TzChip(stringResource(R.string.goal_date_months, m), f.months == m, { setMonths(m) }, style = ChipStyle.Choice)
                }
            }
        }
        PrimaryButton(
            stringResource(R.string.jar_save),
            { haptics.perform(HapticLevel.Confirm); save() },
            Modifier.fillMaxWidth(),
            enabled = f.canSave,
        )
    }
}
