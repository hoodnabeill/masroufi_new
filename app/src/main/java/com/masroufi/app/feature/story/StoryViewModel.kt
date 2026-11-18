package com.masroufi.app.feature.story

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.MonthStory
import com.masroufi.app.data.repo.StoryRepository
import com.masroufi.app.ui.haptics.HapticLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Each slide's segment fills linearly over this (05-MOTION "Monthly story slide"). */
const val SLIDE_MS = 5_000L
private const val TICK_MS = 50L
private const val TAG = "Story"

data class StoryUi(
    val loading: Boolean = true,
    val story: MonthStory? = null,
    val slides: List<StorySlide> = emptyList(),
    val index: Int = 0,
    /** The "share without amounts" switch. */
    val hideAmounts: Boolean = true,
) {
    val missing: Boolean get() = !loading && story == null
    val slide: StorySlide? get() = slides.getOrNull(index)
    val isLast: Boolean get() = index == slides.lastIndex
}

@HiltViewModel
class StoryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val stories: StoryRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    val id: Long = checkNotNull(savedState.get<Long>("id")) { "story id" }

    private val story = MutableStateFlow<MonthStory?>(null)
    private val loading = MutableStateFlow(true)
    private val index = MutableStateFlow(0)
    private val _progress = MutableStateFlow(0f)
    private val _held = MutableStateFlow(false)
    private val haptic = Channel<HapticLevel>(Channel.BUFFERED)
    private var elapsed = 0L
    private var bestShown = false
    private var slides = emptyList<StorySlide>()

    val state: StateFlow<StoryUi> = combine(story, loading, index, settings.settings.map { it.storyHideAmounts }) { s, l, i, hide ->
        StoryUi(l, s, s?.let { storySlides(it.payload) }.orEmpty(), i, hide)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryUi())

    /** The current slide's segment, 0..1; the earlier segments are full, the later ones empty. */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /** A finger is down: the segment waits. */
    val held: StateFlow<Boolean> = _held.asStateFlow()

    /** Tick per slide change; Milestone once when the "best month" line shows. */
    val haptics: Flow<HapticLevel> = haptic.receiveAsFlow()

    init {
        viewModelScope.launch {
            val loaded = try {
                stories.get(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "story not read", e)
                null
            }
            slides = loaded?.let { storySlides(it.payload) }.orEmpty()
            story.value = loaded
            loading.value = false
            if (loaded != null) run()
        }
    }

    /** The segment fills while nothing holds it; at the end of a slide the next one shows, the last one stays. */
    private suspend fun run() {
        val count = slides.size
        while (true) {
            delay(TICK_MS)
            if (_held.value || (index.value == count - 1 && elapsed >= SLIDE_MS)) continue
            elapsed += TICK_MS
            when {
                elapsed < SLIDE_MS -> _progress.value = elapsed.toFloat() / SLIDE_MS
                index.value < count - 1 -> go(index.value + 1)
                else -> _progress.value = 1f
            }
        }
    }

    private fun go(i: Int) {
        index.value = i
        elapsed = 0
        _progress.value = 0f
        val best = slides.getOrNull(i) == StorySlide.GRID && story.value?.payload?.comparison?.isBest == true && !bestShown
        if (best) bestShown = true
        haptic.trySend(if (best) HapticLevel.Milestone else HapticLevel.Tick)
    }

    /** Tap on the "next" half (or the arrow). */
    fun next() {
        if (index.value < slides.lastIndex) go(index.value + 1)
    }

    /** Tap on the "back" half; on the first slide it starts that slide again. */
    fun back() {
        if (slides.isEmpty()) return
        if (index.value > 0) go(index.value - 1) else { elapsed = 0; _progress.value = 0f }
    }

    fun hold(down: Boolean) {
        _held.value = down
    }

    fun setHideAmounts(on: Boolean) {
        viewModelScope.launch { settings.setStoryHideAmounts(on) }
    }

    /** Called when the screen closes: a first view is remembered, a replay changes nothing. */
    suspend fun markShown() {
        try {
            stories.markShown(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "story not marked", e)
        }
    }
}
