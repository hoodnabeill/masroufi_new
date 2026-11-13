package com.masroufi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius

/**
 * Bottom sheet on Material3 ModalBottomSheet, styled to the system: Surface, top radius 32, 40 x 5 Dashed grabber
 * (10 dp above, 14 below), Night scrim at 60%. The 1 dp Line top border (AddPunch.dc.html:19, border-top only) is
 * drawn inside the sheet content along the two 32 dp corners, so it moves with the sheet. Pass [sheetState] to hide
 * it with the exit animation. M3 supplies finger tracking and fling dismissal (its own spring, not [TzSpring.Sheet]).
 */
// shortcut: no rise-from-the-+ and no 0.96 scale of the screen behind, and M3's dismiss spring instead of TzSpring.Sheet; Phase 3 (add sheet) builds those.
@Composable
fun TzSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = TzRadius.sheetTop, topEnd = TzRadius.sheetTop),
        containerColor = Tz.surface,
        contentColor = Tz.text,
        tonalElevation = 0.dp,
        scrimColor = Tz.night.copy(alpha = 0.6f),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().drawBehind { topBorder() }) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .background(Tz.dashed, RoundedCornerShape(50)),
            )
            content()
        }
    }
}

// Stroke inset by half its width so it sits inside the edge, like a CSS border-top on a rounded box.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.topBorder() {
    val s = 1.dp.toPx()
    val h = s / 2f
    val r = TzRadius.sheetTop.toPx()
    val path = Path().apply {
        moveTo(h, r)
        arcTo(Rect(h, h, 2 * r - h, 2 * r - h), 180f, 90f, false)
        lineTo(size.width - r, h)
        arcTo(Rect(size.width - 2 * r + h, h, size.width - h, 2 * r - h), 270f, 90f, false)
    }
    drawPath(path, Tz.line, style = Stroke(s))
}
