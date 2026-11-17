package com.masroufi.app.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.BuildConfig
import com.masroufi.app.R
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.ui.components.Segmented
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/**
 * الخطة: the three views under one switch. Seasons and the jar are Tasks 32 and 33; until then they show a sleepy Qersh.
 * Debug builds keep the gallery link at the bottom.
 */
@Composable
fun PlanScreen(
    viewModel: EnvelopesViewModel,
    onOpenEnvelope: (Long) -> Unit,
    onEditEnvelope: (Long) -> Unit,
    onNewEnvelope: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenFixedCosts: () -> Unit,
    onGallery: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(viewModel) { viewModel.openEdit.collect { onEditEnvelope(it) } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_plan), style = TzType.titleTab, color = Tz.text)
            if (segment == 0) CircleButton(TzIcons.add, stringResource(R.string.plan_new_envelope)) { if (viewModel.requestNew()) onNewEnvelope() }
        }
        Segmented(
            listOf(R.string.plan_seg_envelopes, R.string.plan_seg_seasons, R.string.plan_seg_jar).map { stringResource(it) },
            segment, { segment = it },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextLink(stringResource(R.string.plan_link_subscriptions), onOpenSubscriptions)
            TextLink(stringResource(R.string.plan_link_fixed), onOpenFixedCosts)
        }
        when (segment) {
            0 -> EnvelopesSection(
                state,
                onNew = { if (viewModel.requestNew()) onNewEnvelope() },
                onOpen = onOpenEnvelope,
                onSuggest = viewModel::suggest,
                onDrop = viewModel::requestMove,
                onMoveTip = viewModel::openMove,
                onMoveFrom = viewModel::openMoveFrom,
            )
            // Task 32 replaces this with the jar; Task 33 replaces the seasons one.
            else -> Soon()
        }
        if (BuildConfig.DEBUG) TextLink("Gallery", onGallery, Modifier.align(Alignment.CenterHorizontally))
    }
    when (val sheet = state.sheet) {
        EnvelopeSheet.Plus -> PlusSheet(viewModel::dismissSheet)
        is EnvelopeSheet.Move -> MoveSheet(sheet, state.cards, viewModel::confirmMove, viewModel::dismissSheet)
        null -> Unit
    }
}

@Composable
private fun Soon() {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { Qersh(QershMood.Sleepy, 150.dp) }
}
