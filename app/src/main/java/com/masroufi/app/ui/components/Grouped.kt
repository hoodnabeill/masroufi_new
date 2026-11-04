package com.masroufi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion

/**
 * The inset-grouped list (Settings app): an optional footnote header, one rounded card of [rows] separated by
 * hairlines inset to the text column ([inset]: 16 dp, or 60 dp when rows lead with an icon tile), and an
 * optional footnote under the card.
 */
@Composable
fun GroupedSection(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    inset: Dp = 16.dp,
) {
    val glass = LocalGlass.current
    Column(modifier.fillMaxWidth()) {
        header?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp).semantics { heading() },
            )
        }
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill)) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.padding(start = inset).fillMaxWidth().heightIn(min = 0.5.dp, max = 0.5.dp).background(glass.strokeBottom))
                row()
            }
        }
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/**
 * One cell: optional solid icon tile, title and subtitle, then a [value], a [trailing] control or, for a row that
 * navigates, a chevron. Tappable rows highlight on touch-down, like UITableView.
 */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
    value: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val glass = LocalGlass.current
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlight by animateColorAsState(
        if (pressed) glass.fillStrong else glass.fillStrong.copy(alpha = 0f),
        if (pressed || reduce) snap() else Motion.effects(false),
        label = "rowHighlight",
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(highlight)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconBadge(icon, iconTint, size = 30.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        when {
            trailing != null -> trailing()
            onClick != null -> {
                Spacer(Modifier.width(0.dp))
                Icon(MIcons.ChevronForward, contentDescription = null, tint = glass.textTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A cell that holds a full-width control (segmented choice, field) with the cell's padding. */
@Composable
fun GroupedContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) { content() }
}
