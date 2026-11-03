package com.masroufi.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Logging streak: consecutive days with at least one entry. Today counts as alive until it ends. One missed day per
 * week (weeks start on Saturday) is bridged automatically by a free freeze; [frozenDays] are the days it covered.
 */
data class Streak(
    val current: Int,
    val best: Int,
    val loggedToday: Boolean,
    val frozenDays: Set<LocalDate> = emptySet(),
    /** True while this week's freeze is still unused. */
    val freezeAvailable: Boolean = true,
)

data class Level(val number: Int, val xp: Int, val floorXp: Int, val nextXp: Int?) {
    /** 0..1 progress towards the next level; a maxed-out level reads as full. */
    val progress: Float get() = nextXp?.let { ((xp - floorXp).toFloat() / (it - floorXp)).coerceIn(0f, 1f) } ?: 1f
}

/** The nine handoff badges and the points each one awards. */
enum class Badge(val reward: Int, val target: Int) {
    FIRST_LOG(10, 1),
    STARTED_RIGHT(20, 3),
    FULL_WEEK(50, 7),
    ZERO_DAY(20, 1),
    HOME_COOK(50, 3),
    PLANNER(30, 3),
    EMERGENCY_GUARD(30, 1),
    GAMEYA_MEMBER(20, 1),
    GREEN_MONTH(100, 1),
}

data class BadgeProgress(val badge: Badge, val done: Int) {
    val earned: Boolean get() = done >= badge.target
    val fraction: Float get() = (done.toFloat() / badge.target).coerceIn(0f, 1f)
}

/** What progression is computed from. Everything is derived from data the app already keeps. */
data class ProgressInput(
    val logDays: Set<LocalDate>,
    val entries: Int,
    /** Entries logged today, so the reward screen knows whether this was the day's first. */
    val entriesToday: Int = 0,
    /** Days with at least one expense, and days with an eating-out (food) expense. */
    val expenseDays: Set<LocalDate> = emptySet(),
    val foodDays: Set<LocalDate> = emptySet(),
    val activeBudgets: Int = 0,
    val emergencyGoal: Boolean = false,
    val gameyas: Int = 0,
    /** The user's own gameya instalments marked paid. */
    val gameyaPaid: Int = 0,
    /** A finished month where spending stayed at or below income. */
    val greenMonth: Boolean = false,
    val onboardingDone: Boolean = false,
)

data class ProgressSnapshot(
    val streak: Streak,
    val level: Level,
    val badges: List<BadgeProgress> = emptyList(),
    val logDays: Set<LocalDate> = emptySet(),
    val points: Int = 0,
    val entriesToday: Int = 0,
) {
    val earned: Set<Badge> get() = badges.filter { it.earned }.mapTo(HashSet()) { it.badge }
}

/**
 * Progression is derived, never stored: points come from habits already in the data, so they cannot drift, survive
 * restore and sync, and need no migration. It rewards logging, never spending.
 * Points: +10 per day logged, +5 per own gameya instalment paid, +20 for finishing setup, plus badge rewards.
 */
object Progress {
    /** Level floors and the handoff's titles: مبتدئ 0, منظّم 100, شاطر 300, محترف 700, ملك الفلوس 1,500. */
    val levelFloors = listOf(0, 100, 300, 700, 1_500)

    private fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

    fun streak(days: Set<LocalDate>, today: LocalDate): Streak {
        val loggedToday = today in days
        val frozen = HashSet<LocalDate>()
        var cursor = if (loggedToday) today else today.minusDays(1)
        var current = 0
        while (true) {
            if (cursor in days) {
                current++
            } else if (cursor.minusDays(1) in days && frozen.none { weekStart(it) == weekStart(cursor) }) {
                // A single missed day inside a run, bridged by that week's freeze.
                frozen += cursor
            } else {
                break
            }
            cursor = cursor.minusDays(1)
        }
        val best = bestRun(days)
        return Streak(
            current = current,
            best = maxOf(best, current),
            loggedToday = loggedToday,
            frozenDays = frozen,
            freezeAvailable = frozen.none { weekStart(it) == weekStart(today) },
        )
    }

    /** Longest run of logged days, letting one missed day per week bridge a run (same rule as the live streak). */
    private fun bestRun(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        val usedWeeks = HashSet<LocalDate>()
        for (day in sorted) {
            run = when {
                previous == null -> 1
                previous.plusDays(1) == day -> run + 1
                previous.plusDays(2) == day && usedWeeks.add(weekStart(previous.plusDays(1))) -> run + 1
                else -> 1
            }
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** Longest run of consecutive logged days with no eating-out expense (طبّاخ البيت). */
    private fun homeCookRun(logDays: Set<LocalDate>, foodDays: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in logDays.sorted()) {
            run = if (day in foodDays) 0 else if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    fun level(xp: Int): Level {
        val index = levelFloors.indexOfLast { xp >= it }.coerceAtLeast(0)
        return Level(index + 1, xp, levelFloors[index], levelFloors.getOrNull(index + 1))
    }

    fun badges(input: ProgressInput, streak: Streak, today: LocalDate): List<BadgeProgress> {
        // A zero day is a finished day the user did open the books on (logged something) without spending.
        val zeroDays = input.logDays.count { it.isBefore(today) && it !in input.expenseDays }
        return listOf(
            BadgeProgress(Badge.FIRST_LOG, minOf(input.entries, 1)),
            BadgeProgress(Badge.STARTED_RIGHT, minOf(streak.best, 3)),
            BadgeProgress(Badge.FULL_WEEK, minOf(streak.best, 7)),
            BadgeProgress(Badge.ZERO_DAY, minOf(zeroDays, 1)),
            BadgeProgress(Badge.HOME_COOK, minOf(homeCookRun(input.logDays, input.foodDays), 3)),
            BadgeProgress(Badge.PLANNER, minOf(input.activeBudgets, 3)),
            BadgeProgress(Badge.EMERGENCY_GUARD, if (input.emergencyGoal) 1 else 0),
            BadgeProgress(Badge.GAMEYA_MEMBER, if (input.gameyas > 0 && input.gameyaPaid > 0) 1 else 0),
            BadgeProgress(Badge.GREEN_MONTH, if (input.greenMonth) 1 else 0),
        )
    }

    fun points(input: ProgressInput, badges: List<BadgeProgress>): Int =
        input.logDays.size * 10 + input.gameyaPaid * 5 + (if (input.onboardingDone) 20 else 0) +
            badges.filter { it.earned }.sumOf { it.badge.reward }

    fun snapshot(input: ProgressInput, today: LocalDate): ProgressSnapshot {
        val streak = streak(input.logDays, today)
        val badges = badges(input, streak, today)
        val points = points(input, badges)
        return ProgressSnapshot(streak, level(points), badges, input.logDays, points, input.entriesToday)
    }

    /** Finished months (before the current one) where spending did not exceed income. */
    fun hasGreenMonth(totals: Map<YearMonth, Pair<Long, Long>>, today: LocalDate): Boolean =
        totals.any { (month, value) -> month.isBefore(YearMonth.from(today)) && value.second > 0 && value.second <= value.first }
}
