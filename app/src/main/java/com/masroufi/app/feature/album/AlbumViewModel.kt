package com.masroufi.app.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.repo.AlbumRepository
import com.masroufi.app.data.repo.AlbumView
import com.masroufi.app.data.repo.CollectedProverb
import com.masroufi.app.data.repo.Milestone
import com.masroufi.app.data.repo.MilestoneRepository
import com.masroufi.app.domain.LockHint
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.levelProgress
import com.masroufi.app.domain.lockHints
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** [earnedOn] null = still locked. [count]: how many times it was earned (1 for a one-off). */
data class StampSlot(val kind: StampKind, val earnedOn: LocalDate?, val count: Int)

data class AlbumUi(
    val loading: Boolean = true,
    val album: AlbumView? = null,
    /** Newest first; the first one is marked "new". */
    val cards: List<CollectedProverb> = emptyList(),
    /** Placeholders after the collected cards (the mock shows three). */
    val locked: List<LockHint> = emptyList(),
    /** Earned stamps first (oldest first), then the locked ones in their usual order. */
    val stamps: List<StampSlot> = emptyList(),
    val totalCards: Int = ProverbCatalog.all.size,
    /** Through the current level, 0..1. */
    val progress: Float = 0f,
) {
    val empty: Boolean get() = !loading && cards.isEmpty()
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    album: AlbumRepository,
    private val milestones: MilestoneRepository,
) : ViewModel() {
    val state: StateFlow<AlbumUi> = album.observe().map { a ->
        val cards = a.proverbs.sortedWith(compareByDescending<CollectedProverb> { it.collectedOn }.thenByDescending { it.proverb.number })
        AlbumUi(
            loading = false, album = a, cards = cards,
            locked = lockHints(cards.map { it.proverb.number }.toSet()),
            progress = levelProgress(a.handClosed),
            stamps = a.stamps.map { StampSlot(it.kind, it.earnedOn, it.count) } +
                StampKind.entries.filter { k -> a.stamps.none { it.kind == k } }.map { StampSlot(it, null, 0) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUi())

    /** The milestone to celebrate now (a level before stamps); null when nothing is new. */
    val celebration: StateFlow<Milestone?> = milestones.unseen().map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The celebration played: everything unseen now is marked, so a backlog (an update that adds stamps) plays once, not once each. */
    fun celebrated() {
        viewModelScope.launch { milestones.unseen().first().forEach { milestones.markSeen(it) } }
    }
}
