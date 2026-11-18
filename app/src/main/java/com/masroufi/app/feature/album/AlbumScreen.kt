package com.masroufi.app.feature.album

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatDayMonth
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.CollectedProverb
import com.masroufi.app.data.repo.Milestone
import com.masroufi.app.domain.GoalKind
import com.masroufi.app.domain.LEVEL_NAMES
import com.masroufi.app.domain.LockHint
import com.masroufi.app.domain.MatchDay
import com.masroufi.app.domain.Proverb
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.domain.StampKind
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzPanel
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.pluralText
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.ProverbCard
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.paper.Stamp
import com.masroufi.app.ui.paper.color
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.delay

/** How long the new-level / new-stamp banner stays up. */
private const val CELEBRATION_MS = 2_400L

private const val GRID_COLUMNS = 3
private const val STUBS_SHOWN = 6

/** One cell of the proverb grid. [key] is stable across the list changing. */
private sealed class Cell(val key: String) {
    class Collected(val card: CollectedProverb, val isNew: Boolean) : Cell("card-${card.proverb.number}")
    class Locked(val hint: LockHint) : Cell("lock-${hint.name}")
}

/** ألبومك (Album.dc.html), a pushed screen. */
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    onBack: () -> Unit,
    onToday: () -> Unit,
    onOpenRoll: () -> Unit,
    onOpenBasket: () -> Unit,
    onOpenPockets: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenStories: () -> Unit = {},
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    // 06-HAPTICS: a new level or stamp is a Milestone. The banner shows once: everything unseen is marked when it ends.
    LaunchedEffect(celebration?.key) {
        if (celebration != null) {
            haptics.perform(HapticLevel.Milestone)
            delay(CELEBRATION_MS)
            viewModel.celebrated()
        }
    }
    AlbumContent(ui, celebration, onBack, onToday, onOpenRoll, onOpenBasket, onOpenPockets, onOpenSettings, onOpenProfile, onOpenStories)
}

@Composable
fun AlbumContent(
    ui: AlbumUi,
    celebration: Milestone?,
    onBack: () -> Unit,
    onToday: () -> Unit,
    onOpenRoll: () -> Unit,
    onOpenBasket: () -> Unit,
    onOpenPockets: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenStories: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf<Proverb?>(null) }
    val album = ui.album
    val cells: List<Cell> = remember(ui.cards, ui.locked) {
        ui.cards.mapIndexed { i, c -> Cell.Collected(c, isNew = i == 0) } + ui.locked.map { Cell.Locked(it) }
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        ) {
            item(key = "header") {
                Box(Modifier.padding(bottom = 14.dp)) {
                    PushedHeader(stringResource(R.string.album_title), onBack) {
                        CircleButton(TzIcons.settings, stringResource(R.string.album_settings_cd), onOpenSettings)
                    }
                }
            }
            if (album == null) {
                items(3, key = { "skeleton$it" }) { RowSkeleton(modifier = Modifier.padding(bottom = 14.dp)) }
                return@LazyColumn
            }
            item(key = "identity") { Box(Modifier.padding(bottom = 14.dp)) { Identity(ui, onOpenProfile) } }
            if (ui.empty) {
                item(key = "empty") {
                    Box(Modifier.padding(bottom = 14.dp)) {
                        EmptyState(
                            { Qersh(QershMood.Sleepy, 96.dp) }, R.string.empty_album_title, R.string.empty_album_line,
                            R.string.empty_album_action, onToday, primaryAction = true,
                        )
                    }
                }
            } else {
                item(key = "proverbs-title") {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.album_proverbs_title), style = TzType.titlePushed, color = Tz.text)
                        Text(
                            stringResource(R.string.album_proverbs_count, ui.cards.size.toString(), ui.totalCards.toString()),
                            style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2,
                        )
                    }
                }
                val rows = cells.chunked(GRID_COLUMNS)
                items(rows, key = { it.first().key }) { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cell ->
                            when (cell) {
                                is Cell.Collected -> CardCell(cell, Modifier.weight(1f)) { open = cell.card.proverb }
                                is Cell.Locked -> LockedCell(cell.hint, Modifier.weight(1f))
                            }
                        }
                        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item(key = "stamps") { Box(Modifier.padding(top = 6.dp, bottom = 14.dp)) { Stamps(ui) } }
                if (album.stubs.isNotEmpty()) item(key = "stubs") { Box(Modifier.padding(bottom = 14.dp)) { Stubs(album.stubs.take(STUBS_SHOWN), onOpenRoll) } }
            }
            item(key = "links") { Links(onOpenStories, onOpenBasket, onOpenPockets, onOpenSettings) }
        }
        val last = remember { mutableStateOf<Milestone?>(null) }
        if (celebration != null) last.value = celebration
        AnimatedVisibility(
            celebration != null,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = fadeOut(),
        ) { last.value?.let { Banner(it) } }
    }
    open?.let { CardDialog(it) { open = null } }
}

