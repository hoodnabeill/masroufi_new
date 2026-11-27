package com.masroufi.app.feature.plan

import com.masroufi.app.feature.pockets.LoadingRows
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.feature.pockets.pocketColor
import com.masroufi.app.feature.roll.EntryLine
import com.masroufi.app.feature.roll.editPrefill
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** ظرف واحد: hero number, bar, the honest line, then the cycle's entries in its categories as roll rows (04-SCREENS "Screens not drawn"). */
@Composable
fun EnvelopeDetailScreen(
    viewModel: EnvelopeDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenAddSheet: (AddPrefill?) -> Unit,
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.missing) { if (s.missing) onBack() }
    EnvelopeDetailContent(s, onBack, onEdit = { onEdit(viewModel.id) }, onClose = viewModel::close, onOpenAddSheet = onOpenAddSheet)
}

@Composable
internal fun EnvelopeDetailContent(
    s: EnvelopeDetailUi,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onOpenAddSheet: (AddPrefill?) -> Unit,
) {
    val haptics = LocalHaptics.current
    val card = s.card
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(card?.view?.name.orEmpty(), onBack) {
            if (card != null) CircleButton(TzIcons.edit, stringResource(R.string.env_edit), onEdit)
        }
        if (card == null) {
            if (s.loading) LoadingRows()
            return@Column
        }
        val v = card.view
        val color = pocketColor(v.color)
        TzCard {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Odometer(v.left, TzType.hero.copy(color = color))
                Text(stringResource(R.string.env_left_of, poundsText(v.monthly)), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2)
                TzProgressBar(listOf((if (card.line is CardLine.Sealed) 1f else v.bar) to color), height = 8.dp)
                when (val l = card.line) {
                    is CardLine.Sealed -> Text(stringResource(R.string.env_sealed_note), style = TzType.caption, color = Tz.text2)
                    is CardLine.RunsOut -> Text(stringResource(R.string.env_runs_out, l.day.toString()), style = TzType.caption, color = Tz.carnelianText)
                    CardLine.OnPace -> Text(stringResource(R.string.env_on_pace), style = TzType.caption, color = Tz.text2)
                    CardLine.BetterThanLast -> Text(stringResource(R.string.env_better), style = TzType.caption, color = Tz.text2)
                    null -> Unit
                }
            }
        }
        TzCard {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.env_detail_entries), style = TzType.cardHeading, color = Tz.text, modifier = Modifier.padding(bottom = 4.dp))
                if (s.entries.isEmpty()) {
                    Text(stringResource(R.string.env_detail_empty), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2, modifier = Modifier.padding(vertical = 8.dp))
                }
                s.entries.forEachIndexed { i, e ->
                    EntryLine(e, s.categories, false, "", i == 0) { onOpenAddSheet(it.editPrefill()) }
                }
            }
        }
        SecondaryButton(stringResource(R.string.env_edit), { haptics.perform(HapticLevel.Tap); onEdit() }, Modifier.fillMaxWidth())
        TextLink(stringResource(R.string.env_close), { haptics.perform(HapticLevel.Tap); onClose() }, Modifier.align(Alignment.CenterHorizontally))
    }
}
