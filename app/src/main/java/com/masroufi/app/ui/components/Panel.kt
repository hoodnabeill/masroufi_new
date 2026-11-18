package com.masroufi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius

/** A Surface card whose children are [spacing] apart (TzCard has no spacing). */
@Composable
fun TzPanel(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(TzRadius.card)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
