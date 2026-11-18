package com.masroufi.app.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.ceilToPound
import com.masroufi.app.data.DataWipe
import com.masroufi.app.data.repo.AlbumRepository
import com.masroufi.app.data.repo.MatchRepository
import com.masroufi.app.data.repo.ProfileRepository
import com.masroufi.app.domain.DEFAULT_BIRTHDAY_TARGET
import com.masroufi.app.domain.MainGoal
import com.masroufi.app.domain.WorkKind
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

/** The birthday gift: whole pounds a day (rounded up) towards [target] piastres. */
data class GiftUi(val days: Int, val perDayPounds: Long, val target: Long)

enum class ProfileSheet { Birthday, Target, Wipe }

/** What the household steppers show when nothing was saved (the mock's four). */
const val DEFAULT_HOUSEHOLD = 4
const val HOUSEHOLD_MAX = 20
const val NAME_MAX = 30

data class ProfileUi(
    val loading: Boolean = true,
    val name: String = "",
    val level: Int = 1,
    val levelName: String = "",
    val ticketsClosed: Int = 0,
    val streak: Int = 0,
    val proverbs: Int = 0,
    val wonOf: Int = 0,
    val ofLast: Int = 0,
    val birthday: LocalDate? = null,
    val gift: GiftUi? = null,
    val workKind: WorkKind? = null,
    val householdSize: Int = DEFAULT_HOUSEHOLD,
    val inSchool: Int = 0,
    val mainGoal: MainGoal? = null,
    val sheet: ProfileSheet? = null,
    val target: AmountInput = AmountInput(),
    /** The data is gone: the screen restarts the app. */
    val wiped: Boolean = false,
) {
    /** The identity disc's letter. */
    val initial: String get() = name.trim().take(1).ifEmpty { "ق" }
}

private data class Local(
    val name: String? = null,
    val sheet: ProfileSheet? = null,
    val target: AmountInput = AmountInput(),
    val wiped: Boolean = false,
)

private const val TAG = "Profile"

/** ملفك: every field optional, saved on the phone as it changes. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profile: ProfileRepository,
    album: AlbumRepository,
    match: MatchRepository,
    private val dataWipe: DataWipe,
) : ViewModel() {
    private val local = MutableStateFlow(Local())

    /** Saves run one at a time: a stepper tapped twice quickly reads what the first tap wrote. */
    private val saves = Mutex()

    val state: StateFlow<ProfileUi> = combine(profile.profile, profile.birthdayPlan(), album.observe(), match.observe(), local) { s, plan, a, m, l ->
        ProfileUi(
            loading = false,
            name = l.name ?: s.profileName.orEmpty(),
            level = a.level, levelName = a.levelName, ticketsClosed = a.handClosed,
            streak = a.streak, proverbs = a.proverbs.size, wonOf = m.wonOf, ofLast = m.ofLast,
            birthday = s.birthday,
            gift = plan?.let { GiftUi(it.days, it.perDay.ceilToPound() / 100, it.target) },
            workKind = s.workKind,
            householdSize = s.householdSize ?: DEFAULT_HOUSEHOLD,
            inSchool = s.inSchool ?: 0,
            mainGoal = s.mainGoal,
            sheet = l.sheet, target = l.target, wiped = l.wiped,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi())

    fun setName(v: String) {
        val t = v.take(NAME_MAX)
        local.update { it.copy(name = t) }
        act { profile.setName(t) }
    }

    fun setBirthday(v: LocalDate?) {
        closeSheet()
        act { profile.setBirthday(v) }
    }

    /** Tapping the chosen kind again clears it (every field is optional). */
    fun setWorkKind(k: WorkKind) = act { profile.setWorkKind(k.takeIf { it != profile.profile.first().workKind }) }

    fun setMainGoal(g: MainGoal) = act { profile.setMainGoal(g.takeIf { it != profile.profile.first().mainGoal }) }

    fun stepSize(delta: Int) = act {
        val s = profile.profile.first()
        val size = ((s.householdSize ?: DEFAULT_HOUSEHOLD) + delta).coerceIn(1, HOUSEHOLD_MAX)
        profile.setHousehold(size, s.inSchool?.coerceAtMost(size))
    }

    fun stepSchool(delta: Int) = act {
        val s = profile.profile.first()
        val size = s.householdSize ?: DEFAULT_HOUSEHOLD
        profile.setHousehold(size, ((s.inSchool ?: 0) + delta).coerceIn(0, size))
    }

    fun open(sheet: ProfileSheet) {
        local.update {
            it.copy(
                sheet = sheet,
                target = if (sheet == ProfileSheet.Target) AmountInput.of(state.value.gift?.target ?: DEFAULT_BIRTHDAY_TARGET) else it.target,
            )
        }
    }

    fun closeSheet() = local.update { it.copy(sheet = null) }

    fun digit(d: Int) = local.update { it.copy(target = it.target.digit(d)) }
    fun point() = local.update { it.copy(target = it.target.point()) }
    fun backspace() = local.update { it.copy(target = it.target.backspace()) }

    /** An empty or zero target goes back to the default (1,000). */
    fun saveTarget() {
        val piastres = local.value.target.toPiastres()
        closeSheet()
        act { profile.setBirthdayTarget(piastres.takeIf { it > 0 }) }
    }

    /** Delete all my data (decision 6): after this the screen relaunches the app so nothing in memory survives. */
    fun wipe() {
        if (local.value.wiped) return
        closeSheet()
        act {
            dataWipe.wipe()
            local.update { it.copy(wiped = true) }
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                saves.withLock { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "profile action failed", e)
            }
        }
    }
}
