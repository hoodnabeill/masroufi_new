package com.masroufi.app.feature.onboarding

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** The five onboarding screens in one route; the view model's step picks which one shows. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(viewModel) { viewModel.events.collect { finished() } }
    OnboardingContent(state, viewModel)
}

@Composable
fun OnboardingContent(state: OnboardingUi, actions: OnboardingActions) {
    BackHandler(enabled = state.step > STEP_WELCOME) { actions.back() }
    // Night until the saved step is known: no flash of the welcome screen on a resume.
    if (!state.loaded) {
        Box(Modifier.fillMaxSize().background(Tz.night))
        return
    }
    val reduce = LocalReduceMotion.current
    Crossfade(state.step, Modifier.fillMaxSize(), animationSpec = tween(if (reduce) 120 else 140), label = "onboardingStep") { step ->
        when (step) {
            STEP_WELCOME -> WelcomeStep(actions)
            STEP_PAYDAY -> PaydayStep(state, actions)
            STEP_MONEY -> MoneyStep(state, actions)
            STEP_FIXED -> FixedStep(state, actions)
            else -> RevealStep(state, actions)
        }
    }
}

/**
 * The frame every question shares (ObPayday.dc.html): Night, 20 dp sides, a header of back + three bars ([bars] filled;
 * null = no header), scrolling [content], and [bottom] (the buttons) pinned under it.
 */
@Composable
internal fun StepFrame(
    bars: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    top: Dp = 24.dp,
    gap: Dp = 18.dp,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(Tz.night).statusBarsPadding().navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = top, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (bars != null) StepHeader(bars, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap), content = content)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), content = bottom)
    }
}

@Composable
private fun StepHeader(filled: Int, onBack: () -> Unit) {
    val progress = stringResource(R.string.ob_progress_cd, filled.toString())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Tz.surface).border(1.dp, Tz.line, CircleShape), contentAlignment = Alignment.Center) {
                TzIcon(TzIcons.back, 20.dp, stringResource(R.string.ob_back), tint = Tz.text)
            }
        }
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = progress }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i < filled) Tz.yellow else Tz.line))
            }
        }
    }
}

/** The question: Lalezar [size] sp on a 1.15 line. */
@Composable
internal fun StepTitle(text: String, size: Int = 42) {
    Text(text, style = TzType.hero.copy(fontSize = size.sp, lineHeight = 1.15.em, color = Tz.text))
}

/** The yellow pill's label: Lalezar 22 on Night (ObPayday.dc.html). */
internal val StepButtonText: TextStyle = TzType.button.copy(fontSize = 22.sp)

/** A 1.5 dp dashed outline on a rounded box (the mocks' dashed rows). */
internal fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val w = 1.5.dp.toPx()
    drawRoundRect(
        color,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}
