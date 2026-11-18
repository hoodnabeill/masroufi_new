package com.masroufi.app.feature.home

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.db.WishAnswer
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.add.FeedbackBus
import com.masroufi.app.feature.add.Pending
import com.masroufi.app.feature.add.PendingPunches
import com.masroufi.app.feature.story.entity
import com.masroufi.app.feature.story.fullPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Payday Day(25), one cash pocket with 3,900 EGP: on Thursday 2026-11-12 (13 days left) the ticket is 300.
 *
 * Main is a StandardTestDispatcher driven by hand. runTest is not used on purpose: it auto-advances virtual time
 * whenever its scheduler idles, which would fire the midnight delay while a test waits for Room's real threads.
 */
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val nov12 = LocalDate.of(2026, 11, 12)
    private val nov13 = nov12.plusDays(1)
    private val nov25 = LocalDate.of(2026, 11, 25)
    private val clock = TestClock(at(nov12, 9), zone)
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = ViewModelStore()
    private val pending = PendingPunches()
    private val process = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture
    private lateinit var vm: HomeViewModel
    private var cash = 0L

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TazkaraDatabase::class.java)
            .allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements())
        process.registry.currentState = Lifecycle.State.CREATED
        cash = runBlocking { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 390_000, color = 0)) }
    }

    @After fun close() {
        store.clear()
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

    private fun at(date: LocalDate, hour: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, 0), zone).toInstant()

    private fun newVm(): HomeViewModel {
        val factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    f.home, f.tickets, f.entries, db.categoryDao(), db.pocketDao(), pending, process.lifecycle, clock, FeedbackBus(), f.settings, f.onboarding,
                    PendingDeletes(f.entries, io), f.wishes, f.eom, f.story,
                )
            }
        }
        vm = ViewModelProvider.create(store, factory)[HomeViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} } // a screen collecting it
        return vm
    }

    /** Runs Main's due tasks until [cond] holds; Room and DataStore work on real threads meanwhile. */
    private fun settle(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (true) {
            main.scheduler.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            check(System.nanoTime() < end) { "timed out waiting for $what" }
            Thread.sleep(5)
        }
    }

    private val ready get() = vm.state.value as? HomeUiState.Ready
    private fun row(date: LocalDate) = runBlocking { db.dayTicketDao().forDate(date) }
    private fun entries(): List<EntryEntity> = runBlocking { db.entryDao().forDay(0, Long.MAX_VALUE) }
    private fun category(name: String) = runBlocking { db.categoryDao().insert(CategoryEntity(name = name, icon = "coffee", color = 0)) }
    private fun spend(amount: Long) = runBlocking {
        f.entries.log(NewEntry(EntryKind.SPENT, amount, cash, happenedAt = clock.millis()))
    }

    @Test fun anUnseenStoryOpensOnceAndTheTicketMomentWaitsForIt() {
        val id = runBlocking { db.storyDao().insert(entity(fullPayload())) }
        newVm()
        vm.viewModelScope.launch { vm.pendingStory.collect {} } // the screen collecting it
        settle("the story is pending") { vm.pendingStory.value == id && ready != null }
        assertFalse("the new-ticket moment waits for the story", ready!!.newTicket)
        vm.storyOpened(id)
        settle("not offered a second time") { vm.pendingStory.value == null }
        assertFalse("and keeps waiting while the story is open", ready!!.newTicket)
        // The story closes: marked seen, so the moment plays and nothing opens again.
        val mark = io.launch { f.story.markShown(id) }
        settle("seen") { mark.isCompleted && ready?.newTicket == true }
        assertNull(vm.pendingStory.value)
    }

    @Test fun withoutAStoryNothingOpensAndTheTicketMomentPlays() {
        newVm()
        vm.viewModelScope.launch { vm.pendingStory.collect {} }
        settle("ready") { ready != null }
        assertNull(vm.pendingStory.value)
        assertTrue(ready!!.newTicket)
    }

    @Test fun printsOnStart() {
        newVm()
        settle("today's ticket") { ready?.today == nov12 }
        clock.instant = at(nov13, 9)
        main.scheduler.runCurrent()
        assertNull("nothing re-prints before the app starts again", row(nov13))
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        settle("the new day's ticket after ON_START") { row(nov13) != null && ready?.today == nov13 }
    }

    @Test fun midnightReprintsTicket() {
        clock.instant = ZonedDateTime.of(nov12, LocalTime.of(23, 59), zone).toInstant()
        newVm()
        settle("today's ticket") { ready?.today == nov12 }
        clock.instant = at(nov13, 0).plusSeconds(5)
        main.scheduler.advanceTimeBy(30_000)
        main.scheduler.runCurrent()
        assertNull("the timer waits for midnight + 1 s (61 s after 23:59)", row(nov13))
        main.scheduler.advanceTimeBy(32_000)
        settle("the ticket printed at midnight") { row(nov13) != null && ready?.today == nov13 }
    }

    @Test fun repeatChipLogsOnce() {
        val coffee = category("قهوة")
        newVm()
        settle("ready") { ready != null }
        val chip = RepeatChip(coffee, "قهوة", 2_500, cash)
        vm.punchRepeat(chip)
        vm.punchRepeat(chip) // a double tap inside 400 ms
        settle("one stored hole") { ready?.ticket?.holes == 1 && pending.pending.value.isEmpty() }
        val e = entries().single()
        assertEquals(listOf(EntryKind.SPENT, 2_500L, coffee, cash), listOf(e.kind, e.amount, e.categoryId, e.pocketId))
        clock.instant = clock.instant.plusMillis(500)
        vm.punchRepeat(chip)
        settle("a second hole after 400 ms") { ready?.ticket?.holes == 2 && pending.pending.value.isEmpty() }
        assertEquals(2, entries().size)
    }

    @Test fun quickLogParses() {
        val coffee = category("قهوة")
        newVm()
        settle("ready") { ready != null }
        val prefills = mutableListOf<AddPrefill>()
        vm.viewModelScope.launch { vm.prefill.collect { prefills += it } }

        vm.quickText = "25 قهوة"
        vm.submitQuickLog()
        settle("logged straight away") { entries().size == 1 && vm.quickText.isEmpty() }
        val e = entries().single()
        assertEquals(listOf(2_500L, coffee, cash), listOf(e.amount, e.categoryId, e.pocketId))
        assertEquals("no sheet for a full parse", emptyList<AddPrefill>(), prefills)

        clock.instant = clock.instant.plusMillis(500)
        vm.quickText = "40 حاجة غريبة"
        vm.submitQuickLog()
        settle("handed to the sheet") { prefills.isNotEmpty() }
        assertEquals(AddPrefill(4_000, null, "40 حاجة غريبة"), prefills.single())
        assertEquals("no category: nothing logged", 1, entries().size)
        assertEquals("", vm.quickText)
    }

    @Test fun overTicketSubLine() {
        newVm()
        settle("ready") { ready?.ticket != null }
        assertEquals(30_000L, ready?.ticket?.amount)
        spend(33_500)
        settle("over the ticket") { ready?.ticket?.subLine is SubLine.Over }
        assertEquals(SubLine.Over(3_500, spread = true), ready?.ticket?.subLine)
        assertEquals(-3_500L, ready?.ticket?.left)
    }

    @Test fun lastDayOmitsSpreadSentence() {
        clock.instant = at(LocalDate.of(2026, 11, 24), 9)
        newVm()
        settle("ready") { ready?.ticket != null }
        assertEquals("one day left: the whole balance", 390_000L, ready?.ticket?.amount)
        spend(395_000)
        settle("over the ticket") { ready?.ticket?.subLine is SubLine.Over }
        assertEquals(SubLine.Over(5_000, spread = false), ready?.ticket?.subLine)
    }

    @Test fun pendingAddsToTicket() {
        newVm()
        settle("ready") { ready?.ticket != null }
        val id = pending.add(Pending(0, 5_000))
        settle("the pending punch on the ticket") { ready?.ticket?.holes == 1 }
        assertEquals(5_000L, ready?.ticket?.spent)
        assertEquals(SubLine.Spent(30_000, 5_000, 1), ready?.ticket?.subLine)
        assertEquals("spendable already without it", 385_000L, ready?.cycle?.spendable)
        pending.remove(id)
        settle("back to none") { ready?.ticket?.holes == 0 }
        assertEquals(SubLine.None, ready?.ticket?.subLine)
    }

    @Test fun tomorrowPreviewAfterClose() {
        newVm()
        settle("ready") { ready?.ticket != null }
        // Through io while Main keeps pumping: a blocking call here deadlocks against the print's settings edit.
        var result: com.masroufi.app.data.repo.CloseResult? = null
        val call = io.launch { result = f.tickets.closeDay(nov12, LeftoverDestination.TOMORROW) }
        settle("close call") { call.isCompleted }
        val closed = checkNotNull(result)
        settle("closed") { ready?.ticket == null }
        // 3,900 with the 300 leftover carried: (3,900 - 300) / 12 days + 300.
        assertEquals(TomorrowUi(60_000, closed.proverbNumber), ready?.tomorrow)
    }

    /** Two wishes parked three days ago: both are past their 48 hours when Home starts. */
    private fun parkedBefore(): Pair<Long, Long> {
        val start = clock.instant
        clock.instant = at(nov12.minusDays(3), 9)
        val a = runBlocking { f.wishes.park("سماعة", 180_000) }
        clock.instant = at(nov12.minusDays(3), 10)
        val b = runBlocking { f.wishes.park("ساعة", 95_000) }
        clock.instant = start
        return a to b
    }

    @Test fun dueWishCardIsTheOldestDueWish() {
        val (a, _) = parkedBefore()
        newVm()
        settle("the 48-hour card") { ready?.dueWish != null }
        assertEquals(a, ready?.dueWish?.id)
    }

    @Test fun aWishStillInItsFortyEightHoursAsksNothing() {
        runBlocking { f.wishes.park("سماعة", 180_000) }
        newVm()
        settle("ready") { ready?.ticket != null }
        assertNull(ready?.dueWish)
    }

    @Test fun dueWishWaitsForTheFirstWeekCardAndEndOfMonth() {
        parkedBefore()
        newVm()
        settle("the wish card") { ready?.dueWish != null }
        spend(1_000) // the first entry: the first-week "first entry" card shows and goes first
        settle("a first-week card") { ready?.firstWeekCard != null }
        assertNull("never next to a first-week card", ready?.dueWish)

        vm.dismissCard(ready!!.firstWeekCard!!.id)
        settle("the wish card once the first-week card is gone") { ready?.firstWeekCard == null && ready?.dueWish != null }

        val row = row(nov12)!!
        runBlocking { db.dayTicketDao().update(row.copy(endOfMonth = true)) }
        settle("end-of-month mode hides it") { ready?.dueWish == null && ready?.ticket != null }
    }

    @Test fun boughtOpensThePrefillAndAnswersNothingYet() {
        val (a, _) = parkedBefore()
        newVm()
        settle("the card") { ready?.dueWish != null }
        val prefills = mutableListOf<AddPrefill>()
        vm.viewModelScope.launch { vm.prefill.collect { prefills += it } }
        vm.wishBought(a)
        settle("the prefill") { prefills.isNotEmpty() }
        assertEquals(listOf<Any?>(180_000L, "سماعة", a), prefills.single().let { listOf(it.amount, it.note, it.wishId) })
        assertEquals(WishAnswer.WAITING, runBlocking { db.wishDao().byId(a) }!!.answer)
        assertEquals("the card stays until the entry is saved", a, ready?.dueWish?.id)
    }

    @Test fun changedMindAsksAboutTheJarAndThenTheNextWishComes() {
        val (a, b) = parkedBefore()
        newVm()
        settle("the card") { ready?.dueWish?.id == a }
        vm.wishChangedMind(ready!!.dueWish!!)
        settle("the jar question") { vm.wishAsk.value != null }
        assertEquals(listOf(a, 180_000L, 180_000L), vm.wishAsk.value!!.let { listOf(it.wishId, it.price, it.offer) })

        vm.wishJarAnswer(true)
        settle("answered with 1,800 in the jar") { ready?.jarTotal == 180_000L && ready?.dueWish?.id == b }
        assertEquals(180_000L, runBlocking { db.wishDao().byId(a) }!!.movedToJar)
    }

    /** Three printed days of 600 before today: today's 300 is under 60% of their average. */
    private fun printedBefore() = runBlocking {
        (1L..3L).forEach {
            db.dayTicketDao().insert(
                DayTicketEntity(date = nov12.minusDays(it), amount = 60_000, base = 60_000, status = TicketStatus.CLOSED_AUTO),
            )
        }
    }

    /** DataStore and Room work runs on the io scope while Main is pumped by [settle]: never block Main on it. */
    private fun <T> onIo(block: suspend () -> T): T {
        val out = java.util.concurrent.atomic.AtomicReference<Result<T>?>(null)
        io.launch { out.set(runCatching { block() }) }
        settle("an io task") { out.get() != null }
        return out.get()!!.getOrThrow()
    }

    @Test fun offerCardShowsUnderSixtyPercentAndIsDismissedForTheCycle() {
        printedBefore()
        newVm()
        settle("the offer card") { ready?.endOfMonthOffer == true }
        assertEquals(30_000L, ready?.ticket?.amount)
        vm.dismissOffer()
        settle("the offer is gone") { ready?.endOfMonthOffer == false }
        assertEquals(nov25.toEpochDay(), onIo { f.settings.current().eomOfferDismissedFor })
    }

    @Test fun noOfferWithoutThreePrintedDays() {
        newVm()
        settle("ready") { ready?.ticket != null }
        assertEquals(false, ready?.endOfMonthOffer)
    }

    @Test fun offerTakesTheFirstWeekCardSlotAfterAFirstWeekCardAndBeforeTheWishQuestion() {
        printedBefore()
        parkedBefore()
        newVm()
        settle("the offer card") { ready?.endOfMonthOffer == true }
        assertNull("the offer goes before the 48-hour question", ready?.dueWish)
        spend(1_000) // the first entry: the first-week "first entry" card shows and goes first
        settle("a first-week card") { ready?.firstWeekCard != null }
        assertEquals("never two cards at once", false, ready?.endOfMonthOffer)
        vm.dismissCard(ready!!.firstWeekCard!!.id)
        settle("the offer is back") { ready?.firstWeekCard == null && ready?.endOfMonthOffer == true }
        assertNull(ready?.dueWish)
        vm.dismissOffer()
        settle("the wish question after the offer") { ready?.endOfMonthOffer == false && ready?.dueWish != null }
    }

    @Test fun theOfferSwitchesTheModeOnAndTheCardGoes() {
        printedBefore()
        newVm()
        settle("the offer card") { ready?.endOfMonthOffer == true }
        vm.switchOnEndOfMonth()
        settle("switched on") { ready?.endOfMonthOffer == false && onIo { f.settings.current().eomOn } }
        assertEquals("3,900 over 13 days", 30_000L, ready?.ticket?.amount)
    }

    @Test fun tomorrowDoesNotCarryTheLeftoverOnTopInTheMode() {
        runBlocking { f.tickets.printToday(); f.eom.switchOn() }
        spend(1_000)
        newVm()
        settle("ready") { ready?.ticket != null }
        onIo { f.tickets.closeDay(nov12, LeftoverDestination.TOMORROW) }
        settle("tomorrow's preview") { ready?.tomorrow != null }
        // 3,890 over 12 days = 324: the 290 leftover is already in the 3,890 (carried on top it would show 590).
        assertEquals(32_400L, ready?.tomorrow?.amount)
    }
}
