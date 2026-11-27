package com.masroufi.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.spec
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** "اتمسحت. رجّعها؟": the one undo bar every delete shows for its 5 s (the roll shows it itself, the rest through [UndoHost]). */
@Composable
fun UndoBar(visible: Boolean, onUndo: () -> Unit, modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(20.dp)
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn(tween(160)) + if (reduce) EnterTransition.None else slideInVertically(TzSpring.Move.spec()) { it },
        exit = fadeOut(tween(160)) + if (reduce) ExitTransition.None else slideOutVertically(TzSpring.Move.spec()) { it },
    ) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Tz.raised).border(1.dp, Tz.line, shape).padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.roll_undo), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
            TextLink(stringResource(R.string.roll_undo_action), { haptics.perform(HapticLevel.Tap); onUndo() })
        }
    }
}
