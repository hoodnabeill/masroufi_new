package com.masroufi.app.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.WishEntity
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.domain.HomeCard
import com.masroufi.app.feature.add.PunchFeedback
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Home at 200% font, right-to-left, animations off (Task 60): every card state the screen can be in. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAuditTest {
    @get:Rule val rule = createComposeRule()

    private val week = listOf(
        WeekStub(StubKind.Under, 4_000, false), WeekStub(StubKind.Over, 3_500, false), WeekStub(StubKind.Under, 7_200, false),
        WeekStub(StubKind.Empty, 0, false), WeekStub(StubKind.Today, 0, true), WeekStub(StubKind.Empty, 0, false), WeekStub(StubKind.Empty, 0, false),
    )
    private val open = TicketUi(24_000, 8_400, 3, SubLine.Spent(24_000, 8_400, 3))
    private val chips = listOf(RepeatChip(1, "مواصلات", 1_500, 1), RepeatChip(2, "قهوة", 3_500, 1), RepeatChip(3, "سندوتشات", 2_500, 1))

    private fun ready(ticket: TicketUi? = open) = HomeUiState.Ready(
        today = LocalDate.of(2026, 11, 12), greeting = Greeting.Morning, streak = 12, ticket = ticket,
        tomorrow = if (ticket == null) TomorrowUi(24_000, 18) else null, repeats = chips, week = week, closedUnder = 4, jarTotal = 184_000,
        cycle = CycleUi(13, 17f / 30, 312_000, 62_000, 25), collected = 17, cardOrder = HomeCard.values().toList(),
    )

    private fun audit(state: HomeUiState.Ready, feedback: PunchFeedback.Saved? = null, saveFailed: Boolean = false, onMic: (() -> Unit)? = {}) {
        rule.audit {
            HomeContent(state, "", {}, {}, onMic, saveFailed, {}, {}, {}, feedback = feedback)
        }.assertNone()
    }

    @Test fun ticketOpenWithEveryLine() = audit(ready().copy(freelanceOn = true), PunchFeedback.Saved(15_600, firstOfDay = true, pausedHabit = true))
    @Test fun overTheTicket() = audit(ready(TicketUi(24_000, 27_500, 4, SubLine.Over(3_500, spread = true))), PunchFeedback.Saved(-3_500, firstOfDay = false), saveFailed = true, onMic = null)
    @Test fun shortEndOfMonthRow() = audit(ready(TicketUi(0, 0, 0, SubLine.Short(50_000))))
    @Test fun afterClose() = audit(ready(null))
    @Test fun firstWeekMoneyCard() = audit(ready().copy(firstWeekCard = FirstWeekCard.Money))
    @Test fun firstWeekTextCard() = audit(ready().copy(firstWeekCard = FirstWeekCard.Day3))
    @Test fun selfPaydayFull() = audit(ready().copy(selfPayCard = SelfPayCard("k", 700_000, 700_000, 5, false, true)))
    @Test fun selfPaydayShort() = audit(ready().copy(selfPayCard = SelfPayCard("k", 300_000, 700_000, 5, true, true)))
    @Test fun selfPaydayEmptyVault() = audit(ready().copy(selfPayCard = SelfPayCard("k", 0, 700_000, null, true, true)))
    @Test fun endOfMonthOffer() = audit(ready().copy(endOfMonthOffer = true))
    @Test fun wishQuestion() = audit(ready().copy(dueWish = WishEntity(name = "سماعة بلوتوث", price = 150_000, parkedAt = 0)))
    @Test fun trialCardAndEndsSoonLine() = audit(ready().copy(trialOffer = TrialOffer(LocalDate.of(2026, 11, 26)), trialEndsSoon = TrialEndsSoon(2)))

    @Test fun moneySheet() {
        rule.audit { MoneySheet({}, {}) }.assertNone()
    }
}
