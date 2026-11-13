package com.masroufi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIconDef
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType

enum class TzTab { Today, Roll, Plan, People }

// Product words from the spec; Phase 11 moves them to string resources.
private fun TzTab.label() = when (this) {
    TzTab.Today -> "اليوم"
    TzTab.Roll -> "الدفتر"
    TzTab.Plan -> "الخطة"
    TzTab.People -> "الناس"
}

private fun TzTab.icon(): TzIconDef = when (this) {
    TzTab.Today -> TzIcons.today
    TzTab.Roll -> TzIcons.roll
    TzTab.Plan -> TzIcons.plan
    TzTab.People -> TzIcons.people
}

/**
 * Floating bar from Main.dc.html: 16 dp side margins, 72 dp, Surface, 1 dp Line, radius 26, tabs 60 dp wide with
 * 24 dp icon, 4 dp gap, 11 sp label; 56 dp yellow + in the middle. Order is Today, Roll, +, Plan, People, laid out
 * by the layout direction (RTL puts Today at the right).
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
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
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

@Composable
private fun TabItem(tab: TzTab, current: TzTab, onSelect: (TzTab) -> Unit) {
    val haptics = LocalHaptics.current
    val isSelected = tab == current
    val source = remember { MutableInteractionSource() }
    val color = if (isSelected) Tz.yellow else Tz.text2
    Column(
        Modifier
            .width(60.dp)
            .fillMaxHeight()
            .selectable(selected = isSelected, interactionSource = source, indication = null, role = Role.Tab) {
                if (!isSelected) haptics.perform(HapticLevel.Tick)
                onSelect(tab)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        TzIcon(
            icon = tab.icon(),
            size = 24.dp,
            contentDescription = null,
            modifier = Modifier.pressScale(source, PressScale.TabIcon),
            tint = color,
            filled = isSelected,
            inkColor = Tz.surface,
        )
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
    Box(
        Modifier
            .pressScale(source, PressScale.Plus)
            .size(56.dp)
            .clip(CircleShape)
            .background(Tz.yellow)
            .semantics { contentDescription = "سجّل عملية جديدة" }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptics.perform(HapticLevel.Tap)
                onAdd()
            },
        contentAlignment = Alignment.Center,
    ) {
        TzIcon(TzIcons.add, 26.dp, null, tint = Tz.night)
    }
}
