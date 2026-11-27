package com.masroufi.app.feature.people

import com.masroufi.app.feature.roll.withoutDeleted
import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.roll.PendingDelete
import com.masroufi.app.feature.roll.DeleteKind
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.repo.DutyRepository
import com.masroufi.app.data.repo.GameyaRepository
import com.masroufi.app.data.repo.GameyaView
import com.masroufi.app.data.repo.KidsRepository
import com.masroufi.app.data.repo.PeopleFilter
import com.masroufi.app.data.repo.PeopleRepository
import com.masroufi.app.data.repo.PeopleTotals
import com.masroufi.app.data.repo.PersonRow
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.data.repo.UpcomingDuty
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.domain.computeShares
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** The one action a row offers. */
enum class RowAction { REMIND, SETTLE_TAB, REPAY }

/** Which of the user's balances with this person is the one to act on. */
fun rowAction(row: PersonRow): RowAction? = when {
    row.onTab > 0 && row.owedToMe == 0L && row.iOweLoans == 0L -> RowAction.SETTLE_TAB
    row.owedToMe > 0 && row.balance > 0 -> RowAction.REMIND
    row.iOweLoans > 0 -> RowAction.REPAY
    row.onTab > 0 -> RowAction.SETTLE_TAB
    else -> null
}

/** [days] since the oldest open debt or tab item; [overdue] when an open debt is past its due date. */
data class PersonRowUi(val row: PersonRow, val action: RowAction?, val days: Long?, val overdue: Boolean)

enum class NoticeKind { TAB_SETTLED, REPAID, RECEIVED, REMINDED, SPLIT }

/** The feedback line under the header ("النوتة اتسدّدت…", "بعتله رسالة بلُطف."); [name] fills the tab line. */
data class PeopleNotice(val kind: NoticeKind, val name: String = "")

/** The lilac card: the next occasion of someone else's, and what the user's own occasion brought in. */
data class DutyCardUi(val upcoming: UpcomingDuty? = null, val ownTotal: Long = 0, val ownGivers: Int = 0)

sealed interface PeopleSheet {
    data object Add : PeopleSheet
    data object Split : PeopleSheet

    /** Settle every open item on a shop's tab. */
    data class SettleTab(val personId: Long, val name: String, val amount: Long) : PeopleSheet

    /** Pay back everything the user owes this person in loans. */
    data class Repay(val personId: Long, val name: String, val amount: Long) : PeopleSheet
}

/** What "سجّل مع حد" can record. */
enum class AddMode { THEY_OWE, I_OWE, TAB }

/** The participant id of the user in a split. */
const val ME = 0L

/**
 * The split being typed. [participants] holds [ME] and people ids in tap order; [edited] is the raw text of the shares
 * the user typed over, the rest are equal ([computeShares]).
 */
data class SplitDraft(
    val amount: AmountInput = AmountInput(),
    val pocketId: Long? = null,
    val participants: List<Long> = listOf(ME),
    val edited: Map<Long, String> = emptyMap(),
) {
    val total: Long get() = amount.toPiastres()

    /** Person id (or [ME]) to piastres; null when the typed shares cannot add up to the total. */
    val shares: Map<Long, Long>? get() = computeShares(total, participants, edited.mapValues { parsePiastres(it.value) })

    /** Someone other than the user pays a share, and it all adds up. */
    val canSave: Boolean get() = pocketId != null && shares?.any { (id, v) -> id != ME && v > 0 } == true
}

internal fun parsePiastres(text: String): Long =
    text.fold(AmountInput()) { a, c -> if (c.isDigit()) a.digit(c.digitToInt()) else if (c == '.') a.point() else a }.toPiastres()

/** The kids card: how many children and what they cost a month (0 children = the card invites the first). */
data class KidsCardUi(val children: Int = 0, val monthEstimate: Long = 0)

