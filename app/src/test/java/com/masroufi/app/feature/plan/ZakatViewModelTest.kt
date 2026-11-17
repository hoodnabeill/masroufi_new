package com.masroufi.app.feature.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZakatViewModelTest : PlanVmTestBase() {
    private lateinit var vm: ZakatViewModel
    private val state get() = vm.state.value

    @Before fun open() {
        runBlocking {
            f.pockets.addPocket("كاش", PocketKind.CASH, 500_000, true, 0)
            f.jar.deposit(200_000)
        }
        vm = make(ZakatViewModel::class, { it.state }) { ZakatViewModel(f.pockets, f.jar, f.settings, plus) }
        settle("loaded") { !state.loading && state.zakatable == 700_000L }
    }

    @Test fun defaultsAreEveryPocketPlusTheJarAndNoNisabWithoutAGoldPrice() {
        assertNull(state.nisab)
        assertNull("nothing to compare with", state.zakat)
        assertEquals(1, state.people)
    }

    @Test fun nisabFromGoldThenZakatAboveAndBelowIt() {
        runBlocking { f.settings.setGoldPrice(1_000, 1) }
        settle("nisab") { state.nisab == 85_000L }
        assertEquals("2.5% of 7,000.00", 17_500L, state.zakat)

        vm.setNisab(2_000_000)
        settle("typed nisab") { state.nisab == 2_000_000L }
        assertEquals("below the nisab", 0L, state.zakat)

        vm.setZakatable(2_000_000)
        settle("typed amount") { state.zakatable == 2_000_000L }
        assertEquals("exactly at the nisab counts", 50_000L, state.zakat)
    }

    @Test fun fitrIsPeopleTimesTheTypedAmount() {
        vm.stepPeople(3)
        vm.setPerPerson(10_000)
        settle("fitr") { state.fitr == 40_000L }
        vm.stepPeople(-9)
        settle("floor") { state.people == 0 && state.fitr == 0L }
    }
}
