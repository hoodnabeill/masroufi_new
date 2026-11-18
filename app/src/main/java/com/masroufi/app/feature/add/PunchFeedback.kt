package com.masroufi.app.feature.add

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the add sheet tells Home after a save; Home shows it under the ticket. */
sealed interface PunchFeedback {
    /** A punch was stored. [left] is today's ticket left after it (negative = over by -left), piastres. */
    data class Saved(val left: Long, val firstOfDay: Boolean, val pausedHabit: Boolean = false) : PunchFeedback

    /** The write failed and the punch was rolled back ("ماتسجّلتش. جرّب تاني."). */
    data object Failed : PunchFeedback
}

/** One-shot events from the add sheet to Home (the sheet is closed by the time they arrive). */
@Singleton
class FeedbackBus @Inject constructor() {
    private val flow = MutableSharedFlow<PunchFeedback>(extraBufferCapacity = 8)
    val events: SharedFlow<PunchFeedback> = flow.asSharedFlow()

    fun emit(f: PunchFeedback) {
        flow.tryEmit(f)
    }
}
