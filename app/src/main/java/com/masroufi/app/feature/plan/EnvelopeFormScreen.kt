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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.ui.components.PlusSheet
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.repo.CATEGORY_COLORS
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import androidx.compose.ui.graphics.Color

/** ظرف جديد / عدّل الظرف: a pushed screen, one question per block, yellow save button (04-SCREENS "Screens not drawn"). */
@Composable
fun EnvelopeFormScreen(viewModel: EnvelopeFormViewModel, onBack: () -> Unit, onNewFixedCost: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    LaunchedEffect(f.saved) { if (f.saved) onBack() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(if (viewModel.isEdit) R.string.env_edit_title else R.string.env_new_title), onBack)

        FormBlock(R.string.env_name_q) { NameField(f.name, viewModel::setName, R.string.env_name_hint) }

        FormBlock(R.string.env_color_q) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CATEGORY_COLORS.forEachIndexed { i, argb ->
                    Swatch(Color(argb), f.colorIndex == i, stringResource(R.string.env_color_desc, (i + 1).toString())) { viewModel.setColor(i) }
                }
            }
        }

        FormBlock(R.string.env_monthly_q) { AmountKeypad(f.monthly, viewModel::digit, viewModel::point, viewModel::backspace) }

        FormBlock(R.string.env_kind_q) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ENVELOPE_KINDS.forEach { kind ->
                    val label = if (kind == EnvelopeKind.FIXED) R.string.env_kind_fixed else R.string.env_kind_spending
                    TzChip(stringResource(label), f.kind == kind, { viewModel.setKind(kind) }, style = ChipStyle.Choice)
                }
            }
        }

        FormBlock(R.string.env_cats_q) {
            // Rows of three, each scrolling sideways when the names are long.
            f.categories.chunked(3).forEach { row ->
                ChipRow { row.forEach { c -> TzChip(c.name, c.id in f.categoryIds, { viewModel.toggleCategory(c.id) }, style = ChipStyle.Filter) } }
            }
        }

        if (f.kind == EnvelopeKind.FIXED) FormBlock(R.string.env_fixed_q) {
            f.fixedCosts.chunked(2).forEach { row ->
                ChipRow { row.forEach { r -> TzChip(r.name, f.recurringId == r.id, { viewModel.pickFixed(r.id) }, style = ChipStyle.Choice) } }
            }
            TextLink(stringResource(R.string.env_fixed_add), { haptics.perform(HapticLevel.Tap); onNewFixedCost() })
        }

        if (f.failed) Text(stringResource(R.string.env_failed), style = TzType.label, color = Tz.carnelianText)
        PrimaryButton(
            stringResource(R.string.env_save),
            { haptics.perform(HapticLevel.Confirm); viewModel.save() },
            Modifier.fillMaxWidth(),
            enabled = f.canSave,
        )
    }
    if (f.plus) PlusSheet(R.string.env_plus_line, PlusFeature.ENVELOPES, viewModel::dismissPlus)
}
