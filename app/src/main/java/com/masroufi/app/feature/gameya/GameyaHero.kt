package com.masroufi.app.feature.gameya

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.data.model.Gameya
import com.masroufi.app.domain.GameyaSummary
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The gameya at a glance: when the user cashes out and how much, then every turn as a dot — done, now, theirs and
 * still ahead — so the whole cycle reads in one line.
 */
@Composable
internal fun GameyaHero(gameya: Gameya, summary: GameyaSummary) {
    val glass = LocalGlass.current
    val finance = LocalFinanceColors.current
    val violet = MaterialTheme.colorScheme.secondary
    val lime = MaterialTheme.colorScheme.primary
    val locale = LocalConfiguration.current.locales[0]
    val months = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val today = remember { LocalDate.now() }
    val mine = summary.myPayoutRound
    val current = summary.rounds.firstOrNull { !it.date.isBefore(today) }?.round
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.extraLarge).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (mine != null) {
            Text(
                if (mine.payout != null) stringResource(R.string.gameya_hero_received) else stringResource(R.string.gameya_hero_payout, mine.date.format(months)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedAmount(mine.potMinor, gameya.currency, style = MaterialTheme.typography.displaySmall, color = lime)
            if (mine.payout == null) {
                val days = ChronoUnit.DAYS.between(today, mine.date).toInt()
                Text(
                    if (days <= 0) stringResource(R.string.gameya_hero_today) else pluralStringResource(R.plurals.gameya_hero_in_days, days, days),
                    style = MaterialTheme.typography.labelMedium,
                    color = violet,
                    modifier = Modifier.clip(CircleShape).background(violet.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        } else {
            Text(stringResource(R.string.gameya_pot), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedAmount(summary.potMinor, gameya.currency, style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.rounds.forEach { r ->
                val done = r.date.isBefore(today) && r.round != current
                val now = r.round == current
                val label = stringResource(R.string.gameya_round_n, r.round)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp).semantics(mergeDescendants = true) { contentDescription = label }) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    r.isMyPayout -> violet
                                    done -> lime.copy(alpha = 0.2f)
                                    else -> glass.fillStrong
                                },
                            )
                            .then(if (now) Modifier.border(2.dp, finance.streak, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            r.round.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                r.isMyPayout -> MaterialTheme.colorScheme.onSecondary
                                done -> lime
                                now -> finance.streak
                                else -> glass.textTertiary
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        r.date.monthValue.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Members with a saved number, each one tap from a call or a WhatsApp chat (to remind them of their instalment). */
@Composable
internal fun GameyaPeople(members: List<com.masroufi.app.data.model.GameyaMember>) {
    val glass = LocalGlass.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(vertical = 6.dp),
    ) {
        members.forEach { m ->
            val phone = m.phone ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                com.masroufi.app.ui.components.GlassIconButton(onClick = { com.masroufi.app.ui.components.Phones.call(context, phone) }) {
                    androidx.compose.material3.Icon(com.masroufi.app.ui.components.MIcons.Phone, stringResource(R.string.contact_call))
                }
                Spacer(Modifier.width(8.dp))
                com.masroufi.app.ui.components.GlassIconButton(onClick = { com.masroufi.app.ui.components.Phones.whatsapp(context, phone) }) {
                    androidx.compose.material3.Icon(com.masroufi.app.ui.components.MIcons.Chat, stringResource(R.string.contact_whatsapp))
                }
            }
        }
    }
}
