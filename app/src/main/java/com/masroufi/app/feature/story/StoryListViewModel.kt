package com.masroufi.app.feature.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.repo.StoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class StoryListRow(
    val id: Long,
    val cycleStart: LocalDate,
    val cycleEnd: LocalDate,
    val torn: Int,
    val cycleDays: Int,
    /** Never opened. */
    val isNew: Boolean,
)

data class StoryListUi(val loading: Boolean = true, val rows: List<StoryListRow> = emptyList()) {
    val empty: Boolean get() = !loading && rows.isEmpty()
}

/** الحكايات: every saved story, newest first (the album's list); opening one never recalculates. */
@HiltViewModel
class StoryListViewModel @Inject constructor(stories: StoryRepository) : ViewModel() {
    val state: StateFlow<StoryListUi> = stories.all()
        .map { list -> StoryListUi(false, list.map { StoryListRow(it.id, it.cycleStart, it.cycleEnd, it.payload.torn, it.payload.cycleDays, it.shownAt == null) }) }
        .catch { emit(StoryListUi(false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryListUi())
}
