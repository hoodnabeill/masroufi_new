package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.newId
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.model.Debt
import com.masroufi.app.data.model.DebtPayment
import com.masroufi.app.data.model.DebtWithPaid
import com.masroufi.app.data.model.Gameya
import com.masroufi.app.data.model.GameyaMember
import com.masroufi.app.data.model.GameyaPayment
import com.masroufi.app.data.model.GameyaPayout
import com.masroufi.app.domain.DebtMath
import com.masroufi.app.domain.GameyaMath
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebtRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.debtDao()

    fun observeDebts(): Flow<List<DebtWithPaid>> = dao.observeWithPaid()
    fun observeDebt(id: String): Flow<DebtWithPaid?> = dao.observeOne(id)
    fun observePayments(debtId: String): Flow<List<DebtPayment>> = dao.observePayments(debtId)
    suspend fun get(id: String) = dao.get(id)

    suspend fun save(debt: Debt) = db.withTransaction {
        ensure(debt.personName.isNotBlank(), FinanceError.NAME_REQUIRED)
        ensure(debt.originalMinor > 0, FinanceError.AMOUNT_REQUIRED)
        // The original amount can't drop below what has already been repaid.
        ensure(debt.originalMinor >= dao.paid(debt.id), FinanceError.PAYMENT_EXCEEDS_REMAINING)
        dao.upsert(debt.copy(personName = debt.personName.trim(), note = debt.note.trim(), updatedAt = clock.millis()))
    }

    /** Checked inside the transaction so two quick taps can't over-repay a debt. */
    suspend fun addPayment(debtId: String, amountMinor: Long, note: String) = db.withTransaction {
        val debt = dao.get(debtId) ?: throw FinanceException(FinanceError.NOT_FOUND)
        ensure(DebtMath.isValidPayment(amountMinor, debt.originalMinor, dao.paid(debtId)), FinanceError.PAYMENT_EXCEEDS_REMAINING)
        val now = clock.millis()
        dao.upsertPayment(DebtPayment(newId(), debtId, amountMinor, now, note.trim(), now))
    }

    suspend fun deletePayment(payment: DebtPayment) {
        dao.upsertPayment(payment.copy(deleted = true, updatedAt = clock.millis()))
    }

    suspend fun delete(id: String) {
        val debt = dao.get(id) ?: return
        dao.upsert(debt.copy(deleted = true, updatedAt = clock.millis()))
    }
}

data class MemberDraft(val id: String? = null, val name: String = "")

@Singleton
class GameyaRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.gameyaDao()

    fun observeAll(): Flow<List<Gameya>> = dao.observeAll()
    fun observe(id: String): Flow<Gameya?> = dao.observe(id)
    fun observeMembers(id: String): Flow<List<GameyaMember>> = dao.observeMembers(id)
    fun observePayments(id: String): Flow<List<GameyaPayment>> = dao.observePayments(id)
    fun observePayouts(id: String): Flow<List<GameyaPayout>> = dao.observePayouts(id)
    fun observeAllMembers() = dao.observeAllMembers()
    fun observeAllPayments() = dao.observeAllPayments()
    fun observeAllPayouts() = dao.observeAllPayouts()
    suspend fun get(id: String) = dao.get(id)
    suspend fun members(id: String) = dao.members(id)

    /**
     * List order defines the payout order. Members keep their ids when renamed or reordered, so recorded
     * payments stay with the right person; members removed from the list are soft-deleted.
     */
    suspend fun save(gameya: Gameya, members: List<MemberDraft>, meIndex: Int?) = db.withTransaction {
        ensure(gameya.name.isNotBlank(), FinanceError.NAME_REQUIRED)
        ensure(gameya.contributionMinor > 0, FinanceError.AMOUNT_REQUIRED)
        ensure(members.size >= 2 && members.all { it.name.isNotBlank() }, FinanceError.MEMBERS_REQUIRED)
        val now = clock.millis()
        dao.upsert(gameya.copy(name = gameya.name.trim(), updatedAt = now))
        val existing = dao.members(gameya.id)
        val updated = members.mapIndexed { index, draft ->
            GameyaMember(
                id = draft.id ?: newId(),
                gameyaId = gameya.id,
                name = draft.name.trim(),
                payoutOrder = index + 1,
                isMe = index == meIndex,
                updatedAt = now,
            )
        }
        val keptIds = updated.map { it.id }.toSet()
        val removed = existing.filter { it.id !in keptIds }.map { it.copy(deleted = true, updatedAt = now) }
        dao.upsertMembers(updated + removed)
    }

    suspend fun setPaid(gameya: Gameya, memberId: String, round: Int, paid: Boolean) {
        val id = GameyaMath.paymentId(gameya.id, memberId, round)
        val now = clock.millis()
        val current = dao.payment(id)
        dao.upsertPayment(
            (current ?: GameyaPayment(id, gameya.id, memberId, round, gameya.contributionMinor, now, now))
                .copy(deleted = !paid, paidAt = if (paid) now else current?.paidAt ?: now, updatedAt = now),
        )
    }

    suspend fun setPayoutReceived(gameya: Gameya, round: Int, potMinor: Long, received: Boolean) {
        val id = GameyaMath.payoutId(gameya.id, round)
        val now = clock.millis()
        val current = dao.payout(id)
        dao.upsertPayout(
            (current ?: GameyaPayout(id, gameya.id, round, potMinor, now, now))
                .copy(deleted = !received, amountMinor = potMinor, receivedAt = if (received) now else current?.receivedAt ?: now, updatedAt = now),
        )
    }

    suspend fun delete(id: String) {
        val gameya = dao.get(id) ?: return
        dao.upsert(gameya.copy(deleted = true, updatedAt = clock.millis()))
    }
}
