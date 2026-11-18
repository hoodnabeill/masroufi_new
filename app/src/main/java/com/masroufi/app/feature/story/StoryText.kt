package com.masroufi.app.feature.story

import android.content.Context
import androidx.annotation.StringRes
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.MonthStory
import com.masroufi.app.data.repo.StoryPayload
import com.masroufi.app.data.repo.StoryRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

enum class StorySlide { OPENING, GRID, MONEY, JAR, CLOSING }

/** Every amount in a masked share is drawn as this (11-DATA "Monthly story"). */
const val AMOUNT_DOTS = "•••"

/** A thin month (fewer than 7 torn days) is told in two slides, no comparisons. */
fun storySlides(p: StoryPayload): List<StorySlide> =
    if (p.thin) listOf(StorySlide.OPENING, StorySlide.JAR) else StorySlide.entries.toList()

/** The month a cycle is named after: the month of its middle day (a 25th-to-25th cycle is named for the month most of it falls in). */
fun storyMonthName(start: LocalDate, end: LocalDate, locale: Locale): String =
    start.plusDays(ChronoUnit.DAYS.between(start, end) / 2).month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

data class StoryRow(val label: String, val value: String)

/**
 * Everything one slide says, as text. The screen draws it with [bigValue] counting up; the share picture draws it as is.
 * With "hide" on every amount in it is [AMOUNT_DOTS] (counts, streaks and the month name stay).
 */
data class SlideText(
    val slide: StorySlide,
    val header: String,
    val kicker: String?,
    /** The big number's count-up target (pounds when [bigIsMoney]); null = [big] is text (the month name) or absent. */
    val bigValue: Long?,
    val bigIsMoney: Boolean,
    val big: String,
    val suffix: String?,
    /** The month's days, one char each (U under, O over, N never torn), with the legend and a spoken summary. */
    val grid: String?,
    val gridCd: String?,
    val legend: List<String>,
    val rows: List<StoryRow>,
    val line: String?,
    /** The line saying "best month" is on this slide (Milestone haptic). */
    val bestMonth: Boolean,
    /** Slide 5: the rows are the summary card. */
    val summary: Boolean,
)

fun slideText(context: Context, story: MonthStory, slide: StorySlide, hide: Boolean): SlideText {
    val p = story.payload
    val locale = context.resources.configuration.locales[0]
    fun money(piastres: Long) = if (hide) AMOUNT_DOTS else formatPounds(piastres)
    fun s(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)
    fun month(startDay: Long, endDay: Long) = storyMonthName(LocalDate.ofEpochDay(startDay), LocalDate.ofEpochDay(endDay), locale)
    val monthName = storyMonthName(story.cycleStart, story.cycleEnd, locale)
    val header = s(R.string.story_title_month, monthName)
    val blank = SlideText(slide, header, null, null, false, "", null, null, null, emptyList(), emptyList(), null, false, false)
    return when (slide) {
        StorySlide.OPENING -> blank.copy(
            kicker = s(R.string.story_open_kicker),
            big = monthName,
            line = p.bestDay?.let { s(R.string.story_open_best_day, LocalDate.ofEpochDay(it.epochDay).dayOfMonth.toString(), money(it.leftover)) }
                ?: s(R.string.story_open_plain),
        )
        StorySlide.GRID -> {
            val c = p.comparison
            val line = if (c == null) null else {
                val prevName = c.prevStart?.let { month(it, c.prevEnd ?: it) }
                val bestName = c.bestStart?.let { month(it, c.bestEnd ?: it) }
                val prevTorn = c.prevTorn
                when {
                    c.isBest -> (prevName ?: bestName)?.let { s(R.string.story_cmp_best, it, (prevTorn ?: c.bestTorn ?: 0).toString()) }
                    prevTorn != null && prevName != null ->
                        s(if (p.torn > prevTorn) R.string.story_cmp_more else R.string.story_cmp_other, prevName, prevTorn.toString())
                    else -> null
                }
            }
            blank.copy(
                kicker = s(R.string.story_grid_kicker),
                bigValue = p.torn.toLong(), big = p.torn.toString(), suffix = s(R.string.story_grid_suffix, p.cycleDays.toString()),
                grid = p.grid,
                gridCd = s(R.string.story_grid_cd, p.cycleDays.toString(), p.under.toString(), p.over.toString(), p.never.toString()),
                legend = listOf(
                    s(R.string.story_legend_under, p.under.toString()), s(R.string.story_legend_over, p.over.toString()),
                    s(R.string.story_legend_never, p.never.toString()),
                ),
                line = line, bestMonth = c?.isBest == true && line != null,
            )
        }
        StorySlide.MONEY -> blank.copy(
            kicker = s(R.string.story_money_kicker),
            bigValue = if (hide) null else Math.floorDiv(p.totalSpent, 100L), bigIsMoney = true, big = money(p.totalSpent),
            rows = p.topEnvelopes.map { StoryRow(it.name, money(it.spent)) },
            line = p.largestEntry?.let {
                if (it.category != null) s(R.string.story_money_line, money(it.amount), it.category) else s(R.string.story_money_line_plain, money(it.amount))
            } ?: s(R.string.story_money_none),
        )
        StorySlide.JAR -> blank.copy(
            kicker = s(R.string.story_jar_kicker),
            bigValue = if (hide) null else Math.floorDiv(p.jarMoved, 100L), bigIsMoney = true, big = money(p.jarMoved),
            rows = listOf(
                StoryRow(s(R.string.story_jar_cards), p.cardsWon.toString()),
                StoryRow(s(R.string.story_jar_streak), s(R.string.story_days, p.longestStreak.toString())),
            ),
        )
        StorySlide.CLOSING -> {
            val g = p.suggestion
            val line = when (g.rule) {
                StoryRule.ENVELOPE_RAN_OUT -> s(R.string.story_sug_ran_out, g.subject.orEmpty(), g.a.toString(), g.b.toString())
                StoryRule.HABIT_GREW -> s(R.string.story_sug_habit, g.subject.orEmpty(), money(g.a), money(g.b))
                StoryRule.WEEKDAY_OVER -> s(
                    R.string.story_sug_weekday, DayOfWeek.of(g.a.toInt()).getDisplayName(TextStyle.FULL, locale), g.b.toString(), g.c.toString(),
                )
                StoryRule.NEUTRAL -> s(R.string.story_sug_neutral)
            }
            blank.copy(
                kicker = s(R.string.story_next_kicker), line = line, summary = true,
                rows = listOf(
                    StoryRow(s(R.string.story_sum_tickets), s(R.string.story_sum_of, p.torn.toString(), p.cycleDays.toString())),
                    StoryRow(s(R.string.story_jar_kicker), money(p.jarMoved)),
                    StoryRow(s(R.string.story_jar_cards), p.cardsWon.toString()),
                    StoryRow(s(R.string.story_jar_streak), s(R.string.story_days, p.longestStreak.toString())),
                ),
            )
        }
    }
}
