package com.masroufi.app.feature.kids

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.CATEGORY_COLORS
import com.masroufi.app.feature.plan.AmountKeypad
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.plan.FormBlock
import com.masroufi.app.feature.plan.NameField
import com.masroufi.app.feature.plan.Swatch
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

/** طفل جديد / عدّل الطفل: name, birth year, colour, weekly, the jar goal and the match rule. One question per block, yellow save. */
@Composable
fun ChildFormScreen(viewModel: ChildFormViewModel, onBack: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    val gone by viewModel.gone.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(f.saved, gone) { if (f.saved || gone) onBack() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(if (viewModel.isEdit) R.string.kids_form_edit else R.string.kids_form_new), onBack)

        FormBlock(R.string.kids_form_name_q) { NameField(f.name, viewModel::setName, R.string.kids_form_name_hint) }

        FormBlock(R.string.kids_form_year_q) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(TzIcons.minus, stringResource(R.string.kids_form_year_minus)) { haptics.perform(HapticLevel.Tick); viewModel.setBirthYear(f.birthYear - 1) }
                Text(f.birthYear.toString(), style = TzType.hero, color = Tz.text)
                CircleButton(TzIcons.add, stringResource(R.string.kids_form_year_plus)) { haptics.perform(HapticLevel.Tick); viewModel.setBirthYear(f.birthYear + 1) }
            }
        }

        FormBlock(R.string.kids_form_color_q) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CATEGORY_COLORS.forEachIndexed { i, argb ->
                    Swatch(Color(argb), f.colorIndex == i, stringResource(R.string.env_color_desc, (i + 1).toString())) { viewModel.setColor(i) }
                }
            }
        }

        FormBlock(R.string.kids_form_weekly_q) {
            AmountKeypad(f.weekly, { viewModel.digit(ChildAmount.WEEKLY, it) }, { viewModel.point(ChildAmount.WEEKLY) }, { viewModel.backspace(ChildAmount.WEEKLY) })
        }

        FormBlock(R.string.kids_form_jar_q) {
            ChipRow {
                TzChip(stringResource(R.string.kids_form_jar_no), !f.jar, { viewModel.setJar(false) }, style = ChipStyle.Choice)
                TzChip(stringResource(R.string.kids_form_jar_yes), f.jar, { viewModel.setJar(true) }, style = ChipStyle.Choice)
            }
            if (f.jar) {
                NameField(f.jarLabel, viewModel::setJarLabel, R.string.kids_form_jar_hint)
                Text(stringResource(R.string.kids_form_target_q), style = TzType.caption, color = Tz.text2)
                AmountKeypad(f.jarTarget, { viewModel.digit(ChildAmount.TARGET, it) }, { viewModel.point(ChildAmount.TARGET) }, { viewModel.backspace(ChildAmount.TARGET) })
            }
        }

        if (f.jar) {
            FormBlock(R.string.kids_form_match_q) {
                ChipRow {
                    TzChip(stringResource(R.string.kids_form_match_no), !f.match, { viewModel.setMatch(false) }, style = ChipStyle.Choice)
                    TzChip(stringResource(R.string.kids_form_match_yes), f.match, { viewModel.setMatch(true) }, style = ChipStyle.Choice)
                }
                if (f.match) {
                    Text(stringResource(R.string.kids_form_per_q), style = TzType.caption, color = Tz.text2)
                    AmountKeypad(f.matchPer, { viewModel.digit(ChildAmount.PER, it) }, { viewModel.point(ChildAmount.PER) }, { viewModel.backspace(ChildAmount.PER) })
                    Text(stringResource(R.string.kids_form_add_q), style = TzType.caption, color = Tz.text2)
                    AmountKeypad(f.matchAdd, { viewModel.digit(ChildAmount.ADD, it) }, { viewModel.point(ChildAmount.ADD) }, { viewModel.backspace(ChildAmount.ADD) })
                }
            }
        }

        if (f.failed) Text(stringResource(R.string.kids_form_failed), style = TzType.label, color = Tz.carnelianText)
        PrimaryButton(
            stringResource(R.string.kids_form_save),
            { haptics.perform(HapticLevel.Confirm); viewModel.save() },
            Modifier.fillMaxWidth(),
            enabled = f.canSave,
        )
        if (viewModel.isEdit) TextLink(stringResource(R.string.kids_form_delete), { haptics.perform(HapticLevel.Heavy); viewModel.delete() }, Modifier.align(Alignment.CenterHorizontally))
    }
}
