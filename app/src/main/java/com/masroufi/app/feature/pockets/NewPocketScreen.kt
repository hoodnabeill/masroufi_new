package com.masroufi.app.feature.pockets

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.feature.add.Keypad
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzToggle
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** جيب جديد: a pushed screen, one question per block, yellow save button (04-SCREENS "Screens not drawn"). */
@Composable
fun NewPocketScreen(viewModel: NewPocketViewModel, onBack: () -> Unit) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(f.saved) { if (f.saved) onBack() }
    NewPocketContent(
        f, onBack, viewModel::setName, viewModel::setKind, viewModel::digit, viewModel::point, viewModel::backspace,
        viewModel::setCountsInTicket, viewModel::save,
    )
}

/** Stateless new-pocket form. */
@Composable
fun NewPocketContent(
    f: NewPocketForm,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onKind: (PocketKind) -> Unit,
    onDigit: (Int) -> Unit,
    onPoint: () -> Unit,
    onBackspace: () -> Unit,
    onCounts: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val haptics = LocalHaptics.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.new_pocket_title), onBack)

        Block(R.string.new_pocket_name_q) {
            val shape = RoundedCornerShape(16.dp)
            BasicTextField(
                value = f.name,
                onValueChange = onName,
                singleLine = true,
                textStyle = TzType.body.copy(fontSize = 17.sp, color = Tz.text),
                cursorBrush = SolidColor(Tz.yellow),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Tz.night, shape).border(1.dp, Tz.line, shape).padding(horizontal = 14.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    if (f.name.isEmpty()) Text(stringResource(R.string.new_pocket_name_hint), style = TzType.body.copy(fontSize = 17.sp), color = Tz.text3)
                    inner()
                },
            )
        }

        Block(R.string.new_pocket_kind_q) {
            NEW_POCKET_KINDS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { kind ->
                        TzChip(stringResource(kind.label()), f.kind == kind, { onKind(kind) }, style = ChipStyle.Choice)
                    }
                }
            }
        }

        Block(R.string.new_pocket_opening_q) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(f.opening.text.ifEmpty { "0" }, style = TzType.hero, color = Tz.text)
                Text(stringResource(R.string.paper_currency), style = TzType.titlePushed, color = Tz.text2, modifier = Modifier.padding(bottom = 8.dp))
            }
            Keypad(onDigit, onPoint, onBackspace)
        }

        TzCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.pocket_counts_title), style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
                    Text(stringResource(R.string.pocket_counts_caption), style = TzType.caption, color = Tz.text2)
                }
                TzToggle(f.countsInTicket, onCounts, stringResource(R.string.pocket_counts_title))
            }
        }

        if (f.failed) Text(stringResource(R.string.new_pocket_failed), style = TzType.label, color = Tz.carnelianText)
        PrimaryButton(
            stringResource(R.string.new_pocket_save),
            { haptics.perform(HapticLevel.Confirm); onSave() },
            Modifier.fillMaxWidth(),
            enabled = f.canSave,
        )
    }
}

private fun PocketKind.label(): Int = when (this) {
    PocketKind.CASH -> R.string.new_pocket_kind_cash
    PocketKind.MOBILE_WALLET -> R.string.new_pocket_kind_wallet
    PocketKind.BANK -> R.string.new_pocket_kind_bank
    PocketKind.UNDER_TILE, PocketKind.VAULT -> R.string.new_pocket_kind_under_tile
}

@Composable
private fun Block(question: Int, content: @Composable () -> Unit) {
    TzCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(question), style = TzType.cardHeading, color = Tz.text)
            content()
        }
    }
}
