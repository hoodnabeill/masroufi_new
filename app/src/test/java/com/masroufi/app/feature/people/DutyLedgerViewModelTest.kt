package com.masroufi.app.feature.people

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.feature.plan.PlanVmTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Today 2026-11-01. */
@RunWith(AndroidJUnit4::class)
class DutyLedgerViewModelTest : PlanVmTestBase() {
    private lateinit var vm: DutyLedgerViewModel
    private val state get() = vm.state.value

    @Before fun open() {
        plus.setDebugOverride(true)
        vm = make(DutyLedgerViewModel::class, { it.state }) { DutyLedgerViewModel(f.duty, f.people, plus, clock, deletes) }
        settle("loaded") { !state.loading && state.plus }
    }

    private fun person(name: String) = runBlocking { f.people.addPerson(name) }
    private fun occasion(name: String, date: LocalDate, whose: OccasionOwner, personId: Long? = null) =
        runBlocking { f.duty.addOccasion(name, date, whose, personId) }
    private fun gift(occasionId: Long, personId: Long, amount: Long, dir: GiftDirection = GiftDirection.RECEIVED) =
        runBlocking { f.duty.addGift(occasionId, personId, amount, dir) }

    @Test fun occasionsAreGroupedMineAndTheirsNewestFirst() {
        val mama = person("ماما")
        occasion("فرحي", LocalDate.of(2026, 6, 1), OccasionOwner.MINE)
        occasion("خطوبتي", LocalDate.of(2026, 9, 1), OccasionOwner.MINE)
        val theirs = occasion("فرح ابن خالتك", LocalDate.of(2026, 12, 20), OccasionOwner.THEIRS, mama)
        gift(theirs, mama, 30_000, GiftDirection.GIVEN)
        settle("grouped") { state.mine.size == 2 && state.theirs.size == 1 }
        assertEquals(listOf("خطوبتي", "فرحي"), state.mine.map { it.occasion.name })
        val row = state.theirs.single().gifts.single()
        assertEquals("ماما", row.person?.name)
        assertEquals(GiftDirection.GIVEN, row.gift.direction)
        assertFalse(state.empty)
    }

    @Test fun emptyWhenThereAreNoOccasions() {
        assertTrue(state.empty)
        assertNull(state.upcoming)
    }

    @Test fun suggestionShownWhenThePersonGaveAtMyOccasion() {
        val uncle = person("عمو")
        gift(occasion("فرحي", LocalDate.of(2026, 6, 1), OccasionOwner.MINE), uncle, 50_000)
        occasion("فرح ابنه", LocalDate.of(2026, 11, 11), OccasionOwner.THEIRS, uncle)
        settle("upcoming") { state.upcoming != null }
        assertEquals(50_000L, state.upcoming?.gaveMe)
        assertEquals(10L, state.upcoming?.daysUntil)
    }

    @Test fun suggestionHiddenWithNoRecord() {
        val stranger = person("حد جديد")
        occasion("فرح جارنا", LocalDate.of(2026, 11, 11), OccasionOwner.THEIRS, stranger)
        settle("upcoming") { state.upcoming != null }
        assertNull("nothing invented", state.upcoming?.gaveMe)
    }

    @Test fun ownOccasionSummaryCountsDistinctGivers() {
        val a = person("أ")
        val b = person("ب")
        val wedding = occasion("فرحي", LocalDate.of(2026, 6, 1), OccasionOwner.MINE)
        gift(wedding, a, 50_000)
        gift(wedding, a, 20_000)
        gift(wedding, b, 100_000)
        gift(wedding, b, 10_000, GiftDirection.GIVEN)
        settle("summary") { state.mine.singleOrNull()?.givers == 2 }
        assertEquals(170_000L, state.mine.single().receivedTotal)
    }

    @Test fun lockFollowsPlus() {
        plus.setDebugOverride(false)
        settle("locked") { !state.plus }
        plus.setDebugOverride(true)
        settle("unlocked") { state.plus }
    }

    @Test fun addAndDeleteGiftWithANewPerson() {
        val wedding = occasion("فرحي", LocalDate.of(2026, 6, 1), OccasionOwner.MINE)
        settle("occasion") { state.mine.size == 1 }
        vm.openGift(wedding)
        settle("sheet") { state.sheet is DutySheet.Gift }
        vm.saveGift(wedding, null, null, "  خالتي ", 75_000, GiftDirection.RECEIVED)
        settle("gift") { state.mine.single().gifts.size == 1 && state.sheet == null }
        val g = state.mine.single().gifts.single()
        assertEquals("خالتي", g.person?.name)
        assertEquals(75_000L, g.gift.amount)
        vm.deleteGift(g.gift.id)
        settle("gone") { state.mine.single().gifts.isEmpty() }
    }

    @Test fun editGiftChangesTheAmount() {
        val p = person("عمو")
        val wedding = occasion("فرحي", LocalDate.of(2026, 6, 1), OccasionOwner.MINE)
        gift(wedding, p, 10_000)
        settle("gift") { state.mine.singleOrNull()?.gifts?.size == 1 }
        val g = state.mine.single().gifts.single().gift
        vm.saveGift(wedding, g, p, null, 20_000, GiftDirection.RECEIVED)
        settle("edited") { state.mine.single().gifts.single().gift.amount == 20_000L }
    }

    @Test fun deletingAnOccasionWithGiftsAsksFirstAnEmptyOneDoesNot() {
        val p = person("عمو")
        val full = occasion("فرحي", LocalDate.of(2026, 6, 1), OccasionOwner.MINE)
        val empty = occasion("عقيقة", LocalDate.of(2026, 7, 1), OccasionOwner.THEIRS)
        gift(full, p, 10_000)
        settle("both") { state.mine.size == 1 && state.theirs.size == 1 && state.mine.single().gifts.size == 1 }
        vm.requestDeleteOccasion(state.mine.single())
        settle("asks") { state.sheet is DutySheet.ConfirmDelete }
        assertEquals(1, (state.sheet as DutySheet.ConfirmDelete).gifts)
        assertEquals(1, state.mine.size)
        vm.confirmDeleteOccasion(full)
        settle("deleted with gifts") { state.mine.isEmpty() && state.sheet == null }
        vm.requestDeleteOccasion(state.theirs.single())
        settle("empty one gone at once") { state.theirs.isEmpty() }
        assertNotNull(empty)
        assertNull(state.sheet)
    }

    @Test fun saveOccasionAddsAndEdits() {
        vm.saveOccasion(null, " فرح ", LocalDate.of(2026, 12, 1), OccasionOwner.THEIRS, null)
        settle("added") { state.theirs.size == 1 }
        val o = state.theirs.single().occasion
        assertEquals("فرح", o.name)
        vm.saveOccasion(o, "فرحي", LocalDate.of(2026, 12, 2), OccasionOwner.MINE, null)
        settle("moved to mine") { state.mine.size == 1 && state.theirs.isEmpty() }
        assertEquals(LocalDate.of(2026, 12, 2), state.mine.single().occasion.date)
    }
}
