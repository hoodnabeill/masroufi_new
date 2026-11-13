package com.masroufi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType

enum class TzTab { Today, Roll, Plan, People }

@Composable
private fun TzTab.label(): String = stringResource(
    when (this) {
        TzTab.Today -> R.string.tab_today
        TzTab.Roll -> R.string.tab_roll
        TzTab.Plan -> R.string.tab_plan
        TzTab.People -> R.string.tab_people
    },
)

private fun TzTab.icon(): TzIconDef = when (this) {
    TzTab.Today -> TzIcons.today
    TzTab.Roll -> TzIcons.roll
    TzTab.Plan -> TzIcons.plan
    TzTab.People -> TzIcons.people
}

/**
 * Floating bar from Main.dc.html: 16 dp side margins, 72 dp, Surface, 1 dp Line, radius 26, tabs share the width
 * equally with 24 dp icon, 4 dp gap, 11 sp label; 56 dp yellow + in the middle. Order is Today, Roll, +, Plan,
 * People, laid out by the layout direction (RTL puts Today at the right).
 */
@Composable
fun TzTabBar(
    selected: TzTab,
    onSelect: (TzTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The mock sits the bar 20 dp above the screen edge; here the system bar inset plus 8 dp stands in for it.
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(TzRadius.tabBar))
                .background(Tz.surface)
                .border(1.dp, Tz.line, RoundedCornerShape(TzRadius.tabBar))
                .padding(horizontal = 6.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(TzTab.Today, selected, onSelect)
            TabItem(TzTab.Roll, selected, onSelect)
            AddButton(onAdd)
            TabItem(TzTab.Plan, selected, onSelect)
            TabItem(TzTab.People, selected, onSelect)
        }
    }
}

/** Selecting animates icon tint, label colour and the outline-to-filled cross-fade with Snappy. */
@Composable
private fun RowScope.TabItem(tab: TzTab, current: TzTab, onSelect: (TzTab) -> Unit) {
    val haptics = LocalHaptics.current
    val isSelected = tab == current
    val source = remember { MutableInteractionSource() }
    val color = animateColorAsState(if (isSelected) Tz.yellow else Tz.text2, motionSpec(TzSpring.Snappy), label = "tabColor").value
    val fill = animateFloatAsState(if (isSelected) 1f else 0f, motionSpec(TzSpring.Snappy), label = "tabFill").value
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = isSelected, interactionSource = source, indication = null, role = Role.Tab) {
                if (!isSelected) haptics.perform(HapticLevel.Tick)
                onSelect(tab)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.pressScale(source, PressScale.TabIcon)) {
            TzIcon(tab.icon(), 24.dp, null, Modifier.alpha(1f - fill), tint = color)
            TzIcon(tab.icon(), 24.dp, null, Modifier.alpha(fill), tint = color, filled = true, inkColor = Tz.surface)
        }
        Text(
            tab.label(),
            style = TzType.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = color,
            ),
        )
    }
}

@Composable
private fun AddButton(onAdd: () -> Unit) {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val description = stringResource(R.string.add_entry)
    Box(
        Modifier
            .pressScale(source, PressScale.Plus)
            .size(56.dp)
            .clip(CircleShape)
            .background(Tz.yellow)
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.perform(HapticLevel.Tap)
                onAdd()
            },
        contentAlignment = Alignment.Center,
    ) {
        TzIcon(TzIcons.add, 26.dp, null, tint = Tz.night)
    }
}
