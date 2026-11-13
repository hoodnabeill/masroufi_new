package com.masroufi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius

/**
 * Bottom sheet on Material3 ModalBottomSheet, styled to the system: Surface, top radius 32, 40 x 5 Dashed grabber,
 * Night scrim at 60%, 1 dp Line top border (AddPunch.dc.html:19), grabber 10 dp above and 14 below. Pass [sheetState] to hide it with the exit animation. M3 supplies finger tracking and fling dismissal (its own spring, not [TzSpring.Sheet]).
 */
// shortcut: no rise-from-the-+ and no 0.96 scale of the screen behind, and M3's dismiss spring instead of TzSpring.Sheet; Phase 3 (add sheet) builds those.
@Composable
fun TzSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = TzRadius.sheetTop, topEnd = TzRadius.sheetTop)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.border(1.dp, Tz.line, shape),
        sheetState = sheetState,
        shape = shape,
        containerColor = Tz.surface,
        contentColor = Tz.text,
        tonalElevation = 0.dp,
        scrimColor = Tz.night.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .background(Tz.dashed, RoundedCornerShape(50)),
            )
        },
        content = content,
    )
}