data class PeopleUi(
    val loading: Boolean = true,
    val filter: PeopleFilter = PeopleFilter.ALL,
    val totals: PeopleTotals = PeopleTotals(0, 0, 0, 0),
    val rows: List<PersonRowUi> = emptyList(),
    val persons: List<PersonEntity> = emptyList(),
    val pockets: List<PocketEntity> = emptyList(),
    val plus: Boolean = false,
    val duty: DutyCardUi = DutyCardUi(),
    val gameya: GameyaView? = null,
    val kids: KidsCardUi = KidsCardUi(),
    val sheet: PeopleSheet? = null,
    val split: SplitDraft = SplitDraft(),
    val notice: PeopleNotice? = null,
)

/** What the People screen and its sheets call on the view model (a stateless screen takes this, so a test can pass a no-op). */
interface PeopleActions {
    fun setFilter(f: PeopleFilter) {}
    fun openAdd() {}
    fun openSplit() {}
    fun dismissSheet() {}
    fun onAction(row: PersonRow) {}
    fun reminded() {}
    fun clearNotice() {}
    fun settleTab(personId: Long, name: String, pocketId: Long) {}
    fun repayAll(personId: Long, pocketId: Long?) {}
    fun add(mode: AddMode, personId: Long?, newName: String?, amount: Long, text: String?, pocketId: Long?, dueInDays: Int?) {}
    fun splitDigit(d: Int) {}
    fun splitPoint() {}
    fun splitBackspace() {}
    fun splitPocket(id: Long) {}
    fun toggleParticipant(id: Long) {}
    fun editShare(id: Long, text: String) {}
    fun addSplitPerson(name: String) {}
    fun confirmSplit() {}
}

