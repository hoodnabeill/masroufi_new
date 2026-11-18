package com.masroufi.app.feature.profile

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.DataWipe
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.ProverbCollectedEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.AlbumRepository
import com.masroufi.app.data.repo.MatchRepository
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.MainGoal
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.WorkKind
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.ui.AppRootViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Today is Tuesday 2026-11-17 09:00 Cairo. */
@RunWith(AndroidJUnit4::class)
class ProfileViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 17)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val main = StandardTestDispatcher()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var db: TazkaraDatabase
    private lateinit var f: RepoFixture

    @Before fun open() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(context, TazkaraDatabase::class.java).allowMainThreadQueries().build()
        f = RepoFixture(db, io, tmp.root, clock, DebugEntitlements(false))
    }

    @After fun close() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
        io.cancel()
        db.close()
    }

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

    private fun visit(): ProfileViewModel {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    ProfileViewModel(
                        f.profile,
                        AlbumRepository(db.dayTicketDao(), db.proverbDao(), db.stampDao(), clock),
                        MatchRepository(db.dayTicketDao(), DebugEntitlements(false), clock),
                        DataWipe(context, db, f.settings),
                    )
                }
            },
        )[ProfileViewModel::class]
        vm.viewModelScope.launch { vm.state.collect {} }
        settle("profile") { !vm.state.value.loading }
        return vm
    }

    private fun saved() = runBlocking { f.settings.current() }

    @Test fun anEmptyProfileShowsTheMocksDefaultsAndSavesNothing() {
        val ui = visit().state.value
        assertEquals(listOf("", "ق", 4, 0), listOf(ui.name, ui.initial, ui.householdSize, ui.inSchool))
        assertNull(ui.gift)
        assertNull(ui.workKind)
        assertNull(ui.mainGoal)
        assertNull(saved().householdSize)
    }

    @Test fun theNameIsSavedAsItIsTypedAndBlankClearsIt() {
        val vm = visit()
        vm.setName("  يوسف ")
        settle("name") { saved().profileName == "يوسف" }
        assertEquals("ي", vm.state.value.initial)
        vm.setName("")
        settle("cleared") { saved().profileName == null }
    }

    @Test fun theBirthdayIsSavedAndClearable() {
        val vm = visit()
        vm.setBirthday(LocalDate.of(1998, 3, 14))
        settle("birthday") { saved().birthday == LocalDate.of(1998, 3, 14) }
        settle("ui") { vm.state.value.birthday == LocalDate.of(1998, 3, 14) }
        vm.setBirthday(null)
        settle("cleared") { saved().birthday == null }
    }

    @Test fun theKindOfWorkIsSavedAndTappingItAgainClearsIt() {
        val vm = visit()
        vm.setWorkKind(WorkKind.FREELANCE)
        settle("work") { saved().workKind == WorkKind.FREELANCE }
        vm.setWorkKind(WorkKind.STUDENT)
        settle("student") { saved().workKind == WorkKind.STUDENT }
        vm.setWorkKind(WorkKind.STUDENT)
        settle("cleared") { saved().workKind == null }
    }

    @Test fun theMainGoalIsSavedAndTappingItAgainClearsIt() {
        val vm = visit()
        vm.setMainGoal(MainGoal.SAVE)
        settle("goal") { saved().mainGoal == MainGoal.SAVE }
        settle("ui") { vm.state.value.mainGoal == MainGoal.SAVE }
        vm.setMainGoal(MainGoal.SAVE)
        settle("cleared") { saved().mainGoal == null }
    }

    @Test fun theBirthdayGiftIsWholePoundsRoundedUpOverTheDaysLeft() {
        val vm = visit()
        vm.setBirthday(LocalDate.of(1998, 3, 14))
        settle("gift") { vm.state.value.gift != null }
        // 117 days to 2027-03-14: 1,000 / 117 = 8.55, so 9 a day (never 8).
        assertEquals(GiftUi(days = 117, perDayPounds = 9, target = 100_000), vm.state.value.gift)

        vm.open(ProfileSheet.Target)
        settle("sheet") { vm.state.value.sheet == ProfileSheet.Target }
        assertEquals("starts from the default 1,000", 100_000L, vm.state.value.target.toPiastres())
        repeat(4) { vm.backspace() }
        listOf(1, 2, 0, 0).forEach(vm::digit)
        vm.saveTarget()
        settle("target") { saved().birthdayTarget == 120_000L }
        settle("gift 12") { vm.state.value.gift?.perDayPounds == 11L }
        assertEquals(GiftUi(days = 117, perDayPounds = 11, target = 120_000), vm.state.value.gift)
        assertNull("the sheet is closed", vm.state.value.sheet)

        vm.open(ProfileSheet.Target)
        repeat(4) { vm.backspace() }
        vm.saveTarget()
        settle("default again") { saved().birthdayTarget == null }
    }

    @Test fun householdLinesFollowTheSteppers() {
        val vm = visit()
        vm.stepSize(1)
        settle("size") { saved().householdSize == 5 }
        vm.stepSchool(1)
        vm.stepSchool(1)
        settle("school") { saved().inSchool == 2 }
        settle("ui") { vm.state.value.let { it.householdSize == 5 && it.inSchool == 2 } }
        assertEquals("the school season is suggested for 2 children", 100_000L, runBlocking { f.profile.schoolSuggestion(50_000) })
        assertTrue("never written over a season", runBlocking { db.seasonDao().allNow() }.all { it.target == 0L })

        vm.stepSize(-3) // 2 people, both in school
        settle("two") { saved().householdSize == 2 }
        assertEquals(2, saved().inSchool)
        vm.stepSize(-1) // fewer people than children in school: the count follows
        settle("one") { saved().householdSize == 1 }
        assertEquals(1, saved().inSchool)
        vm.stepSize(-1)
        vm.stepSchool(1)
        settle("kept") { saved().householdSize == 1 }
        assertEquals("never below 1, school never above the household", 1, saved().inSchool)
    }

    @Test fun theIdentityCardCountsTheAlbum() {
        runBlocking {
            (1..8).forEach {
                db.dayTicketDao().insert(
                    DayTicketEntity(date = today.minusDays(it.toLong()), amount = 10_000, base = 10_000, spent = 5_000, status = TicketStatus.CLOSED_BY_HAND, leftover = 5_000),
                )
            }
            db.proverbDao().insert(ProverbCollectedEntity(1, today.minusDays(1)))
        }
        val ui = visit().state.value
        assertEquals(listOf(2, "ماشي", 8, 8, 1), listOf(ui.level, ui.levelName, ui.ticketsClosed, ui.streak, ui.proverbs))
    }

    @Test fun theShareTextHasNoAmounts() {
        val ui = ProfileUi(loading = false, name = "يوسف", level = 4, levelName = "مدبّر", streak = 13, proverbs = 18, wonOf = 6, ofLast = 8)
        val text = profileShareText(context, ui)
        assertTrue(text.contains("يوسف"))
        assertEquals("only the counts: level, streak, proverbs, matches", listOf("4", "13", "18", "6", "8"), Regex("\\d+").findAll(text).map { it.value }.toList())
        assertFalse(text.contains("جنيه") || text.contains("EGP"))
        assertFalse("no name: no empty slot", profileShareText(context, ui.copy(name = " ")).startsWith(" "))
    }

    @Test fun afterAWipeTheAppStartsAtOnboardingWithAnEmptyHome() {
        runBlocking {
            f.settings.setOnboarded(true)
            f.settings.setProfileName("يوسف")
            f.settings.setTicketRaise(30_000, today.plusDays(8).toEpochDay())
            val cash = db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 100_000, color = 0))
            db.dayTicketDao().insert(DayTicketEntity(date = today, amount = 24_000, base = 24_000))
            db.proverbDao().insert(ProverbCollectedEntity(3, today.minusDays(1)))
            assertEquals(1, db.pocketDao().all().first().size)
            assertTrue(cash > 0)
        }
        val before = runBlocking { f.home.observeHome().first() }
        assertEquals(24_000L, before.today?.amount)

        val vm = visit()
        vm.open(ProfileSheet.Wipe)
        settle("confirm") { vm.state.value.sheet == ProfileSheet.Wipe }
        vm.wipe()
        settle("wiped") { vm.state.value.wiped }
        assertNull("the confirm sheet is gone", vm.state.value.sheet)

        val store = ViewModelStore().also { stores += it }
        val root = ViewModelProvider.create(store, viewModelFactory { initializer { AppRootViewModel(f.settings) } })[AppRootViewModel::class]
        root.viewModelScope.launch { root.onboarded.collect {} }
        settle("onboarding") { root.onboarded.value == false }

        val home = runBlocking { f.home.observeHome().first() }
        assertNull("no ticket", home.today)
        assertEquals(listOf(0, 0L, 0), listOf(home.entryCount, home.spendable, home.collected))
        assertTrue(runBlocking { db.pocketDao().all().first() }.isEmpty())
        val s = saved()
        assertNull(s.profileName)
        assertNull("a raise does not outlive the wipe", s.ticketRaise)
        assertFalse(s.onboarded)
    }
}