@Composable
private fun Identity(ui: AlbumUi, onOpenProfile: () -> Unit) {
    val a = checkNotNull(ui.album)
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(TzRadius.hero)
    val levelText = stringResource(R.string.album_level, a.level.toString())
    val identityCd = stringResource(R.string.album_identity_cd, a.levelName)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpenProfile() }
                .semantics(mergeDescendants = true) { contentDescription = identityCd },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Qersh(if (a.streak > 0) QershMood.Happy else QershMood.Sleepy, 96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(levelText, style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2)
                Text(a.levelName, style = TzType.card.copy(lineHeight = 1.1.em), color = Tz.text)
                Text(
                    if (a.streak > 0) stringResource(R.string.album_qersh_happy, a.streak.toString()) else stringResource(R.string.album_qersh_waiting),
                    style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TzProgressBar(listOf(ui.progress to Tz.yellow), height = 10.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.album_cut, a.handClosed.toString()), style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2)
                // Hidden at the top level.
                if (a.ticketsToNext != null) {
                    // The next level's name: LEVEL_NAMES is 0-based, a.level is 1-based.
                    Text(
                        stringResource(R.string.album_to_next, a.ticketsToNext.toString(), LEVEL_NAMES[a.level]),
                        style = TzType.caption.copy(fontSize = 12.sp), color = Tz.text2,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(a.streak, R.string.album_tile_streak, Tz.yellow, Modifier.weight(1f))
            Tile(a.longestStreak, R.string.album_tile_longest, Tz.text, Modifier.weight(1f))
            Tile(a.daysUnder, R.string.album_tile_under, Tz.turquoise, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(value: Int, @StringRes label: Int, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Tz.night).padding(horizontal = 6.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value.toString(), style = TzType.card.copy(fontSize = 24.sp), color = color)
        Text(stringResource(label), style = TzType.caption.copy(fontSize = 11.sp, textAlign = TextAlign.Center), color = Tz.text2)
    }
}

/** Ink on a card: dark on every colour, teal-black on turquoise and red-black on carnelian (as the reward screen). */
private fun inkOn(color: Color): Color = when (color) {
    Tz.turquoise -> Tz.turquoiseInk
    Tz.carnelian -> Tz.carnelianInk
    else -> Tz.night
}

@Composable
private fun CardCell(cell: Cell.Collected, modifier: Modifier, onOpen: () -> Unit) {
    val haptics = LocalHaptics.current
    val p = cell.card.proverb
    val color = p.set.color()
    val ink = inkOn(color)
    val cd = stringResource(R.string.album_card_cd, p.number.toString(), p.text)
    Column(
        modifier.height(140.dp).clip(RoundedCornerShape(18.dp)).background(color)
            .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpen() }
            .semantics(mergeDescendants = true) { contentDescription = cd }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (cell.isNew) stringResource(R.string.album_card_new, p.number.toString()) else p.number.toString(),
            style = TzType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = ink,
        )
        Text(p.text, style = TzType.card.copy(fontSize = 16.sp, lineHeight = 1.3.em), color = ink)
    }
}

