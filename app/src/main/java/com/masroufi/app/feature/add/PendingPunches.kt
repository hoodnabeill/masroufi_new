package com.masroufi.app.feature.add

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** A punch shown on the ticket before storage has it; [punched] is in piastres. */
data class Pending(val id: Long, val punched: Long)

/** Optimistic punches: Home adds them to today's spent and holes until the stored row has them. */
@Singleton
class PendingPunches @Inject constructor() {
    private val ids = AtomicLong()
    private val state = MutableStateFlow<List<Pending>>(emptyList())
    val pending: StateFlow<List<Pending>> = state.asStateFlow()

    /** Stores [p] under a fresh id (p.id is ignored) and returns that id. */
    fun add(p: Pending): Long {
        val id = ids.incrementAndGet()
        state.update { it + p.copy(id = id) }
        return id
    }

    fun remove(id: Long) = state.update { list -> list.filterNot { it.id == id } }
}
