package com.masroufi.app.ui.components

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/**
 * The Plus-locked pattern (decision 1): [content] is drawn blurred (alpha 0.15 below API 31), not interactive and hidden
 * from TalkBack, under one bubble-style card with the [what] line and "جرّب بلس".
 */
@Composable
fun PlusLocked(@StringRes what: Int, onTryPlus: () -> Unit, content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    val look = if (Build.VERSION.SDK_INT >= 31) Modifier.blur(12.dp) else Modifier.alpha(0.15f)
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.clearAndSetSemantics {}.then(look).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
        ) { content() }
        Column(
            Modifier.padding(horizontal = 24.dp).clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(what), style = TzType.body.copy(textAlign = TextAlign.Center), color = Tz.text2)
            PrimaryButton(stringResource(R.string.env_plus_try), { haptics.perform(HapticLevel.Tap); onTryPlus() }, height = 48.dp)
        }
    }
}

/** Placeholder target of "جرّب بلس" until billing arrives in Phase 10. */
@Composable
fun PlusComingSheet(onDismiss: () -> Unit) {
    TzSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Qersh(QershMood.Happy, 96.dp)
            Text(stringResource(R.string.env_plus_soon), style = TzType.cardHeading, color = Tz.text)
            PrimaryButton(stringResource(R.string.env_plus_ok), onDismiss, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}
