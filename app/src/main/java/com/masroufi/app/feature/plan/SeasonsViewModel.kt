package com.masroufi.app.feature.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.SeasonKind
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.DutyRepository
import com.masroufi.app.data.repo.OccasionView
import com.masroufi.app.data.repo.SeasonRepository
import com.masroufi.app.data.repo.SeasonView
import com.masroufi.app.data.repo.TogetherView
import com.masroufi.app.entitlements.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What [person] gave at the user's own occasion, for a personal season about one of their occasions. */
data class LedgerNote(val person: String, val amount: Long)

data class SeasonsUi(
    val loading: Boolean = true,
    val isPlus: Boolean = false,
    /** Every season in the user's order (by date where they never ordered). */
    val seasons: List<SeasonView> = emptyList(),
    /** The nearest season with a target, else the nearest one. */
    val hero: SeasonView? = null,
    val together: TogetherView? = null,
    /** Seasons still short of their target. */
    val togetherCount: Int = 0,
    val reordering: Boolean = false,
    val plusSheet: Boolean = false,
    /** By season id: the ledger line for a personal occasion whose person has a record. */
    val ledger: Map<Long, LedgerNote> = emptyMap(),
) {
    val rest: List<SeasonView> get() = seasons.filter { it.season.id != hero?.season?.id }
}

private const val TAG = "Seasons"

@HiltViewModel
class SeasonsViewModel @Inject constructor(
    private val seasons: SeasonRepository,
    private val duty: DutyRepository,
    private val settings: SettingsStore,
    entitlements: Entitlements,
) : ViewModel() {
    private val reordering = MutableStateFlow(false)
    private val plusSheet = MutableStateFlow(false)

    val state: StateFlow<SeasonsUi> = combine(
        combine(seasons.observe(), duty.observe()) { all, views -> Triple(all, seasons.together(), ledgerNotes(all, views)) },
        settings.settings, entitlements.isPlus, reordering, plusSheet,
    ) { (all, together, ledger), s, plus, re, sheet ->
        // observe() is by date: the nearest with a target leads the page, the rest follow the user's order.
        val hero = all.firstOrNull { it.season.target > 0 } ?: all.firstOrNull()
        val rank = { v: SeasonView -> s.seasonOrder.indexOf(v.season.id).let { if (it < 0) Int.MAX_VALUE else it } }
        SeasonsUi(
            loading = false, isPlus = plus, seasons = all.sortedBy(rank), hero = hero, together = together,
            togetherCount = all.count { it.season.target > it.saved }, reordering = re, plusSheet = sheet, ledger = ledger,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeasonsUi())

    /**
     * No season->person link exists (no schema change): a personal season is matched to the ledger occasion of someone else's
     * with the same name and a person; the note shows only when that person has given at the user's own occasion.
     */
    private suspend fun ledgerNotes(all: List<SeasonView>, views: List<OccasionView>): Map<Long, LedgerNote> = buildMap {
        for (v in all.filter { it.season.kind == SeasonKind.PERSONAL }) {
            val person = views.firstOrNull { it.occasion.whose == OccasionOwner.THEIRS && it.occasion.name.trim() == v.season.name.trim() }?.person ?: continue
            duty.suggestion(person.id)?.let { put(v.season.id, LedgerNote(person.name, it)) }
        }
    }

    fun toggleReorder() { reordering.value = !reordering.value }
    fun openPlus() { plusSheet.value = true }
    fun dismissPlus() { plusSheet.value = false }

    /** Moves a season one step among the others (-1 up, +1 down) and saves the whole order. */
    fun move(id: Long, delta: Int) {
        val ids = state.value.rest.map { it.season.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        act { settings.setSeasonOrder(listOfNotNull(state.value.hero?.season?.id) + ids) }
    }

    fun addRamadan() = act { seasons.addRamadan() }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "seasons action failed", e)
            }
        }
    }
}
