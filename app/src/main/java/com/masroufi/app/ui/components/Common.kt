package com.masroufi.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    if (onBack == null) {
        // Tab roots get an Apple-style large title; actions sit in glass circles beside it.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        return
    }
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            GlassIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                Icon(MIcons.Back, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = { Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() } },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
    )
}

/** A 44dp glass circle: the minimum comfortable touch target, with the press response starting on touch-down. */
@Composable
fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(48.dp)
            .liquidPress(interaction, CircleShape, scaleTo = 1.12f)
            .glass(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The house card: glass material that dips under the finger with a critically-damped spring the instant it is
 * pressed. [color] tints the glass rather than replacing it.
 */
@Composable
fun PressableCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    decoration: (DrawScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .then(if (onClick != null) Modifier.liquidPress(interaction, shape, scaleTo = 0.975f) else Modifier)
            // Decorations draw inside the glass, so they are clipped and scale with the press like the card itself.
            .glass(shape, tint = color, onDrawFront = decoration)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier,
            )
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    // A small tinted gem: deeper colour at the bottom, a lit rim on top, so badges read as objects, not dots.
    val shape = RoundedCornerShape(percent = 34)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.14f), tint.copy(alpha = 0.30f))))
            .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.06f))), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.54f))
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
    val reduce = LocalReduceMotion.current
    val primary = MaterialTheme.colorScheme.primary
    val float = if (reduce) null else rememberInfiniteTransition(label = "emptyFloat").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The empty state's icon floats in a glass orb with a soft halo, so an empty screen still feels designed.
        Box(
            Modifier
                .size(132.dp)
                .graphicsLayer { translationY = sin(float?.value ?: 0f) * 6.dp.toPx() }
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.28f), Color.Transparent)), size.minDimension / 2)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(92.dp).glass(CircleShape, tint = primary.copy(alpha = 0.18f), strong = true), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(40.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            GlassButton(onClick = onAction) { Text(actionLabel) }
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
    GlassDialog(
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
                    Icon(MIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
