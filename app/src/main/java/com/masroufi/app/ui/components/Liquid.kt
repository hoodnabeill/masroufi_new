package com.masroufi.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.sin

/** Sheets and dialogs float above the app: a translucent pane that the system blurs the app behind (Android 12+). */
@Composable
private fun paneColor(): Color = if (LocalGlass.current.dark) Color(0xE0121619) else Color(0xE8F9FAFD)

@Composable
private fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    tint: Color,
    contentColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val reduce = LocalReduceMotion.current
    val fade by animateFloatAsState(if (enabled) 1f else 0.42f, Motion.effects(reduce), label = "buttonEnabled")
    Row(
        modifier
            .defaultMinSize(minHeight = 52.dp)
            .graphicsLayer { alpha = fade }
            .liquidPress(interaction, CircleShape, scaleTo = 1.04f)
            .glass(CircleShape, tint = tint, strong = true)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, LocalTextStyle provides MaterialTheme.typography.titleSmall) { content() }
    }
}

/** The primary action: a tinted drop of liquid glass that swells under the finger. */
@Composable
fun GlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    LiquidButton(onClick, modifier, enabled, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, content)
}

/** Secondary action: clear glass with accent-coloured label, so it ranks below [GlassButton] without a new style. */
@Composable
fun GlassSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    LiquidButton(onClick, modifier, enabled, Color.Unspecified, MaterialTheme.colorScheme.primary, content)
}

/**
 * A liquid toggle. The knob stretches into a capsule as it travels (and while pressed), then settles round on the
 * other side; the track floods with the accent. Semantics come from `toggleable` (role Switch, on/off state).
 */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val position by animateFloatAsState(
        if (checked) 1f else 0f,
        if (reduce) snap<Float>() else spring<Float>(dampingRatio = 0.68f, stiffness = 520f),
        label = "switch",
    )
    val squash by animateFloatAsState(if (pressed && !reduce) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "switchPress")
    val primary = MaterialTheme.colorScheme.primary
    val dark = LocalGlass.current.dark
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.14f)
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch) {
            haptics.tick()
            onCheckedChange(it)
        }
    } else {
        Modifier
    }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .then(toggle)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .size(52.dp, 32.dp)
            .drawBehind {
                val p = position.coerceIn(0f, 1f)
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(lerp(off, primary, p), cornerRadius = radius)
                drawRoundRect(Color.White.copy(alpha = if (dark) 0.10f else 0.45f), cornerRadius = radius, style = Stroke(1.dp.toPx()))
                val inset = 3.dp.toPx()
                val knob = size.height - inset * 2
                val width = knob + (sin(PI.toFloat() * p) * 0.4f + squash * 0.22f) * knob
                val travel = size.width - inset * 2 - width
                var x = inset + travel * position
                if (layoutDirection == LayoutDirection.Rtl) x = size.width - x - width
                drawRoundRect(Color.Black.copy(alpha = 0.16f), Offset(x, inset + 1.5.dp.toPx()), Size(width, knob), CornerRadius(knob / 2))
                drawRoundRect(Color.White, Offset(x, inset), Size(width, knob), CornerRadius(knob / 2))
            },
    )
}

/** Bottom sheet as a frosted pane: translucent, system-blurred behind, with a soft grabber and large top corners. */
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = MaterialTheme.shapes.extraLarge
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = top.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        containerColor = paneColor(),
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(38.dp, 5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
            )
        },
    ) {
        BlurBehindWindow()
        content()
    }
}

/** Alert dialog as a frosted pane with the app blurred behind it. Same slots as Material's AlertDialog. */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlurBehindWindow()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = paneColor(),
        tonalElevation = 0.dp,
    )
}

/** Snackbars float as a dark frosted capsule above the content instead of Material's flat inverse bar. */
@Composable
fun GlassSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    SnackbarHost(state, modifier) { data ->
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .glass(MaterialTheme.shapes.large, tint = glass.base.copy(alpha = 0.72f), strong = true)
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            data.visuals.actionLabel?.let { label -> TextButton(onClick = { data.performAction() }) { Text(label) } }
        }
    }
}

/** Text fields read as recessed glass wells: frosted fill, hairline rim, accent rim when focused. */
@Composable
fun glassFieldColors(): TextFieldColors {
    val glass = LocalGlass.current
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = glass.fillStrong,
        unfocusedContainerColor = glass.fill,
        disabledContainerColor = glass.fill,
        errorContainerColor = scheme.error.copy(alpha = 0.08f),
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = glass.strokeTop.copy(alpha = if (glass.dark) 0.5f else 0.9f),
        disabledBorderColor = glass.strokeTop.copy(alpha = 0.4f),
        // Pickers render as disabled read-only fields under a click overlay; keep them fully legible.
        disabledTextColor = scheme.onSurface,
        disabledLabelColor = scheme.onSurfaceVariant,
        disabledTrailingIconColor = scheme.onSurfaceVariant,
        disabledLeadingIconColor = scheme.onSurfaceVariant,
    )
}

/** Choice chips as small glass capsules that fill with accent light when chosen. */
@Composable
fun glassChipColors(): SelectableChipColors {
    val glass = LocalGlass.current
    val scheme = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        containerColor = glass.fill,
        selectedContainerColor = scheme.primary.copy(alpha = if (glass.dark) 0.26f else 0.16f),
        selectedLabelColor = scheme.onSurface,
        selectedLeadingIconColor = scheme.primary,
    )
}

@Composable
fun glassAssistChipColors(): ChipColors = AssistChipDefaults.assistChipColors(containerColor = LocalGlass.current.fill)
