package com.masroufi.app.feature.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.feature.plan.ChipRow
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressClickable
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import kotlinx.coroutines.delay

private val AvatarColors = listOf(
    Tz.turquoise to Tz.turquoiseInk, Tz.lilac to Color(0xFF2A0E38), Tz.sky to Color(0xFF06173D), Tz.yellow to Tz.night,
)

/** 46 dp disc (People.dc.html): initials in Lalezar 20 on a colour taken from the id; a shop is yellow with the shop icon. */
@Composable
fun Avatar(person: PersonEntity) {
    val (bg, ink) = if (person.kind == PersonKind.SHOP) Tz.yellow to Tz.night else AvatarColors[(person.id % AvatarColors.size).toInt()]
    Box(Modifier.size(46.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (person.kind == PersonKind.SHOP) TzIcon(TzIcons.shop, 22.dp, null, tint = ink)
        else Text(person.name.trim().take(1), style = TzType.button.copy(fontSize = 20.sp), color = ink)
    }
}

/** Pocket chips for the sheets; [none] adds "من غير ما أحرّك فلوس" (a record with no money moved). */
@Composable
fun PocketPicker(pockets: List<PocketEntity>, selected: Long?, none: Boolean, onSelect: (Long?) -> Unit) {
    ChipRow {
        pockets.forEach { p -> TzChip(p.name, selected == p.id, { onSelect(p.id) }, style = ChipStyle.Filter) }
        if (none) TzChip(stringResource(R.string.people_no_pocket), selected == null, { onSelect(null) }, style = ChipStyle.Filter)
    }
}

@Composable
fun noticeText(n: PeopleNotice): String = when (n.kind) {
    NoticeKind.TAB_SETTLED -> stringResource(R.string.people_notice_tab, n.name)
    NoticeKind.REPAID -> stringResource(R.string.people_notice_repaid)
    NoticeKind.RECEIVED -> stringResource(R.string.people_notice_received)
    NoticeKind.REMINDED -> stringResource(R.string.people_notice_remind)
    NoticeKind.SPLIT -> stringResource(R.string.people_notice_split)
}

/** The feedback line: a turquoise-dim band that clears itself after four seconds. */
@Composable
fun NoticeLine(notice: PeopleNotice?, onClear: () -> Unit) {
    if (notice == null) return
    LaunchedEffect(notice) {
        delay(NOTICE_MS)
        onClear()
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tz.turquoiseDim).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Tz.turquoise), contentAlignment = Alignment.Center) {
            TzIcon(TzIcons.check, 18.dp, null, tint = Tz.turquoiseInk)
        }
        Text(noticeText(notice), style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.turquoiseDimText)
    }
}

private const val NOTICE_MS = 4_000L

/** The action pill of a person row, drawn 32 dp tall with a 48 dp touch target: outlined turquoise (a nudge) or filled yellow (a payment). */
@Composable
fun SmallPill(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.minimumInteractiveComponentSize().pressClickable(PressScale.Chip) { haptics.perform(HapticLevel.Tap); onClick() }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(min = 32.dp).clip(shape)
                .then(if (filled) Modifier.background(Tz.yellow) else Modifier.border(1.dp, Tz.turquoise, shape))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = TzType.label.copy(fontSize = 12.sp, fontWeight = if (filled) FontWeight.Bold else FontWeight.SemiBold), color = if (filled) Tz.night else Tz.turquoise)
        }
    }
}

/** What Task 38 (duty ledger) and Task 39 (gameya) replace: a pushed screen that says it is coming. */
@Composable
fun SoonScreen(title: Int, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PushedHeader(stringResource(title), onBack)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Qersh(QershMood.Sleepy, 120.dp)
            Text(stringResource(R.string.people_soon), style = TzType.cardHeading, color = Tz.text2)
        }
    }
}
