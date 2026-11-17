package com.masroufi.app.feature.roll

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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** An entry the user deleted that storage still has; [punched] is what it took from its day's ticket (piastres, 0 if none). */
data class PendingDelete(val id: Long, val punched: Long, val day: LocalDate, val committing: Boolean = false)

/** What the entry took from its day's ticket: spends and move fees from a counting pocket, else 0. */
fun EntryRow.ticketPunch(): Long =
    EntryForDay(kind, amount, roundUp, fee, countsInTicket, recurringId).let { if (it.punches) it.punched else 0L }

private const val UNDO_MS = 5_000L
private const val CATCH_UP_MS = 300L
private const val TAG = "PendingDeletes"

/**
 * Deletes wait here for 5 s (10-PERFORMANCE): the roll hides them and Home leaves them out of its numbers, but nothing is
 * written until the time is up. [undo] before that restores everything. shortcut: a committed delete stays listed for
 * 300 ms so the row does not flash back before Room re-emits; Home can show a dip in that window.
 */
@Singleton
class PendingDeletes @Inject constructor(
    private val entries: EntryRepository,
    @AppScope private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<Map<Long, PendingDelete>>(emptyMap())
    val pending: StateFlow<Map<Long, PendingDelete>> = state.asStateFlow()
    private val jobs = HashMap<Long, Job>()
    private val done = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** One event per delete that has really been written (the roll plays the Heavy haptic). */
    val committed: SharedFlow<Unit> = done.asSharedFlow()

    @Synchronized
    fun schedule(p: PendingDelete) {
        if (p.id in state.value) return
        state.update { it + (p.id to p) }
        jobs[p.id] = scope.launch {
            delay(UNDO_MS)
            commit(p.id)
        }
    }

    /** Puts the entry back, unless its delete has already started. Returns whether it did. */
    @Synchronized
    fun undo(id: Long): Boolean {
        if (state.value[id]?.committing != false) return false
        state.update { it - id }
        jobs.remove(id)?.cancel()
        return true
    }

    /** The screen is leaving: every waiting delete is written now. */
    @Synchronized
    fun flush() {
        for (id in jobs.keys.toList()) {
            if (state.value[id]?.committing != false) continue // already being written
            jobs.remove(id)?.cancel()
            scope.launch { commit(id) }
        }
    }

    private suspend fun commit(id: Long) {
        // The one step that closes the undo window: after it undo() says no.
        var ok = false
        state.update { m ->
            val p = m[id]
            ok = p != null && !p.committing
            if (ok) m + (id to p!!.copy(committing = true)) else m
        }
        if (!ok) return
        try {
            entries.delete(id)
            done.tryEmit(Unit)
            delay(CATCH_UP_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "delete failed", e) // the row comes back; nothing was removed
        } finally {
            synchronized(this) { jobs.remove(id) }
            state.update { it - id }
        }
    }
}