@Composable
private fun LockedCell(hint: LockHint, modifier: Modifier) {
    val label = stringResource(
        when (hint) {
            LockHint.TOMORROW -> R.string.album_lock_tomorrow
            LockHint.FULL_WEEK -> R.string.album_lock_week
            LockHint.RARE -> R.string.album_lock_rare
        },
    )
    val cd = stringResource(R.string.album_locked_cd, label)
    Column(
        modifier.height(140.dp).drawBehind {
            val w = 1.5.dp.toPx()
            drawRoundRect(
                Tz.dashed, Offset(w / 2f, w / 2f), Size(size.width - w, size.height - w), CornerRadius(18.dp.toPx() - w / 2f),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }.semantics(mergeDescendants = true) { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        TzIcon(TzIcons.lock, 20.dp, null, tint = Tz.text3)
        Text(label, style = TzType.caption.copy(fontSize = 11.sp), color = Tz.text3)
    }
}

@StringRes
private fun StampKind.nameRes(): Int = when (this) {
    StampKind.FIRST_TICKET -> R.string.stamp_first_ticket
    StampKind.FULL_WEEK -> R.string.stamp_full_week
    StampKind.HAT_TRICK -> R.string.stamp_hat_trick
    StampKind.TIGHT_FIST -> R.string.stamp_tight_fist
    StampKind.FIRST_GRAM -> R.string.stamp_first_gram
    StampKind.SETTLED -> R.string.stamp_settled
}

@StringRes
private fun StampKind.howRes(): Int = when (this) {
    StampKind.FIRST_TICKET -> R.string.stamp_first_ticket_how
    StampKind.FULL_WEEK -> R.string.stamp_full_week_how
    StampKind.HAT_TRICK -> R.string.stamp_hat_trick_how
    StampKind.TIGHT_FIST -> R.string.stamp_tight_fist_how
    StampKind.FIRST_GRAM -> R.string.stamp_first_gram_how
    StampKind.SETTLED -> R.string.stamp_settled_how
}

private fun StampKind.stampColor(): Color = when (this) {
    StampKind.FIRST_TICKET, StampKind.FIRST_GRAM -> Tz.yellow
    StampKind.FULL_WEEK, StampKind.SETTLED -> Tz.turquoise
    StampKind.HAT_TRICK -> Tz.lilac
    StampKind.TIGHT_FIST -> Tz.sky
}

private val Tilts = listOf(-8f, 6f, -4f)

@Composable
private fun Stamps(ui: AlbumUi) {
    val locale = LocalConfiguration.current.locales[0]
    TzPanel(spacing = 14.dp) {
        Text(stringResource(R.string.album_stamps_title), style = TzType.titlePushed, color = Tz.text)
        ui.stamps.chunked(GRID_COLUMNS).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, slot ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val earned = slot.earnedOn
                        val sub = when {
                            earned == null -> stringResource(slot.kind.howRes())
                            slot.count > 1 -> pluralText(R.plurals.album_stamp_times, slot.count)
                            else -> formatDayMonth(earned, locale)
                        }
                        Stamp(stringResource(slot.kind.nameRes()), if (earned != null) slot.kind.stampColor() else null, sub, Tilts[(r * GRID_COLUMNS + c) % Tilts.size])
                    }
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val StubTilts = listOf(0f, -3f, 0f, 2f, 0f, -2f)

@Composable
private fun Stubs(stubs: List<MatchDay>, onOpenRoll: () -> Unit) {
    val haptics = LocalHaptics.current
    TzPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.album_stubs_title), style = TzType.titlePushed, color = Tz.text)
            TextLink(stringResource(R.string.album_stubs_all), { haptics.perform(HapticLevel.Tap); onOpenRoll() })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stubs.forEachIndexed { i, d -> StubTile(d, StubTilts[i % StubTilts.size], Modifier.weight(1f)) }
            repeat(STUBS_SHOWN - stubs.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StubTile(d: MatchDay, tilt: Float, modifier: Modifier) {
    val (bg, ink) = when {
        d.goal == GoalKind.AGAINST -> Tz.carnelian to Tz.carnelianInk
        d.amount == 0L -> Tz.yellow to Tz.night
        else -> Tz.turquoise to Tz.turquoiseInk
    }
    val amount = when {
        d.goal == GoalKind.AGAINST -> "-${formatPounds(d.amount)}"
        d.amount == 0L -> stringResource(R.string.album_stub_ok)
        else -> "+${formatPounds(d.amount)}"
    }
    val cd = stringResource(R.string.album_stub_cd, d.date.dayOfMonth.toString(), amount)
    Column(
        modifier.height(64.dp).graphicsLayer { rotationZ = tilt }
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomEnd = 12.dp, bottomStart = 12.dp)).background(bg)
            .drawBehind {
                drawLine(
                    Tz.night, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
            .semantics(mergeDescendants = true) { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(d.date.dayOfMonth.toString(), style = TzType.card.copy(fontSize = 18.sp, lineHeight = 1.em), color = ink)
        Text(amount, style = TzType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = ink, maxLines = 1)
    }
}

@Composable
private fun Links(onOpenStories: () -> Unit, onOpenBasket: () -> Unit, onOpenPockets: () -> Unit, onOpenSettings: () -> Unit) {
    val shape = RoundedCornerShape(TzRadius.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)) {
        LinkRow(R.string.album_link_stories, onOpenStories, divider = true)
        LinkRow(R.string.album_link_basket, onOpenBasket, divider = true)
        LinkRow(R.string.album_link_pockets, onOpenPockets, divider = true)
        LinkRow(R.string.album_link_settings, onOpenSettings, divider = false)
    }
}

@Composable
private fun LinkRow(@StringRes text: Int, onClick: () -> Unit, divider: Boolean) {
    val haptics = LocalHaptics.current
    Column(Modifier.clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onClick() }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(text), style = TzType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Tz.text, modifier = Modifier.weight(1f))
            TzIcon(TzIcons.chevron, 18.dp, null, tint = Tz.text2)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
    }
}

/** A collected card, opened over the album (the reward screen's card). */
@Composable
private fun CardDialog(p: Proverb, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProverbCard(
                setName = stringResource(R.string.proverb_set_title),
                number = p.number,
                total = ProverbCatalog.all.size,
                proverb = p.text,
                line = p.line,
                color = p.set.color(),
            )
            PrimaryButton(stringResource(R.string.album_card_close), onDismiss, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}

/** The short celebration for a new level or stamp. */
@Composable
private fun Banner(m: Milestone) {
    val text = when (m) {
        is Milestone.NewLevel -> stringResource(R.string.album_celebrate_level, m.name)
        is Milestone.NewStamp -> stringResource(R.string.album_celebrate_stamp, stringResource(m.kind.nameRes()))
    }
    val shape = RoundedCornerShape(TzRadius.card)
    Row(
        Modifier.padding(horizontal = 16.dp).clip(shape).background(Tz.yellow).padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Qersh(QershMood.Delighted, 44.dp, onYellow = true)
        Text(text, style = TzType.cardHeading, color = Tz.night)
    }
}
