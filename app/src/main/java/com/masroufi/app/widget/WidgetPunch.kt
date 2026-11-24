package com.masroufi.app.widget

import android.util.Log
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.EntryRepository
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.data.repo.toEntry
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The widget's one-tap repeat: logs the entry Home's chip logs (`RepeatChip.toEntry`) through the same repository, which
 * rewrites the summary and redraws the widgets. Does nothing while the lock is on, in child mode, or for a button the
 * stored summary no longer shows (a stale tap on a closed or old day).
 */
@Singleton
class WidgetPunch @Inject constructor(
    private val store: DaySummaryStore,
    private val settings: SettingsStore,
    private val entries: EntryRepository,
    private val clock: Clock,
) {
    suspend fun punch(chip: RepeatChip): Boolean {
        val s = settings.current()
        if (s.lockOn || s.childModeId != null) return false
        val summary = store.read() ?: return false
        if (!summary.open || summary.day != LocalDate.now(clock).toEpochDay() || chip !in summary.chips) return false
        return try {
            entries.log(chip.toEntry(clock.millis()))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WidgetPunch", "log failed", e)
            false
        }
    }
}
