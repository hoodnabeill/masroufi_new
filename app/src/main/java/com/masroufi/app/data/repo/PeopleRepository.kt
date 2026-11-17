package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.DebtDirection
import com.masroufi.app.data.db.DebtEntity
import com.masroufi.app.data.db.DutyDao
import com.masroufi.app.data.db.EntryEntity
import com.masroufi.app.data.db.PeopleDao
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.db.PersonKind
import com.masroufi.app.data.db.TabItemEntity
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.domain.EntryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** الكل / سلف / النوتة / الواجب. */
enum class PeopleFilter { ALL, LOANS, TAB, DUTY }

/** [theyOwe] ليك (from [theyOweCount] people), [iOwe] عليك (loans and the tab), of which [onTab] is on the tab. */
data class PeopleTotals(val theyOwe: Long, val theyOweCount: Int, val iOwe: Long, val onTab: Long)

/** Open amounts only; [balance] = what they owe me − what I owe them (loans and the tab). */
data class PersonRow(
    val person: PersonEntity,
    val owedToMe: Long,
    val iOweLoans: Long,
    val onTab: Long,
    val openDebts: Int,
    val tabItems: Int,
    val hasGifts: Boolean,
    /** The oldest open debt or tab item, and the soonest due date among open debts. */
    val since: LocalDate?,
    val nextDue: LocalDate?,
) {
    val balance: Long get() = owedToMe - iOweLoans - onTab
    val hasOpen: Boolean get() = openDebts > 0 || tabItems > 0
}

data class PersonDetail(val row: PersonRow, val debts: List<DebtEntity>, val tabItems: List<TabItemEntity>)

data class DebtWithPerson(val debt: DebtEntity, val person: PersonEntity)

data class SplitResult(val ownEntryId: Long?, val debtEntryId: Long, val debtIds: List<Long>)

internal fun personRows(
    persons: List<PersonEntity>,
    debts: List<DebtEntity>,
    tab: List<TabItemEntity>,
    giftPeople: Set<Long>,
): List<PersonRow> = persons.map { p ->
    val open = debts.filter { it.personId == p.id && !it.settled }
    val items = tab.filter { it.personId == p.id && !it.settled }
    PersonRow(
        person = p,
        owedToMe = open.filter { it.direction == DebtDirection.THEY_OWE }.sumOf { it.amount },
        iOweLoans = open.filter { it.direction == DebtDirection.I_OWE }.sumOf { it.amount },
        onTab = items.sumOf { it.amount },
        openDebts = open.size,
        tabItems = items.size,
        hasGifts = p.id in giftPeople,
        since = (open.map { it.date } + items.map { it.date }).minOrNull(),
        nextDue = open.mapNotNull { it.dueDate }.minOrNull(),
    )
}

