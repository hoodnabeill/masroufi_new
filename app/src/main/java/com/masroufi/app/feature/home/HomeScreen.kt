package com.masroufi.app.feature.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.add.PunchFeedback
import com.masroufi.app.ui.paper.PrintIn
import com.masroufi.app.ui.paper.Ticket
import kotlinx.coroutines.delay

private enum class Phase { Blank, Skeleton, Content, Error }

/** Skip the skeleton for loads under this (10-PERFORMANCE). */
private const val SKELETON_AFTER_MS = 150L

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCloseDay: (epochDay: Long) -> Unit,
    onOpenProverb: (epochDay: Long) -> Unit,
    onOpenAddSheet: (AddPrefill?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val openSheet by rememberUpdatedState(onOpenAddSheet)
    LaunchedEffect(viewModel) { viewModel.prefill.collect { openSheet(it) } }

    val context = LocalContext.current
    val speech = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
    }
    // No recognizer on the device: no mic button (the manifest <queries> makes it visible on API 30+).
    val micAvailable = remember { speech.resolveActivity(context.packageManager) != null }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) {
            viewModel.quickText = heard
            viewModel.submitQuickLog()
        }
    }

    HomeContent(
        state = state,
        quickText = viewModel.quickText,
        onQuickText = { viewModel.quickText = it },
        onSubmitQuickLog = viewModel::submitQuickLog,
        onMic = if (micAvailable) ({ recognizer.launch(speech) }) else null,
        saveFailed = saveFailed,
        onChip = viewModel::punchRepeat,
        onCloseDay = onCloseDay,
        onOpenProverb = onOpenProverb,
        feedback = feedback,
        onTicketShown = viewModel::markTicketShown,
    )
}

/** Stateless Home: skeleton after 150 ms of loading, 160 ms cross-fade to the content or the error. */
@Composable
fun HomeContent(
    state: HomeUiState,
    quickText: String,
    onQuickText: (String) -> Unit,
    onSubmitQuickLog: () -> Unit,
    onMic: (() -> Unit)?,
    saveFailed: Boolean,
    onChip: (RepeatChip) -> Unit,
    onCloseDay: (epochDay: Long) -> Unit,
    onOpenProverb: (epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
    feedback: PunchFeedback.Saved? = null,
    onTicketShown: (epochDay: Long) -> Unit = {},
) {
    var skeletonDue by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SKELETON_AFTER_MS)
        skeletonDue = true
    }
    val phase = when (state) {
        is HomeUiState.Ready -> Phase.Content
        HomeUiState.Error -> Phase.Error
        HomeUiState.Loading -> if (skeletonDue) Phase.Skeleton else Phase.Blank
    }
    val padding = Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
    Crossfade(phase, modifier.fillMaxSize(), animationSpec = tween(160), label = "home") { p ->
        when (p) {
            Phase.Blank -> Box(Modifier.fillMaxSize())
            Phase.Skeleton -> HomeSkeleton(padding)
            Phase.Error -> HomeError(Modifier.statusBarsPadding())
            // The Ticket composes only with a loaded row, so its punch never replays on open.
            Phase.Content -> (state as? HomeUiState.Ready)?.let { ready ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(padding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ReadyContent(ready, quickText, onQuickText, onSubmitQuickLog, onMic, saveFailed, onChip, onCloseDay, onOpenProverb, feedback, onTicketShown)
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    s: HomeUiState.Ready,
    quickText: String,
    onQuickText: (String) -> Unit,
    onSubmitQuickLog: () -> Unit,
    onMic: (() -> Unit)?,
    saveFailed: Boolean,
    onChip: (RepeatChip) -> Unit,
    onCloseDay: (Long) -> Unit,
    onOpenProverb: (Long) -> Unit,
    feedback: PunchFeedback.Saved?,
    onTicketShown: (Long) -> Unit,
) {
    val epochDay = s.today.toEpochDay()
    HomeHeader(s.today, s.greeting, s.streak)
    // 26 dp above the ticket (on top of the 16 dp gap) leaves room for Qersh peeking over it.
    Box(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        val t = s.ticket
        if (t != null) {
            PrintIn(play = s.newTicket, onShown = { onTicketShown(epochDay) }) { slide, qershScale ->
            Ticket(
                label = stringResource(R.string.home_ticket_label),
                amountLabel = stringResource(R.string.home_ticket_amount_label),
                leftPiastres = t.left,
                subLine = subLineText(t.subLine),
                holes = t.holes,
                caption = stringResource(R.string.home_ticket_caption),
                stub = { TicketStub { onCloseDay(epochDay) } },
                contentDescription = stringResource(
                    R.string.home_ticket_cd,
                    formatPounds(t.left.coerceAtLeast(0)), formatPounds(t.amount), formatPounds(t.spent), t.holes.toString(),
                ),
                qershScale = qershScale,
                modifier = Modifier.graphicsLayer(slide),
            )
            }
        } else if (s.tomorrow != null) {
            TomorrowPreview(s.tomorrow) { onOpenProverb(epochDay) }
        }
    }
    if (feedback != null && s.ticket != null) PunchFeedbackLines(feedback, spread = s.cycle.daysLeft > 1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickLogField(quickText, onQuickText, onSubmitQuickLog, onMic)
        if (saveFailed) SaveFailedLine()
    }
    if (s.repeats.isNotEmpty()) RepeatChips(s.repeats, onChip)
    WeekCard(s.week, s.closedUnder)
    JarCard(s.jarTotal)
    CycleCard(s.cycle)
    if (s.ticket != null) ProverbTeaser(s.collected)
}
