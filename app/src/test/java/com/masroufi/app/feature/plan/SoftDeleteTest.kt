package com.masroufi.app.feature.plan

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.ScheduleType
import com.masroufi.app.feature.kids.ChildFormViewModel
import com.masroufi.app.feature.kids.KidsViewModel
import com.masroufi.app.feature.people.PeopleViewModel
import com.masroufi.app.feature.people.PersonDetailViewModel
import com.masroufi.app.feature.roll.DeleteKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Every delete the user can make waits 5 s (11-DATA-AND-RULES "Safety rules"): hidden at once, back on undo, written after the
 * window on virtual time even when the screen is gone. Entries have their own tests (the roll).
 */
@RunWith(AndroidJUnit4::class)
class SoftDeleteTest : PlanVmTestBase() {
    private var cash = 0L

    @Before fun open() {
        cash = readOnIo { db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, openingBalance = 5_000_000, color = 0)) }
    }

    /** [shown]: the list still has it. [stored]: the database still has it. */
    private fun exercise(delete: () -> Unit, shown: () -> Boolean, stored: () -> Boolean) {
        settle("listed") { shown() }
        delete()
        settle("hidden at once") { !shown() }
        assertTrue("nothing written yet", stored())

        assertTrue(deletes.undoNewest(entries = false))
        settle("back after the undo") { shown() }
        passFiveSeconds()
        assertTrue("an undone delete is never written", stored())

        delete()
        settle("hidden again") { !shown() }
        main.scheduler.advanceTimeBy(4_000)
        main.scheduler.runCurrent()
        assertTrue("still inside the 5 s", stored())
        leaveScreens() // the screen going away does not cancel it
        passFiveSeconds()
        settle("really deleted") { !stored() }
    }

    @Test fun fixedCost() {
        val id = readOnIo {
            db.recurringDao().insert(
                RecurringEntity(name = "rent", amount = 250_000, pocketId = cash, scheduleDay = 20, nextDue = LocalDate.of(2026, 11, 20), kind = RecurringKind.BILL),
            )
        }
        val list = make(FixedCostsViewModel::class, { it.state }) { FixedCostsViewModel(f.recurring, f.envelopes, clock, deletes) }
        val form = make(FixedCostFormViewModel::class, { it.form }) {
            FixedCostFormViewModel(f.recurring, f.pockets, clock, SavedStateHandle(mapOf("id" to id)), deletes)
        }
        settle("form loaded") { form.form.value.loaded }
        val seen = mutableListOf<DeleteKind>()
        CoroutineScope(main).launch { deletes.committed.collect { seen += it } }
        main.scheduler.runCurrent()
        exercise({ form.delete() }, { list.state.value.items.isNotEmpty() }, { readOnIo { f.recurring.get(id) } != null })
        settle("the Heavy haptic's event") { seen.isNotEmpty() }
        assertEquals(listOf(DeleteKind.FIXED_COST), seen)
    }

    @Test fun subscription() {
        val id = readOnIo { f.subscriptions.add("net", 25_000, cash, LocalDate.of(2026, 11, 20), ScheduleType.MONTHLY, null, null, 1) }
        val list = make(SubscriptionsViewModel::class, { it.state }) { SubscriptionsViewModel(f.subscriptions, f.recurring, f.people, f.settings, clock, deletes) }
        val form = make(SubscriptionFormViewModel::class, { it.form }) {
            SubscriptionFormViewModel(f.subscriptions, f.recurring, f.pockets, clock, SavedStateHandle(mapOf("id" to id)), deletes)
        }
        settle("form loaded") { form.form.value.loaded }
        exercise({ form.delete() }, { list.state.value.rows.isNotEmpty() }, { readOnIo { f.recurring.get(id) } != null })
    }

    @Test fun person() {
        val id = readOnIo { f.people.addPerson("ahmed") }
        readOnIo { f.people.lend(id, 50_000, null) }
        val list = make(PeopleViewModel::class, { it.state }) { PeopleViewModel(f.people, f.duty, f.gameyas, f.pockets, f.kids, plus, clock, deletes) }
        val detail = make(PersonDetailViewModel::class, { it.state }) { PersonDetailViewModel(f.people, f.pockets, clock, SavedStateHandle(mapOf("id" to id)), deletes) }
        exercise(
            { detail.delete() },
            { list.state.value.rows.isNotEmpty() && !detail.state.value.missing },
            { readOnIo { db.peopleDao().personById(id) } != null },
        )
        assertTrue("their debts went with them", readOnIo { db.peopleDao().openDebts() }.isEmpty())
    }

    @Test fun child() {
        val id = readOnIo { f.kids.addChild("omar", 2013, 3, 15_000) }
        val list = make(KidsViewModel::class, { it.state }) { KidsViewModel(f.kids, f.childMode, f.pin, clock, deletes) }
        val form = make(ChildFormViewModel::class, { it.form }) { ChildFormViewModel(f.kids, clock, SavedStateHandle(mapOf("id" to id)), deletes) }
        settle("form loaded") { form.form.value.loaded }
        exercise({ form.delete() }, { list.state.value.tickets.isNotEmpty() }, { readOnIo { f.kids.view().children.isNotEmpty() } })
    }
}
