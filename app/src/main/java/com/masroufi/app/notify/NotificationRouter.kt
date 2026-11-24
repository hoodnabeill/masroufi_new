package com.masroufi.app.notify

import android.content.Intent
import com.masroufi.app.domain.NotifKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Set by the match widget's tap. */
const val EXTRA_OPEN_MATCH = "openMatch"

/** Where a tapped notification should land; [arg] is the day (evening, streak) or the pocket (low balance). */
data class NotifTarget(val kind: NotifKind, val arg: Long?, val match: Boolean = false)

/**
 * Holds the tapped notification until the app's navigation can use it (it is not composed while the lock or the child
 * screen is up). The activity hands every intent here; the navigation host takes the target and navigates.
 */
@Singleton
class NotificationRouter @Inject constructor() {
    private val _target = MutableStateFlow<NotifTarget?>(null)
    val target: StateFlow<NotifTarget?> = _target.asStateFlow()

    fun handle(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_MATCH, false)) {
            // The match widget: the kind is only a placeholder, the route is Match.
            _target.value = NotifTarget(NotifKind.MORNING, null, match = true)
            intent.removeExtra(EXTRA_OPEN_MATCH)
            return
        }
        val kind = intent.getStringExtra(EXTRA_NOTIF_KIND)?.let { runCatching { NotifKind.valueOf(it) }.getOrNull() } ?: return
        _target.value = NotifTarget(kind, if (intent.hasExtra(EXTRA_NOTIF_ARG)) intent.getLongExtra(EXTRA_NOTIF_ARG, 0) else null)
        // Taken once: a recreated activity gets the same intent again.
        intent.removeExtra(EXTRA_NOTIF_KIND)
        intent.removeExtra(EXTRA_NOTIF_ARG)
    }

    fun consume() { _target.value = null }
}
