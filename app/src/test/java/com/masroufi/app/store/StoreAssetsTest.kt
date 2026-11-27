package com.masroufi.app.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.R
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.GameyaEntity
import com.masroufi.app.data.db.GameyaMemberEntity
import com.masroufi.app.data.db.GameyaPlanEntity
import com.masroufi.app.data.db.GoalEntity
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.repo.DayKind
import com.masroufi.app.data.repo.EnvelopeView
import com.masroufi.app.data.repo.GameyaView
import com.masroufi.app.data.repo.GoalView
import com.masroufi.app.data.repo.GoldView
import com.masroufi.app.data.repo.JarSources
import com.masroufi.app.data.repo.JarView
import com.masroufi.app.data.repo.PeopleFilter
import com.masroufi.app.data.repo.PeopleTotals
import com.masroufi.app.data.repo.PersonRow
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.data.repo.RollDay
import com.masroufi.app.data.repo.RollFilter
import com.masroufi.app.data.repo.RollSummary
import com.masroufi.app.data.repo.SlotState
import com.masroufi.app.data.repo.WheelSlot
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.EventKind
import com.masroufi.app.domain.FreshGoals
import com.masroufi.app.domain.GoalKind
import com.masroufi.app.domain.HatTrick
import com.masroufi.app.domain.HomeCard
import com.masroufi.app.domain.LeagueRow
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.MatchDay
import com.masroufi.app.domain.MatchEvent
import com.masroufi.app.domain.MatchView
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.WeekResult
import com.masroufi.app.feature.add.AddForm
import com.masroufi.app.feature.add.AddSheetActions
import com.masroufi.app.feature.add.AddSheetContent
import com.masroufi.app.feature.add.AddUiState
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.home.CycleUi
import com.masroufi.app.feature.home.Greeting
import com.masroufi.app.feature.home.HomeContent
import com.masroufi.app.feature.home.HomeUiState
import com.masroufi.app.feature.home.StubKind
import com.masroufi.app.feature.home.SubLine
import com.masroufi.app.feature.home.TicketUi
import com.masroufi.app.feature.home.WeekStub
import com.masroufi.app.feature.match.MatchContent
import com.masroufi.app.feature.match.MatchUi
import com.masroufi.app.feature.people.PeopleActions
import com.masroufi.app.feature.people.PeopleContent
import com.masroufi.app.feature.people.PeopleUi
import com.masroufi.app.feature.people.PersonRowUi
import com.masroufi.app.feature.people.RowAction
import com.masroufi.app.feature.plan.CardLine
import com.masroufi.app.feature.plan.EnvelopeCardUi
import com.masroufi.app.feature.plan.EnvelopesSection
import com.masroufi.app.feature.plan.EnvelopesUi
import com.masroufi.app.feature.plan.GoalRowUi
import com.masroufi.app.feature.plan.JarActions
import com.masroufi.app.feature.plan.JarContent
import com.masroufi.app.feature.plan.JarUi
import com.masroufi.app.feature.plan.MoveTip
import com.masroufi.app.feature.plan.PlanContent
import com.masroufi.app.feature.plan.SplitSummary
import com.masroufi.app.feature.proverb.ProverbRewardContent
import com.masroufi.app.feature.proverb.ProverbRewardUi
import com.masroufi.app.feature.roll.RollContent
import com.masroufi.app.feature.roll.RollUi
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.theme.Alexandria
import com.masroufi.app.ui.theme.Lalezar
import com.masroufi.app.ui.theme.MasroufiTheme
import com.masroufi.app.ui.theme.Tz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private object NoAddActions : AddSheetActions {
    override fun setType(type: EntryKind) = Unit
    override fun digit(d: Int) = Unit
    override fun point() = Unit
    override fun backspace() = Unit
    override fun focusFee(on: Boolean) = Unit
    override fun selectCategory(id: Long) = Unit
    override fun selectPocket(id: Long) = Unit
    override fun selectToPocket(id: Long) = Unit
    override fun setNote(text: String) = Unit
    override fun setRoundUp(on: Boolean) = Unit
}

/**
 * Task 65 (Phase 13, decision 2): the Play Store images, rendered by CI from the app's own screens with sample data.
 * Phone screenshots are 1080 x 2340 (a 360 x 780 dp screen at 3x), one set per language, each carrying a visible
 * "sample data" tag drawn here, not by the app. The files land in `app/build/store-assets/` and CI uploads them as the
 * `store-assets` artifact. A test only checks that each file exists and has the right pixel size; how a screen looks is
 * for a person to judge in the artifact.
 */