private const val TAG = "People"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val people: PeopleRepository,
    duty: DutyRepository,
    gameyas: GameyaRepository,
    pocketRepo: PocketRepository,
    kidsRepo: KidsRepository,
    entitlements: Entitlements,
    private val clock: Clock,
    deletes: PendingDeletes,
) : ViewModel(), PeopleActions {
    private val filter = MutableStateFlow(PeopleFilter.ALL)
    private val sheet = MutableStateFlow<PeopleSheet?>(null)
    private val split = MutableStateFlow(SplitDraft())
    private val notice = MutableStateFlow<PeopleNotice?>(null)

    private val content = combine(
        filter.flatMapLatest { f -> people.observeList(f).map { f to it } },
        people.observeTotals(),
        people.observePersons(),
        pocketRepo.observePockets(),
        deletes.hidden(DeleteKind.PERSON),
    ) { (f, list), totals, allPersons, pockets, gone ->
        val persons = allPersons.filter { it.id !in gone }
        val today = LocalDate.now(clock)
        PeopleUi(
            loading = false, filter = f, totals = totals, persons = persons, pockets = pockets.map { it.pocket },
            rows = list.filter { it.person.id !in gone }.map { r ->
                PersonRowUi(r, rowAction(r), r.since?.let { ChronoUnit.DAYS.between(it, today) }, r.nextDue?.isBefore(today) == true)
            },
        )
    }

    private val cards = combine(
        entitlements.isPlus,
        duty.observe().map { views ->
            val own = views.firstOrNull { it.occasion.whose == OccasionOwner.MINE && it.givers > 0 }
            DutyCardUi(duty.upcoming(), own?.receivedTotal ?: 0, own?.givers ?: 0)
        },
        gameyas.observe().withoutDeleted(deletes, DeleteKind.GAMEYA) { it.gameya.id }.map { it.firstOrNull() },
    ) { plus, dutyCard, gameya -> Triple(plus, dutyCard, gameya) }

    private val overlay = combine(sheet, split, notice) { s, d, n -> Triple(s, d, n) }

    private val kidsCard = combine(kidsRepo.observe(), deletes.hidden(DeleteKind.CHILD)) { v, gone -> KidsCardUi(v.children.count { it.child.id !in gone }, v.monthEstimate) }

    val state: StateFlow<PeopleUi> = combine(content, cards, overlay, kidsCard) { c, (plus, dutyCard, gameya), (s, d, n), k ->
        c.copy(plus = plus, duty = dutyCard, gameya = gameya, kids = k, sheet = s, split = d, notice = n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUi())

    override fun setFilter(f: PeopleFilter) {
        filter.value = f
    }

    // -- sheets

    override fun openAdd() = sheet.update { PeopleSheet.Add }

    override fun openSplit() {
        split.value = SplitDraft(pocketId = state.value.pockets.firstOrNull()?.id)
        sheet.value = PeopleSheet.Split
    }

    override fun dismissSheet() {
        sheet.value = null
    }

    /** The row's button: a reminder is the screen's (it needs the share sheet), the others open their pocket sheet. */
    override fun onAction(row: PersonRow) {
        when (rowAction(row)) {
            RowAction.SETTLE_TAB -> sheet.value = PeopleSheet.SettleTab(row.person.id, row.person.name, row.onTab)
            RowAction.REPAY -> sheet.value = PeopleSheet.Repay(row.person.id, row.person.name, row.iOweLoans)
            RowAction.REMIND, null -> Unit
        }
    }

    override fun reminded() {
        notice.value = PeopleNotice(NoticeKind.REMINDED)
    }

    override fun clearNotice() {
        notice.value = null
    }

    // -- settle

    /** One SPENT entry from [pocketId] for the open tab items; the notice names the shop. */
    override fun settleTab(personId: Long, name: String, pocketId: Long) = launchSafe("settle tab") {
        people.settleTab(personId, pocketId)
        sheet.value = null
        notice.value = PeopleNotice(NoticeKind.TAB_SETTLED, name)
    }

    /** Pays back every open loan the user owes [personId], each in full; [pocketId] null records only. */
    override fun repayAll(personId: Long, pocketId: Long?) = launchSafe("repay") {
        people.debtsIOwe().filter { it.person.id == personId }.forEach { people.repay(it.debt.id, it.debt.amount, pocketId) }
        sheet.value = null
        notice.value = PeopleNotice(NoticeKind.REPAID)
    }

    // -- new record

    /**
     * "سجّل مع حد": a loan either way or a tab item. [personId] null makes a new person named [newName] (a shop for the
     * tab). [text] is the reason, or the tab item's label.
     */
    override fun add(mode: AddMode, personId: Long?, newName: String?, amount: Long, text: String?, pocketId: Long?, dueInDays: Int?) = launchSafe("add") {
        val due = dueInDays?.let { LocalDate.now(clock).plusDays(it.toLong()) }
        val id = personId ?: people.addPerson(requireNotNull(newName), kind = if (mode == AddMode.TAB) PersonKind.SHOP else PersonKind.PERSON)
        val reason = text?.trim()?.ifEmpty { null }
        when (mode) {
            AddMode.THEY_OWE -> people.lend(id, amount, pocketId, reason, due)
            AddMode.I_OWE -> people.borrow(id, amount, pocketId, reason, due)
            AddMode.TAB -> people.addTabItem(id, requireNotNull(reason), amount)
        }
        sheet.value = null
    }

    // -- quick split

    override fun splitDigit(d: Int) = split.update { it.copy(amount = it.amount.digit(d)) }
    override fun splitPoint() = split.update { it.copy(amount = it.amount.point()) }
    override fun splitBackspace() = split.update { it.copy(amount = it.amount.backspace()) }
    override fun splitPocket(id: Long) = split.update { it.copy(pocketId = id) }

    /** Selects or drops a participant; a dropped one forgets the share typed for them. */
    override fun toggleParticipant(id: Long) = split.update {
        if (id in it.participants) it.copy(participants = it.participants - id, edited = it.edited - id)
        else it.copy(participants = it.participants + id)
    }

    /** The share typed over for [id]; blank goes back to the equal share. */
    override fun editShare(id: Long, text: String) = split.update { it.copy(edited = if (text.isBlank()) it.edited - id else it.edited + (id to text)) }

    /** A person added in the split sheet joins it. */
    override fun addSplitPerson(name: String) = launchSafe("add split person") {
        if (name.isBlank()) return@launchSafe
        val id = people.addPerson(name)
        split.update { it.copy(participants = it.participants + id) }
    }

    override fun confirmSplit() = launchSafe("split") {
        val d = split.value
        val shares = d.shares ?: return@launchSafe
        if (!d.canSave) return@launchSafe
        people.split(d.total, checkNotNull(d.pocketId), shares.filter { (id, v) -> id != ME && v > 0 })
        sheet.value = null
        notice.value = PeopleNotice(NoticeKind.SPLIT)
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
