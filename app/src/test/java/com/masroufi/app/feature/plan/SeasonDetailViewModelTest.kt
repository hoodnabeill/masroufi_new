package com.masroufi.app.feature.plan

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.SeasonKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeasonDetailViewModelTest : PlanVmTestBase() {
    private fun open(id: Long) = make(SeasonDetailViewModel::class, { it.view }) {
        SeasonDetailViewModel(f.seasons, f.jar, clock, SavedStateHandle(mapOf("id" to id)))
    }

    @Test fun earmarkIsCappedAtTheFreeJar() {
        val summer = runBlocking { f.seasons.observe().first().first { it.season.kind == SeasonKind.SUMMER }.season.id }
        runBlocking { f.jar.deposit(10_000) }
        val vm = open(summer)
        settle("free") { vm.free.value == 10_000L && vm.view.value != null }

        vm.earmark(15_000) // above the free 10,000: nothing moves
        vm.earmark(6_000)
        settle("earmarked") { vm.view.value?.saved == 6_000L }
        assertEquals(4_000L, vm.free.value)

        vm.release(9_000) // more than is earmarked: nothing moves
        vm.release(1_000)
        settle("released") { vm.view.value?.saved == 5_000L }
        assertEquals(5_000L, vm.free.value)
    }

    @Test fun aNewOccasionIsCreatedFromTheForm() {
        val vm = open(0)
        vm.setName("فرح")
        vm.setMonth(12)
        vm.digit(5); vm.digit(0); vm.digit(0)
        vm.save()
        settle("saved") { vm.form.value.saved }
        val made = runBlocking { f.seasons.observe().first().single { it.season.kind == SeasonKind.PERSONAL } }
        assertEquals("فرح", made.season.name)
        assertEquals(50_000L, made.season.target)
        assertEquals(12, made.date.monthValue)
    }
}
