package com.masroufi.app.feature.add

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A punch shown on the ticket before storage has it; [punched] is in piastres. [expectedHoles] is today's stored hole
 * count once it is stored: Home stops counting it from then on (Int.MAX_VALUE: count it until it is removed).
 */
data class Pending(val id: Long, val punched: Long, val expectedHoles: Int = Int.MAX_VALUE)

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

    /** Drops the punches a stored row with [storedHoles] holes already shows. */
    fun prune(storedHoles: Int) {
        if (state.value.any { it.expectedHoles <= storedHoles }) state.update { list -> list.filter { it.expectedHoles > storedHoles } }
    }
}
