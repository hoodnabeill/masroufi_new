package com.masroufi.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.masroufi.app.core.Payday
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.MainGoal
import com.masroufi.app.domain.MatchSeen
import com.masroufi.app.domain.WorkKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val payday: Payday = Payday.Day(25),
    val leftoverDefault: LeftoverDestination = LeftoverDestination.JAR,
    /** Step in pounds (0, 5, 10, 50). */
    val roundUpStep: Int = 0,
    val proverbSeed: Long,
    val onboarded: Boolean = false,
    /** Epoch day of the last ticket Home printed on screen with the new-ticket moment; null = never. */
    val lastTicketShown: Long? = null,
    /** Onboarding screen to resume at (0 welcome .. 4 reveal); past the last screen once finished. */
    val onboardingStep: Int = 0,
    /** The user skipped "how much money": the starter ticket prints until they say. */
    val moneyUnknown: Boolean = false,
    /** Hour (0..23) of the daily reminder; null = none. */
    val reminderHour: Int? = null,
    val firstWeekCardsDismissed: Set<String> = emptySet(),
    /** Epoch day of the first settings read; first-week cards count from it. */
    val firstOpenDay: Long? = null,
    /** The last income split by the saved split and what no envelope took ("من غير ظرف"); null = never split. */
    val lastSplitIncome: Long? = null,
    val lastSplitUnassigned: Long? = null,
    /** The salary of the last payday ritual; null = the ritual was never completed. */
    val usualSalary: Long? = null,
    /** The jar share chosen at the last ritual when it was a percent; null = an amount, or never. */
    val lastJarSharePercent: Int? = null,
    /** 21-karat price per gram typed by the user, and the epoch day it was typed; null = never typed. */
    val goldPricePerGram: Long? = null,
    val goldPriceDate: Long? = null,
    /** The income entry the payday ritual was last offered for (offered once per income). */
    val lastRitualForEntryId: Long? = null,
    /** The user's "most important first" order of season ids; seasons not listed follow by date. */
    val seasonOrder: List<Long> = emptyList(),
    /** Subscription id -> epoch day of the "remind me" before its trial ends (the notification itself is Phase 11). */
    val trialReminders: Map<Long, Long> = emptyMap(),
    /** Profile (11-DATA "Profile"): every field optional, stored on the phone only. */
    val profileName: String? = null,
    val birthday: LocalDate? = null,
    val workKind: WorkKind? = null,
    val householdSize: Int? = null,
    /** How many of the household are in school. */
    val inSchool: Int? = null,
    val mainGoal: MainGoal? = null,
    val birthdayTarget: Long? = null,
    /** Milestones the celebration already played: "level:N" and "stamp:KIND". */
    val milestonesSeen: Set<String> = emptySet(),
    /** A raised daily amount the user accepted from the basket's suggestion, and the epoch day (payday) it stops holding. */
    val ticketRaise: Long? = null,
    val ticketRaiseUntil: Long? = null,
    /** "yyyy-MM" of the month the user dismissed the basket's ticket suggestion ("سيبها"). */
    val suggestionDismissedMonth: String? = null,
    /** The match score last shown (task 42: goals since then animate). */
    val matchSeen: MatchSeen? = null,
)