@Singleton
class PeopleRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val dao: PeopleDao,
    private val dutyDao: DutyDao,
    private val entries: EntryRepository,
    private val clock: Clock,
) {
    private fun rows(): Flow<List<PersonRow>> =
        combine(dao.persons(), dao.debts(), dao.tabItems(), dutyDao.gifts()) { persons, debts, tab, gifts ->
            personRows(persons, debts, tab, gifts.map { it.personId }.toSet())
        }

    fun observeTotals(): Flow<PeopleTotals> = rows().map { rows ->
        PeopleTotals(
            theyOwe = rows.sumOf { it.owedToMe },
            theyOweCount = rows.count { it.owedToMe > 0 },
            iOwe = rows.sumOf { it.iOweLoans + it.onTab },
            onTab = rows.sumOf { it.onTab },
        )
    }

    /** ALL: anyone with something open; LOANS: open debts; TAB: open tab items; DUTY: anyone in the duty ledger. Biggest balance first. */
    fun observeList(filter: PeopleFilter): Flow<List<PersonRow>> = rows().map { rows ->
        rows.filter {
            when (filter) {
                PeopleFilter.ALL -> it.hasOpen
                PeopleFilter.LOANS -> it.openDebts > 0
                PeopleFilter.TAB -> it.tabItems > 0
                PeopleFilter.DUTY -> it.hasGifts
            }
        }.sortedWith(compareByDescending<PersonRow> { abs(it.balance) }.thenBy { it.person.id })
    }

    fun observePersons(): Flow<List<PersonEntity>> = dao.persons()

    suspend fun personByName(name: String): PersonEntity? = dao.personByName(name)

    /** The money moves behind a person's debts, newest first. */
    fun observeDebtEntries(personId: Long): Flow<List<EntryEntity>> = dao.entriesOf(personId)

    fun observeDetail(personId: Long): Flow<PersonDetail?> =
        combine(rows(), dao.debts(), dao.tabItems()) { rows, debts, tab ->
            rows.firstOrNull { it.person.id == personId }?.let { row ->
                PersonDetail(row, debts.filter { it.personId == personId }.sortedByDescending { it.id }, tab.filter { it.personId == personId }.sortedByDescending { it.id })
            }
        }

    // -- people

    suspend fun addPerson(name: String, phone: String? = null, kind: PersonKind = PersonKind.PERSON): Long {
        require(name.isNotBlank()) { "name must not be blank" }
        return dao.insertPerson(PersonEntity(name = name.trim(), phone = phone?.trim()?.ifEmpty { null }, kind = kind, updatedAt = clock.millis()))
    }

    suspend fun updatePerson(id: Long, name: String, phone: String?, kind: PersonKind) {
        require(name.isNotBlank()) { "name must not be blank" }
        val p = checkNotNull(dao.personById(id)) { "no person $id" }
        dao.updatePerson(p.copy(name = name.trim(), phone = phone?.trim()?.ifEmpty { null }, kind = kind, updatedAt = clock.millis()))
    }

    /** The person goes with their debts, tab items and gifts; money entries keep their record. */
    suspend fun deletePerson(id: Long) = db.withTransaction {
        dao.unlinkEntriesOf(id)
        dao.deleteDebtsOf(id)
        dao.deleteTabOf(id)
        dutyDao.deleteGiftsFrom(id)
        dutyDao.clearPerson(id)
        dao.deletePerson(id)
    }

    // -- loans (decision 1)

    /** Lend: they owe me; [pocketId] null records the debt without moving money. */
    suspend fun lend(personId: Long, amount: Long, pocketId: Long?, reason: String? = null, dueDate: LocalDate? = null): Long =
        openDebt(DebtDirection.THEY_OWE, personId, amount, pocketId, reason, dueDate)

    /** Borrow: I owe them. */
    suspend fun borrow(personId: Long, amount: Long, pocketId: Long?, reason: String? = null, dueDate: LocalDate? = null): Long =
        openDebt(DebtDirection.I_OWE, personId, amount, pocketId, reason, dueDate)

    private suspend fun openDebt(direction: DebtDirection, personId: Long, amount: Long, pocketId: Long?, reason: String?, dueDate: LocalDate?): Long {
        require(amount > 0) { "amount must be > 0" }
        return db.withTransaction {
            val person = checkNotNull(dao.personById(personId)) { "no person $personId" }
            val id = dao.insertDebt(
                DebtEntity(personId = personId, direction = direction, amount = amount, reason = reason, date = LocalDate.now(clock), dueDate = dueDate),
            )
            if (pocketId != null) logMoney(pocketId, if (direction == DebtDirection.THEY_OWE) -amount else amount, person, id)
            id
        }
    }

    /**
     * Pays back [amount] of a debt: they repay me (money in) or I repay them (money out) from [pocketId], or with no
     * pocket just the record. A part lowers what is open; all of it settles the debt.
     */
    suspend fun repay(debtId: Long, amount: Long, pocketId: Long?) {
        db.withTransaction {
            val debt = checkNotNull(dao.debtById(debtId)) { "no debt $debtId" }
            require(!debt.settled) { "debt $debtId is already settled" }
            require(amount in 1..debt.amount) { "amount must be 1..${debt.amount}" }
            dao.updateDebt(if (amount == debt.amount) debt.copy(settled = true) else debt.copy(amount = debt.amount - amount))
            if (pocketId != null) {
                val person = checkNotNull(dao.personById(debt.personId))
                logMoney(pocketId, if (debt.direction == DebtDirection.THEY_OWE) amount else -amount, person, debtId)
            }
        }
    }

    private suspend fun logMoney(pocketId: Long, signed: Long, person: PersonEntity, debtId: Long?) {
        entries.log(
            NewEntry(
                kind = EntryKind.DEBT, amount = signed, pocketId = pocketId, happenedAt = clock.millis(),
                note = person.name.take(NOTE_MAX_CHARS), debtId = debtId,
            ),
        )
    }

    /** Open debts that are due today or overdue, oldest due first. */
    suspend fun dueOrOverdue(): List<DebtWithPerson> {
        val today = LocalDate.now(clock)
        return withPeople(dao.openDebts().filter { d -> d.dueDate?.let { !it.isAfter(today) } == true }.sortedBy { it.dueDate })
    }

    /** What I owe people (the tab is not here): the payday ritual's step. */
    suspend fun debtsIOwe(): List<DebtWithPerson> = withPeople(dao.openDebts().filter { it.direction == DebtDirection.I_OWE })

    private suspend fun withPeople(debts: List<DebtEntity>): List<DebtWithPerson> =
        debts.mapNotNull { d -> dao.personById(d.personId)?.let { DebtWithPerson(d, it) } }

    // -- quick split (decision 2)

    /**
     * I paid [total] from [pocketId]; [shares] are the others' parts (person -> piastres). My own part is the rest: one
     * SPENT entry that punches today (none when it is 0), the others' parts one DEBT entry (-) and a debt "ليك" each.
     * That DEBT entry covers several debts, so it names none.
     */
    suspend fun split(total: Long, pocketId: Long, shares: Map<Long, Long>, categoryId: Long? = null, note: String? = null): SplitResult {
        require(shares.isNotEmpty()) { "pick at least one person" }
        require(shares.values.all { it > 0 }) { "every share must be > 0" }
        val others = shares.values.sum()
        require(others <= total) { "the shares cannot pass the total" }
        val own = total - others
        return db.withTransaction {
            val today = LocalDate.now(clock)
            val now = clock.millis()
            shares.keys.forEach { checkNotNull(dao.personById(it)) { "no person $it" } }
            val ownId = if (own > 0) {
                entries.log(NewEntry(kind = EntryKind.SPENT, amount = own, pocketId = pocketId, categoryId = categoryId, note = note, happenedAt = now))
            } else {
                null
            }
            val debtEntry = entries.log(NewEntry(kind = EntryKind.DEBT, amount = -others, pocketId = pocketId, note = note, happenedAt = now))
            val ids = shares.map { (person, amount) ->
                dao.insertDebt(DebtEntity(personId = person, direction = DebtDirection.THEY_OWE, amount = amount, reason = note, date = today))
            }
            SplitResult(ownId, debtEntry, ids)
        }
    }

    // -- the tab (decision 3)

    suspend fun addTabItem(shopId: Long, label: String, amount: Long, date: LocalDate = LocalDate.now(clock)): Long {
        require(amount > 0) { "amount must be > 0" }
        require(label.isNotBlank()) { "label must not be blank" }
        val shop = checkNotNull(dao.personById(shopId)) { "no person $shopId" }
        require(shop.kind == PersonKind.SHOP) { "a tab belongs to a shop" }
        return dao.insertTab(TabItemEntity(personId = shopId, label = label.trim(), amount = amount, date = date))
    }

    /** Removes an unsettled item (a mistake); a settled one is history. */
    suspend fun removeTabItem(id: Long) {
        val item = dao.tabById(id) ?: return
        require(!item.settled) { "a settled item stays" }
        dao.deleteTab(id)
    }

    /** One SPENT entry for the open items' total from [pocketId] (it punches today); the items are marked settled. Returns the entry id. */
    suspend fun settleTab(shopId: Long, pocketId: Long, itemIds: Set<Long>? = null): Long = db.withTransaction {
        val shop = checkNotNull(dao.personById(shopId)) { "no person $shopId" }
        val items = dao.openTabOf(shopId).filter { itemIds == null || it.id in itemIds }
        require(items.isNotEmpty()) { "nothing open on the tab" }
        val entryId = entries.log(
            NewEntry(kind = EntryKind.SPENT, amount = items.sumOf { it.amount }, pocketId = pocketId, note = shop.name.take(NOTE_MAX_CHARS), happenedAt = clock.millis()),
        )
        items.forEach { dao.updateTab(it.copy(settled = true, settledEntryId = entryId)) }
        entryId
    }
}
