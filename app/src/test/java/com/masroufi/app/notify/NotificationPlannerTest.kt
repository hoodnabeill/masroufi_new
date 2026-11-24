package com.masroufi.app.notify

import com.masroufi.app.domain.NotifKind
import com.masroufi.app.domain.NotifKind.BILL
import com.masroufi.app.domain.NotifKind.EVENING
import com.masroufi.app.domain.NotifKind.GAMEYA_DUE
import com.masroufi.app.domain.NotifKind.GAMEYA_TURN
import com.masroufi.app.domain.NotifKind.LOW_BALANCE
import com.masroufi.app.domain.NotifKind.MORNING
import com.masroufi.app.domain.NotifKind.OCCASION
import com.masroufi.app.domain.NotifKind.STREAK
import com.masroufi.app.domain.NotifKind.TRIAL_PLUS
import com.masroufi.app.domain.NotifKind.TRIAL_SUB
import com.masroufi.app.domain.NotifKind.WISH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The rules on a plain clock: Tuesday 2026-11-17 is the day; the hour moves. */
class NotificationPlannerTest {
    private val day = LocalDate.of(2026, 11, 17)
    private val today = day.toEpochDay()
    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(day, LocalTime.of(hour, minute))
    private val defaults = NotifKind.entries.filter { it.defaultOn }.toSet()
    private fun cfg(reminderHour: Int? = 21, mask: Boolean = false, enabled: Set<NotifKind> = defaults) =
        NotifConfig(if (reminderHour == null) enabled - EVENING else enabled + EVENING, reminderHour, mask)

    private fun plan(now: LocalDateTime, facts: NotifFacts, cfg: NotifConfig = cfg(), state: NotifState = NotifState()) =
        NotificationPlanner.plan(now, facts, cfg, state)

    private fun kinds(n: List<Notice>) = n.map { it.kind }

    private val openTicket = NotifFacts(ticketOpen = true, ticketLeft = 7_200, ticketAmount = 24_000)

    // evening reminder

    @Test fun eveningFiresInItsHourWithWhatIsLeft() {
        val n = plan(at(21, 5), openTicket).single()
        assertEquals(EVENING, n.kind)
        assertEquals(listOf("72"), n.args)
        assertEquals(today, n.arg)
    }

    @Test fun eveningDoesNotFireBeforeItsHourOrAfterTheNext() {
        assertTrue(plan(at(20, 59), openTicket).isEmpty())
        assertTrue(plan(at(23, 0), openTicket).isEmpty())
    }

    @Test fun eveningIsSkippedWhenTheTicketIsClosed() {
        assertTrue(plan(at(21), openTicket.copy(ticketOpen = false)).isEmpty())
    }

    @Test fun eveningIsSkippedWithoutAReminderHour() {
        assertTrue(plan(at(21), openTicket, cfg(reminderHour = null)).isEmpty())
    }

    @Test fun eveningFiresOnlyOncePerDay() {
        val first = plan(at(21), openTicket).single()
        assertTrue(plan(at(22), openTicket, state = NotifState(sent = setOf(first.key))).isEmpty())
    }

    @Test fun eveningWithNothingLeftHasNoAmount() {
        val n = plan(at(21), openTicket.copy(ticketLeft = -500)).single()
        assertTrue(n.alt)
        assertTrue(n.args.isEmpty())
    }

    // morning

    @Test fun morningIsOffByDefaultAndFiresInTheMorningWhenOn() {
        assertTrue(plan(at(9), openTicket, cfg(reminderHour = null)).isEmpty())
        val on = cfg(reminderHour = null, enabled = defaults + MORNING)
        assertEquals(listOf("240"), plan(at(9), openTicket, on).single().args)
        assertTrue(plan(at(14), openTicket, on).isEmpty())
        assertTrue(plan(at(9), NotifFacts(), on).isEmpty()) // no ticket printed
    }

    // the day-before ones

    @Test fun aBillNameThatSaysBillIsNotDoubled() {
        val n = plan(at(10), NotifFacts(billsTomorrow = listOf(DueItem(1, "فاتورة الكهربا", 25_000)))).single()
        assertEquals(listOf("الكهربا", "250"), n.args)
    }

    @Test fun twoBillsTheSameDayGetTwoNotificationIds() {
        val notices = plan(at(10), NotifFacts(billsTomorrow = listOf(DueItem(1, "النت", 100), DueItem(2, "الغاز", 100))))
        // the daily cap is 2, so both come out
        assertEquals(2, notices.size)
        assertEquals(2, notices.map { noticeId(it) }.toSet().size)
        // the same item tomorrow keeps its id, a different kind never shares one
        assertEquals(noticeId(notices[0]), noticeId(notices[0].copy(key = notices[0].key.substringBefore('@') + "@1")))
        assertTrue(noticeId(Notice(EVENING, "evening@1", emptyList())) != noticeId(Notice(STREAK, "streak@1", emptyList())))
    }

