package com.masroufi.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.SeasonView
import com.masroufi.app.feature.pockets.pocketColor
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.PlusComingSheet
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.EmptyJar
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.format.DateTimeFormatter

/** A season counts as close (red date) inside this many days while its target is unmet. */
private const val CLOSE_DAYS = 45L

/** المواسم: the seasons view inside the Plan tab (Seasons.dc.html), Plus-locked as a whole. */
@Composable
fun SeasonsSection(viewModel: SeasonsViewModel, onOpen: (Long) -> Unit, onNewOccasion: () -> Unit, onZakat: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    if (ui.loading) return
    val content: @Composable () -> Unit = { SeasonsContent(ui, viewModel, onOpen, onNewOccasion, onZakat) }
    if (ui.isPlus) content() else PlusLocked(R.string.seasons_intro, viewModel::openPlus, content)
    if (ui.plusSheet) PlusComingSheet(viewModel::dismissPlus)
}

@Composable
private fun SeasonsContent(ui: SeasonsUi, vm: SeasonsViewModel, onOpen: (Long) -> Unit, onNewOccasion: () -> Unit, onZakat: () -> Unit) {
    val hero = ui.hero
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (hero == null) {
            EmptyState({ EmptyJar() }, R.string.empty_seasons_title, R.string.empty_seasons_line, R.string.empty_seasons_action, vm::addRamadan, primaryAction = true)
        } else {
            Text(stringResource(R.string.seasons_intro), style = TzType.body.copy(fontSize = 14.sp, lineHeight = 24.sp), color = Tz.text2, modifier = Modifier.padding(horizontal = 4.dp))
            SeasonHero(hero, ui.ledger[hero.season.id]) { onOpen(hero.season.id) }
            Timeline(ui, vm, onOpen)
            TextLink(stringResource(R.string.seasons_add_occasion), onNewOccasion, Modifier.align(Alignment.CenterHorizontally))
            ui.together?.takeIf { it.neededPerMonth > 0 }?.let { Together(ui, it.neededPerMonth, it.usualPerMonth, it.gap > 0, vm) }
        }
        TzCard(onClick = onZakat) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.zakat_row_title), style = TzType.cardHeading, color = Tz.text)
                    Text(stringResource(R.string.zakat_row_line), style = TzType.caption.copy(lineHeight = 19.sp), color = Tz.text2)
                }
                TzIcon(TzIcons.chevron, 20.dp, null)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonHero(v: SeasonView, note: LedgerNote?, onClick: () -> Unit) {
    val s = v.season
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.yellow).clickable(role = Role.Button, onClick = onClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, style = TzType.titleTab, color = Tz.night)
            Text(
                stringResource(if (v.approximate) R.string.seasons_days_hijri else R.string.seasons_days, v.daysUntil.toInt()),
                style = TzType.caption.copy(fontWeight = FontWeight.Bold), color = Tz.yellow,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Tz.night).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Text(formatPounds(v.saved), style = TzType.hero, color = Tz.night)
            Text(
                stringResource(R.string.seasons_of, formatPounds(s.target)),
                style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.yellowInk, modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val bar = RoundedCornerShape(5.dp)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(bar).background(Tz.yellowRule2)) {
            val fraction = if (s.target > 0) (v.saved.toFloat() / s.target).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Tz.night, bar))
        }
        if (v.perDayNeeded > 0) {
            Text(stringResource(R.string.seasons_per_day, formatPounds(v.perDayNeeded)), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.night)
        }
        if (note != null) Text(stringResource(R.string.season_ledger_line, note.person, formatPounds(note.amount)), style = TzType.caption.copy(fontWeight = FontWeight.SemiBold), color = Tz.night)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            v.items.forEach {
                Text(
                    stringResource(R.string.seasons_chip, it.label, formatPounds(it.amount)),
                    style = TzType.caption.copy(fontWeight = FontWeight.SemiBold), color = Tz.night,
                    modifier = Modifier.border(1.5.dp, Tz.night, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Timeline(ui: SeasonsUi, vm: SeasonsViewModel, onOpen: (Long) -> Unit) {
    val rest = ui.rest
    if (rest.isEmpty()) return
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 10.dp, bottom = 10.dp).width(2.dp).fillMaxHeight().background(Tz.line))
        Column(Modifier.fillMaxWidth().padding(start = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rest.forEachIndexed { i, v ->
                SeasonRow(v, ui.ledger[v.season.id], ui.reordering, canUp = i > 0, canDown = i < rest.lastIndex, onMove = { vm.move(v.season.id, it) }) { onOpen(v.season.id) }
            }
        }
    }
}

@Composable
private fun SeasonRow(v: SeasonView, note: LedgerNote?, reordering: Boolean, canUp: Boolean, canDown: Boolean, onMove: (Int) -> Unit, onClick: () -> Unit) {
    val s = v.season
    val locale = LocalConfiguration.current.locales[0]
    val month = remember(locale) { DateTimeFormatter.ofPattern("MMMM", locale) }.format(v.date)
    val close = s.target > v.saved && v.daysUntil <= CLOSE_DAYS
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.align(Alignment.TopStart).offset(x = (-22).dp, y = 20.dp).size(14.dp)
                .background(if (close) Tz.carnelian else pocketColor(s.color), CircleShape).border(3.dp, Tz.night, CircleShape),
        )
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, if (close) Tz.carnelian else Tz.line, shape)
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(s.name, style = TzType.cardHeading, color = Tz.text)
                    Text(
                        stringResource(if (v.approximate) R.string.seasons_when_hijri else R.string.seasons_when, month, v.daysUntil.toInt()),
                        style = TzType.caption.copy(fontWeight = if (close) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (close) Tz.carnelianText else Tz.text2,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(v.items.joinToString("، ") { it.label }, style = TzType.body.copy(fontSize = 13.sp), color = Tz.text2, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.jar_goal_amounts, formatPounds(v.saved), formatPounds(s.target)),
                        style = TzType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Tz.text,
                    )
                }
                if (s.target > 0) TzProgressBar(listOf((v.saved.toFloat() / s.target) to pocketColor(s.color)), height = 6.dp)
                if (note != null) Text(stringResource(R.string.season_ledger_line, note.person, formatPounds(note.amount)), style = TzType.caption, color = Tz.text2)
            }
            if (reordering) {
                Column {
                    Arrow(false, canUp, stringResource(R.string.seasons_up, s.name)) { onMove(-1) }
                    Arrow(true, canDown, stringResource(R.string.seasons_down, s.name)) { onMove(1) }
                }
            }
        }
    }
}

