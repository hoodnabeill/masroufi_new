package com.masroufi.app.domain

import java.time.LocalDate

/** Logging streak: consecutive days with at least one entry. Today counts as alive until it ends. */
data class Streak(val current: Int, val best: Int, val loggedToday: Boolean)

data class Level(val number: Int, val xp: Int, val floorXp: Int, val nextXp: Int?) {
    /** 0..1 progress towards the next level; a maxed-out level reads as full. */
    val progress: Float get() = nextXp?.let { ((xp - floorXp).toFloat() / (it - floorXp)).coerceIn(0f, 1f) } ?: 1f
}

enum class Badge { FIRST_LOG, STREAK_3, STREAK_7, STREAK_30, STREAK_100, LOGS_50, LOGS_250, FIRST_GOAL, GOAL_DONE, SUBSCRIPTIONS, GAMEYA, PAYDAY }

/** What the progression system is computed from. Everything is derived from data the app already keeps. */
data class ProgressInput(
    val logDays: Set<LocalDate>,
    val entries: Int,
    val goals: Int,
    val goalsDone: Int,
    val subscriptions: Int,
    val gameyas: Int,
    val paydaySet: Boolean,
)

data class ProgressSnapshot(val streak: Streak, val level: Level, val badges: Set<Badge>)

/**
 * Progression is derived, never stored: XP comes from logging habits, so it cannot drift from the data, survives
 * restore and sync, and needs no migration. Rewards favour consistency (days and streaks) over raw volume.
 */
object Progress {
    /** XP needed to reach each level (index 0 is level 1). */
    val levelFloors = listOf(0, 100, 300, 600, 1_000, 1_600, 2_500, 4_000, 6_000, 9_000)

    fun streak(days: Set<LocalDate>, today: LocalDate): Streak {
        val loggedToday = today in days
        var cursor = if (loggedToday) today else today.minusDays(1)
        var current = 0
        while (cursor in days) {
            current++
            cursor = cursor.minusDays(1)
        }
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days.sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return Streak(current, maxOf(best, current), loggedToday)
    }

    fun xp(input: ProgressInput, streak: Streak): Int =
        input.entries * 5 + input.logDays.size * 15 + streak.best * 10 + input.goalsDone * 150 + (if (input.paydaySet) 25 else 0)

    fun level(xp: Int): Level {
        val index = levelFloors.indexOfLast { xp >= it }.coerceAtLeast(0)
        return Level(index + 1, xp, levelFloors[index], levelFloors.getOrNull(index + 1))
    }

    fun badges(input: ProgressInput, streak: Streak): Set<Badge> = buildSet {
        if (input.entries >= 1) add(Badge.FIRST_LOG)
        if (input.entries >= 50) add(Badge.LOGS_50)
        if (input.entries >= 250) add(Badge.LOGS_250)
        if (streak.best >= 3) add(Badge.STREAK_3)
        if (streak.best >= 7) add(Badge.STREAK_7)
        if (streak.best >= 30) add(Badge.STREAK_30)
        if (streak.best >= 100) add(Badge.STREAK_100)
        if (input.goals >= 1) add(Badge.FIRST_GOAL)
        if (input.goalsDone >= 1) add(Badge.GOAL_DONE)
        if (input.subscriptions >= 1) add(Badge.SUBSCRIPTIONS)
        if (input.gameyas >= 1) add(Badge.GAMEYA)
        if (input.paydaySet) add(Badge.PAYDAY)
    }

    fun snapshot(input: ProgressInput, today: LocalDate): ProgressSnapshot {
        val streak = streak(input.logDays, today)
        return ProgressSnapshot(streak, level(xp(input, streak)), badges(input, streak))
    }
}