    @Test fun billTomorrowFiresInTheDaytimeOnly() {
        val facts = NotifFacts(billsTomorrow = listOf(DueItem(3, "النت", 25_000)))
        val n = plan(at(10), facts).single()
        assertEquals(BILL, n.kind)
        assertEquals(listOf("النت", "250"), n.args)
        assertTrue(plan(at(3), facts).isEmpty())
        assertTrue(plan(at(22), facts, cfg(reminderHour = null)).isEmpty())
        assertTrue(plan(at(10), NotifFacts()).isEmpty())
    }

    @Test fun aBillIsNotRepeatedEveryHour() {
        val facts = NotifFacts(billsTomorrow = listOf(DueItem(3, "النت", 25_000)))
        val first = plan(at(10), facts).single()
        assertTrue(plan(at(11), facts, state = NotifState(sent = setOf(first.key))).isEmpty())
        // the next day it is a new key
        assertEquals(1, plan(at(10).plusDays(1), facts, state = NotifState(sent = setOf(first.key))).size)
    }

    @Test fun gameyaInstallmentTomorrow() {
        val n = plan(at(12), NotifFacts(gameyaDueTomorrow = listOf(DueItem(1, "جمعية", 200_000)))).single()
        assertEquals(GAMEYA_DUE, n.kind)
        assertEquals(listOf("2,000"), n.args)
    }

    @Test fun gameyaTurnThisMonthFiresOncePerMonth() {
        val facts = NotifFacts(gameyaTurns = listOf(TurnItem(1, 202611, 2_000_000)))
        val n = plan(at(12), facts).single()
        assertEquals(GAMEYA_TURN, n.kind)
        assertEquals(listOf("20,000"), n.args)
        assertTrue(plan(at(13), facts, state = NotifState(sent = setOf(n.key))).isEmpty())
    }

    @Test fun wishAfterFortyEightHoursFiresOncePerWish() {
        val facts = NotifFacts(wishesDue = listOf(DueItem(5, "سماعة بلوتوث", 90_000)))
        val n = plan(at(12), facts).single()
        assertEquals(WISH, n.kind)
        assertEquals(listOf("سماعة بلوتوث"), n.args)
        assertTrue(plan(at(13), facts, state = NotifState(sent = setOf(n.key))).isEmpty())
    }

    @Test fun occasionAWeekAheadSaysWhatTheyGave() {
        val n = plan(at(12), NotifFacts(occasions = listOf(OccasionItem(2, "فرح ابن خالتك", 50_000)))).single()
        assertEquals(OCCASION, n.kind)
        assertEquals(listOf("فرح ابن خالتك", "500"), n.args)
        assertFalse(n.alt)
        val none = plan(at(12), NotifFacts(occasions = listOf(OccasionItem(2, "فرح ابن خالتك", null)))).single()
        assertTrue(none.alt)
        assertEquals(listOf("فرح ابن خالتك"), none.args)
    }

    @Test fun plusTrialEndingTomorrowAndSubscriptionTrials() {
        val plus = plan(at(12), NotifFacts(plusTrialEndsTomorrow = true)).single()
        assertEquals(TRIAL_PLUS, plus.kind)
        assertTrue(plan(at(12), NotifFacts(plusTrialEndsTomorrow = false)).isEmpty())
        val subs = NotifFacts(subTrials = listOf(DueItem(8, "نتفليكس")))
        val sub = plan(at(12), subs).single()
        assertEquals(TRIAL_SUB, sub.kind)
        assertEquals(listOf("نتفليكس"), sub.args)
        assertTrue(plan(at(13), subs, state = NotifState(sent = setOf(sub.key))).isEmpty())
    }

    @Test fun aSwitchedOffKindNeverFires() {
        val facts = NotifFacts(billsTomorrow = listOf(DueItem(3, "النت", 25_000)))
        assertTrue(plan(at(10), facts, cfg(enabled = defaults - BILL)).isEmpty())
    }

    // streak at risk

    @Test fun streakAtRiskFiresOnceAtTwentyThreeWhenTheDayIsOpen() {
        val facts = openTicket.copy(streak = 13)
        val n = plan(at(23), facts, cfg(reminderHour = null)).single()
        assertEquals(STREAK, n.kind)
        assertEquals(listOf("13"), n.args)
        assertTrue(plan(at(23, 30), facts, cfg(reminderHour = null), NotifState(sent = setOf(n.key))).isEmpty())
    }

    @Test fun streakDoesNotFireEarlierWhenShortWhenClosedOrBehindALateReminder() {
        assertTrue(plan(at(22), openTicket.copy(streak = 13), cfg(reminderHour = null)).isEmpty())
        assertTrue(plan(at(23), openTicket.copy(streak = 1), cfg(reminderHour = null)).isEmpty())
        assertTrue(plan(at(23), openTicket.copy(streak = 13, ticketOpen = false), cfg(reminderHour = null)).isEmpty())
        // an evening reminder at 22 already nudged an hour before
        assertTrue(plan(at(23), openTicket.copy(streak = 13), cfg(reminderHour = 22)).none { it.kind == STREAK })
    }

    // low balance

