package com.masroufi.app.feature.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.ceilDiv
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.AboveFreeJar
import com.masroufi.app.data.repo.GoalView
import com.masroufi.app.data.repo.JarRepository
import com.masroufi.app.data.repo.JarView
import com.masroufi.app.data.repo.SeasonRepository
import com.masroufi.app.data.repo.SeasonView
import com.masroufi.app.entitlements.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

sealed interface JarSheet {
    data object Deposit : JarSheet
    data object Withdraw : JarSheet
    data object Gold : JarSheet
    data object Plus : JarSheet
    data class Goal(val id: Long) : JarSheet
}

/** [eta] only after 30 days of jar history. */
data class GoalRowUi(val view: GoalView, val eta: LocalDate?)

data class JarUi(
    val loading: Boolean = true,
    val jar: JarView? = null,
    val isPlus: Boolean = false,
    val roundUpStep: Int = 0,
    /** The nearest season that has a target; null when none. */
    val season: SeasonView? = null,
    val goals: List<GoalRowUi> = emptyList(),
    val sheet: JarSheet? = null,
) {
    /** No money has ever moved and no goal exists: the empty state. */
    val empty: Boolean
        get() = jar != null && jar.firstAt == null && jar.goals.isEmpty()
}

private const val ETA_MIN_DAYS = 30L
private const val TAG = "Jar"

/** At the jar's average pace since its first movement, the day the goal's remaining amount is reached; null under 30 days of history. */
internal fun goalEta(remaining: Long, total: Long, firstAt: Long?, today: LocalDate, clock: Clock): LocalDate? {
    if (firstAt == null || remaining <= 0 || total <= 0) return null
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(firstAt).atZone(clock.zone).toLocalDate(), today)
    if (days < ETA_MIN_DAYS) return null
    return today.plusDays((remaining * days).ceilDiv(total))
}

@HiltViewModel
class JarViewModel @Inject constructor(
    private val jar: JarRepository,
    seasons: SeasonRepository,
    private val settings: SettingsStore,
    private val entitlements: Entitlements,
    private val clock: Clock,
) : ViewModel() {
    private val sheet = MutableStateFlow<JarSheet?>(null)

    val state: StateFlow<JarUi> = combine(jar.observe(), seasons.observe(), settings.settings, entitlements.isPlus, sheet) { v, ss, s, plus, sh ->
        val today = LocalDate.now(clock)
        JarUi(
            loading = false,
            jar = v,
            isPlus = plus,
            roundUpStep = s.roundUpStep,
            season = ss.firstOrNull { it.season.target > 0 },
            goals = v.goals.map { g -> GoalRowUi(g, goalEta(g.goal.target - g.earmarked, v.total, v.firstAt, today, clock)) },
            sheet = sh,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JarUi())

    fun open(s: JarSheet) { sheet.value = s }
    fun dismiss() { sheet.value = null }

    fun deposit(amount: Long) = act { jar.deposit(amount) }

    /** Above the free total nothing moves ([AboveFreeJar] is swallowed; the sheet already refuses it). */
    fun withdraw(amount: Long) = act { jar.withdraw(amount) }

    fun earmark(goalId: Long, amount: Long) = act { jar.earmark(amount, goalId = goalId) }
    fun release(goalId: Long, amount: Long) = act { jar.release(amount, goalId = goalId) }
    fun setGoldPrice(perGram: Long) = act { settings.setGoldPrice(perGram, LocalDate.now(clock).toEpochDay()) }
    fun setRoundUpStep(step: Int) = act { settings.setRoundUpStep(step) }

    private fun act(block: suspend () -> Unit) {
        sheet.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AboveFreeJar) {
                Log.w(TAG, "above the free jar (${e.free})")
            } catch (e: Exception) {
                Log.e(TAG, "jar action failed", e)
            }
        }
    }
}
