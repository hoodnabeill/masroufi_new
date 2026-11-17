package com.masroufi.app.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    private val empty = List(7) { WeekStub(StubKind.Empty, 0, false) }

    private fun ready(ticket: TicketUi?, week: List<WeekStub> = empty, tomorrow: TomorrowUi? = null) = HomeUiState.Ready(
        today = LocalDate.of(2026, 11, 12),
        greeting = Greeting.Morning,
        streak = 12,
        ticket = ticket,
        tomorrow = tomorrow,
        repeats = emptyList(),
        week = week,
        closedUnder = 4,
        jarTotal = 184_000,
        cycle = CycleUi(13, 17f / 30, 312_000, 62_000, 25),
        collected = 17,
    )

    @Composable
    private fun Home(state: HomeUiState) = MasroufiTheme {
        HomeContent(state, "", {}, {}, null, false, {}, {}, {})
    }

    @Test fun skeletonThenContent() {
        var state by mutableStateOf<HomeUiState>(HomeUiState.Loading)
        rule.mainClock.autoAdvance = false
        rule.setContent { Home(state) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag(HomeSkeletonTag).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag(HomeSkeletonTag).assertExists("skeleton after 150 ms of loading")
        state = ready(TicketUi(24_000, 0, 0, SubLine.None))
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag(HomeSkeletonTag).assertDoesNotExist()
        rule.onNodeWithText("أسبوعك").assertExists()
        rule.onNodeWithText("مثل النهارده مقفول").assertExists("teaser while the day is open")
    }

    @Test fun subLineTexts() {
        val texts = mutableListOf<String>()
        rule.setContent {
            MasroufiTheme {
                texts.clear()
                texts += subLineText(SubLine.None)
                texts += subLineText(SubLine.Spent(24_000, 8_400, 3))
                texts += subLineText(SubLine.Over(3_500, spread = true))
                texts += subLineText(SubLine.Over(3_500, spread = false))
                texts += subLineText(SubLine.Short(50_000))
            }
        }
        rule.waitForIdle()
        assertEquals(
            listOf(
                "لسه مصرفتش حاجة النهارده",
                "من 240 · صرفت 84 في 3 مرات",
                "عدّيت تذكرة النهارده بـ 35. اتوزّعوا على باقي الأيام.",
                "عدّيت تذكرة النهارده بـ 35.",
                "اللي معاك أقل من اللي لازم يتدفع بـ 500. ولا يهمك، نرتّبها سوا.",
            ),
            texts,
        )
    }

    @Test fun ticketReadsLeftOfTicketAndSpent() {
        rule.setContent { Home(ready(TicketUi(24_000, 8_400, 3, SubLine.Spent(24_000, 8_400, 3)))) }
        rule.onNodeWithContentDescription("معاك النهارده 156 جنيه من 240، صرفت 84 في 3 مرات").assertExists()
    }

    @Test fun weekStripStates() {
        val week = listOf(
            WeekStub(StubKind.Under, 4_000, false),
            WeekStub(StubKind.Under, 7_200, false),
            WeekStub(StubKind.Over, 3_500, false),
            WeekStub(StubKind.Under, 500, false),
            WeekStub(StubKind.Empty, 0, false),
            WeekStub(StubKind.Today, 0, true),
            WeekStub(StubKind.Empty, 0, false),
        )
        rule.setContent { Home(ready(TicketUi(24_000, 0, 0, SubLine.None), week)) }
        rule.onNodeWithText("+72").assertExists("closed under: +leftover")
        rule.onNodeWithText("-35").assertExists("closed over: -overspend")
        rule.onNodeWithText("النهارده").assertExists("today's open stub")
        rule.onNodeWithText("4 تذاكر مقفولة تحت الحد").assertExists()
    }

    @Test fun closedDayShowsTomorrow() {
        rule.setContent { Home(ready(null, tomorrow = TomorrowUi(24_000, 2))) }
        rule.onNodeWithText("تذكرة بكره: 240").assertExists()
        rule.onNodeWithText("مثل النهارده مقفول").assertDoesNotExist()
    }
}
