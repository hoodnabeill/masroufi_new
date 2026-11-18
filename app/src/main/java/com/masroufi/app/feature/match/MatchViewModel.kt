package com.masroufi.app.feature.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.MatchRepository
import com.masroufi.app.domain.FreshGoals
import com.masroufi.app.domain.MatchEvent
import com.masroufi.app.domain.MatchView
import com.masroufi.app.domain.freshGoals
import com.masroufi.app.domain.matchEvents
import com.masroufi.app.domain.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [fresh]: what scored since the last visit (read once on opening; the score digit and event rows animate for it). */
data class MatchUi(
    val loading: Boolean = true,
    val view: MatchView? = null,
    val events: List<MatchEvent> = emptyList(),
    val fresh: FreshGoals = FreshGoals(0, 0, 0, emptySet()),
)

@HiltViewModel
class MatchViewModel @Inject constructor(
    private val repo: MatchRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val visit = MutableStateFlow<FreshGoals?>(null)

    val state: StateFlow<MatchUi> = combine(repo.observe(), visit) { view, fresh ->
        if (fresh == null) MatchUi() else MatchUi(false, view, matchEvents(view, fresh.dates), fresh)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchUi())

    init {
        // The score is stored as seen right away: the next visit animates only what is new by then.
        viewModelScope.launch {
            val view = repo.observe().first()
            visit.value = freshGoals(settings.current().matchSeen, view)
            settings.setMatchSeen(weekStart(view.today), view.goalsFor, view.goalsAgainst)
        }
    }
}
