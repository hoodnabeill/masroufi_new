package com.masroufi.app.feature.rewards

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.repo.AlbumView
import com.masroufi.app.data.repo.BasketData
import com.masroufi.app.data.repo.CollectedProverb
import com.masroufi.app.data.repo.HabitFacts
import com.masroufi.app.data.repo.LateNightFact
import com.masroufi.app.data.repo.LossRow
import com.masroufi.app.data.repo.Milestone
import com.masroufi.app.data.repo.StoryPayload
import com.masroufi.app.data.repo.SuggestedTicket
import com.masroufi.app.data.repo.WeekdayFact
import com.masroufi.app.domain.EventKind
import com.masroufi.app.domain.FreshGoals
import com.masroufi.app.domain.GoalKind
import com.masroufi.app.domain.HatTrick
import com.masroufi.app.domain.LeagueRow
import com.masroufi.app.domain.LockHint
import com.masroufi.app.domain.MatchDay
import com.masroufi.app.domain.MatchEvent
import com.masroufi.app.domain.MatchView
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.WeekResult
import com.masroufi.app.domain.WorkKind
import com.masroufi.app.domain.MainGoal
import com.masroufi.app.entitlements.BillingProduct
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.TrialState
import com.masroufi.app.feature.album.AlbumContent
import com.masroufi.app.feature.album.AlbumUi
import com.masroufi.app.feature.album.StampSlot
import com.masroufi.app.feature.basket.BasketActions
import com.masroufi.app.feature.basket.BasketContent
import com.masroufi.app.feature.basket.BasketItemUi
import com.masroufi.app.feature.basket.BasketUi
import com.masroufi.app.feature.basket.HeroUi
import com.masroufi.app.feature.basket.ItemLine
import com.masroufi.app.feature.basket.Trend
import com.masroufi.app.feature.match.MatchContent
import com.masroufi.app.feature.match.MatchUi
import com.masroufi.app.feature.paywall.Headline
import com.masroufi.app.feature.paywall.PaywallContent
import com.masroufi.app.feature.paywall.PaywallRow
import com.masroufi.app.feature.paywall.PaywallUi
import com.masroufi.app.feature.paywall.PlanUi
import com.masroufi.app.feature.paywall.PurchaseState
import com.masroufi.app.feature.profile.GiftUi
import com.masroufi.app.feature.profile.ProfileActions
import com.masroufi.app.feature.profile.ProfileContent
import com.masroufi.app.feature.profile.ProfileSheet
import com.masroufi.app.feature.profile.ProfileUi
import com.masroufi.app.feature.story.StoryActions
import com.masroufi.app.feature.story.StoryContent
import com.masroufi.app.feature.story.StoryListContent
import com.masroufi.app.feature.story.StoryListRow
import com.masroufi.app.feature.story.StoryListUi
import com.masroufi.app.feature.story.StorySlide
import com.masroufi.app.feature.story.StoryUi
import com.masroufi.app.feature.story.fullPayload
import com.masroufi.app.feature.story.story
import com.masroufi.app.feature.story.storySlides
import com.masroufi.app.feature.story.thinPayload
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Album, match, basket, profile, story list, story and paywall at 200% font, right-to-left, animations off (Task 62).
 * The one thing left out is the profile's birthday picker (Material's own dialog).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RewardsAuditTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)

    // ---- album

    private val proverbs = ProverbCatalog.all.take(7).map { CollectedProverb(it, today.minusDays(it.number.toLong())) }
    private val stubs = (1..6).map { MatchDay(today.minusDays(it.toLong()), if (it == 3) GoalKind.AGAINST else GoalKind.FOR, if (it == 2) 0 else 12_345L * it) }
    private val albumView = AlbumView(
        level = 4, levelName = "مدبّر", handClosed = 31, nextLevelAt = 50, ticketsToNext = 19, streak = 6, longestStreak = 12, daysUnder = 24,
        proverbs = proverbs, lockedProverbs = 43, stamps = emptyList(), stubs = stubs,
    )
    private val album = AlbumUi(
        loading = false, album = albumView, cards = proverbs, locked = listOf(LockHint.TOMORROW, LockHint.FULL_WEEK, LockHint.RARE),
        stamps = listOf(
            StampSlot(StampKind.FIRST_TICKET, today.minusDays(40), 1), StampSlot(StampKind.FULL_WEEK, today.minusDays(5), 3),
            StampSlot(StampKind.HAT_TRICK, null, 0), StampSlot(StampKind.TIGHT_FIST, null, 0), StampSlot(StampKind.FIRST_GRAM, null, 0),
            StampSlot(StampKind.SETTLED, null, 0),
        ),
        progress = 0.6f,
    )

    private fun album(ui: AlbumUi, celebration: Milestone? = null) {
        rule.audit { AlbumContent(ui, celebration, {}, {}, {}, {}, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun albumFull() = album(album, Milestone.NewStamp(StampKind.FULL_WEEK))
    @Test fun albumNewLevelBanner() = album(album, Milestone.NewLevel(5, "حريف"))
    @Test fun albumEmpty() = album(AlbumUi(loading = false, album = albumView.copy(proverbs = emptyList(), stubs = emptyList(), streak = 0), cards = emptyList()))
    @Test fun albumLoading() = album(AlbumUi())

    // ---- match

    private fun league(n: Int, current: Boolean, result: WeekResult?) =
        LeagueRow(today.minusDays(7L * (4 - n)), 44 + n, result, 3, listOf(GoalKind.FOR, GoalKind.FOR, GoalKind.AGAINST, GoalKind.NONE, GoalKind.FOR, GoalKind.NONE, GoalKind.NONE), 54_300L * n, current)

    private val matchView = MatchView(
        today = today, weekNumber = 47, daysLeft = 3, goalsFor = 4, goalsAgainst = 1, lastWeekGoalsFor = 2, days = stubs, bestDay = today.minusDays(2), todayLeft = 7_500,
        hatTrick = HatTrick.ONE_AWAY, league = listOf(league(4, true, null), league(3, false, WeekResult.WIN), league(2, false, WeekResult.DRAW), league(1, false, WeekResult.LOSS)),
        lockedWeeks = 5, wonOf = 3, ofLast = 6, longestWinRun = 2, firstWeek = false,
    )
    private val matchEvents = listOf(
        MatchEvent(today.minusDays(4), EventKind.GOAL, 18_000, false), MatchEvent(today.minusDays(3), EventKind.AGAINST, 4_000, false),
        MatchEvent(today.minusDays(2), EventKind.BEST, 45_000, true), MatchEvent(today.minusDays(1), EventKind.LAST_MINUTE, 500, false),
        MatchEvent(today, EventKind.OPEN, 7_500, false),
    )

    private fun match(ui: MatchUi) {
        rule.audit { MatchContent(ui, {}, {}) }.assertNone()
    }

    @Test fun matchWeek() = match(MatchUi(loading = false, view = matchView, events = matchEvents, fresh = FreshGoals(1, 1, 0, setOf(today.minusDays(2)))))
    @Test fun matchHatTrickDone() = match(MatchUi(loading = false, view = matchView.copy(hatTrick = HatTrick.DONE, lockedWeeks = 0), events = matchEvents.take(2)))
    @Test fun matchFirstWeek() = match(MatchUi(loading = false, view = matchView.copy(firstWeek = true, hatTrick = HatTrick.NONE, league = emptyList())))
    @Test fun matchLoading() = match(MatchUi())

    // ---- basket

    private val none = object : BasketActions {}
    private val basket = BasketUi(
        loading = false,
        items = listOf(
            BasketItemUi(1, "طماطم", 2_200, 47, ItemLine.LastPlace("سوق العبور", 1_500)),
            BasketItemUi(2, "زيت", 8_500, -9, ItemLine.Cheapest("كارفور", 8_000)),
            BasketItemUi(3, "رز", 5_000, 0, ItemLine.Times(6)),
            BasketItemUi(4, "لبن", null, null, null),
        ),
        hero = HeroUi(9, 148_000, 161_200, 8, Trend.UP), suggestion = SuggestedTicket(24_000, 26_000),
        habits = HabitFacts(WeekdayFact(java.time.DayOfWeek.THURSDAY, 30_000, 20_000), LateNightFact(5, 40_000), -18),
    )

    private fun basket(ui: BasketUi) {
        rule.audit { BasketContent(ui, none, {}) }.assertNone()
    }

    @Test fun basketFull() = basket(basket)
    @Test fun basketRaised() = basket(basket.copy(suggestion = null, raisedTo = 26_000, hero = basket.hero!!.copy(trend = Trend.DOWN)))
    @Test fun basketEmpty() = basket(BasketUi(loading = false))
    @Test fun basketLocked() = basket(BasketUi(loading = false, locked = true))
    @Test fun basketLoading() = basket(BasketUi())
    @Test fun basketAddSheet() = basket(basket.copy(addSheet = true))

    // ---- profile

    private val profileNone = object : ProfileActions {}
    private val profile = ProfileUi(
        loading = false, name = "يوسف", level = 4, levelName = "مدبّر", ticketsClosed = 31, streak = 6, proverbs = 7, wonOf = 3, ofLast = 6,
        birthday = LocalDate.of(1990, 12, 20), gift = GiftUi(38, 27, 100_000), workKind = WorkKind.FREELANCE, householdSize = 4, inSchool = 2, mainGoal = MainGoal.SAVE,
    )

    private fun profile(ui: ProfileUi) {
        rule.audit { ProfileContent(ui, profileNone, {}, {}) }.assertNone()
    }

    @Test fun profileFull() = profile(profile)
    @Test fun profileBlank() = profile(ProfileUi(loading = false))
    @Test fun profileLoading() = profile(ProfileUi())
    @Test fun profileTargetSheet() = profile(profile.copy(sheet = ProfileSheet.Target))
    @Test fun profileWipeSheet() = profile(profile.copy(sheet = ProfileSheet.Wipe))

    // ---- stories

    private fun storyList(ui: StoryListUi) {
        rule.audit { StoryListContent(ui, {}, {}) }.assertNone()
    }

    @Test fun storyListRows() = storyList(
        StoryListUi(
            loading = false,
            rows = listOf(
                StoryListRow(2, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), 26, 31, isNew = true),
                StoryListRow(1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), 20, 30, isNew = false),
            ),
        ),
    )
    @Test fun storyListEmpty() = storyList(StoryListUi(loading = false))
    @Test fun storyListLoading() = storyList(StoryListUi())

    private val storyNone = object : StoryActions {}

    private fun storyScreen(payload: StoryPayload, slide: Int, hide: Boolean = false) {
        val ui = StoryUi(loading = false, story = story(payload), slides = storySlides(payload), index = slide, hideAmounts = hide)
        rule.audit { StoryContent(ui, mutableStateOf(0.4f), storyNone, {}, { _, _ -> }) }.assertNone()
    }

    @Test fun storyOpening() = storyScreen(fullPayload(), 0)
    @Test fun storyGrid() = storyScreen(fullPayload(isBest = true), 1)
    @Test fun storyMoney() = storyScreen(fullPayload(), 2)
    @Test fun storyJar() = storyScreen(fullPayload(), 3, hide = true)
    @Test fun storyClosing() = storyScreen(fullPayload(), 4)
    @Test fun storyThinMonth() = storyScreen(thinPayload(), 1)

    @Test fun storyMissing() {
        rule.audit { StoryContent(StoryUi(loading = false), mutableStateOf(0f), storyNone, {}, { _, _ -> }) }.assertNone()
    }

    // ---- paywall

    private val plans = listOf(
        PlanUi(PlusPlan.YEARLY, "399 ج.م / سنة", "EGP", savePercent = 32, perMonth = 33), PlanUi(PlusPlan.MONTHLY, "49 ج.م / شهر", "EGP"),
    )
    private val paywall = PaywallUi(
        loading = false, trialState = TrialState.ACTIVE, daysLeft = 1, endDay = LocalDate.of(2026, 11, 13), headline = Headline.Tomorrow,
        rows = listOf(
            PaywallRow.Loss(LossRow.RoundUps(8_600, 1), null), PaywallRow.Loss(LossRow.Gold(37, 490_000, 2), null),
            PaywallRow.Loss(LossRow.Seasons(2, "رمضان", 300_000, 40, 1), null), PaywallRow.Loss(LossRow.Duty(14, "فرح ابن خالي", 12, 1), null),
        ),
        plans = plans,
    )

    private fun paywall(ui: PaywallUi) {
        rule.audit { PaywallContent(ui, {}, {}, {}) }.assertNone()
    }

    @Test fun paywallTrialEnding() = paywall(paywall)
    @Test fun paywallBuying() = paywall(paywall.copy(purchase = PurchaseState.Buying))
    @Test fun paywallFailed() = paywall(paywall.copy(purchase = PurchaseState.Failed, selected = PlusPlan.MONTHLY))
    @Test fun paywallDone() = paywall(paywall.copy(purchase = PurchaseState.Done, trialState = TrialState.ENDED, headline = Headline.Ended))
    @Test fun paywallNoPrices() = paywall(paywall.copy(plans = emptyList(), trialState = TrialState.NOT_YET, headline = Headline.Plain))
}
