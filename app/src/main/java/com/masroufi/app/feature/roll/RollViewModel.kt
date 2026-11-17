package com.masroufi.app.feature.roll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.PRIMARY_CURRENCY
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.repo.RollDay
import com.masroufi.app.data.repo.RollFilter
import com.masroufi.app.data.repo.RollMonth
import com.masroufi.app.data.repo.RollRepository
import com.masroufi.app.data.repo.RollSummary
import com.masroufi.app.data.repo.punched
import com.masroufi.app.domain.EntryKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * What the roll shows. [loading] keeps the previous [days] / [results] until the new ones arrive (the screen decides
 * when a skeleton is worth showing). [results] is non-null while a search query is active.
 */
data class RollUi(
    val today: LocalDate,
    val month: YearMonth,
    val filter: RollFilter = RollFilter.ALL,
    val searching: Boolean = false,
    val query: String = "",
    val months: List<YearMonth> = emptyList(),
    val loading: Boolean = true,
    val summary: RollSummary = RollSummary(0, 0, 0, 0),
    val days: List<RollDay> = emptyList(),
    val results: List<EntryRow>? = null,
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    /** A delete is waiting its 5 s: show the undo bar. */
    val undoVisible: Boolean = false,
)

private sealed interface Source {
    data class Month(val data: RollMonth) : Source
    data class Results(val rows: List<EntryRow>) : Source
}

private data class Frame(
    val month: YearMonth,
    val filter: RollFilter,
    val query: String?,
    val source: Source?,
    val months: List<YearMonth>,
    val categories: Map<Long, CategoryEntity>,
    val deletes: Map<Long, PendingDelete>,
)

@HiltViewModel
class RollViewModel @Inject constructor(
    private val repo: RollRepository,
    categoryDao: CategoryDao,
    private val pendingDeletes: PendingDeletes,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val month = MutableStateFlow(YearMonth.from(today))
    private val filter = MutableStateFlow(RollFilter.ALL)
    /** null = search closed; "" = open, nothing typed. */
    private val query = MutableStateFlow<String?>(null)

    /** A delete has really been written: the screen plays the Heavy haptic. */
    val committed: SharedFlow<Unit> get() = pendingDeletes.committed

    private val params = combine(month, filter, query, ::Triple).distinctUntilChanged()

    private val source: Flow<Source?> = params.flatMapLatest { (m, f, q) ->
        val data: Flow<Source> = if (q.isNullOrBlank()) repo.month(m, f).map { Source.Month(it) } else repo.search(q).map { Source.Results(it) }
        flow {
            emit(null) // loading
            emitAll(data)
        }
    }

    private val months = repo.months().map { (it + YearMonth.from(today)).distinct().sortedDescending() }
    private val categories = categoryDao.all().map { list -> list.associateBy { it.id } }

    val state: StateFlow<RollUi> = combine(params, source, months, categories, pendingDeletes.pending) { (m, f, q), s, ms, c, d ->
        Frame(m, f, q, s, ms, c, d)
    }
        .runningFold(RollUi(today, month.value)) { prev, fr -> build(prev, fr) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RollUi(today, month.value))

    private fun build(prev: RollUi, fr: Frame): RollUi {
        val base = prev.copy(
            month = fr.month,
            filter = fr.filter,
            searching = fr.query != null,
            query = fr.query.orEmpty(),
            months = fr.months,
            categories = fr.categories,
            undoVisible = fr.deletes.values.any { !it.committing },
        )
        val hidden = fr.deletes.keys
        return when (val s = fr.source) {
            null -> base.copy(loading = true)
            is Source.Results -> base.copy(loading = false, results = s.rows.filter { it.id !in hidden })
            is Source.Month -> {
                val gone = s.data.days.flatMap { d -> d.entries.filter { it.id in hidden } }
                val egp = gone.filter { it.currency == PRIMARY_CURRENCY }
                val days = s.data.days.map { d ->
                    val out = d.entries.filter { it.id in hidden }
                    if (out.isEmpty()) d else d.copy(
                        entries = d.entries - out.toSet(),
                        ticket = d.ticket?.let { t ->
                            t.copy(
                                spent = (t.spent - out.sumOf { it.ticketPunch() }).coerceAtLeast(0),
                                entryCount = (t.entryCount - out.count { it.ticketPunch() > 0 }).coerceAtLeast(0),
                            )
                        },
                    )
                }.filter { it.ticket != null || it.entries.isNotEmpty() }
                base.copy(
                    loading = false,
                    results = null,
                    days = days,
                    summary = s.data.summary.copy(
                        spent = s.data.summary.spent - egp.sumOf { it.punched },
                        received = s.data.summary.received - egp.filter { it.kind == EntryKind.RECEIVED }.sumOf { it.amount },
                    ),
                )
            }
        }
    }

    fun setFilter(f: RollFilter) {
        filter.value = f
    }

    fun selectMonth(m: YearMonth) {
        month.value = m
    }

    fun openSearch() {
        if (query.value == null) query.value = ""
    }

    fun closeSearch() {
        query.value = null
    }

    fun setQuery(text: String) {
        if (query.value != null) query.value = text
    }

    /** "رجّعها": the newest waiting delete is cancelled; no write ever happened. */
    fun undo() {
        pendingDeletes.pending.value.values.lastOrNull { !it.committing }?.let { pendingDeletes.undo(it.id) }
    }

    /** The screen leaves: waiting deletes are written now. True if there were any. */
    fun flush(): Boolean {
        val any = pendingDeletes.pending.value.values.any { !it.committing }
        pendingDeletes.flush()
        return any
    }
}
