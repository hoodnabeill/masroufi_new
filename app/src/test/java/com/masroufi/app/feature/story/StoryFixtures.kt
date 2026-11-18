package com.masroufi.app.feature.story

import com.masroufi.app.data.db.MonthStoryEntity
import com.masroufi.app.data.repo.MonthStory
import com.masroufi.app.data.repo.StoryBestDay
import com.masroufi.app.data.repo.StoryComparison
import com.masroufi.app.data.repo.StoryEnvelope
import com.masroufi.app.data.repo.StoryLargest
import com.masroufi.app.data.repo.StoryPayload
import com.masroufi.app.data.repo.StoryRule
import com.masroufi.app.data.repo.StorySuggestion
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** October 2026 as a 31-day cycle; every amount is distinctive so a test can look for it in the text. */
val octStart: LocalDate = LocalDate.of(2026, 10, 1)
val novStart: LocalDate = LocalDate.of(2026, 11, 1)
val sepStart: LocalDate = LocalDate.of(2026, 9, 1)

/** The amounts as the screen shows them (pounds with a thousands comma). */
val storyAmounts = listOf("8,765", "12,345", "6,543", "2,500", "3,456", "3,210", "1,234")

fun fullPayload(
    isBest: Boolean = false,
    comparison: StoryComparison? = StoryComparison(sepStart.toEpochDay(), octStart.toEpochDay(), 19, sepStart.toEpochDay(), octStart.toEpochDay(), 19, isBest),
    suggestion: StorySuggestion = StorySuggestion(StoryRule.NEUTRAL),
) = StoryPayload(
    cycleDays = 31, torn = 26, under = 22, over = 4, never = 5,
    grid = "UUUOUUNUUOUUNNUUUUOUUUUNUUUOUUN", thin = false,
    bestDay = StoryBestDay(octStart.plusDays(13).toEpochDay(), 876_500),
    comparison = comparison,
    topEnvelopes = listOf(StoryEnvelope(1, "Food", 654_300), StoryEnvelope(2, "Transport", 300_000), StoryEnvelope(3, "Fun", 100_000)),
    totalSpent = 1_234_500, largestEntry = StoryLargest(250_000, octStart.plusDays(5).toEpochDay(), "Rent"),
    jarMoved = 345_600, cardsWon = 4, longestStreak = 9, suggestion = suggestion,
)

fun thinPayload() = StoryPayload(
    cycleDays = 31, torn = 3, under = 3, over = 0, never = 28, grid = "UUU" + "N".repeat(28), thin = true,
    jarMoved = 345_600, cardsWon = 1, longestStreak = 2,
)

fun story(payload: StoryPayload, id: Long = 1, start: LocalDate = octStart, end: LocalDate = novStart, shownAt: Long? = null) =
    MonthStory(id, start, end, shownAt, payload)

fun entity(payload: StoryPayload, start: LocalDate = octStart, end: LocalDate = novStart, shownAt: Long? = null) =
    MonthStoryEntity(
        cycleStart = start, cycleEnd = end, shownAt = shownAt,
        payload = Json { encodeDefaults = true }.encodeToString(StoryPayload.serializer(), payload),
    )

/** Every string a slide says. */
fun SlideText.allText(): List<String> =
    listOfNotNull(header, kicker, big, suffix, line) + legend + rows.flatMap { listOf(it.label, it.value) }
