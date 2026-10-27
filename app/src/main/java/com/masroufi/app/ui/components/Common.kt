package com.masroufi.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasroufiTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = { actions() },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** A surface that dips slightly under the finger with a spring, giving tactile feedback without extra noise. */
@Composable
fun PressableCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed && !reduce) 0.97f else 1f, Motion.bouncy(reduce), label = "press")
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.large)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick) else Modifier,
            ),
        color = color,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun categoryColor(index: Int?): Color {
    val palette = LocalFinanceColors.current.categories
    return palette[((index ?: 0) % palette.size + palette.size) % palette.size]
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 72.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun LabeledRow(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        value()
    }
}

class Haptics(private val view: android.view.View) {
    fun confirm() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun reject() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(LocalFinanceColors.current.categories.size) { index ->
            val color = categoryColor(index)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (index == selected) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
