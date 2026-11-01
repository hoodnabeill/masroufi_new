package com.masroufi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.masroufi.app.R

/**
 * A group of related fields on one glass panel with a quiet label above it, so a long form reads as three or four
 * short steps instead of one wall of inputs.
 */
@Composable
fun FormSection(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp).semantics { heading() },
            )
        }
        PressableCard(onClick = null, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Save stays under the thumb: pinned above the navigation bar and keyboard instead of at the end of the scroll. */
@Composable
fun EditorSaveBar(onSave: () -> Unit, enabled: Boolean = true, label: String = stringResource(R.string.action_save)) {
    GlassButton(
        onClick = onSave,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp).height(56.dp),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}
