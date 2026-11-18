package com.masroufi.app.feature.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.feature.kids.PinKey
import com.masroufi.app.feature.kids.canAuthenticateParent
import com.masroufi.app.feature.kids.fragmentActivity
import com.masroufi.app.feature.kids.promptParent
import com.masroufi.app.feature.profile.restartApp
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.util.Locale

/** القفل: the only thing the app shows while it is locked. The system prompt opens by itself when the screen appears. */
@Composable
fun LockScreen(viewModel: LockViewModel) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    val activity = remember(context) { context.fragmentActivity() }
    val canAuth = remember(context) { canAuthenticateParent(context) }
    val title = stringResource(R.string.lock_prompt_title)
    val cancel = stringResource(R.string.kids_unlock_cancel)
    val prompt = { if (activity != null && canAuth) promptParent(activity, title, cancel, viewModel::biometricOk) }
    LaunchedEffect(viewModel) { viewModel.haptics.collect { haptics.perform(it) } }
    LaunchedEffect(ui.wiped) { if (ui.wiped) restartApp(context) }
    val offered = canAuth && (ui.fingerprint || ui.unreadable)
    LaunchedEffect(viewModel) { viewModel.start() }
    // The prompt opens by itself, once, when the screen is ready; a cancel leaves the PIN pad.
    LaunchedEffect(ui.loaded) { if (ui.loaded && offered) prompt() }
    LockContent(ui, offered, viewModel::digit, viewModel::backspace, { prompt() }, viewModel::openForgot)
    if (ui.forgot) ForgotSheet(viewModel)
}

@Composable
fun LockContent(
    ui: LockUi,
    fingerprintKey: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onFingerprint: () -> Unit,
    onForgot: () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(ui.shake) {
        if (ui.shake > 0 && !reduce) for (t in floatArrayOf(-12f, 12f, -8f, 8f, 0f)) shake.animateTo(t, tween(50))
    }
    val quiet = ui.waitMillis > 0
    BoxWithConstraints(Modifier.fillMaxSize().background(Tz.night)) {
        val screenHeight = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width - 40.dp.toPx(), 40.dp.toPx())
            for (r in listOf(70, 100, 130, 160, 190)) drawCircle(Tz.raised, r.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        }
        Column(
            // Scrolls on a short screen (the keys must never be squeezed away); on a tall one the keys sit at the bottom.
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = screenHeight).statusBarsPadding().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(132.dp)) {
                Qersh(QershMood.Guard, 132.dp, contentDescription = stringResource(R.string.lock_guard_cd))
                Box(
                    Modifier.align(Alignment.BottomStart).offset(x = (-6).dp).size(48.dp).clip(CircleShape).background(Tz.turquoise)
                        .border(3.dp, Tz.night, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { TzIcon(TzIcons.lock, 22.dp, null, tint = Tz.turquoiseInk, filled = true, inkColor = Tz.turquoise) }
            }
            Text(stringResource(R.string.lock_title), Modifier.padding(top = 6.dp), style = TzType.titlePushed.copy(fontSize = 30.sp), color = Tz.text)
            Text(
                statusText(ui), Modifier.fillMaxWidth(),
                style = TzType.body.copy(fontSize = 14.sp, textAlign = TextAlign.Center),
                color = if (ui.wrong || quiet || ui.unreadable) Tz.carnelianText else Tz.text2,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.padding(top = 10.dp).offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                        .clearAndSetSemantics { contentDescription = "${ui.digits.length}/${ui.pinLength}" },
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    repeat(ui.pinLength) { i ->
                        Box(Modifier.size(18.dp).clip(CircleShape).then(if (i < ui.digits.length) Modifier.background(Tz.yellow) else Modifier.border(2.dp, Tz.dashed, CircleShape)))
                    }
                }
                Spacer(Modifier.weight(1f))
                Keys(quiet, fingerprintKey, onDigit, onBackspace, onFingerprint)
            }
            TextLink(stringResource(R.string.lock_forgot), onForgot)
        }
    }
}

@Composable
private fun statusText(ui: LockUi): String = when {
    ui.waitMillis > 0 -> waitLine(ui.waitMillis)
    ui.unreadable -> stringResource(R.string.lock_unreadable)
    ui.wrong -> stringResource(R.string.lock_wrong)
    else -> stringResource(R.string.lock_sub)
}

/** 30 000 ms is "استنى 30 ثانية"; from a minute up "استنى 1:30 دقيقة". */
@Composable
private fun waitLine(ms: Long): String {
    val s = (ms + 999) / 1000
    return if (s < 60) stringResource(R.string.lock_wait, s.toString())
    else stringResource(R.string.lock_wait_min, String.format(Locale.US, "%d:%02d", s / 60, s % 60))
}

/** The add sheet's keys (3 columns, digits left to right), the fingerprint bottom-left in yellow and delete bottom-right. */
@Composable
private fun Keys(quiet: Boolean, fingerprintKey: Boolean, onDigit: (Int) -> Unit, onBackspace: () -> Unit, onFingerprint: () -> Unit) {
    val gap = Arrangement.spacedBy(10.dp)
    Column(Modifier.fillMaxWidth().alpha(if (quiet) 0.4f else 1f), verticalArrangement = gap) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = gap) { row.forEach { d -> DigitKey(d, !quiet, Modifier.weight(1f), onDigit) } }
        }
        Row(horizontalArrangement = gap) {
            if (fingerprintKey) {
                PinKey(Tz.yellow, Modifier.weight(1f), true, onFingerprint, 64.dp, 22.dp) {
                    TzIcon(TzIcons.fingerprint, 28.dp, stringResource(R.string.lock_fingerprint_cd), tint = Tz.night)
                }
            } else {
                Box(Modifier.weight(1f))
            }
            DigitKey(0, !quiet, Modifier.weight(1f), onDigit)
            PinKey(Color.Transparent, Modifier.weight(1f), !quiet, onBackspace, 64.dp, 22.dp, outline = Tz.line) {
                TzIcon(TzIcons.delete, 26.dp, stringResource(R.string.lock_delete_cd), tint = Tz.text2)
            }
        }
    }
}

@Composable
private fun DigitKey(d: Int, enabled: Boolean, modifier: Modifier, onDigit: (Int) -> Unit) {
    PinKey(Tz.surface, modifier, enabled, { onDigit(d) }, 64.dp, 22.dp, outline = Tz.line) {
        Text(d.toString(), style = TzType.button.copy(fontSize = 28.sp), color = Tz.text)
    }
}

/** Decision 2: one sentence on the only way in, then the erase, which needs a tap of its own. */
@Composable
private fun ForgotSheet(vm: LockViewModel) {
    TzSheet(vm::closeForgot) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.lock_forgot_title), style = TzType.cardHeading, color = Tz.text)
            Text(stringResource(R.string.lock_forgot_body), style = TzType.body, color = Tz.text2)
            PrimaryButton(stringResource(R.string.lock_forgot_erase), vm::confirmWipe, Modifier.fillMaxWidth(), height = 52.dp)
            TextLink(stringResource(R.string.lock_forgot_back), vm::closeForgot, Modifier.fillMaxWidth())
        }
    }
}
