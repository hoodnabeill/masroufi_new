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
    /** End-of-month mode (phase 9): on by hand, from [eomSince] up to [eomUntil] (the payday), epoch days. */
    val eomOn: Boolean = false,
    val eomSince: Long? = null,
    val eomUntil: Long? = null,
    /** Category ids paused in the mode (a reminder only) and the epoch days closed by hand with no spending. */
    val eomPaused: Set<Long> = emptySet(),
    val eomNoSpend: Set<Long> = emptySet(),
    /** The payday (epoch day) of the cycle whose offer card was dismissed. */
    val eomOfferDismissedFor: Long? = null,
    /** Freelancer mode (phase 9a): on, the fixed self-salary, the self-payday (day of month) and the payday it replaced. */
    val freelanceOn: Boolean = false,
    val selfSalary: Long? = null,
    val selfPayday: Int? = null,
    val prevPayday: Payday? = null,
    /** The month (yyyymm) whose self-payday ran, the MOVED entry it made (null when the vault was empty), what it paid and if that was short. */
    val selfPaidMonth: Long? = null,
    val selfPayEntryId: Long? = null,
    val selfPaid: Long? = null,
    val selfPayShort: Boolean = false,
    /** The month (yyyymm) a raise/lower suggestion was answered, and the month "keep it" was tapped (silences three months). */
    val selfSuggestMonth: Long? = null,
    val selfKeptMonth: Long? = null,
    /** Installment id -> month (yyyymm) its early-payoff suggestion was dismissed; id -> the due epoch day last logged by itself. */
    val earlyPayoffMonths: Map<Long, Long> = emptyMap(),
    val installmentLogged: Map<Long, Long> = emptyMap(),
    /** Kids' child screen (phase 9b): the child the app is pinned to; null = the normal app. Cleared only by [com.masroufi.app.core.ChildMode]. */
    val childModeId: Long? = null,
    /** The story's "share without amounts" switch (phase 9b): on until the user turns it off. */
    val storyHideAmounts: Boolean = true,
) {
    /**
     * The mode is on at [date]: switched on, started by then and not past its payday. With [nextPayday] (the date's pay
     * cycle) it also ends when the cycle is not the one it was switched on for (a not-fixed income came in).
     */
    fun eomActiveOn(date: LocalDate, nextPayday: LocalDate? = null): Boolean {
        val day = date.toEpochDay()
        return eomOn && day >= (eomSince ?: Long.MIN_VALUE) && day < (eomUntil ?: Long.MIN_VALUE) &&
            (nextPayday == null || nextPayday.toEpochDay() == eomUntil)
    }
}

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
    private val eomOnKey = booleanPreferencesKey("eomOn")
    private val eomSinceKey = longPreferencesKey("eomSince")
    private val eomUntilKey = longPreferencesKey("eomUntil")
    private val eomPausedKey = stringSetPreferencesKey("eomPaused")
    private val eomNoSpendKey = stringSetPreferencesKey("eomNoSpend")
    private val eomOfferKey = longPreferencesKey("eomOfferDismissedFor")
    private val freelanceOnKey = booleanPreferencesKey("freelanceOn")
    private val selfSalaryKey = longPreferencesKey("selfSalary")
    private val selfPaydayKey = intPreferencesKey("selfPayday")
    private val prevPaydayKey = stringPreferencesKey("prevPayday")
    private val selfPaidMonthKey = longPreferencesKey("selfPaidMonth")
    private val selfPayEntryKey = longPreferencesKey("selfPayEntryId")
    private val selfPaidKey = longPreferencesKey("selfPaid")
    private val selfPayShortKey = booleanPreferencesKey("selfPayShort")
    private val selfSuggestMonthKey = longPreferencesKey("selfSuggestMonth")
    private val selfKeptMonthKey = longPreferencesKey("selfKeptMonth")
    private val earlyPayoffKey = stringPreferencesKey("earlyPayoffMonths")
    private val installmentLoggedKey = stringPreferencesKey("installmentLogged")
    private val childModeKey = longPreferencesKey("childModeId")
    private val storyHideAmountsKey = booleanPreferencesKey("storyHideAmounts")

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
    /** Switches the mode on from [since] to [until] with a fresh challenge; habits paused before it stay paused. */
    suspend fun eomSwitchOn(since: Long, until: Long) {
        store.edit {
            it[eomOnKey] = true
            it[eomSinceKey] = since
            it[eomUntilKey] = until
            it.remove(eomNoSpendKey)
        }
    }
    suspend fun eomSwitchOff() {
        store.edit {
            it.remove(eomOnKey)
            it.remove(eomSinceKey)
            it.remove(eomUntilKey)
            it.remove(eomPausedKey)
            it.remove(eomNoSpendKey)
        }
    }
    suspend fun eomSetPaused(categoryId: Long, paused: Boolean) {
        store.edit {
            val all = it[eomPausedKey] ?: emptySet()
            it[eomPausedKey] = if (paused) all + categoryId.toString() else all - categoryId.toString()
        }
    }
    suspend fun eomAddNoSpend(epochDay: Long) { store.edit { it[eomNoSpendKey] = (it[eomNoSpendKey] ?: emptySet()) + epochDay.toString() } }
    suspend fun eomDismissOffer(cyclePayday: Long) { store.edit { it[eomOfferKey] = cyclePayday } }
    /** Switches freelancer mode on (or edits it): [restoreTo] is the payday to come back to on "off", kept from the first switch-on. */
    suspend fun freelanceSwitchOn(salary: Long, selfPayday: Int, restoreTo: Payday?) {
        require(salary > 0) { "self-salary must be > 0" }
        store.edit {
            it[freelanceOnKey] = true
            it[selfSalaryKey] = salary
            it[selfPaydayKey] = selfPayday
            it[paydayKey] = encode(Payday.Day(selfPayday))
            if (restoreTo != null) it[prevPaydayKey] = encode(restoreTo)
        }
    }
    /** Off: the previous payday comes back; the salary, the day and this month's pay record stay. */
    suspend fun freelanceSwitchOff() {
        store.edit {
            it.remove(freelanceOnKey)
            it[prevPaydayKey]?.let { prev -> it[paydayKey] = prev }
            it.remove(prevPaydayKey)
        }
    }
    suspend fun setSelfSalary(v: Long) {
        require(v > 0) { "self-salary must be > 0" }
        store.edit { it[selfSalaryKey] = v }
    }
    suspend fun markSelfPaid(month: Long, entryId: Long?, paid: Long, short: Boolean) {
        store.edit {
            it[selfPaidMonthKey] = month
            if (entryId == null) it.remove(selfPayEntryKey) else it[selfPayEntryKey] = entryId
            it[selfPaidKey] = paid
            it[selfPayShortKey] = short
        }
    }
    suspend fun setSelfSuggestMonth(month: Long) { store.edit { it[selfSuggestMonthKey] = month } }
    suspend fun setSelfKeptMonth(month: Long) {
        store.edit {
            it[selfKeptMonthKey] = month
            it[selfSuggestMonthKey] = month
        }
    }
    suspend fun setEarlyPayoffMonth(planId: Long, month: Long) {
        store.edit { it[earlyPayoffKey] = encodeMap(decodeReminders(it[earlyPayoffKey]) + (planId to month)) }
    }
    suspend fun setInstallmentLogged(planId: Long, dueEpochDay: Long) {
        store.edit { it[installmentLoggedKey] = encodeMap(decodeReminders(it[installmentLoggedKey]) + (planId to dueEpochDay)) }
    }
    /** Persists the child screen mode (null = off); callers go through ChildMode, which checks the PIN first. */
    suspend fun setChildMode(childId: Long?) { store.edit { if (childId == null) it.remove(childModeKey) else it[childModeKey] = childId } }
    suspend fun setStoryHideAmounts(v: Boolean) { store.edit { it[storyHideAmountsKey] = v } }
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
        eomOn = p[eomOnKey] ?: false,
        eomSince = p[eomSinceKey],
        eomUntil = p[eomUntilKey],
        eomPaused = p[eomPausedKey]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
        eomNoSpend = p[eomNoSpendKey]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
        eomOfferDismissedFor = p[eomOfferKey],
        freelanceOn = p[freelanceOnKey] ?: false,
        selfSalary = p[selfSalaryKey],
        selfPayday = p[selfPaydayKey],
        prevPayday = p[prevPaydayKey]?.let(::decodePayday),
        selfPaidMonth = p[selfPaidMonthKey],
        selfPayEntryId = p[selfPayEntryKey],
        selfPaid = p[selfPaidKey],
        selfPayShort = p[selfPayShortKey] ?: false,
        selfSuggestMonth = p[selfSuggestMonthKey],
        selfKeptMonth = p[selfKeptMonthKey],
        earlyPayoffMonths = decodeReminders(p[earlyPayoffKey]),
        installmentLogged = decodeReminders(p[installmentLoggedKey]),
        childModeId = p[childModeKey],
        storyHideAmounts = p[storyHideAmountsKey] ?: true,
    )

    private fun encodeMap(m: Map<Long, Long>) = m.entries.joinToString(",") { e -> "${e.key}:${e.value}" }

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