abstract class StoreShots(private val ar: Boolean) {
    @get:Rule val rule = createComposeRule()

    private val lang get() = if (ar) "ar" else "en"
    protected fun t(arabic: String, english: String) = if (ar) arabic else english

    private val today = LocalDate.of(2026, 11, 12)
    private val sky = 0xFF8FB4FF.toInt()
    private val turquoise = 0xFF35E0D0.toInt()

    // ---- the harness

    private fun write(bitmap: Bitmap, path: String, w: Int, h: Int) {
        val soft = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val out = if (soft.width == w && soft.height == h) soft else {
            println("store-assets: $path rendered at ${soft.width} x ${soft.height}, scaled to $w x $h")
            Bitmap.createScaledBitmap(soft, w, h, true)
        }
        val file = File("build/store-assets/$path")
        file.parentFile!!.mkdirs()
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$path was not written", file.exists() && file.length() > 0)
        val back = BitmapFactory.decodeFile(file.path)
        assertEquals("$path width", w, back.width)
        assertEquals("$path height", h, back.height)
    }

    /**
     * captureToImage first; if the test environment cannot deliver it (it waits on a PixelCopy callback), draw the
     * compose view itself into a bitmap and cut the node's bounds out of it.
     */
    private fun grab(node: SemanticsNodeInteraction): Bitmap {
        try {
            return node.captureToImage().asAndroidBitmap()
        } catch (e: Throwable) {
            println("store-assets: captureToImage failed (${e.message}), drawing the view instead")
        }
        val sn = node.fetchSemanticsNode()
        val view = sn.root as android.view.View
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(full))
        val b = sn.boundsInRoot
        return Bitmap.createBitmap(full, b.left.toInt(), b.top.toInt(), b.width.toInt().coerceAtMost(full.width - b.left.toInt()), b.height.toInt().coerceAtMost(full.height - b.top.toInt()))
    }

    /** One phone screenshot: the screen on the night colour, animations off, the sample-data tag on top. */
    protected fun shoot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MasroufiTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    Box(Modifier.fillMaxSize().background(Tz.night)) {
                        content()
                        Box(
                            Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color(0xCC000000))
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        ) {
                            Text("بيانات تجريبية · sample data", color = Tz.text2, fontSize = 11.sp, fontFamily = Alexandria)
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        // The capture waits for the app to be idle, which a stopped clock never is.
        rule.mainClock.autoAdvance = true
        write(grab(rule.onRoot()), "screenshots/$lang/$name.png", 1080, 2340)
    }

    protected fun featureGraphic() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MasroufiTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    val d = LocalDensity.current
                    val w = with(d) { 1024.toDp() }
                    val h = with(d) { 500.toDp() }
                    Box(Modifier.fillMaxSize().background(Tz.night)) {
                        Box(Modifier.size(w, h).testTag("feature").background(Tz.night), contentAlignment = Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy(with(d) { 20.toDp() }), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(with(d) { 400.toDp() }), contentAlignment = Alignment.Center) {
                                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(with(d) { 720.toDp() }))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("مصروفي", color = Tz.yellow, fontSize = with(d) { 150.toSp() }, fontFamily = Lalezar)
                                    Text("كل يوم ليك تذكرة", color = Tz.text2, fontSize = with(d) { 42.toSp() }, fontFamily = Alexandria)
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        // The capture waits for the app to be idle, which a stopped clock never is.
        rule.mainClock.autoAdvance = true
        write(grab(rule.onNodeWithTag("feature")), "feature-graphic-1024x500.png", 1024, 500)
    }

    /** The adaptive icon's two layers on one 512 square (the launcher's mask is not applied). */
    protected fun icon() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (id in listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            val d = ContextCompat.getDrawable(ctx, id)!!
            d.setBounds(0, 0, 512, 512)
            d.draw(canvas)
        }
        write(bitmap, "icon-512.png", 512, 512)
    }

    // ---- sample data, in the language of the set

    private val cash get() = PocketEntity(id = 1, name = t("الكاش", "Cash"), kind = PocketKind.CASH, color = 0)
    private val wallet get() = PocketEntity(id = 2, name = t("فودافون كاش", "Mobile wallet"), kind = PocketKind.MOBILE_WALLET, color = 0xFF3DD6C8.toInt())

    private val week = listOf(
        WeekStub(StubKind.Under, 4_000, false), WeekStub(StubKind.Over, 3_500, false), WeekStub(StubKind.Under, 7_200, false),
        WeekStub(StubKind.Empty, 0, false), WeekStub(StubKind.Today, 0, true), WeekStub(StubKind.Empty, 0, false), WeekStub(StubKind.Empty, 0, false),
    )

    private fun home() = HomeUiState.Ready(
        today = today, greeting = Greeting.Afternoon, streak = 12, ticket = TicketUi(24_000, 8_400, 3, SubLine.Spent(24_000, 8_400, 3)),
        tomorrow = null,
        repeats = listOf(
            RepeatChip(1, t("مواصلات", "Transport"), 1_500, 1), RepeatChip(2, t("قهوة", "Coffee"), 3_500, 1), RepeatChip(3, t("سندوتشات", "Sandwiches"), 2_500, 1),
        ),
        week = week, closedUnder = 4, jarTotal = 184_000, cycle = CycleUi(13, 17f / 30, 312_000, 62_000, 25), collected = 17,
        cardOrder = HomeCard.values().toList(),
    )

    @Composable
    private fun HomeScreen() = HomeContent(home(), "", {}, {}, {}, false, {}, {}, {})

    // ---- the eight phone screenshots

    @Test fun shotHome() = shoot("01-home") { HomeScreen() }

    @Test fun shotAddSheet() = shoot("02-add") {
        val cats = listOf(
            CategoryEntity(1, t("أكل", "Food"), "grid", 0), CategoryEntity(2, t("مواصلات", "Transport"), "grid", 0), CategoryEntity(3, t("فواتير", "Bills"), "grid", 0),
        )
        val form = AddForm(
            type = EntryKind.SPENT, amount = AmountInput("25"), pocketId = cash.id, toPocketId = wallet.id, pockets = listOf(cash, wallet),
            categories = cats, categoryId = 1, note = t("غدا", "Lunch"),
        )
        val state = AddUiState(form, DayTicketEntity(date = today, amount = 15_600, base = 15_600), plus = false, roundUpStep = 0)
        Box(Modifier.fillMaxSize()) {
            HomeScreen()
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)))
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Tz.surface),
            ) { AddSheetContent(state, NoAddActions, onClose = {}, onSave = {}) }
        }
    }

    @Test fun shotCloseDayAndProverb() = shoot("03-close-day-proverb") {
        ProverbRewardContent(
            ProverbRewardUi.Ready(
                ticketNumber = 47, proverb = ProverbCatalog.all.first { it.number == 18 }, allCollected = false, leftover = 7_200,
                destination = LeftoverDestination.JAR, streak = 13, weekDays = 5,
            ),
            {}, {},
        )
    }

    @Test fun shotRoll() = shoot("04-roll") {
        val noon = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L
        val food = t("أكل", "Food")
        val cats = mapOf(1L to CategoryEntity(1, food, "grid", 0))
        fun entry(id: Long, kind: EntryKind = EntryKind.SPENT, amount: Long = 3_500, note: String? = null, recurringId: Long? = null) = EntryRow(
            id, kind, amount, 0, 0, noon + id * 60_000, note, 1, food, 1, cash.name, "EGP", true, null, null, recurringId,
        )
        fun ticket(date: LocalDate, spent: Long, status: TicketStatus, dest: LeftoverDestination? = null) =
            DayTicketEntity(date = date, amount = 24_000, base = 24_000, spent = spent, entryCount = 3, status = status, destination = dest)
        val days = listOf(
            RollDay(today, ticket(today, 8_400, TicketStatus.OPEN), listOf(entry(1, note = t("فول وطعمية", "Breakfast")), entry(2, amount = 4_900, note = t("قهوة", "Coffee"))), DayKind.TICKET),
            RollDay(today.minusDays(1), ticket(today.minusDays(1), 23_500, TicketStatus.CLOSED_BY_HAND, LeftoverDestination.JAR), listOf(entry(3)), DayKind.TICKET),
            RollDay(today.minusDays(2), ticket(today.minusDays(2), 27_500, TicketStatus.CLOSED_AUTO), listOf(entry(4)), DayKind.TICKET),
            RollDay(today.minusDays(3), null, listOf(entry(5, EntryKind.RECEIVED, 700_000, t("المرتب", "Salary"))), DayKind.NON_TICKET),
            RollDay(today.minusDays(4), ticket(today.minusDays(4), 12_000, TicketStatus.CLOSED_BY_HAND, LeftoverDestination.JAR), listOf(entry(6)), DayKind.TICKET),
        )
        val ui = RollUi(
            today = today, month = YearMonth.from(today), filter = RollFilter.ALL, searching = false, query = "",
            months = listOf(YearMonth.from(today)), loading = false, summary = RollSummary(1_234_500, 700_000, 3, 4),
            days = days, results = null, categories = cats, undoVisible = false,
        )
        RollContent(ui, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun shotPlanEnvelopes() = shoot("05-plan") {
        fun env(id: Long, name: String, left: Long, monthly: Long, kind: EnvelopeKind = EnvelopeKind.SPENDING) = EnvelopeView(
            id = id, name = name, color = if (id % 2 == 0L) sky else turquoise, kind = kind, monthly = monthly, spent = monthly - left, left = left,
            bar = (monthly - left).toFloat() / monthly, sealed = false, paidLabelRes = null, line = null, betterThanLast = false, categoryIds = emptySet(),
        )
        val cards = listOf(
            EnvelopeCardUi(env(1, t("الأكل والشرب", "Food and drink"), 180_000, 300_000), CardLine.RunsOut(21)),
            EnvelopeCardUi(env(2, t("المواصلات", "Transport"), 90_000, 150_000), CardLine.OnPace),
            EnvelopeCardUi(env(3, t("الخروجات", "Going out"), 40_000, 100_000), CardLine.BetterThanLast),
            EnvelopeCardUi(env(4, t("الفواتير", "Bills"), 0, 120_000, EnvelopeKind.FIXED), CardLine.Sealed(120_000, 5)),
            EnvelopeCardUi(env(5, t("حاجات البيت", "Home"), 70_000, 80_000), null),
        )
        val ui = EnvelopesUi(
            loading = false, cards = cards, split = SplitSummary(1_200_000, 150_000, today),
            tip = MoveTip(1, 2, t("الأكل والشرب", "Food and drink"), t("المواصلات", "Transport"), 30_000),
        )
        PlanContent(0, {}, {}, {}, {}, {}, {}, {}) { EnvelopesSection(ui, {}, {}, {}, { _, _ -> }, {}, {}) }
    }

    @Test fun shotPeople() = shoot("06-people") {
        val sara = PersonEntity(1, t("سارة محمود", "Sara Mahmoud"), "0100", PersonKind.PERSON)
        val shop = PersonEntity(2, t("بقالة عم حسين", "Hussein's grocery"), null, PersonKind.SHOP)
        val omar = PersonEntity(3, t("عمر", "Omar"), null, PersonKind.PERSON)
        fun row(p: PersonEntity, owed: Long, iOwe: Long, tab: Long, debts: Int, items: Int, since: LocalDate?, due: LocalDate?) =
            PersonRow(p, owed, iOwe, tab, debts, items, false, since, due)
        val rows = listOf(
            PersonRowUi(row(sara, 250_000, 0, 0, 1, 0, today.minusDays(20), today.plusDays(8)), RowAction.REMIND, 20, false),
            PersonRowUi(row(shop, 0, 0, 18_500, 0, 4, today.minusDays(3), null), RowAction.SETTLE_TAB, 3, false),
            PersonRowUi(row(omar, 0, 120_000, 0, 1, 0, today.minusDays(70), today.minusDays(5)), RowAction.REPAY, 70, true),
        )
        fun member(id: Long, name: String, turn: Int) = GameyaMemberEntity(id, 1, name, turn)
        val members = listOf(
            member(1, t("منى", "Mona"), 1), member(2, t("أحمد", "Ahmed"), 2), member(3, t("هالة", "Hala"), 3),
            member(4, t("كريم", "Karim"), 5), member(5, t("نورا", "Noura"), 6),
        )
        val wheel = listOf(
            WheelSlot(1, members[0], SlotState.PAID_OUT), WheelSlot(2, members[1], SlotState.PAID_OUT), WheelSlot(3, members[2], SlotState.THIS_MONTH),
            WheelSlot(4, null, SlotState.WAITING), WheelSlot(5, members[3], SlotState.WAITING), WheelSlot(6, members[4], SlotState.WAITING),
        )
        val gameya = GameyaView(
            gameya = GameyaEntity(1, t("جمعية الشغل", "Work gameya"), 200_000, 202609, 4), people = 6, payout = 1_200_000, myTurnMonth = YearMonth.of(2026, 12),
            monthNumber = 3, finished = false, thisMonth = YearMonth.of(2026, 11), monthsUntil = 1, myPaidSoFar = 600_000, myPaidThisMonth = false, wheel = wheel,
            paidMembers = listOf(members[0], members[1]), unpaidMembers = listOf(members[2], members[3], members[4]), paidCount = 2,
            plan = listOf(GameyaPlanEntity(1, 1, t("دفعة مقدّم الشقة", "Flat deposit"), 700_000), GameyaPlanEntity(2, 1, t("فرش", "Furniture"), 300_000)),
            unplanned = 200_000,
        )
        val ui = PeopleUi(
            loading = false, filter = PeopleFilter.ALL, totals = PeopleTotals(250_000, 1, 138_500, 18_500), rows = rows, persons = listOf(sara, shop, omar),
            pockets = listOf(cash, wallet), plus = false, gameya = gameya,
        )
        PeopleContent(ui, object : PeopleActions {}, { _, _ -> }, {}, {}, {}, {})
    }

    @Test fun shotMatch() = shoot("07-match") {
        val stubs = (1..6).map { MatchDay(today.minusDays(it.toLong()), if (it == 3) GoalKind.AGAINST else GoalKind.FOR, if (it == 2) 0 else 12_345L * it) }
        fun league(n: Int, current: Boolean, result: WeekResult?) = LeagueRow(
            today.minusDays(7L * (4 - n)), 44 + n, result, 3,
            listOf(GoalKind.FOR, GoalKind.FOR, GoalKind.AGAINST, GoalKind.NONE, GoalKind.FOR, GoalKind.NONE, GoalKind.NONE), 54_300L * n, current,
        )
        val view = MatchView(
            today = today, weekNumber = 47, daysLeft = 3, goalsFor = 4, goalsAgainst = 1, lastWeekGoalsFor = 2, days = stubs, bestDay = today.minusDays(2),
            todayLeft = 7_500, hatTrick = HatTrick.ONE_AWAY,
            league = listOf(league(4, true, null), league(3, false, WeekResult.WIN), league(2, false, WeekResult.DRAW), league(1, false, WeekResult.LOSS)),
            lockedWeeks = 5, wonOf = 3, ofLast = 6, longestWinRun = 2, firstWeek = false,
        )
        val events = listOf(
            MatchEvent(today.minusDays(4), EventKind.GOAL, 18_000, false), MatchEvent(today.minusDays(3), EventKind.AGAINST, 4_000, false),
            MatchEvent(today.minusDays(2), EventKind.BEST, 45_000, true), MatchEvent(today.minusDays(1), EventKind.LAST_MINUTE, 500, false),
            MatchEvent(today, EventKind.OPEN, 7_500, false),
        )
        MatchContent(MatchUi(loading = false, view = view, events = events, fresh = FreshGoals(1, 1, 0, setOf(today.minusDays(2)))), {}, {})
    }

    @Test fun shotJar() = shoot("08-jar") {
        val goals = listOf(
            GoalRowUi(GoalView(GoalEntity(1, t("موبايل جديد", "New phone"), 1_500_000), 400_000), today.plusMonths(5)),
            GoalRowUi(GoalView(GoalEntity(2, t("سفرية", "Trip"), 3_000_000), 100_000), null),
        )
        val ui = JarUi(
            loading = false,
            jar = JarView(
                total = 1_250_000, monthGrowth = 80_000, sources = JarSources(6, 700_000, 150_000, 400_000), goals = goals.map { it.view }, free = 850_000,
                gold = GoldView(3_800_000, today, 133, null), firstAt = 1_700_000_000_000,
            ),
            isPlus = false, roundUpStep = 0, season = null, goals = goals,
        )
        PlanContent(2, {}, {}, {}, {}, {}, {}, {}) { JarContent(ui, {}, {}, {}, JarActions.None) }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreAssetsArTest : StoreShots(true) {
    @Test fun storeIcon() = icon()
    @Test fun storeFeatureGraphic() = featureGraphic()
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreAssetsEnTest : StoreShots(false)