@Composable
private fun Arrow(down: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        Modifier.size(44.dp).clickable(enabled = enabled, role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        TzIcon(TzIcons.arrowUp, 20.dp, description, Modifier.rotate(if (down) 180f else 0f), tint = if (enabled) Tz.text else Tz.text3)
    }
}

@Composable
private fun Together(ui: SeasonsUi, needed: Long, usual: Long, over: Boolean, vm: SeasonsViewModel) {
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.turquoiseDim).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.seasons_together_title), style = TzType.cardHeading, color = Tz.text)
        val lead = stringResource(R.string.seasons_together_lead, ui.togetherCount)
        val need = stringResource(R.string.seasons_together_need, formatPounds(needed))
        val tail = if (over) stringResource(R.string.seasons_together_over, formatPounds(usual)) else stringResource(R.string.seasons_together_ok)
        Text(
            buildAnnotatedString {
                append("$lead ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Tz.text)) { append(need) }
                append(tail)
            },
            style = TzType.body.copy(fontSize = 13.sp, lineHeight = 22.sp), color = Tz.turquoiseDimText,
        )
        Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Tz.turquoise)
                .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); vm.toggleReorder() }.padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(if (ui.reordering) R.string.seasons_reorder_done else R.string.seasons_reorder), style = TzType.button.copy(fontSize = 16.sp), color = Tz.turquoiseInk)
        }
    }
}
