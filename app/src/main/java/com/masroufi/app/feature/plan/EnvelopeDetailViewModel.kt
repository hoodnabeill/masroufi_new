package com.masroufi.app.feature.plan

import com.masroufi.app.feature.roll.withoutDeleted
import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.roll.PendingDelete
import com.masroufi.app.feature.roll.DeleteKind
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.repo.EnvelopeRepository
import com.masroufi.app.data.repo.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnvelopeDetailUi(
    val loading: Boolean = true,
    /** The envelope is gone (closed): the screen leaves. */
    val missing: Boolean = false,
    val card: EnvelopeCardUi? = null,
    /** This cycle's spending in the envelope's categories, newest first. */
    val entries: List<EntryRow> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EnvelopeDetailViewModel @Inject constructor(
    private val envelopes: EnvelopeRepository,
    recurring: RecurringRepository,
    categoryDao: CategoryDao,
    savedState: SavedStateHandle,
    private val deletes: PendingDeletes,
) : ViewModel() {
    val id: Long = checkNotNull(savedState.get<Long>("id")) { "envelope id" }

    val state: StateFlow<EnvelopeDetailUi> = combine(envelopes.observe().withoutDeleted(deletes, DeleteKind.ENVELOPE) { it.id }, recurring.observe(), categoryDao.all(), ::Triple)
        .flatMapLatest { (views, bills, categories) ->
            val v = views.firstOrNull { it.id == id }
            if (v == null) {
                flowOf(EnvelopeDetailUi(loading = false, missing = true))
            } else {
                val card = EnvelopeCardUi(v, cardLine(v, bills.filter { it.envelopeId == id }))
                envelopes.cycleEntries(v.categoryIds).map { EnvelopeDetailUi(false, false, card, it, categories.associateBy { c -> c.id }) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnvelopeDetailUi())

    /** "اقفل الظرف": archived, so it leaves the plan; its entries stay in the roll. */
    fun close() {
        val id = id
        deletes.schedule(PendingDelete(id, kind = DeleteKind.ENVELOPE)) { envelopes.archive(id) }
    }
}
