package com.masroufi.app.feature.people

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.DebtEntity
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.TabItemEntity
import com.masroufi.app.data.repo.PeopleRepository
import com.masroufi.app.data.repo.PersonRow
import com.masroufi.app.data.repo.PocketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** One line of a person's history; [sort] orders lines of one day (newest id first). */
sealed interface HistoryItem {
    val key: String
    val date: LocalDate
    val sort: Long

    data class Loan(val debt: DebtEntity) : HistoryItem {
        override val key get() = "d${debt.id}"
        override val date get() = debt.date
        override val sort get() = debt.id
    }

    /** Money that paid a debt down: [toMe] = they paid me, else I paid them. [amount] is positive. */
    data class Repayment(val entry: EntryEntity, val toMe: Boolean, override val date: LocalDate, val amount: Long) : HistoryItem {
        override val key get() = "r${entry.id}"
        override val sort get() = entry.id
    }

    data class TabLine(val item: TabItemEntity) : HistoryItem {
        override val key get() = "t${item.id}"
        override val date get() = item.date
        override val sort get() = item.id
    }
}

/**
 * Debts, the repayments among their money moves (the move that opened a loan is the loan row, not a repayment) and
 * tab items, newest first (a repayment before the loan of its day).
 */
internal fun buildHistory(debts: List<DebtEntity>, entries: List<EntryEntity>, tab: List<TabItemEntity>, clock: Clock): List<HistoryItem> {
    val byId = debts.associateBy { it.id }
    val repayments = entries.mapNotNull { e ->
        val debt = e.debtId?.let(byId::get) ?: return@mapNotNull null
        val opening = if (debt.direction == DebtDirection.THEY_OWE) e.amount < 0 else e.amount > 0
        if (opening) null
        else HistoryItem.Repayment(e, debt.direction == DebtDirection.THEY_OWE, Instant.ofEpochMilli(e.happenedAt).atZone(clock.zone).toLocalDate(), kotlin.math.abs(e.amount))
    }
    return (debts.map { HistoryItem.Loan(it) } + repayments + tab.map { HistoryItem.TabLine(it) })
        .sortedWith(compareByDescending<HistoryItem> { it.date }.thenBy { if (it is HistoryItem.Repayment) 0 else 1 }.thenByDescending { it.sort })
}

sealed interface DetailSheet {
    /** Settle every open tab item. */
    data object Settle : DetailSheet

    /** Pay down (or get paid for) one debt; the amount is typed. */
    data class Money(val debt: DebtEntity) : DetailSheet
    data object Edit : DetailSheet
    data object Delete : DetailSheet
}

data class PersonDetailUi(
    val loading: Boolean = true,
    /** The person is gone (deleted): the screen leaves. */
    val missing: Boolean = false,
    val row: PersonRow? = null,
    val history: List<HistoryItem> = emptyList(),
    val pockets: List<PocketEntity> = emptyList(),
    val sheet: DetailSheet? = null,
    val notice: PeopleNotice? = null,
) {
    val person: PersonEntity? get() = row?.person
}

/** What the person screen and its sheets call on the view model (a stateless screen takes this, so a test can pass a no-op). */
interface PersonDetailActions {
    fun openSettle() {}
    fun openMoney(debt: DebtEntity) {}
    fun openEdit() {}
    fun openDelete() {}
    fun dismissSheet() {}
    fun reminded() {}
    fun clearNotice() {}
    fun settleTab(pocketId: Long) {}
    fun settleDebt(debt: DebtEntity, amount: Long, pocketId: Long?) {}
    fun saveEdit(name: String, phone: String, kind: PersonKind) {}
    fun delete() {}
}

private const val TAG = "PersonDetail"

/** Person detail (pushed): totals, history, "سدّد" / "فكّره بلُطف", edit, settle the tab, delete. */
@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val people: PeopleRepository,
    pocketRepo: PocketRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel(), PersonDetailActions {
    /** The nav argument (PersonDetail.id). */
    val id: Long = checkNotNull(savedState.get<Long>("id")) { "PersonDetail needs an id" }

    private val sheet = MutableStateFlow<DetailSheet?>(null)
    private val notice = MutableStateFlow<PeopleNotice?>(null)

    val state: StateFlow<PersonDetailUi> = combine(
        people.observeDetail(id), people.observeDebtEntries(id), pocketRepo.observePockets(), sheet, notice,
    ) { detail, entries, pockets, s, n ->
        if (detail == null) {
            PersonDetailUi(loading = false, missing = true)
        } else {
            PersonDetailUi(
                loading = false, row = detail.row, history = buildHistory(detail.debts, entries, detail.tabItems, clock),
                pockets = pockets.map { it.pocket }, sheet = s, notice = n,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailUi())

    override fun openSettle() { sheet.value = DetailSheet.Settle }
    override fun openMoney(debt: DebtEntity) { sheet.value = DetailSheet.Money(debt) }
    override fun openEdit() { sheet.value = DetailSheet.Edit }
    override fun openDelete() { sheet.value = DetailSheet.Delete }
    override fun dismissSheet() { sheet.value = null }
    override fun reminded() { notice.value = PeopleNotice(NoticeKind.REMINDED) }
    override fun clearNotice() { notice.value = null }

    /** One SPENT entry from [pocketId] for the open tab items. */
    override fun settleTab(pocketId: Long) = launchSafe("settle tab") {
        val name = state.value.person?.name.orEmpty()
        sheet.value = null
        people.settleTab(id, pocketId)
        notice.value = PeopleNotice(NoticeKind.TAB_SETTLED, name)
    }

    /** [amount] of [debt] paid either way; [pocketId] null records only. */
    override fun settleDebt(debt: DebtEntity, amount: Long, pocketId: Long?) = launchSafe("repay") {
        sheet.value = null
        people.repay(debt.id, amount, pocketId)
        notice.value = PeopleNotice(if (debt.direction == DebtDirection.THEY_OWE) NoticeKind.RECEIVED else NoticeKind.REPAID)
    }

    override fun saveEdit(name: String, phone: String, kind: PersonKind) = launchSafe("edit") {
        sheet.value = null
        people.updatePerson(id, name, phone, kind)
    }

    /** Deletes the person with their debts, tab items and gifts (the screen asked first); the state turns "missing". */
    override fun delete() = launchSafe("delete") {
        people.deletePerson(id)
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
