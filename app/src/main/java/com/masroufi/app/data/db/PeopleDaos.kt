package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PeopleDao {
    @Insert suspend fun insertPerson(person: PersonEntity): Long
    @Update suspend fun updatePerson(person: PersonEntity)
    @Query("DELETE FROM person WHERE id = :id") suspend fun deletePerson(id: Long)
    @Query("SELECT * FROM person ORDER BY id") fun persons(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM person WHERE id = :id") suspend fun personById(id: Long): PersonEntity?
    @Query("SELECT * FROM person WHERE name = :name LIMIT 1") suspend fun personByName(name: String): PersonEntity?

    @Insert suspend fun insertDebt(debt: DebtEntity): Long
    @Update suspend fun updateDebt(debt: DebtEntity)
    @Query("DELETE FROM debt WHERE id = :id") suspend fun deleteDebt(id: Long)
    @Query("SELECT * FROM debt ORDER BY id") fun debts(): Flow<List<DebtEntity>>
    @Query("SELECT * FROM debt WHERE id = :id") suspend fun debtById(id: Long): DebtEntity?
    @Query("SELECT * FROM debt WHERE settled = 0 ORDER BY id") suspend fun openDebts(): List<DebtEntity>
    @Query("DELETE FROM debt WHERE personId = :personId") suspend fun deleteDebtsOf(personId: Long)

    /** The unsettled debt a shared subscription's payment of [amount] on [date] created. */
    @Query(
        """SELECT * FROM debt WHERE sourceRecurringId = :recurringId AND date = :date AND amount = :amount AND settled = 0
        ORDER BY id LIMIT 1"""
    )
    suspend fun shareDebt(recurringId: Long, date: LocalDate, amount: Long): DebtEntity?

    /** The money moves of a person's debts: the loan itself and every repayment that touched a pocket. */
    @Query("SELECT * FROM entry WHERE debtId IN (SELECT id FROM debt WHERE personId = :personId) ORDER BY happenedAt DESC, id DESC")
    fun entriesOf(personId: Long): Flow<List<EntryEntity>>

    /** A deleted person's debts leave; the entries that moved money keep their record, minus the link. */
    @Query("UPDATE entry SET debtId = NULL WHERE debtId IN (SELECT id FROM debt WHERE personId = :personId)")
    suspend fun unlinkEntriesOf(personId: Long)

    @Insert suspend fun insertTab(item: TabItemEntity): Long
    @Update suspend fun updateTab(item: TabItemEntity)
    @Query("DELETE FROM tab_item WHERE id = :id") suspend fun deleteTab(id: Long)
    @Query("SELECT * FROM tab_item ORDER BY id") fun tabItems(): Flow<List<TabItemEntity>>
    @Query("SELECT * FROM tab_item WHERE id = :id") suspend fun tabById(id: Long): TabItemEntity?
    @Query("SELECT * FROM tab_item WHERE personId = :personId AND settled = 0 ORDER BY id")
    suspend fun openTabOf(personId: Long): List<TabItemEntity>
    @Query("DELETE FROM tab_item WHERE personId = :personId") suspend fun deleteTabOf(personId: Long)
}

@Dao
interface DutyDao {
    @Insert suspend fun insertOccasion(occasion: OccasionEntity): Long
    @Update suspend fun updateOccasion(occasion: OccasionEntity)
    @Query("DELETE FROM occasion WHERE id = :id") suspend fun deleteOccasion(id: Long)
    @Query("SELECT * FROM occasion ORDER BY date, id") fun occasions(): Flow<List<OccasionEntity>>
    @Query("SELECT * FROM occasion ORDER BY date, id") suspend fun occasionsNow(): List<OccasionEntity>
    @Query("UPDATE occasion SET personId = NULL WHERE personId = :personId") suspend fun clearPerson(personId: Long)

    @Insert suspend fun insertGift(gift: GiftEntity): Long
    @Query("DELETE FROM gift WHERE id = :id") suspend fun deleteGift(id: Long)
    @Query("DELETE FROM gift WHERE occasionId = :occasionId") suspend fun deleteGiftsOf(occasionId: Long)
    @Query("DELETE FROM gift WHERE personId = :personId") suspend fun deleteGiftsFrom(personId: Long)
    @Query("SELECT * FROM gift ORDER BY id") fun gifts(): Flow<List<GiftEntity>>
    @Query("SELECT * FROM gift ORDER BY id") suspend fun giftsNow(): List<GiftEntity>
}

@Dao
interface GameyaDao {
    @Insert suspend fun insert(gameya: GameyaEntity): Long
    @Update suspend fun update(gameya: GameyaEntity)
    @Query("SELECT * FROM gameya ORDER BY id") fun all(): Flow<List<GameyaEntity>>
    @Query("SELECT * FROM gameya WHERE id = :id") suspend fun byId(id: Long): GameyaEntity?
    @Query("SELECT COUNT(*) FROM gameya WHERE archived = 0") suspend fun activeCount(): Int

    @Insert suspend fun insertMember(member: GameyaMemberEntity): Long
    @Update suspend fun updateMember(member: GameyaMemberEntity)
    @Query("SELECT * FROM gameya_member ORDER BY gameyaId, turn") fun members(): Flow<List<GameyaMemberEntity>>
    @Query("SELECT * FROM gameya_member WHERE id = :id") suspend fun memberById(id: Long): GameyaMemberEntity?
    @Query("SELECT * FROM gameya_member WHERE gameyaId = :gameyaId ORDER BY turn") suspend fun membersOf(gameyaId: Long): List<GameyaMemberEntity>

    @Insert suspend fun insertPayment(payment: GameyaPaymentEntity): Long
    @Update suspend fun updatePayment(payment: GameyaPaymentEntity)
    @Query("SELECT * FROM gameya_payment ORDER BY id") fun payments(): Flow<List<GameyaPaymentEntity>>
    @Query("SELECT * FROM gameya_payment WHERE gameyaId = :gameyaId AND memberId IS :memberId AND month = :month LIMIT 1")
    suspend fun payment(gameyaId: Long, memberId: Long?, month: Int): GameyaPaymentEntity?

    @Insert suspend fun insertPlan(row: GameyaPlanEntity): Long
    @Update suspend fun updatePlan(row: GameyaPlanEntity)
    @Query("DELETE FROM gameya_plan WHERE id = :id") suspend fun deletePlan(id: Long)
    @Query("SELECT * FROM gameya_plan ORDER BY id") fun plans(): Flow<List<GameyaPlanEntity>>
}
