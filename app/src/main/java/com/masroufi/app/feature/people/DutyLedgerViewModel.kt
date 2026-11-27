package com.masroufi.app.feature.people

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.GiftEntity
import com.masroufi.app.data.db.OccasionEntity
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.repo.DutyRepository
import com.masroufi.app.data.repo.GiftRow
import com.masroufi.app.data.repo.OccasionView
import com.masroufi.app.data.repo.PeopleRepository
import com.masroufi.app.data.repo.UpcomingDuty
import com.masroufi.app.entitlements.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The ledger's sheets: an occasion to add or edit, a gift to add or edit, and the "this has gifts" delete question. */
sealed interface DutySheet {
    data class Occasion(val editing: OccasionView?) : DutySheet
    data class Gift(val occasionId: Long, val editing: GiftRow?) : DutySheet
    data class ConfirmDelete(val occasionId: Long, val name: String, val gifts: Int) : DutySheet
}

data class DutyUi(
    val loading: Boolean = true,
    val plus: Boolean = false,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    /** Newest first, each with its gifts. */
    val mine: List<OccasionView> = emptyList(),
    val theirs: List<OccasionView> = emptyList(),
    val upcoming: UpcomingDuty? = null,
    val persons: List<PersonEntity> = emptyList(),
    val sheet: DutySheet? = null,
) {
    val empty: Boolean get() = mine.isEmpty() && theirs.isEmpty()
}

/** What the ledger screen and its sheets call on the view model (a stateless screen takes this, so a test can pass a no-op). */
interface DutyActions {
    fun openOccasion(editing: OccasionView? = null) {}
    fun openGift(occasionId: Long, editing: GiftRow? = null) {}
    fun dismissSheet() {}
    fun saveOccasion(editing: OccasionEntity?, name: String, date: LocalDate, whose: OccasionOwner, personId: Long?) {}
    fun requestDeleteOccasion(v: OccasionView) {}
    fun confirmDeleteOccasion(id: Long) {}
    fun saveGift(occasionId: Long, editing: GiftEntity?, personId: Long?, newName: String?, amount: Long, direction: GiftDirection) {}
    fun deleteGift(id: Long) {}
}

private const val TAG = "DutyLedger"

@HiltViewModel
class DutyLedgerViewModel @Inject constructor(
    private val duty: DutyRepository,
    private val people: PeopleRepository,
    entitlements: Entitlements,
    private val clock: Clock,
) : ViewModel(), DutyActions {
    private val sheet = MutableStateFlow<DutySheet?>(null)

    private val content = combine(duty.observe(), people.observePersons(), entitlements.isPlus) { views, persons, plus ->
        DutyUi(
            loading = false, plus = plus, today = LocalDate.now(clock),
            mine = views.filter { it.occasion.whose == OccasionOwner.MINE },
            theirs = views.filter { it.occasion.whose == OccasionOwner.THEIRS },
            upcoming = duty.upcoming(), persons = persons,
        )
    }

    val state: StateFlow<DutyUi> = combine(content, sheet) { c, s -> c.copy(sheet = s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DutyUi())

    override fun openOccasion(editing: OccasionView?) { sheet.value = DutySheet.Occasion(editing) }
    override fun openGift(occasionId: Long, editing: GiftRow?) { sheet.value = DutySheet.Gift(occasionId, editing) }
    override fun dismissSheet() { sheet.value = null }

    /** Saves a new occasion, or the edit of [editing]. */
    override fun saveOccasion(editing: OccasionEntity?, name: String, date: LocalDate, whose: OccasionOwner, personId: Long?) = launchSafe("save occasion") {
        if (editing == null) duty.addOccasion(name, date, whose, personId)
        else duty.updateOccasion(editing.copy(name = name.trim(), date = date, whose = whose, personId = personId))
        sheet.value = null
    }

    /** Asks first only when the occasion has gifts (they go with it); an empty one just goes. */
    override fun requestDeleteOccasion(v: OccasionView) {
        if (v.gifts.isEmpty()) launchSafe("delete occasion") { duty.deleteOccasion(v.occasion.id); sheet.value = null }
        else sheet.value = DutySheet.ConfirmDelete(v.occasion.id, v.occasion.name, v.gifts.size)
    }

    override fun confirmDeleteOccasion(id: Long) = launchSafe("delete occasion") {
        duty.deleteOccasion(id)
        sheet.value = null
    }

    /** [personId] null makes a new person named [newName]. [editing] replaces that gift instead of adding one. */
    override fun saveGift(occasionId: Long, editing: GiftEntity?, personId: Long?, newName: String?, amount: Long, direction: GiftDirection) = launchSafe("save gift") {
        val who = personId ?: people.addPerson(requireNotNull(newName).trim())
        if (editing == null) duty.addGift(occasionId, who, amount, direction)
        else duty.updateGift(editing.copy(personId = who, amount = amount, direction = direction))
        sheet.value = null
    }

    override fun deleteGift(id: Long) = launchSafe("delete gift") {
        duty.deleteGift(id)
        sheet.value = null
    }

    private fun launchSafe(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$what failed", e)
            }
        }
    }
}
