package com.masroufi.app.backup

import com.masroufi.app.data.model.Allocation
import com.masroufi.app.data.model.Budget
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.Debt
import com.masroufi.app.data.model.DebtPayment
import com.masroufi.app.data.model.Gameya
import com.masroufi.app.data.model.GameyaMember
import com.masroufi.app.data.model.GameyaPayment
import com.masroufi.app.data.model.GameyaPayout
import com.masroufi.app.data.model.Goal
import com.masroufi.app.data.model.GoalEntry
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.SplitRule
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.data.model.Wallet
import kotlinx.serialization.Serializable

/** Full snapshot of user data, including soft-deleted rows so deletions propagate through sync. Receipts are not included. */
@Serializable
data class BackupPayload(
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: Long,
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalEntries: List<GoalEntry> = emptyList(),
    val recurring: List<RecurringRule> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val debtPayments: List<DebtPayment> = emptyList(),
    val gameyas: List<Gameya> = emptyList(),
    val gameyaMembers: List<GameyaMember> = emptyList(),
    val gameyaPayments: List<GameyaPayment> = emptyList(),
    val gameyaPayouts: List<GameyaPayout> = emptyList(),
    val splitRules: List<SplitRule> = emptyList(),
    val allocations: List<Allocation> = emptyList(),
) {
    val transactionCount: Int get() = transactions.count { !it.deleted }

    companion object {
        const val FORMAT_VERSION = 1
    }
}

data class MergeOutcome(val payload: BackupPayload, val conflicts: Int, val changedLocally: Boolean, val changedRemotely: Boolean)

/**
 * Merges two snapshots row by row. Rows are matched by id (so the same transaction never appears twice) and the
 * newer updatedAt wins. A row edited on both sides since the last sync counts as a conflict; the newer edit is kept
 * and the count is reported to the user.
 */
object SyncMerger {
    private class Side<T>(val merged: List<T>, val conflicts: Int, val localChanged: Boolean, val remoteChanged: Boolean)

    private fun <T> merge(local: List<T>, remote: List<T>, id: (T) -> String, updated: (T) -> Long, lastSync: Long?): Side<T> {
        val localById = local.associateBy(id)
        val remoteById = remote.associateBy(id)
        var conflicts = 0
        var localChanged = false
        var remoteChanged = false
        val merged = (localById.keys + remoteById.keys).map { key ->
            val l = localById[key]
            val r = remoteById[key]
            when {
                l == null -> { localChanged = true; r!! }
                r == null -> { remoteChanged = true; l }
                l == r -> l
                else -> {
                    if (lastSync != null && updated(l) > lastSync && updated(r) > lastSync) conflicts++
                    if (updated(r) > updated(l)) { localChanged = true; r } else { remoteChanged = true; l }
                }
            }
        }
        return Side(merged, conflicts, localChanged, remoteChanged)
    }

    fun merge(local: BackupPayload, remote: BackupPayload, lastSync: Long?, now: Long): MergeOutcome {
        var conflicts = 0
        var localChanged = false
        var remoteChanged = false
        fun <T> m(l: List<T>, r: List<T>, id: (T) -> String, up: (T) -> Long): List<T> {
            val side = merge(l, r, id, up, lastSync)
            conflicts += side.conflicts
            localChanged = localChanged || side.localChanged
            remoteChanged = remoteChanged || side.remoteChanged
            return side.merged
        }
        val payload = BackupPayload(
            exportedAt = now,
            wallets = m(local.wallets, remote.wallets, { it.id }, { it.updatedAt }),
            categories = m(local.categories, remote.categories, { it.id }, { it.updatedAt }),
            transactions = m(local.transactions, remote.transactions, { it.id }, { it.updatedAt }),
            budgets = m(local.budgets, remote.budgets, { it.id }, { it.updatedAt }),
            goals = m(local.goals, remote.goals, { it.id }, { it.updatedAt }),
            goalEntries = m(local.goalEntries, remote.goalEntries, { it.id }, { it.updatedAt }),
            recurring = m(local.recurring, remote.recurring, { it.id }, { it.updatedAt }),
            debts = m(local.debts, remote.debts, { it.id }, { it.updatedAt }),
            debtPayments = m(local.debtPayments, remote.debtPayments, { it.id }, { it.updatedAt }),
            gameyas = m(local.gameyas, remote.gameyas, { it.id }, { it.updatedAt }),
            gameyaMembers = m(local.gameyaMembers, remote.gameyaMembers, { it.id }, { it.updatedAt }),
            gameyaPayments = m(local.gameyaPayments, remote.gameyaPayments, { it.id }, { it.updatedAt }),
            gameyaPayouts = m(local.gameyaPayouts, remote.gameyaPayouts, { it.id }, { it.updatedAt }),
            splitRules = m(local.splitRules, remote.splitRules, { it.id }, { it.updatedAt }),
            allocations = m(local.allocations, remote.allocations, { it.id }, { it.updatedAt }),
        )
        return MergeOutcome(payload, conflicts, localChanged, remoteChanged)
    }
}
