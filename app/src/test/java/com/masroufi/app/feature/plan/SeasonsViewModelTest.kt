package com.masroufi.app.feature.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.Pot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SeasonsViewModelTest : PlanVmTestBase() {
    private lateinit var vm: SeasonsViewModel

    @Before fun open() {
        vm = make(SeasonsViewModel::class, { it.state }) { SeasonsViewModel(f.seasons, f.duty, f.settings, plus) }
        settle("seeded") { state.seasons.size == 5 }
    }

    private val state get() = vm.state.value
    private val byDate get() = state.seasons.sortedBy { it.date }

    private fun target(id: Long, piastres: Long) = runBlocking {
        f.seasons.update(f.seasons.observe().first().single { it.season.id == id }.season.copy(target = piastres))
    }

    @Test fun lockFollowsPlus() {
        assertFalse(state.isPlus)
        plus.setDebugOverride(true)
        settle("plus") { state.isPlus }
    }

    @Test fun nearestSeasonWithATargetLeadsElseTheNearest() {
        val near = byDate.first().season.id
        val far = byDate.last().season.id
        assertEquals("no target anywhere: the nearest", near, state.hero?.season?.id)
        target(far, 100_000)
        settle("far leads") { state.hero?.season?.id == far }
        target(near, 50_000)
        settle("near leads") { state.hero?.season?.id == near }
    }

    @Test fun perDayLineShowsOnlyWhileTheTargetIsUnmet() {
        val far = byDate.last().season.id
        target(far, 100_000)
        settle("per day") { (state.hero?.perDayNeeded ?: 0) > 0 }
        runBlocking {
            f.jar.deposit(100_000)
            f.jar.earmark(100_000, seasonId = far)
        }
        settle("met") { state.hero?.saved == 100_000L && state.hero?.perDayNeeded == 0L }
    }

    @Test fun togetherCopyFollowsTheGap() {
        target(byDate.last().season.id, 100_000)
        settle("over") { (state.together?.gap ?: 0) > 0 && state.togetherCount == 1 }
        for (month in listOf(8, 9, 10)) {
            runBlocking {
                db.jarDao().insert(
                    JarMovementEntity(pot = Pot.JAR, amount = 300_000, source = JarSource.MANUAL, at = at(LocalDate.of(2026, month, 10)).toEpochMilli()),
                )
            }
        }
        target(byDate.last().season.id, 100_001) // re-emit with the new jar history
        settle("within") { state.together?.let { it.neededPerMonth > 0 && it.gap == 0L && it.usualPerMonth == 300_000L } == true }
    }

    @Test fun reorderSavesTheOrderAndKeepsTheHeroOnTop() {
        val before = state.rest.map { it.season.id }
        vm.move(before[0], 1)
        settle("moved") { state.rest.map { it.season.id } == listOf(before[1], before[0]) + before.drop(2) }
        assertTrue(state.hero?.season?.id !in state.rest.map { it.season.id })
    }

    @Test fun personalSeasonShowsWhatTheLinkedPersonGave() {
        val season = runBlocking { f.seasons.createPersonal("فرح ابن خالتك", LocalDate.of(2026, 12, 20), 150_000) }
        val dad = runBlocking { f.people.addPerson("أبوه") }
        settle("no record") { state.seasons.size == 6 }
        assertTrue("nothing invented without a record", state.ledger.isEmpty())
        runBlocking {
            val mine = f.duty.addOccasion("فرحي", LocalDate.of(2026, 6, 1), OccasionOwner.MINE)
            f.duty.addGift(mine, dad, 50_000, GiftDirection.RECEIVED)
            f.duty.addOccasion("فرح ابن خالتك", LocalDate.of(2026, 12, 20), OccasionOwner.THEIRS, dad)
        }
        settle("note") { state.ledger[season] == LedgerNote("أبوه", 50_000) }
        assertEquals(1, state.ledger.size)
    }
}