@Singleton
class SettingsStore @Inject constructor(
    private val store: DataStore<Preferences>,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val paydayKey = stringPreferencesKey("payday")
    private val leftoverKey = stringPreferencesKey("leftoverDefault")
    private val roundUpKey = intPreferencesKey("roundUpStep")
    private val seedKey = longPreferencesKey("proverbSeed")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val lastTicketShownKey = longPreferencesKey("lastTicketShown")
    private val onboardingStepKey = intPreferencesKey("onboardingStep")
    private val moneyUnknownKey = booleanPreferencesKey("moneyUnknown")
    private val reminderHourKey = intPreferencesKey("reminderHour")
    private val cardsDismissedKey = stringSetPreferencesKey("firstWeekCardsDismissed")
    private val firstOpenDayKey = longPreferencesKey("firstOpenDay")
    private val lastSplitIncomeKey = longPreferencesKey("lastSplitIncome")
    private val lastSplitUnassignedKey = longPreferencesKey("lastSplitUnassigned")
    private val usualSalaryKey = longPreferencesKey("usualSalary")
    private val lastJarSharePercentKey = intPreferencesKey("lastJarSharePercent")
    private val goldPriceKey = longPreferencesKey("goldPricePerGram")
    private val goldPriceDateKey = longPreferencesKey("goldPriceDate")
    private val lastRitualKey = longPreferencesKey("lastRitualForEntryId")
    private val seasonOrderKey = stringPreferencesKey("seasonOrder")
    private val trialRemindersKey = stringPreferencesKey("trialReminders")
    private val profileNameKey = stringPreferencesKey("profileName")
    private val birthdayKey = longPreferencesKey("birthdayEpochDay")
    private val workKindKey = stringPreferencesKey("workKind")
    private val householdKey = intPreferencesKey("householdSize")
    private val inSchoolKey = intPreferencesKey("inSchool")
    private val mainGoalKey = stringPreferencesKey("mainGoal")
    private val birthdayTargetKey = longPreferencesKey("birthdayTarget")
    private val milestonesSeenKey = stringSetPreferencesKey("milestonesSeen")
    private val ticketRaiseKey = longPreferencesKey("ticketRaise")
    private val ticketRaiseUntilKey = longPreferencesKey("ticketRaiseUntil")
    private val suggestionDismissedKey = stringPreferencesKey("suggestionDismissedMonth")
    private val matchSeenKey = stringPreferencesKey("matchSeen")

    /** Emits only after the seed exists; edit {} is serialised, so concurrent first reads share one seed. */
    val settings: Flow<Settings> = flow {
        store.edit {
            if (it[seedKey] == null) it[seedKey] = SecureRandom().nextLong()
            if (it[firstOpenDayKey] == null) it[firstOpenDayKey] = LocalDate.now(clock).toEpochDay()
        }
        emitAll(store.data.map(::decode))
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setPayday(v: Payday) { store.edit { it[paydayKey] = encode(v) } }
    suspend fun setLeftoverDefault(v: LeftoverDestination) { store.edit { it[leftoverKey] = v.name } }
    suspend fun setRoundUpStep(v: Int) {
        require(v in setOf(0, 5, 10, 50)) { "round-up step must be 0, 5, 10 or 50" }
        store.edit { it[roundUpKey] = v }
    }
    suspend fun setOnboarded(v: Boolean) { store.edit { it[onboardedKey] = v } }
    suspend fun setLastTicketShown(epochDay: Long) { store.edit { it[lastTicketShownKey] = epochDay } }
    suspend fun setOnboardingStep(v: Int) { store.edit { it[onboardingStepKey] = v } }
    suspend fun setMoneyUnknown(v: Boolean) { store.edit { it[moneyUnknownKey] = v } }
    suspend fun setReminderHour(v: Int?) {
        require(v == null || v in 0..23) { "reminder hour must be 0..23" }
        store.edit { if (v == null) it.remove(reminderHourKey) else it[reminderHourKey] = v }
    }
    suspend fun setLastSplit(income: Long, unassigned: Long) {
        store.edit {
            it[lastSplitIncomeKey] = income
            it[lastSplitUnassignedKey] = unassigned
        }
    }
    /** After a payday ritual: the salary, and the jar share percent (null = an amount was chosen). */
    suspend fun setRitualDone(salary: Long, jarSharePercent: Int?) {
        require(salary > 0) { "salary must be > 0" }
        store.edit {
            it[usualSalaryKey] = salary
            if (jarSharePercent == null) it.remove(lastJarSharePercentKey) else it[lastJarSharePercentKey] = jarSharePercent
        }
    }
    suspend fun setTrialReminder(id: Long, epochDay: Long) {
        store.edit {
            val all = decodeReminders(it[trialRemindersKey]) + (id to epochDay)
            it[trialRemindersKey] = all.entries.joinToString(",") { e -> "${e.key}:${e.value}" }
        }
    }
    suspend fun setSeasonOrder(ids: List<Long>) { store.edit { it[seasonOrderKey] = ids.joinToString(",") } }
    suspend fun setLastRitualForEntryId(id: Long) { store.edit { it[lastRitualKey] = id } }
    suspend fun setGoldPrice(perGram: Long, epochDay: Long) {
        require(perGram > 0) { "gold price must be > 0" }
        store.edit {
            it[goldPriceKey] = perGram
            it[goldPriceDateKey] = epochDay
        }
    }
    suspend fun setTicketRaise(amount: Long, untilEpochDay: Long) {
        require(amount > 0) { "raise must be > 0" }
        store.edit {
            it[ticketRaiseKey] = amount
            it[ticketRaiseUntilKey] = untilEpochDay
        }
    }
    suspend fun setSuggestionDismissed(month: String) { store.edit { it[suggestionDismissedKey] = month } }
    suspend fun setProfileName(v: String?) { store.edit { if (v.isNullOrBlank()) it.remove(profileNameKey) else it[profileNameKey] = v.trim() } }
    suspend fun setBirthday(v: LocalDate?) { store.edit { if (v == null) it.remove(birthdayKey) else it[birthdayKey] = v.toEpochDay() } }
    suspend fun setWorkKind(v: WorkKind?) { store.edit { if (v == null) it.remove(workKindKey) else it[workKindKey] = v.name } }
    suspend fun setHousehold(size: Int?, inSchool: Int?) {
        require(size == null || size in 1..20) { "household size must be 1..20" }
        require(inSchool == null || inSchool in 0..(size ?: 20)) { "in school must be 0..household size" }
        store.edit {
            if (size == null) it.remove(householdKey) else it[householdKey] = size
            if (inSchool == null) it.remove(inSchoolKey) else it[inSchoolKey] = inSchool
        }
    }
    suspend fun setMainGoal(v: MainGoal?) { store.edit { if (v == null) it.remove(mainGoalKey) else it[mainGoalKey] = v.name } }
    suspend fun setBirthdayTarget(v: Long?) {
        require(v == null || v > 0) { "birthday target must be > 0" }
        store.edit { if (v == null) it.remove(birthdayTargetKey) else it[birthdayTargetKey] = v }
    }
    suspend fun setMatchSeen(weekStart: LocalDate, goalsFor: Int, goalsAgainst: Int) {
        store.edit { it[matchSeenKey] = "${weekStart.toEpochDay()},$goalsFor,$goalsAgainst" }
    }
    suspend fun markMilestoneSeen(key: String) { store.edit { it[milestonesSeenKey] = (it[milestonesSeenKey] ?: emptySet()) + key } }
    /** Clears every key (delete all my data) and re-seeds in the same edit, so a live collector never sees no seed. */
    suspend fun clearAll() {
        store.edit {
            it.clear()
            it[seedKey] = SecureRandom().nextLong()
            it[firstOpenDayKey] = LocalDate.now(clock).toEpochDay()
        }
    }
    suspend fun dismissFirstWeekCard(id: String) { store.edit { it[cardsDismissedKey] = (it[cardsDismissedKey] ?: emptySet()) + id } }

    private fun decode(p: Preferences) = Settings(
        payday = p[paydayKey]?.let(::decodePayday) ?: Payday.Day(25),
        leftoverDefault = p[leftoverKey]?.let { runCatching { LeftoverDestination.valueOf(it) }.getOrNull() }
            ?: LeftoverDestination.JAR,
        roundUpStep = p[roundUpKey] ?: 0,
        proverbSeed = checkNotNull(p[seedKey]) { "seed is written before the first emit" },
        onboarded = p[onboardedKey] ?: false,
        lastTicketShown = p[lastTicketShownKey],
        onboardingStep = p[onboardingStepKey] ?: 0,
        moneyUnknown = p[moneyUnknownKey] ?: false,
        reminderHour = p[reminderHourKey],
        firstWeekCardsDismissed = p[cardsDismissedKey] ?: emptySet(),
        firstOpenDay = p[firstOpenDayKey],
        lastSplitIncome = p[lastSplitIncomeKey],
        lastSplitUnassigned = p[lastSplitUnassignedKey],
        usualSalary = p[usualSalaryKey],
        lastJarSharePercent = p[lastJarSharePercentKey],
        goldPricePerGram = p[goldPriceKey],
        goldPriceDate = p[goldPriceDateKey],
        lastRitualForEntryId = p[lastRitualKey],
        seasonOrder = p[seasonOrderKey]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
        trialReminders = decodeReminders(p[trialRemindersKey]),
        profileName = p[profileNameKey],
        birthday = p[birthdayKey]?.let(LocalDate::ofEpochDay),
        workKind = p[workKindKey]?.let { runCatching { WorkKind.valueOf(it) }.getOrNull() },
        householdSize = p[householdKey],
        inSchool = p[inSchoolKey],
        mainGoal = p[mainGoalKey]?.let { runCatching { MainGoal.valueOf(it) }.getOrNull() },
        birthdayTarget = p[birthdayTargetKey],
        milestonesSeen = p[milestonesSeenKey] ?: emptySet(),
        ticketRaise = p[ticketRaiseKey],
        ticketRaiseUntil = p[ticketRaiseUntilKey],
        suggestionDismissedMonth = p[suggestionDismissedKey],
        matchSeen = p[matchSeenKey]?.split(",")?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 3 }
            ?.let { MatchSeen(it[0], it[1].toInt(), it[2].toInt()) },
    )

    private fun decodeReminders(s: String?): Map<Long, Long> = s?.split(",")?.mapNotNull {
        val (a, b) = it.split(":").takeIf { x -> x.size == 2 } ?: return@mapNotNull null
        (a.toLongOrNull() ?: return@mapNotNull null) to (b.toLongOrNull() ?: return@mapNotNull null)
    }?.toMap() ?: emptyMap()

    private fun encode(v: Payday) = when (v) {
        is Payday.Day -> "day:${v.day}"
        Payday.LastDay -> "last"
        Payday.NotFixed -> "notfixed"
    }

    // A corrupt value falls back to the default rather than crashing every settings read.
    private fun decodePayday(s: String): Payday = when {
        s == "last" -> Payday.LastDay
        s == "notfixed" -> Payday.NotFixed
        s.startsWith("day:") -> s.removePrefix("day:").toIntOrNull()?.takeIf { it in 1..31 }?.let { Payday.Day(it) }
            ?: Payday.Day(25)
        else -> Payday.Day(25)
    }
}
