package com.masroufi.app.feature.kids

import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.feature.plan.PlanVmTestBase
import com.masroufi.app.ui.haptics.HapticLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import java.time.LocalDate

/**
 * Today is Wednesday 2026-11-18, so the first handover is Saturday 11-21. Repository calls run on an io scope while Main is
 * pumped (never `runBlocking` on the test thread while a ViewModel may be in flight).
 */
abstract class KidsVmTestBase : PlanVmTestBase() {
    protected val today: LocalDate = LocalDate.of(2026, 11, 18)
    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    protected var cash = 0L

    @Before fun openKids() {
        clock.instant = at(today)
        cash = call { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 5_000_000, color = 0)) }
    }

    @After fun closeKids() = callScope.cancel()

    protected fun <T> call(block: suspend () -> T): T {
        var result: Result<T>? = null
        val job = callScope.launch { result = runCatching { block() } }
        settle("a repository call") { job.isCompleted }
        return result!!.getOrThrow()
    }

    protected fun later() { clock.instant = clock.instant.plusSeconds(60) }

    /** Everything the ViewModel's haptics flow emits from now on. */
    protected fun <V : androidx.lifecycle.ViewModel> V.record(flow: Flow<HapticLevel>): MutableList<HapticLevel> {
        val seen = mutableListOf<HapticLevel>()
        viewModelScope.launch { flow.collect { seen += it } }
        return seen
    }

    protected fun malak() = call { f.kids.addChild("ملك", 2017, 3, 7_000, "العجلة", 150_000, 1_000, 500) }
    protected fun omar() = call { f.kids.addChild("عمر", 2013, 3, 15_000) }
}
