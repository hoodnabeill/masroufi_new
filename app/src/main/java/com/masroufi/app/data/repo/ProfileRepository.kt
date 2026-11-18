package com.masroufi.app.data.repo

import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.BirthdayPlan
import com.masroufi.app.domain.HomeCard
import com.masroufi.app.domain.MainGoal
import com.masroufi.app.domain.WorkKind
import com.masroufi.app.domain.birthdayPlan
import com.masroufi.app.domain.homeCardOrder
import com.masroufi.app.domain.scaleForSchool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The profile (all optional) and what it drives: Home's card order, the birthday plan, household scaling. */
@Singleton
class ProfileRepository @Inject constructor(
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    val profile: Flow<Settings> = settings.settings

    /** Home's cards by the main goal. */
    fun homeCards(): Flow<List<HomeCard>> = settings.settings.map { homeCardOrder(it.mainGoal) }.distinctUntilChanged()

    /** Null until a birthday is set. */
    fun birthdayPlan(): Flow<BirthdayPlan?> = settings.settings.map { s ->
        s.birthday?.let { birthdayPlan(it, LocalDate.now(clock), s.birthdayTarget) }
    }

    /** The school-season suggestion for [perChildBase] per child; a suggestion only, never written over the user's target. */
    suspend fun schoolSuggestion(perChildBase: Long): Long = scaleForSchool(perChildBase, settings.current().inSchool)

    suspend fun setName(v: String?) = settings.setProfileName(v)
    suspend fun setBirthday(v: LocalDate?) = settings.setBirthday(v)
    suspend fun setWorkKind(v: WorkKind?) = settings.setWorkKind(v)
    suspend fun setHousehold(size: Int?, inSchool: Int?) = settings.setHousehold(size, inSchool)
    suspend fun setMainGoal(v: MainGoal?) = settings.setMainGoal(v)
    suspend fun setBirthdayTarget(v: Long?) = settings.setBirthdayTarget(v)
}
