package com.masroufi.app.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.Payday
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.AlbumRepository
import com.masroufi.app.data.repo.OnboardingRepository
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.entitlements.Billing
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.PlusRepository
import com.masroufi.app.entitlements.RestoreOutcome
import com.masroufi.app.entitlements.TrialState
import com.masroufi.app.entitlements.trialInfo
import com.masroufi.app.ui.AppLocales
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The small sheets a value on the left of a row opens. [PlusInfo] is round-ups' card in end-of-month mode, where the paywall never shows. */
enum class SettingsSheet { Payday, Reminder, Leftover, RoundUp, PlusInfo }

data class SettingsUi(
    val loading: Boolean = true,
    val name: String = "",
    val level: Int = 1,
    val levelName: String = "",
    val payday: Payday = Payday.Day(25),
    /** Hour (0..23) of the tear-off reminder; null = none. Task 57 schedules it. */
    val reminderHour: Int? = null,
    val leftover: LeftoverDestination = LeftoverDestination.JAR,
    val roundUpStep: Int = 0,
    val isPlus: Boolean = false,
    val vibration: Boolean = true,
    val ticketSounds: Boolean = false,
    val hideAmounts: Boolean = false,
    val lockOn: Boolean = false,
    val plan: PlusPlan? = null,
    val renewsOn: LocalDate? = null,
    val trialEnds: LocalDate? = null,
    val restore: RestoreOutcome? = null,
    /** The last backup's day; Task 56 fills it (none yet). */
    val lastBackup: LocalDate? = null,
    val sheet: SettingsSheet? = null,
) {
    /** The identity disc's letter. */
    val initial: String get() = name.trim().take(1).ifEmpty { "ق" }
}

/** What the screen can do; the view model implements it and the screen tests use a fake. */
interface SettingsActions {
    fun open(sheet: SettingsSheet)
    fun closeSheet()
    fun setPayday(v: Payday)
    fun setReminderHour(v: Int?)
    fun setLeftover(v: LeftoverDestination)
    fun setRoundUpStep(v: Int)
    fun setVibration(v: Boolean)
    fun setTicketSounds(v: Boolean)
    fun setHideAmounts(v: Boolean)
    fun setLanguage(tag: String)
    fun manageUrl(): String
    fun restore()
}

private data class Local(val sheet: SettingsSheet? = null, val restore: RestoreOutcome? = null)

private const val TAG = "Settings"

/** الإعدادات: every row shows its stored value; a switch or a choice saves at once (no save button). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val onboarding: OnboardingRepository,
    album: AlbumRepository,
    private val plus: PlusRepository,
    private val billing: Billing,
    private val locales: AppLocales,
    private val clock: Clock,
) : ViewModel(), SettingsActions {
    private val local = MutableStateFlow(Local())

    val state: StateFlow<SettingsUi> = combine(settings.settings, album.observe(), plus.subscription, plus.isPlus, local) { s, a, sub, isPlus, l ->
        val trial = trialInfo(s, LocalDate.now(clock))
        SettingsUi(
            loading = false,
            name = s.profileName.orEmpty(), level = a.level, levelName = a.levelName,
            payday = s.payday, reminderHour = s.reminderHour, leftover = s.leftoverDefault, roundUpStep = s.roundUpStep, isPlus = isPlus,
            vibration = s.vibration, ticketSounds = s.ticketSounds, hideAmounts = s.hideAmounts, lockOn = s.lockOn,
            plan = sub?.plan, renewsOn = sub?.renewsOn,
            trialEnds = if (sub == null && trial.state == TrialState.ACTIVE) trial.endDay else null,
            restore = l.restore, sheet = l.sheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    override fun open(sheet: SettingsSheet) = local.update { it.copy(sheet = sheet) }
    override fun closeSheet() = local.update { it.copy(sheet = null) }

    /** Saved through the repository onboarding uses. */
    override fun setPayday(v: Payday) { closeSheet(); act { onboarding.setPayday(v) } }
    override fun setReminderHour(v: Int?) { closeSheet(); act { settings.setReminderHour(v) } }
    override fun setLeftover(v: LeftoverDestination) { closeSheet(); act { settings.setLeftoverDefault(v) } }
    override fun setRoundUpStep(v: Int) { closeSheet(); act { settings.setRoundUpStep(v) } }
    override fun setVibration(v: Boolean) = act { settings.setVibration(v) }
    override fun setTicketSounds(v: Boolean) = act { settings.setTicketSounds(v) }
    override fun setHideAmounts(v: Boolean) = act { settings.setHideAmounts(v) }

    /** "ar" or "en"; the activity recreates in the new language. */
    override fun setLanguage(tag: String) = locales.set(tag)

    override fun manageUrl(): String = billing.manageSubscriptionUrl()

    override fun restore() = act { local.update { it.copy(restore = plus.restoreOutcome()) } }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "settings action failed", e)
            }
        }
    }
}