    @Test fun lowBalanceFiresOncePerCrossing() {
        val facts = NotifFacts(lowPockets = listOf(LowPocket(4, "فودافون كاش", 15_000)))
        val first = plan(at(12), facts)
        assertEquals(LOW_BALANCE, first.single().kind)
        assertEquals(listOf("فودافون كاش", "150"), first.single().args)
        val told = NotificationPlanner.lowAfter(NotifState(), facts, first)
        assertEquals(setOf(4L), told)
        // still low an hour later: quiet
        assertTrue(plan(at(13), facts, state = NotifState(lowNotified = told)).isEmpty())
        // it recovers (no longer under the line): the pocket is forgotten ...
        val recovered = NotificationPlanner.lowAfter(NotifState(lowNotified = told), NotifFacts(), emptyList())
        assertTrue(recovered.isEmpty())
        // ... and the next drop tells again
        assertEquals(1, plan(at(12), facts, state = NotifState(lowNotified = recovered)).size)
    }

    // cap and priority

    @Test fun neverMoreThanTwoADayAndTheCounterCarriesAcrossRuns() {
        val facts = NotifFacts(
            billsTomorrow = listOf(DueItem(3, "النت", 25_000)),
            gameyaDueTomorrow = listOf(DueItem(1, "جمعية", 200_000)),
            wishesDue = listOf(DueItem(5, "سماعة", 1)),
            plusTrialEndsTomorrow = true,
        )
        val none = cfg(reminderHour = null)
        val first = plan(at(10), facts, none)
        assertEquals(listOf(BILL, GAMEYA_DUE), kinds(first))
        assertTrue(plan(at(11), facts, none, NotifState(sent = first.map { it.key }.toSet(), postedToday = 2)).isEmpty())
        // one already posted: one more, the highest priority left
        assertEquals(listOf(BILL), kinds(plan(at(11), facts, none, NotifState(postedToday = 1))))
    }

    @Test fun priorityOrderIsEveningBillGameyaTrialsLowStreakTurnOccasionWishMorning() {
        assertEquals(
            listOf(EVENING, BILL, GAMEYA_DUE, TRIAL_SUB, TRIAL_PLUS, LOW_BALANCE, STREAK, GAMEYA_TURN, OCCASION, WISH, MORNING),
            NotifKind.entries,
        )
    }

    @Test fun theEveningReminderGoesFirstWhenItIsDueWithOthers() {
        val facts = openTicket.copy(billsTomorrow = listOf(DueItem(3, "النت", 25_000)), wishesDue = listOf(DueItem(5, "سماعة", 1)))
        assertEquals(listOf(EVENING, BILL), kinds(plan(at(21), facts)))
    }

    @Test fun theDaytimeOnesLeaveOneSlotForTheEveningReminderStillToCome() {
        val facts = openTicket.copy(
            billsTomorrow = listOf(DueItem(3, "النت", 25_000)),
            gameyaDueTomorrow = listOf(DueItem(1, "جمعية", 200_000)),
            wishesDue = listOf(DueItem(5, "سماعة", 1)),
        )
        // 10:00: only one of the others may go, so 21:00 still has a slot
        assertEquals(listOf(BILL), kinds(plan(at(10), facts)))
        // the ticket is closed: nothing to reserve for
        assertEquals(listOf(BILL, GAMEYA_DUE), kinds(plan(at(10), facts.copy(ticketOpen = false))))
    }

    // lock

    @Test fun withTheLockOnEveryAmountIsDots() {
        val facts = openTicket.copy(
            billsTomorrow = listOf(DueItem(3, "النت", 25_000)),
            gameyaDueTomorrow = listOf(DueItem(1, "جمعية", 200_000)),
            gameyaTurns = listOf(TurnItem(1, 202611, 2_000_000)),
            occasions = listOf(OccasionItem(2, "فرح", 50_000)),
            lowPockets = listOf(LowPocket(4, "كاش", 15_000)),
        )
        val all = defaults + MORNING
        val masked = plan(at(21), facts, NotifConfig(all, 21, true)) +
            plan(at(10), facts, NotifConfig(all, null, true)) +
            plan(at(12), facts, NotifConfig(all, null, true), NotifState(postedToday = 0, sent = setOf("bill:3@$today", "gdue:1@$today"))) +
            plan(at(9), facts.copy(billsTomorrow = emptyList(), gameyaDueTomorrow = emptyList(), lowPockets = emptyList(), gameyaTurns = emptyList(), occasions = emptyList()), NotifConfig(all, null, true))
        assertTrue(masked.size >= 5)
        assertTrue(masked.flatMap { it.args }.none { a -> a.any(Char::isDigit) })
        assertTrue(masked.any { NOTIF_DOTS in it.args })
    }

    @Test fun dotsReplaceTheEveningAmount() {
        assertEquals(listOf(NOTIF_DOTS), plan(at(21), openTicket, cfg(mask = true)).single().args)
    }

    // housekeeping

    @Test fun pruneDropsOldDailyKeysAndKeepsOnceOnlyOnes() {
        val kept = NotificationPlanner.prune(setOf("evening@${today - 3}", "evening@${today - 1}", "bill:3@$today", "wish:5", "turn:1:202611"), today)
        assertEquals(setOf("evening@${today - 1}", "bill:3@$today", "wish:5", "turn:1:202611"), kept)
    }
}
