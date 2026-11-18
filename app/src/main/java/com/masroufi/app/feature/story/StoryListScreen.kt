package com.masroufi.app.feature.story

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzRadius
import com.masroufi.app.ui.theme.TzType

/** الحكايات, a pushed screen: one row per saved month; a row opens that month's story. */
@Composable
fun StoryListScreen(viewModel: StoryListViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val locale = LocalContext.current.resources.configuration.locales[0]
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") { PushedHeader(stringResource(R.string.story_list_title), onBack) }
        when {
            ui.loading -> items(3, key = { "skeleton$it" }) { RowSkeleton() }
            ui.empty -> item(key = "empty") {
                EmptyState({ Qersh(QershMood.Sleepy, 96.dp) }, R.string.story_list_empty_title, R.string.story_list_empty_line)
            }
            else -> items(ui.rows, key = { it.id }) { row ->
                val month = storyMonthName(row.cycleStart, row.cycleEnd, locale)
                val shape = RoundedCornerShape(TzRadius.card)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(Tz.surface).border(1.dp, Tz.line, shape)
                        .clickable(role = Role.Button) { haptics.perform(HapticLevel.Tap); onOpen(row.id) }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(month, style = TzType.cardHeading, color = Tz.text)
                        Text(
                            stringResource(R.string.story_list_row, row.torn.toString(), row.cycleDays.toString()),
                            style = TzType.caption, color = Tz.text2,
                        )
                    }
                    if (row.isNew) {
                        Text(
                            stringResource(R.string.story_list_new), style = TzType.caption, color = Tz.night,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Tz.yellow).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    TzIcon(TzIcons.chevron, 18.dp, null, modifier = Modifier.padding(start = 8.dp), tint = Tz.text2)
                }
            }
        }
    }
}
