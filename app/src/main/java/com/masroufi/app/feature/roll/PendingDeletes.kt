package com.masroufi.app.feature.roll

import kotlinx.coroutines.flow.combine
import android.util.Log
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.repo.EntryRepository
import com.masroufi.app.di.AppScope
import com.masroufi.app.domain.EntryForDay
import com.masroufi.app.domain.punched
import com.masroufi.app.domain.punches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What can wait its 5 s: one kind per delete the user can make. */
enum class DeleteKind { ENTRY, FIXED_COST, SUBSCRIPTION, INSTALLMENT, JOB, SEASON_ITEM, PERSON, OCCASION, GIFT, PLAN_ROW, CHILD, GAMEYA, ENVELOPE }

data class DeleteKey(val kind: DeleteKind, val id: Long)

/**
 * Something the user deleted that storage still has. For an entry, [punched] is what it took from its day's ticket (piastres,
 * 0 if none) and [day] its day; other kinds leave both at their defaults.
 */
data class PendingDelete(
    val id: Long,
    val punched: Long = 0,
    val day: LocalDate = LocalDate.EPOCH,
    val committing: Boolean = false,
    val kind: DeleteKind = DeleteKind.ENTRY,
) {
    val key get() = DeleteKey(kind, id)
}

/** The list without what the user deleted and storage still has. */
fun <T> Flow<List<T>>.withoutDeleted(deletes: PendingDeletes, kind: DeleteKind, id: (T) -> Long): Flow<List<T>> =
    combine(deletes.hidden(kind)) { list, hidden -> if (hidden.isEmpty()) list else list.filter { id(it) !in hidden } }

/** What the entry took from its day's ticket: spends and move fees from a counting pocket, else 0. */
fun EntryRow.ticketPunch(): Long =
    EntryForDay(kind, amount, roundUp, fee, countsInTicket, recurringId).let { if (it.punches) it.punched else 0L }

private const val UNDO_MS = 5_000L
private const val CATCH_UP_MS = 300L
private const val TAG = "PendingDeletes"

/**
 * Deletes wait here for 5 s (10-PERFORMANCE), keyed by kind + id: the screens hide them ([hidden]) and show the undo bar, but
 * nothing is written until the time is up. [undo] before that restores everything. The 5 s runs on the app scope, so leaving a
 * screen does not cancel it; only [flush] (the roll, which leaves its entries written now) cuts it short. Process death before
 * the 5 s simply keeps the item: nothing was written. shortcut: a committed delete stays listed for 300 ms so the row does not
 * flash back before Room re-emits; Home can show a dip in that window.
 */
@Singleton
class PendingDeletes @Inject constructor(
    private val entries: EntryRepository,
    @AppScope private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<Map<DeleteKey, PendingDelete>>(emptyMap())
    private val entryState = MutableStateFlow<Map<Long, PendingDelete>>(emptyMap())
    private val jobs = HashMap<DeleteKey, Job>()
    private val writes = HashMap<DeleteKey, suspend () -> Unit>()
    private val done = MutableSharedFlow<DeleteKind>(extraBufferCapacity = 4)

    /** The entries waiting (what the roll and Home leave out), by entry id. */
    val pending: StateFlow<Map<Long, PendingDelete>> = entryState.asStateFlow()

    /** One event per delete that has really been written (the screen plays the Heavy haptic). */
    val committed: SharedFlow<DeleteKind> = done.asSharedFlow()

    /** Ids of [kind] the user deleted that storage still has: the list leaves them out. */
    fun hidden(kind: DeleteKind): Flow<Set<Long>> = state
        .map { m -> m.keys.filter { it.kind == kind }.map { it.id }.toSet() }
        .distinctUntilChanged()

    /** True while a delete (of an entry, or of anything else) still has its undo window open. */
    fun waiting(entries: Boolean): Flow<Boolean> = state.map { m -> m.values.any { !it.committing && (it.kind == DeleteKind.ENTRY) == entries } }
        .distinctUntilChanged()

    /** The one place the waiting set changes, so [pending] (entries only) always matches it. */
    @Synchronized
    private fun mutate(change: (Map<DeleteKey, PendingDelete>) -> Map<DeleteKey, PendingDelete>) {
        val next = change(state.value)
        state.value = next
        entryState.value = next.values.filter { it.kind == DeleteKind.ENTRY }.associateBy { it.id }
    }

    private fun entryWrite(id: Long): suspend () -> Unit = { entries.delete(id) }

    /** [write] is the real delete (the repository's own cascade); an entry needs none. */
    @Synchronized
    fun schedule(p: PendingDelete, write: (suspend () -> Unit)? = null) {
        val key = p.key
        if (key in state.value) return
        writes[key] = write ?: entryWrite(p.id)
        mutate { it + (key to p) }
        jobs[key] = scope.launch {
            delay(UNDO_MS)
            commit(key)
        }
    }

    /** Puts the entry back, unless its delete has already started. Returns whether it did. */
    fun undo(id: Long): Boolean = undo(DeleteKey(DeleteKind.ENTRY, id))

    @Synchronized
    fun undo(key: DeleteKey): Boolean {
        if (state.value[key]?.committing != false) return false
        mutate { it - key }
        jobs.remove(key)?.cancel()
        writes.remove(key)
        return true
    }

    /** The bar's "رجّعها": the newest waiting delete of entries ([entries] true) or of anything else comes back. */
    @Synchronized
    fun undoNewest(entries: Boolean): Boolean {
        val p = state.value.values.lastOrNull { !it.committing && (it.kind == DeleteKind.ENTRY) == entries } ?: return false
        return undo(p.key)
    }

    /** The screen is leaving: every waiting delete (of [kind], or all) is written now. True if there were any. */
    @Synchronized
    fun flush(kind: DeleteKind? = null): Boolean {
        var any = false
        for (key in jobs.keys.toList()) {
            if (kind != null && key.kind != kind) continue
            if (state.value[key]?.committing != false) continue // already being written
            any = true
            jobs.remove(key)?.cancel()
            scope.launch { commit(key) }
        }
        return any
    }

    private suspend fun commit(key: DeleteKey) {
        // The one step that closes the undo window: after it undo() says no.
        var ok = false
        mutate { m ->
            val p = m[key]
            ok = p != null && !p.committing
            if (ok) m + (key to p!!.copy(committing = true)) else m
        }
        if (!ok) return
        try {
            synchronized(this) { writes[key] }?.invoke()
            done.tryEmit(key.kind)
            delay(CATCH_UP_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "delete failed", e) // the row comes back; nothing was removed
        } finally {
            synchronized(this) { jobs.remove(key); writes.remove(key) }
            mutate { it - key }
        }
    }
}
