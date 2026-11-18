package com.masroufi.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class WorkKind { SALARIED, FREELANCE, STUDENT, RUNS_HOUSE }

enum class MainGoal { LAST_THE_MONTH, SAVE, PAY_OFF, OCCASION }

/** Home's cards below the ticket, by the key the screen renders. PEOPLE and SEASONS have no Home card yet. */
enum class HomeCard { WEEK, JAR, CYCLE, PEOPLE, SEASONS }

/** Default order, then the main goal's card moves to the front (11-DATA "Profile"). */
fun homeCardOrder(goal: MainGoal?): List<HomeCard> {
    val first = when (goal) {
        MainGoal.LAST_THE_MONTH -> HomeCard.CYCLE
        MainGoal.SAVE -> HomeCard.JAR
        MainGoal.PAY_OFF -> HomeCard.PEOPLE
        MainGoal.OCCASION -> HomeCard.SEASONS
        null -> return HomeCard.values().toList()
    }
    return listOf(first) + HomeCard.values().toList().filter { it != first }
}

const val DEFAULT_BIRTHDAY_TARGET = 100_000L // 1,000 EGP
private const val BASE_HOUSEHOLD = 4

/** [perDay] piastres, rounded up so the target is met by the birthday. */
data class BirthdayPlan(val days: Int, val perDay: Long, val target: Long)

/** Days until the next birthday, today's own birthday counting as next year's (never 0). */
fun daysToBirthday(birthday: LocalDate, today: LocalDate): Int {
    var next = birthday.withYear(today.year)
    if (!next.isAfter(today)) next = birthday.withYear(today.year + 1)
    return ChronoUnit.DAYS.between(today, next).toInt()
}

fun birthdayPlan(birthday: LocalDate, today: LocalDate, target: Long?): BirthdayPlan {
    val t = target?.takeIf { it > 0 } ?: DEFAULT_BIRTHDAY_TARGET
    val days = daysToBirthday(birthday, today)
    return BirthdayPlan(days, (t + days - 1) / days, t)
}

/**
 * Household scaling of a suggestion (never the user's own amounts): [base] is for a household of four, scaled to
 * [size] and rounded to a whole pound; unknown size leaves it unchanged.
 */
fun scaleForHousehold(base: Long, size: Int?): Long =
    if (size == null || size <= 0) base else (base * size + BASE_HOUSEHOLD * 50) / (BASE_HOUSEHOLD * 100) * 100

/** The school-season suggestion counts the children in school: [perChild] times them; unknown leaves [perChild] as is. */
fun scaleForSchool(perChild: Long, inSchool: Int?): Long = if (inSchool == null || inSchool < 0) perChild else perChild * inSchool
